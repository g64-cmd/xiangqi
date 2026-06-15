package com.xiangqi.app.engine.self

import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Score

/**
 * 纯 Negamax 搜索器(M2 commit 6 最小版本,无 AB / TT / Q)。
 *
 * 后续 commit 会逐步升级:
 * - commit 7:加入 Alpha-Beta 剪枝 + MoveOrdering
 * - commit 9:接入 TranspositionTable
 * - commit 10:叶节点用 QuiescenceSearch
 *
 * **视角约定**:所有分数都是"当前走子方视角",正数利于走子方。
 * 子节点分数取负后回传,实现 Negamax。
 *
 * **终局判定**:`hasAnyLegalMove == false` 时:
 * - 被将 = 将死
 * - 未被将 = 困毙(中国象棋判负)
 * 二者对当前走子方均为输,返回 `-mateScore(ply)`(越早杀分越负)。
 *
 * **超时**:本版本暂不做(由 SelfEngine 在 commit 11 串接协程取消 + deadline)。
 */
class Search(
    private val gen: MoveGenerator,
    private val legality: MoveLegality,
    private val evaluation: Evaluation,
) {

    /** 搜索期间累计访问的节点数(含叶节点)。 */
    var nodes: Long = 0
        private set

    /** 在根节点搜索 [maxDepth] 层,返回最佳走法 + 当前走子方视角分数。 */
    fun searchRoot(board: Board, sideToMove: Side, maxDepth: Int): Pair<Move?, Int> {
        nodes = 0
        var bestMove: Move? = null
        var bestScore = Int.MIN_VALUE
        val moves = legality.legalMoves(board, gen.movesFor(board, sideToMove))
        if (moves.isEmpty()) return null to terminalScore(board, sideToMove, ply = 0)

        for (mv in moves) {
            val after = board.applyMove(mv)
            val score = -negamax(after, sideToMove.opponent, maxDepth - 1, ply = 1)
            if (score > bestScore) {
                bestScore = score
                bestMove = mv
            }
        }
        return bestMove to bestScore
    }

    /** 返回当前走子方视角分数。 */
    internal fun negamax(board: Board, sideToMove: Side, depth: Int, ply: Int): Int {
        nodes++
        val moves = legality.legalMoves(board, gen.movesFor(board, sideToMove))
        if (moves.isEmpty()) return terminalScore(board, sideToMove, ply)
        if (depth <= 0) return evaluation.evaluate(board, sideToMove)

        var best = Int.MIN_VALUE
        for (mv in moves) {
            val after = board.applyMove(mv)
            val score = -negamax(after, sideToMove.opponent, depth - 1, ply + 1)
            if (score > best) best = score
        }
        return best
    }

    /**
     * 当前走子方无合法走法时的分数:
     * - 被将/困毙都判当前走子方输,返回 -mateScore(ply)(越早杀越负)。
     */
    private fun terminalScore(board: Board, sideToMove: Side, ply: Int): Int {
        // 这里不区分将死/困毙,因为中国象棋两者对当前走子方都是输。
        return -Score.mateScore(ply)
    }
}
