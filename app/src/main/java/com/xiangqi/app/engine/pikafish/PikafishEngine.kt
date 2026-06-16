package com.xiangqi.app.engine.pikafish

import com.xiangqi.app.domain.fen.FenPosition
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.AnalysisScore
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.Engine
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
            throw IllegalStateException("pikafish 未返回 bestmove")
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
        val install = installer.install()
        val proc = PikafishProcess(install.executable, install.workingDir)
        val s = UciSession(proc)
        s.send("uci")
        s.send("isready")
        session = s
        return s
    }

    /**
     * 局势评估覆盖实现:发送皮卡鱼独有的 `eval` 命令,拿 NNUE 静态评估。
     * 瞬时返回(无搜索);不输出 mate,所以 [AnalysisScore.isMate] 永远 false。
     *
     * 失败(子进程异常 / parse 失败 / 超时)时返回 0 cp,避免调用方崩溃。
     */
    override suspend fun analyze(board: Board, sideToMove: Side): AnalysisScore =
        withContext(Dispatchers.IO) {
            val s = try {
                ensureSession()
            } catch (_: Throwable) {
                return@withContext AnalysisScore(0f, false, null)
            }
            val fen = FenPosition(board, sideToMove).toFen()
            try {
                s.send("position fen $fen")
                s.send("eval")
                val line = s.waitFor("Final evaluation", timeoutMs = 2000L)
                val parsed = line?.let { parseEvalLine(it) }
                parsed ?: AnalysisScore(0f, false, null)
            } catch (_: Throwable) {
                AnalysisScore(0f, false, null)
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

        /**
         * 解析一行 `Final evaluation\s+([+-]?\d+\.\d+)\s+\((white|black) side\)...`。
         *
         * 皮卡鱼 eval 命令的输出格式参考 chinese-chess-fish-android:
         * `Final evaluation       +0.23 (white side) [with scaled NNUE, ...]`
         *
         * 分数解读:float × 100 = centipawn;`(white side)` 时 white=红方,直接返回;
         * `(black side)` 时取负(等价于"红方视角")。但 [parseEvalLine] 本身只返回
         * sideToMove 视角(保持与 [analyze] 契约一致),不做 POV 规范化——上层 GameViewModel
         * 负责根据 sideToMove 决定是否取负。
         *
         * 实际上 eval 输出永远从皮卡鱼内部 white 视角给出,与传给 `position fen` 的
         * sideToMove 无关;这里通过 line 末尾 `(white|black) side` 标签判断 sign。
         *
         * 返回 null 表示无法解析。
         */
        internal fun parseEvalLine(line: String): AnalysisScore? {
            val regex = Regex(
                """Final evaluation\s+([+-]?\d+\.\d+)\s+\((white|black)\s+side\)""",
            )
            val m = regex.find(line) ?: return null
            val floatScore = m.groupValues[1].toFloatOrNull() ?: return null
            val sideLabel = m.groupValues[2]
            val cp = floatScore * 100f
            return AnalysisScore(
                // eval 永远是 white(=红方)视角;black side 标签时翻转,使其变成 sideToMove 视角
                // —— 但因为调用方会按 sideToMove 决定 POV,我们这里返回 white-POV cp。
                // 即:white side -> 正;black side -> 负。
                scoreCp = if (sideLabel == "white") cp else -cp,
                isMate = false,
                matePlies = null,
            )
        }
    }
}
