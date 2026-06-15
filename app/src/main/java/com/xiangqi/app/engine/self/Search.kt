package com.xiangqi.app.engine.self

import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Score

/**
 * Negamax + Alpha-Beta 搜索器。
 *
 * **升级历程**(本 commit 已完成第 2 阶段):
 * - ✅ commit 6:纯 Negamax
 * - ✅ commit 7:Alpha-Beta 剪枝 + MoveOrdering(MVV-LVA + TT move + check bonus)
 * - ⏳ commit 9:接入 TranspositionTable(本 commit 已预留 [tt] 参数,暂不用)
 * - ⏳ commit 10:叶节点用 QuiescenceSearch
 *
 * **视角约定**:所有分数都是"当前走子方视角",正数利于走子方。
 * 子节点分数取负后回传,实现 Negamax。
 *
 * **终局判定**:`hasAnyLegalMove == false` 时:
 * - 被将 = 将死
 * - 未被将 = 困毙(中国象棋判负)
 * 二者对当前走子方均为输,返回 `-mateScore(ply)`。
 *
 * **超时**:本版本暂不做(由 SelfEngine 在 commit 11 串接协程取消 + deadline)。
 */
class Search(
    private val gen: MoveGenerator,
    private val legality: MoveLegality,
    private val evaluation: Evaluation,
    private val checkDetector: CheckDetector,
    private val moveOrdering: MoveOrdering,
    @Suppress("UNUSED_PARAMETER") tt: TranspositionTable,
) {

    /** 搜索期间累计访问的节点数(含叶节点)。 */
    var nodes: Long = 0
        private set

    /** 在根节点搜索 [maxDepth] 层,返回最佳走法 + 当前走子方视角分数。 */
    fun searchRoot(board: Board, sideToMove: Side, maxDepth: Int): Pair<Move?, Int> {
        nodes = 0
        var alpha = Int.MIN_VALUE + 1
        val beta = Int.MAX_VALUE
        var bestMove: Move? = null
        val moves = legality.legalMoves(board, gen.movesFor(board, sideToMove))
        if (moves.isEmpty()) return null to terminalScore(sideToMove, ply = 0)

        val ordered = moveOrdering.sort(board, moves, sideToMove, ttMove = null)
        for (mv in ordered) {
            val after = board.applyMove(mv)
            val score = -negamax(after, sideToMove.opponent, maxDepth - 1, -beta, -alpha, ply = 1)
            if (score > alpha) {
                alpha = score
                bestMove = mv
            }
        }
        return bestMove to alpha
    }

    /** 返回当前走子方视角分数(在 [alpha, beta] 窗口内)。 */
    internal fun negamax(
        board: Board,
        sideToMove: Side,
        depth: Int,
        alpha: Int,
        beta: Int,
        ply: Int,
    ): Int {
        nodes++
        var localAlpha = alpha
        val moves = legality.legalMoves(board, gen.movesFor(board, sideToMove))
        if (moves.isEmpty()) return terminalScore(sideToMove, ply)
        if (depth <= 0) return evaluation.evaluate(board, sideToMove)

        val ordered = moveOrdering.sort(board, moves, sideToMove, ttMove = null)
        for (mv in ordered) {
            val after = board.applyMove(mv)
            val score = -negamax(after, sideToMove.opponent, depth - 1, -beta, -localAlpha, ply + 1)
            if (score >= beta) return beta   // beta cutoff
            if (score > localAlpha) localAlpha = score
        }
        return localAlpha
    }

    /** 当前走子方无合法走法时的分数:被将/困毙都判输。 */
    private fun terminalScore(sideToMove: Side, ply: Int): Int {
        return -Score.mateScore(ply)
    }
}
