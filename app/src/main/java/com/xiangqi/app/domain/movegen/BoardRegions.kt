package com.xiangqi.app.domain.movegen

import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/** 棋盘几何区域辅助:九宫格、河界、过河判定。 */
internal object BoardRegions {
    /** 9 列 × 10 行。红方初始 row=0..4,黑方初始 row=5..9。 */
    private const val RIVER_RED_MAX = 4

    /** 红方九宫:col 3..5,row 0..2。 */
    private val RED_PALACE_COLS = 3..5
    private val RED_PALACE_ROWS = 0..2

    /** 黑方九宫:col 3..5,row 7..9。 */
    private val BLACK_PALACE_COLS = 3..5
    private val BLACK_PALACE_ROWS = 7..9

    /** 是否在 [side] 方的九宫格内。 */
    fun isInPalace(pos: Position, side: Side): Boolean = when (side) {
        Side.RED -> pos.col in RED_PALACE_COLS && pos.row in RED_PALACE_ROWS
        Side.BLACK -> pos.col in BLACK_PALACE_COLS && pos.row in BLACK_PALACE_ROWS
    }

    /** 是否在 [side] 方的本方半场(未过河)。 */
    fun isOnOwnSide(pos: Position, side: Side): Boolean = when (side) {
        Side.RED -> pos.row <= RIVER_RED_MAX
        Side.BLACK -> pos.row > RIVER_RED_MAX
    }

    /** 是否已过河到对方半场。 */
    fun isAcrossRiver(pos: Position, side: Side): Boolean = !isOnOwnSide(pos, side)
}
