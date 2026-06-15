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
 * Negamax + Alpha-Beta + TranspositionTable 搜索器。
 *
 * **升级历程**:
 * - ✅ commit 6:纯 Negamax
 * - ✅ commit 7:Alpha-Beta 剪枝 + MoveOrdering
 * - ✅ commit 9:接入 TranspositionTable(本 commit)
 * - ⏳ commit 10:叶节点用 QuiescenceSearch
 *
 * **视角约定**:所有分数都是"当前走子方视角",正数利于走子方。
 *
 * **TT 用法**:
 * - 入口先查 TT:`get(key)`,若 depth >= 当前且 flag 兼容则直接用 score
 * - 走法排序把 tt.bestMove 作为 ttMove 优先
 * - 退出前根据 fail-high/low/exact 写回 TT
 * - 杀棋分按 [TranspositionTable.storeMateScore] / [retrieveMateScore] 做 ply 视角转换
 */
class Search(
    private val gen: MoveGenerator,
    private val legality: MoveLegality,
    private val evaluation: Evaluation,
    private val checkDetector: CheckDetector,
    private val moveOrdering: MoveOrdering,
    private val tt: TranspositionTable,
) {

    /** 搜索期间累计访问的节点数(含叶节点)。测试可重置。 */
    var nodes: Long = 0
        internal set

    /**
     * 在根节点搜索 [maxDepth] 层,返回最佳走法 + 当前走子方视角分数。
     *
     * 调用方负责在合适时机 [TranspositionTable.clear](例如新一次完整 search 前)。
     * 迭代加深期间不清 TT,以便下一深度复用上一深度的结果。
     */
    fun searchRoot(board: Board, sideToMove: Side, maxDepth: Int): Pair<Move?, Int> {
        nodes = 0
        var alpha = Int.MIN_VALUE + 1
        val beta = Int.MAX_VALUE
        var bestMove: Move? = null
        val moves = legality.legalMoves(board, gen.movesFor(board, sideToMove))
        if (moves.isEmpty()) return null to terminalScore(sideToMove, ply = 0)

        val key = tt.hash(board, sideToMove)
        val ttMove = tt.get(key)?.bestMove
        val ordered = moveOrdering.sort(board, moves, sideToMove, ttMove)
        var flag = TranspositionTable.Flag.UPPER_BOUND
        for (mv in ordered) {
            val after = board.applyMove(mv)
            val score = -negamax(after, sideToMove.opponent, maxDepth - 1, -beta, -alpha, ply = 1)
            if (score > alpha) {
                alpha = score
                bestMove = mv
                flag = TranspositionTable.Flag.EXACT
            }
        }
        // 根节点的 TT 写入(供下次迭代加深复用)
        tt.put(key, maxDepth, flag, TranspositionTable.storeMateScore(alpha, 0), bestMove)
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
        val key = tt.hash(board, sideToMove)

        // ---- TT probe ----
        val entry = tt.get(key)
        val ttMove = entry?.bestMove
        if (entry != null && entry.depth >= depth) {
            val stored = TranspositionTable.retrieveMateScore(entry.score, ply)
            when (entry.flag) {
                TranspositionTable.Flag.EXACT -> return stored
                TranspositionTable.Flag.LOWER_BOUND -> if (stored >= beta) return stored
                TranspositionTable.Flag.UPPER_BOUND -> if (stored <= alpha) return stored
            }
        }

        val moves = legality.legalMoves(board, gen.movesFor(board, sideToMove))
        if (moves.isEmpty()) return terminalScore(sideToMove, ply)
        if (depth <= 0) return evaluation.evaluate(board, sideToMove)

        val ordered = moveOrdering.sort(board, moves, sideToMove, ttMove)
        var bestMove: Move? = null
        var bestScore = Int.MIN_VALUE
        var flag = TranspositionTable.Flag.UPPER_BOUND
        for (mv in ordered) {
            val after = board.applyMove(mv)
            val score = -negamax(after, sideToMove.opponent, depth - 1, -beta, -localAlpha, ply + 1)
            if (score > bestScore) {
                bestScore = score
                bestMove = mv
            }
            if (score >= beta) {
                flag = TranspositionTable.Flag.LOWER_BOUND
                tt.put(key, depth, flag, TranspositionTable.storeMateScore(score, ply), bestMove)
                return score   // beta cutoff
            }
            if (score > localAlpha) {
                localAlpha = score
                flag = TranspositionTable.Flag.EXACT
            }
        }
        tt.put(key, depth, flag, TranspositionTable.storeMateScore(bestScore, ply), bestMove)
        return bestScore
    }

    /** 当前走子方无合法走法时的分数:被将/困毙都判输。 */
    private fun terminalScore(sideToMove: Side, ply: Int): Int {
        return -Score.mateScore(ply)
    }
}
