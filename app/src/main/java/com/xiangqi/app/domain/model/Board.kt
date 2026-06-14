package com.xiangqi.app.domain.model

/**
 * 中国象棋棋盘:9 列 × 10 行 = 90 格。不可变值类型,任何修改返回新实例。
 *
 * 内部以 [Array]<[Piece]?> 按行优先(row * 9 + col)存储。空格为 null。
 *
 * 通过 [get]/[operator fun get] 读取棋子,通过 [with] 生成"在某格放置/移除棋子"后的新棋盘,
 * 通过 [applyMove] 生成"按走法移动"后的新棋盘。
 */
class Board private constructor(
    private val cells: Array<Piece?>,
) : Iterable<Pair<Position, Piece?>> {

    val size: Int = Position.CELL_COUNT

    operator fun get(pos: Position): Piece? = cells[pos.packed]

    operator fun get(col: Int, row: Int): Piece? = this[Position(col, row)]

    /** 在 [pos] 放置 [piece] 或移除(piece=null),返回新的 Board。 */
    fun with(pos: Position, piece: Piece?): Board {
        if (cells[pos.packed] === piece) return this
        val copy = cells.copyOf()
        copy[pos.packed] = piece
        return Board(copy)
    }

    /** 应用走法:把 from 处的棋子搬到 to。from 必须有棋子(否则抛异常)。 */
    fun applyMove(move: Move): Board {
        val piece = cells[move.from.packed]
            ?: throw IllegalArgumentException("from 处没有棋子: ${move.from}")
        return with(move.from, null).with(move.to, piece)
    }

    /** 是否存在 [side] 方的任一棋子(用于"飞将"和"将帅照面"判定)。 */
    fun any(predicate: (Position, Piece) -> Boolean): Boolean {
        for (i in cells.indices) {
            val p = cells[i] ?: continue
            if (predicate(Position(i), p)) return true
        }
        return false
    }

    /** 找到 [type]+[side] 棋子的所有位置。 */
    fun find(type: PieceType, side: Side): List<Position> {
        val out = ArrayList<Position>(4)
        for (i in cells.indices) {
            val p = cells[i] ?: continue
            if (p.type == type && p.side == side) out += Position(i)
        }
        return out
    }

    /** 找到 [side] 方帅/将的位置。理论上必有且仅有一个;若不存在返回 null。 */
    fun kingPosition(side: Side): Position? = find(PieceType.KING, side).firstOrNull()

    override fun iterator(): Iterator<Pair<Position, Piece?>> = object : Iterator<Pair<Position, Piece?>> {
        private var i = 0
        override fun hasNext(): Boolean = i < cells.size
        override fun next(): Pair<Position, Piece?> {
            val pos = Position(i)
            val piece = cells[i]
            i++
            return pos to piece
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        return cells.contentEquals(other.cells)
    }

    override fun hashCode(): Int = cells.contentHashCode()

    override fun toString(): String {
        val sb = StringBuilder(90)
        for (row in 0..Position.ROW_MAX) {
            for (col in 0..Position.COL_MAX) {
                sb.append(cells[row * Position.COL_COUNT + col]?.fenChar() ?: '.')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        val EMPTY: Board = Board(arrayOfNulls(Position.CELL_COUNT))

        /**
         * 由"逐格定义"的函数构造棋盘:函数接收 (col, row) 返回 Piece 或 null。
         */
        fun build(block: (col: Int, row: Int) -> Piece?): Board {
            val cells = arrayOfNulls<Piece?>(Position.CELL_COUNT)
            var i = 0
            for (row in 0..Position.ROW_MAX) {
                for (col in 0..Position.COL_MAX) {
                    cells[i++] = block(col, row)
                }
            }
            return Board(cells)
        }

        /** 直接从数组构造,长度必须为 90,内部不再拷贝。仅供内部使用。 */
        internal fun unsafeFrom(cells: Array<Piece?>): Board {
            require(cells.size == Position.CELL_COUNT) { "cells 长度应为 ${Position.CELL_COUNT}" }
            return Board(cells)
        }
    }
}
