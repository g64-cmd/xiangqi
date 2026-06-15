package com.xiangqi.app.engine.self

import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.MoveLegality

/**
 * 静态搜索(Quiescence Search):在 `depth == 0` 时不再返回 raw 估值,
 * 而是继续扩展所有**吃子走法**,直到局面"安静"(没有可吃的子)。
 *
 * 解决**地平线效应**:防止搜索停在"刚好下一步要吃高价值子"的位置,
 * 给出过于乐观/悲观的估值。
 *
 * **M2 范围**:只扩展吃子走法(不扩展应将);最大 6 ply 防止失控。
 *
 * **视角**:与 negamax 一致——所有分数是"当前走子方视角",
 * 子节点取负回传,实现 Negamax 形式的 QSearch。
 */
class QuiescenceSearch(
    private val gen: MoveGenerator,
    private val legality: MoveLegality,
    private val evaluation: Evaluation,
    private val moveOrdering: MoveOrdering,
) {

    /** 进入静态搜索后,最多递归多少层(防止连续吃子无限展开)。 */
    private val maxPly = 6

    fun qSearch(board: Board, sideToMove: Side, alpha: Int, beta: Int, ply: Int): Int {
        val standPat = evaluation.evaluate(board, sideToMove)
        if (standPat >= beta || ply >= maxPly) return standPat
        var localAlpha = alpha
        if (standPat > localAlpha) localAlpha = standPat

        // 只展开吃子走法:伪合法里 board[to] != null 的子集,再过滤合法
        val captures = gen.movesFor(board, sideToMove)
            .filter { board[it.to] != null && legality.isLegal(board, it) }
        if (captures.isEmpty()) return localAlpha

        val ordered = moveOrdering.sort(board, captures, sideToMove, ttMove = null)
        for (mv in ordered) {
            val after = board.applyMove(mv)
            val score = -qSearch(after, sideToMove.opponent, -beta, -localAlpha, ply + 1)
            if (score >= beta) return score
            if (score > localAlpha) localAlpha = score
        }
        return localAlpha
    }
}
