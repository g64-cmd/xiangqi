package com.xiangqi.app.domain.rules

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator

/**
 * 将死 / 困毙判定。
 *
 * - 将死(checkmate):[side] 方处于被将状态,且无任何合法走法。
 * - 困毙(stalemate):[side] 方未被将,但无任何合法走法。中国象棋规则下
 *   困毙方判负(与国际象棋判和不同)。
 *
 * [GameResult] 的判定汇总在 [decide]。
 */
class CheckmateDetector(
    private val gen: MoveGenerator,
    private val checkDetector: CheckDetector = CheckDetector(gen),
    private val legality: MoveLegality = MoveLegality(gen, checkDetector),
) {
    /** [side] 方是否被将死。 */
    fun isCheckmate(board: Board, side: Side): Boolean {
        if (!checkDetector.isInCheck(board, side)) return false
        return !legality.hasAnyLegalMove(board, side)
    }

    /** [side] 方是否被困毙(无子可动但未被将)。中国象棋判负。 */
    fun isStalemate(board: Board, side: Side): Boolean {
        if (checkDetector.isInCheck(board, side)) return false
        return !legality.hasAnyLegalMove(board, side)
    }

    /**
     * 当前轮走方 [sideToMove] 的对局结果。
     * - 被将死 → 对方胜。
     * - 被困毙 → 对方胜(中国象棋规则)。
     * - 否则 → ONGOING。
     *
     * 注:重复局面 / 长将循环 / 协议和棋等判定不在本类职责内,由 GameRepository 跟踪。
     */
    fun decide(board: Board, sideToMove: Side): GameResult {
        if (isCheckmate(board, sideToMove) || isStalemate(board, sideToMove)) {
            return if (sideToMove == Side.RED) GameResult.BlackWin else GameResult.RedWin
        }
        return GameResult.ONGOING
    }
}
