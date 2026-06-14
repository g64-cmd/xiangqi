package com.xiangqi.app.domain.fen

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/**
 * 中国象棋 FEN 局面。
 *
 * 标准字段:
 * - [board]:棋盘。
 * - [sideToMove]:轮到谁走,RED 表示红方(对应 FEN 中的 'w',因红方为 white-side)。
 * - [halfMoveClock]:半回合数(无吃子计数)。
 * - [fullMoveNumber]:完整回合数。
 *
 * 中国象棋 FEN 的"王车易位 / 吃过兵"字段以 `-` 占位,本项目不解析也不输出。
 *
 * 棋盘段约定:FEN 字符串中第一段(以 `/` 分隔)对应棋盘的 row=9(黑方底线),
 * 最后一段对应 row=0(红方底线);每段内从左到右对应 col 0..8。
 *
 * @property board 棋盘。
 * @property sideToMove 轮到谁走。
 * @property halfMoveClock 半回合数。
 * @property fullMoveNumber 完整回合数。
 */
data class FenPosition(
    val board: Board,
    val sideToMove: Side,
    val halfMoveClock: Int = 0,
    val fullMoveNumber: Int = 1,
) {
    /** 序列化为标准 FEN 字符串。 */
    fun toFen(): String {
        val boardField = buildString {
            for (row in Position.ROW_MAX downTo 0) {
                var empties = 0
                for (col in 0..Position.COL_MAX) {
                    val piece = board[col, row]
                    if (piece == null) {
                        empties++
                    } else {
                        if (empties > 0) {
                            append(empties)
                            empties = 0
                        }
                        append(piece.fenChar())
                    }
                }
                if (empties > 0) append(empties)
                if (row > 0) append('/')
            }
        }
        val sideChar = if (sideToMove == Side.RED) 'w' else 'b'
        return "$boardField $sideChar - - $halfMoveClock $fullMoveNumber"
    }
}
