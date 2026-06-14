package com.xiangqi.app.domain.model

/**
 * 棋子类型。中国象棋 7 种。
 *
 * 帅/将、仕/士、相/象、马、车、炮、兵/卒 —— 红黑同名异写,FEN 中分别用大小写字母表示。
 */
enum class PieceType {
    KING,
    ADVISOR,
    BISHOP,
    KNIGHT,
    ROOK,
    CANNON,
    PAWN,
}

/**
 * 一颗具体的棋子 = 类型 + 颜色。值类型,可用作 Map key。
 *
 * @property type 棋子种类。
 * @property side 所属方。
 */
data class Piece(val type: PieceType, val side: Side) {
    /** FEN 字符:红方大写、黑方小写。具体字母见 [PieceFenChars.toChar]。 */
    fun fenChar(): Char = PieceFenChars.toChar(this)

    companion object {
        /** 由 FEN 字符构造 Piece。非法字符抛 [IllegalArgumentException]。 */
        fun fromFenChar(c: Char): Piece = PieceFenChars.fromChar(c)
    }
}

/** Piece 与 FEN 字符之间的映射。红方大写,黑方小写。 */
internal object PieceFenChars {
    private val redMap = mapOf(
        'K' to PieceType.KING,
        'A' to PieceType.ADVISOR,
        'B' to PieceType.BISHOP,
        'N' to PieceType.KNIGHT,
        'R' to PieceType.ROOK,
        'C' to PieceType.CANNON,
        'P' to PieceType.PAWN,
    )
    private val blackMap = redMap.mapKeys { (k, _) -> k.lowercaseChar() }

    fun toChar(piece: Piece): Char {
        val base = when (piece.type) {
            PieceType.KING -> 'K'
            PieceType.ADVISOR -> 'A'
            PieceType.BISHOP -> 'B'
            PieceType.KNIGHT -> 'N'
            PieceType.ROOK -> 'R'
            PieceType.CANNON -> 'C'
            PieceType.PAWN -> 'P'
        }
        return if (piece.side == Side.RED) base else base.lowercaseChar()
    }

    fun fromChar(c: Char): Piece {
        val type = redMap[c]
            ?: blackMap[c]
            ?: throw IllegalArgumentException("非法 FEN 棋子字符: '$c'")
        val side = if (c.isUpperCase()) Side.RED else Side.BLACK
        return Piece(type, side)
    }
}
