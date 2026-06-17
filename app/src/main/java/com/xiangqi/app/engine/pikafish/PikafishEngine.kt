package com.xiangqi.app.engine.pikafish

import com.xiangqi.app.domain.fen.FenPosition
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.Engine
import com.xiangqi.app.engine.EngineUnavailableException
import com.xiangqi.app.engine.EngineResult
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.engine.Score
import com.xiangqi.app.engine.SearchInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 皮卡鱼 UCI 引擎实现。
 *
 * 子进程贯穿多次 [search] 复用;每次 [search] 发送 `ucinewgame` + `setoption Skill Level`
 * + `position fen` + `go movetime`。stdout 行通过 [UciSession.consume] 消费,info 行
 * 解析后推送到 [info] StateFlow,bestmove 行结束循环。
 *
 * **取消**:协程取消时 [UciSession.close] 销毁进程,下次 [search] 重启。
 *
 * **UCI score 语义**:pikafish 的 `score cp X` 是当前走子方视角的 centipawn;
 * `score mate N` 表示 N 个半回合内将死,映射到 [Score.mateScore]。
 */
@Singleton
class PikafishEngine @Inject constructor(
    private val installer: PikafishInstaller,
) : Engine {

    override val type: EngineType = EngineType.PIKAFISH

    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    private var session: UciSession? = null

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult = withContext(Dispatchers.IO) {
        _info.value = null
        val s = ensureSession()
        val skill = pikafishSkill(difficulty)
        val movetime = difficulty.moveTimeMs
        val fen = FenPosition(board, sideToMove).toFen()

        s.send("ucinewgame")
        s.send("setoption name Skill Level value $skill")
        s.send("setoption name Threads value 1")
        s.send("position fen $fen")
        val startMs = System.currentTimeMillis()
        s.send("go movetime $movetime")

        var bestMove: Move? = null
        var depth = 0
        var score = 0
        var nodes = 0L
        try {
            s.consume { line ->
                when {
                    line.startsWith("info ") -> {
                        val parsed = parseInfo(line, sideToMove)
                        if (parsed != null) {
                            depth = parsed.depth
                            score = parsed.score
                            nodes = parsed.nodes
                            _info.value = parsed
                        }
                    }
                    line.startsWith("bestmove ") -> {
                        val tokens = line.split(' ')
                        if (tokens.size >= 2 && tokens[1].length == 4) {
                            bestMove = Move.fromUci(tokens[1], sideToMove)
                        }
                        return@consume true
                    }
                }
                ensureActive()
                false
            }
        } catch (e: CancellationException) {
            // 调用方 cancel,关闭会话让下次重启
            closeSession()
            throw e
        }

        val finalMove = bestMove ?: run {
            closeSession()
            // 子进程崩溃 / 输出格式异常 / 被 SELinux 拦下都会走到这里。
            // 包装成 EngineUnavailableException,让 ViewModel 降级为 toast,
            // 而不是 IllegalStateException 逃逸到 CoroutineExceptionHandler 闪退。
            throw EngineUnavailableException("pikafish 未返回 bestmove(进程可能已崩溃)")
        }
        val elapsed = System.currentTimeMillis() - startMs
        val matePlies = Score.mateInPlies(score)
        EngineResult(
            bestMove = finalMove,
            score = score,
            depth = depth,
            pv = listOf(finalMove),
            nodesSearched = nodes,
            timeMs = elapsed,
            isMate = matePlies != null,
            mateInPlies = matePlies,
        )
    }

    private fun ensureSession(): UciSession {
        session?.let { if (it.isAlive) return it }
        try {
            val install = installer.install()
            installer.verifyExecutable()
            val proc = PikafishProcess(install.executablePath, install.workingDir)
            val s = UciSession(proc)
            s.send("uci")
            s.send("isready")
            // NNUE 权重显式传绝对路径,避免依赖 cwd 解析
            s.send("setoption name EvalFile value ${install.nnueFile.absolutePath}")
            session = s
            return s
        } catch (e: IllegalStateException) {
            // verifyExecutable 失败(.so 缺失 / SHA 不匹配)
            throw EngineUnavailableException("pikafish 可执行文件不可用", e)
        } catch (e: IOException) {
            // ProcessBuilder.start() 失败(SELinux 拒绝 / fork 失败 / 文件不可执行)
            throw EngineUnavailableException("pikafish 进程启动失败", e)
        }
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private fun pikafishSkill(d: Difficulty): Int = when (d) {
        Difficulty.BEGINNER -> 0
        Difficulty.ELEMENTARY -> 5
        Difficulty.INTERMEDIATE -> 12
        Difficulty.ADVANCED -> 20
        Difficulty.HINT -> 10
        Difficulty.ANALYZE -> 20
    }

    companion object {

        /**
         * 解析一行 `info depth N ... score cp|mate X ... nodes N ... time T ... pv ...`。
         *
         * 返回 null 表示该行没有 depth/score 字段(如 `info string ...` 调试行)。
         */
        internal fun parseInfo(line: String, sideToMove: Side): SearchInfo? {
            val tokens = line.split(' ')
            var depth = 0
            var score: Int? = null
            var nodes = 0L
            var timeMs = 0L
            var i = 1
            while (i < tokens.size - 1) {
                when (tokens[i]) {
                    "depth" -> {
                        depth = tokens[i + 1].toIntOrNull() ?: 0
                        i += 2
                    }
                    "score" -> {
                        when (tokens[i + 1]) {
                            "cp" -> {
                                score = tokens[i + 2].toIntOrNull() ?: 0
                                i += 3
                            }
                            "mate" -> {
                                val plies = tokens[i + 2].toIntOrNull() ?: 0
                                score = Score.mateScore(plies)
                                i += 3
                            }
                            else -> i += 1
                        }
                    }
                    "nodes" -> {
                        nodes = tokens[i + 1].toLongOrNull() ?: 0L
                        i += 2
                    }
                    "time" -> {
                        timeMs = tokens[i + 1].toLongOrNull() ?: 0L
                        i += 2
                    }
                    else -> i += 1
                }
            }
            if (score == null) return null
            return SearchInfo(
                depth = depth,
                score = score,
                pv = emptyList(),
                nodes = nodes,
                timeMs = timeMs,
            )
        }
    }
}
