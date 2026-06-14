package com.xiangqi.app.domain.rules

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator

/**
 * 将军 / 飞将检测器。
 *
 * - [isInCheck]:判断 [side] 方的将/帅是否处于被对方任一棋子攻击的状态,
 *   或双方将帅形成"飞将"(在中国象棋规则中,飞将使任一方将帅都"被攻击")。
 *
 * 这是 M1-d "走法合法性"判定的基础——一个走法合法,当且仅当走完后己方不被
 * isInCheck。
 *
 * 实现用 [MoveGenerator.attacks] 反查将位——遍历对方所有棋子位置,
 * 对每个调用 [MoveGenerator.attacks] 判断是否攻击 [kingPos]。比"遍历对方所有
 * 走法查 move.to == kingPos"省去 List 分配,适合 M2 引擎搜索时高频调用。
 *
 * TODO(M2): 当前仍 O(N),N 为对方棋子数。深度搜索瓶颈在 isInCheck 调用次数,
 * 进一步优化可改为"对将位 8 个方向反向扫描",把 N 降为常数。M2 启动时重测决定。
 */
class CheckDetector(private val gen: MoveGenerator) {

    /** [side] 方将帅是否被对方攻击或形成飞将。无帅返回 false。 */
    fun isInCheck(board: Board, side: Side): Boolean {
        if (areKingsFacing(board)) return true
        val kingPos = board.kingPosition(side) ?: return false
        val attacker = side.opponent
        for ((pos, piece) in board) {
            if (piece != null && piece.side == attacker && gen.attacks(board, pos, kingPos)) {
                return true
            }
        }
        return false
    }

    /** 双方将帅是否同列且中间无棋子("飞将"状态)。 */
    fun areKingsFacing(board: Board): Boolean {
        val redKing = board.find(PieceType.KING, Side.RED).firstOrNull() ?: return false
        val blackKing = board.find(PieceType.KING, Side.BLACK).firstOrNull() ?: return false
        if (redKing.col != blackKing.col) return false
        val lo = minOf(redKing.row, blackKing.row)
        val hi = maxOf(redKing.row, blackKing.row)
        for (r in (lo + 1) until hi) {
            if (board[redKing.col, r] != null) return false
        }
        return true
    }
}
