package com.xiangqi.app.domain.rules

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator

/**
 * 走法合法性判定。
 *
 * 给定伪合法走法 [move],判断走完后己方是否安全(不被将、不形成飞将)。
 * 中国象棋规则下,"走完一步使己方将帅被攻击"或"形成飞将"均非法。
 *
 * 使用方式:走法生成器产出伪合法走法 -> 调用 [isLegal] 过滤。
 */
class MoveLegality(
    private val gen: MoveGenerator,
    private val checkDetector: CheckDetector = CheckDetector(gen),
) {
    /** 判断 [move] 走完后,走子方是否安全。 */
    fun isLegal(board: Board, move: Move): Boolean {
        val after = board.applyMove(move)
        return !checkDetector.isInCheck(after, move.side)
    }

    /** 过滤伪合法走法,返回真正合法的子集。 */
    fun legalMoves(board: Board, moves: List<Move>): List<Move> =
        moves.filter { isLegal(board, it) }

    /** [side] 方是否还有任何合法走法。 */
    fun hasAnyLegalMove(board: Board, side: Side): Boolean {
        for (move in gen.movesFor(board, side)) {
            if (isLegal(board, move)) return true
        }
        return false
    }
}
