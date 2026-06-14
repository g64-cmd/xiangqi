package com.xiangqi.app.domain.fen

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/**
 * 中国象棋 FEN 解析器。
 *
 * 标准开局:
 * ```
 * rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1
 * ```
 *
 * 字段(空格分隔):棋盘 / 轮走方 / 易位(忽略) / 吃过兵(忽略) / 半回合 / 完整回合。
 * 后 4 个字段缺失时使用默认值。
 */
object FenParser {

    /** 中国象棋标准开局 FEN。 */
    const val INITIAL_FEN =
        "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

    /**
     * 解析 FEN。非法格式抛 [IllegalArgumentException],错误信息含具体出错的字段。
     */
    fun parse(fen: String): FenPosition {
        val trimmed = fen.trim()
        require(trimmed.isNotEmpty()) { "FEN 不能为空" }

        val parts = trimmed.split(Regex("\\s+"))
        require(parts.size in 1..6) { "FEN 字段数应在 1..6: '$fen'" }

        val board = parseBoard(parts[0])
        val sideToMove = if (parts.size >= 2) parseSide(parts[1]) else Side.RED
        val halfMove = if (parts.size >= 5) parts[4].toIntOrNull()
            ?.takeIf { it >= 0 } ?: throw IllegalArgumentException("halfMoveClock 字段非法: '${parts[4]}'")
        else 0
        val fullMove = if (parts.size >= 6) parts[5].toIntOrNull()
            ?.takeIf { it >= 1 } ?: throw IllegalArgumentException("fullMoveNumber 字段非法: '${parts[5]}'")
        else 1

        return FenPosition(board, sideToMove, halfMove, fullMove)
    }

    /** 解析棋盘段(如 "rnbakabnr/.../RNBAKABNR")。 */
    internal fun parseBoard(boardField: String): Board {
        val rows = boardField.split('/')
        require(rows.size == Position.ROW_COUNT) {
            "棋盘段必须有 ${Position.ROW_COUNT} 行,实际 ${rows.size}: '$boardField'"
        }

        var board = Board.EMPTY
        // rows[0] 对应 row=9(黑方底线),rows[9] 对应 row=0(红方底线)
        for ((rowFromTop, rowStr) in rows.withIndex()) {
            val row = Position.ROW_MAX - rowFromTop
            var col = 0
            for (c in rowStr) {
                if (col > Position.COL_MAX) {
                    throw IllegalArgumentException("第 ${rowFromTop + 1} 行格数超过 ${Position.COL_COUNT}: '$rowStr'")
                }
                if (c.isDigit()) {
                    val empty = c.code - '0'.code
                    if (empty < 1 || empty > 9) {
                        throw IllegalArgumentException("空格数字非法: '$c'")
                    }
                    col += empty
                } else {
                    val piece = try {
                        Piece.fromFenChar(c)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("棋盘字符非法: '$c'", e)
                    }
                    board = board.with(Position(col, row), piece)
                    col++
                }
            }
            if (col != Position.COL_COUNT) {
                throw IllegalArgumentException(
                    "第 ${rowFromTop + 1} 行格数应为 ${Position.COL_COUNT},实际 $col: '$rowStr'"
                )
            }
        }
        return board
    }

    private fun parseSide(s: String): Side = when (s) {
        "w" -> Side.RED
        "b" -> Side.BLACK
        else -> throw IllegalArgumentException("sideToMove 字段应为 'w' 或 'b': '$s'")
    }
}
