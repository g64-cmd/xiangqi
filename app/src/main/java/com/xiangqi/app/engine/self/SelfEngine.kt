package com.xiangqi.app.engine.self

import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.Engine
import com.xiangqi.app.engine.EngineResult
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.engine.Score
import com.xiangqi.app.engine.SearchInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext

/**
 * 自研引擎实现。Negamax + Alpha-Beta + TT + Quiescence + 迭代加深。
 *
 * **迭代加深**:从 depth=1 开始,逐层加深。每次完成后更新 [info] StateFlow,
 * 让 UI 能实时看到"思考进展"。
 *
 * **超时机制**:
 * - 协程取消:`negamax` 每 1024 节点调一次 [Search.checkCancel],内含 `ensureActive()`
 *   抛 CancellationException。
 * - movetime:同样在 [Search.checkCancel] 里检查 `System.currentTimeMillis() > deadline`,
 *   到时抛 [SelfEngine.SearchTimeoutException]。
 *
 * **超时回退**:任意一次迭代被中断,返回**上一次完整深度**的结果;若第一次都没完成,
 * 透传异常。
 */
class SelfEngine(
    private val gen: MoveGenerator,
    private val legality: MoveLegality,
    private val evaluation: Evaluation,
    private val checkDetector: CheckDetector,
    private val checkmate: CheckmateDetector,
    private val moveOrdering: MoveOrdering,
    private val transpositionTable: TranspositionTable = TranspositionTable(1 shl 18),
) : Engine {

    override val type: EngineType = EngineType.SELF

    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        _info.value = null
        transpositionTable.clear()

        val deadlineMs = System.currentTimeMillis() + difficulty.moveTimeMs
        val context = coroutineContext
        val checkCancel: () -> Unit = {
            val job = context[Job]
            if (job?.isActive == false) throw CancellationException("SelfEngine 被取消")
            if (System.currentTimeMillis() > deadlineMs) {
                throw SearchTimeoutException
            }
        }

        val search = buildSearch(checkCancel)
        val startTime = System.currentTimeMillis()

        var lastComplete: IterationResult? = null
        try {
            for (depth in 1..difficulty.depth) {
                val (bestMove, score) = search.searchRoot(board, sideToMove, depth)
                if (bestMove != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val info = SearchInfo(
                        depth = depth,
                        score = score,
                        pv = listOf(bestMove),
                        nodes = search.nodes,
                        timeMs = elapsed,
                    )
                    _info.value = info
                    lastComplete = IterationResult(bestMove, score, depth, search.nodes, elapsed)
                    if (Math.abs(score) > Score.MATE_THRESHOLD) break
                }
            }
        } catch (e: CancellationException) {
            if (lastComplete == null) throw e
            // 否则使用上一次完整深度的结果
        } catch (e: SearchTimeoutException) {
            if (lastComplete == null) {
                // 第一次迭代就超时:极少见,抛出未完成异常
                throw IllegalStateException("SelfEngine 第一次迭代即超时,movetime=${difficulty.moveTimeMs}ms 过短")
            }
        }

        val final = lastComplete!!
        val isMate = Math.abs(final.score) > Score.MATE_THRESHOLD
        return EngineResult(
            bestMove = final.bestMove,
            score = final.score,
            depth = final.depth,
            pv = listOf(final.bestMove),
            nodesSearched = final.nodes,
            timeMs = final.elapsedMs,
            isMate = isMate,
            mateInPlies = Score.mateInPlies(final.score),
        )
    }

    private fun buildSearch(checkCancel: () -> Unit): Search {
        val quiescence = QuiescenceSearch(gen, legality, evaluation, moveOrdering)
        return Search(
            gen = gen,
            legality = legality,
            evaluation = evaluation,
            checkDetector = checkDetector,
            moveOrdering = moveOrdering,
            tt = transpositionTable,
            quiescence = quiescence,
            checkCancel = checkCancel,
        )
    }

    private data class IterationResult(
        val bestMove: com.xiangqi.app.domain.model.Move,
        val score: Int,
        val depth: Int,
        val nodes: Long,
        val elapsedMs: Long,
    )

    /** 内部超时信号,与协程取消区分。 */
    internal object SearchTimeoutException : RuntimeException()
}
