package com.xiangqi.app.engine.self

import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.CheckDetector

/**
 * 走法排序:把更可能引发剪枝的走法排前面,显著减少 AB 搜索节点数。
 *
 * M2 启发式优先级(分数从高到低):
 * 1. **TT 着法**(+10_000):上一轮迭代加深的最佳走法,通常仍是好选择。
 * 2. **吃子走法(MVV-LVA)**:`8 * victimValue - attackerValue`,
 *    吃高价值子用低价值攻击子者分高(典型范围 0~720)。
 * 3. **将军走法**(+50):走完后对方被将,迫使应将,搜索树更窄。
 * 4. **其他**(0)。
 *
 * M2 不实现 killer / history heuristic,留给 M3 视性能需要再加。
 */
class MoveOrdering(
    private val gen: MoveGenerator,
    private val checkDetector: CheckDetector,
) {

    /** 返回按分数降序排好的 [moves](原列表不被修改)。 */
    fun sort(board: Board, moves: List<Move>, sideToMove: Side, ttMove: Move?): List<Move> {
        if (moves.isEmpty()) return moves
        val scores = IntArray(moves.size) { i ->
            score(board, moves[i], sideToMove, ttMove)
        }
        // stable sort:同分时保持原序(对确定性有帮助)
        val indices = (0 until moves.size).toMutableList()
        indices.sortByDescending { scores[it] }
        return indices.map { moves[it] }
    }

    private fun score(board: Board, move: Move, sideToMove: Side, ttMove: Move?): Int {
        if (ttMove != null && move == ttMove) return TT_MOVE_BONUS

        val victim = board[move.to]
        val capture = if (victim != null) {
            val v = Evaluation.PIECE_VALUE[victim.type] ?: 0
            val a = Evaluation.PIECE_VALUE[board[move.from]!!.type] ?: 0
            // 基础分保证吃子走法总是 > 非吃子;MVV-LVA 微调叠在上面
            CAPTURE_BASE + MVG_LVA_MULTIPLIER * v - a
        } else 0

        val givesCheck = if (capture == 0) {
            val after = board.applyMove(move)
            if (checkDetector.isInCheck(after, sideToMove.opponent)) CHECK_BONUS else 0
        } else 0

        return capture + givesCheck
    }

    companion object {
        private const val TT_MOVE_BONUS = 10_000
        private const val CAPTURE_BASE = 1_000
        private const val CHECK_BONUS = 50
        private const val MVG_LVA_MULTIPLIER = 8
    }
}
