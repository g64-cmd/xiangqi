package com.xiangqi.app.domain.model

/**
 * 棋盘上的一个格子坐标。
 *
 * 中国象棋棋盘 9 列 × 10 行:
 * - [col] 列,0..8,从左到右;对应 FEN 中每一行的字符顺序,也对应 UCI 列字符 a–i。
 * - [row] 行,0..9,从红方一侧到黑方一侧(红方初始在 row=0–4,黑方初始在 row=5–9);
 *   row 数字直接对应 UCI 行字符 0–9。
 *
 * 值类型,可直接用作 Map key / Set 元素。
 *
 * @throws IllegalArgumentException 当 [col] / [row] 越界。
 */
@JvmInline
value class Position(val packed: Int) : Comparable<Position> {
    constructor(col: Int, row: Int) : this(pack(col, row))

    init {
        require(packed in 0 until CELL_COUNT) { "Position.packed 越界: $packed" }
    }

    val col: Int get() = packed % COL_COUNT
    val row: Int get() = packed / COL_COUNT

    /** 与目标坐标的相对偏移,负值表示向左/向上。不检查是否越界。 */
    operator fun minus(other: Position): Pair<Int, Int> =
        (col - other.col) to (row - other.row)

    /** 沿行列偏移移动,越界返回 null。 */
    fun offsetBy(dc: Int, dr: Int): Position? {
        val c = col + dc
        val r = row + dr
        return if (c in 0..COL_MAX && r in 0..ROW_MAX) Position(c, r) else null
    }

    override fun compareTo(other: Position): Int = packed.compareTo(other.packed)

    override fun toString(): String = "($col,$row)"

    companion object {
        const val COL_COUNT = 9
        const val ROW_COUNT = 10
        const val CELL_COUNT = COL_COUNT * ROW_COUNT
        const val COL_MAX = COL_COUNT - 1
        const val ROW_MAX = ROW_COUNT - 1

        private fun pack(col: Int, row: Int): Int {
            require(col in 0..COL_MAX && row in 0..ROW_MAX) {
                "Position 越界: col=$col, row=$row"
            }
            return row * COL_COUNT + col
        }
    }
}
