package com.xiangqi.app.domain.eval

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/**
 * 局面估值函数。子力 + Piece-Square Table(PST)。
 *
 * **视角约定**:[materialAndPst] 返回**红方视角**的分数(红方占优为正);
 * [evaluate] 把其翻转为**当前走子方视角**(当前走子方占优为正),供 Negamax 直接使用。
 *
 * **坐标系**:PST 表以红方视角 row=0 是红方底线,row=9 是黑方底线。
 * 计算黑方棋子的 PST 时,行号按 `9 - row` 翻转。
 *
 * 这只是搜索的"启发式叶子评估",不识别将死/困毙——后者在 Search 的递归里通过
 * [com.xiangqi.app.domain.rules.MoveLegality.hasAnyLegalMove] 判定。
 */
class Evaluation {

    /** 红方视角的总分。正=红方占优。 */
    fun materialAndPst(board: Board): Int {
        var score = 0
        for ((pos, piece) in board) {
            if (piece == null) continue
            val base = PIECE_VALUE[piece.type] ?: 0
            val pst = pstScore(piece, pos)
            score += if (piece.side == Side.RED) base + pst else -(base + pst)
        }
        return score
    }

    /** 当前走子方视角的总分。正=当前走子方占优。 */
    fun evaluate(board: Board, sideToMove: Side): Int {
        val red = materialAndPst(board)
        return if (sideToMove == Side.RED) red else -red
    }

    /** 单棋子在 [pos] 处的 PST 加成(已按红黑方翻转)。 */
    private fun pstScore(piece: Piece, pos: Position): Int {
        val table = PST[piece.type] ?: return 0
        val row = if (piece.side == Side.RED) pos.row else Position.ROW_MAX - pos.row
        return table[row * Position.COL_COUNT + pos.col]
    }

    companion object {
        /** 棋子基础价值(经验值,红黑同)。帅设极大值以便"丢帅"立刻反映在分数上。 */
        val PIECE_VALUE: Map<PieceType, Int> = mapOf(
            PieceType.KING to 10_000,
            PieceType.ADVISOR to 20,
            PieceType.BISHOP to 20,
            PieceType.KNIGHT to 40,
            PieceType.ROOK to 90,
            PieceType.CANNON to 45,
            PieceType.PAWN to 10,
        )

        /**
         * 各兵种的 Piece-Square Table。索引 = row * 9 + col(红方视角)。
         * 数值越正表示该位置对该兵种越有利。
         */
        internal val PST: Map<PieceType, IntArray> = mapOf(
            PieceType.KING to KING_PST,
            PieceType.ADVISOR to ADVISOR_PST,
            PieceType.BISHOP to BISHOP_PST,
            PieceType.KNIGHT to KNIGHT_PST,
            PieceType.ROOK to ROOK_PST,
            PieceType.CANNON to CANNON_PST,
            PieceType.PAWN to PAWN_PST,
        )
    }
}

// ---- PST 表(红方视角,9 列 × 10 行)----
//
// 设计原则:
// - 车(Rook):开放线/敌阵深处分高,本方首行边缘略低。
// - 马(Knight):中央高,边角低;过河后(敌阵)额外加分。
// - 炮(Cannon):首行中路(炮二平五)+5;河沿 +6。
// - 兵(Pawn):未过河基本 0;过河后每深入 1 格 +8;中路列(col=4)加分。
// - 仕/相(Advisor/Bishop):仅合法位置有正分;其他位置占位会被 Search 视为非法走法,所以表内不设惩罚。

/** 帅:仅九宫 9 格(中央 3 列 × 己方 3 行)给 +5;其余 0。 */
private val KING_PST: IntArray = IntArray(90).also { a ->
    for (row in 0..2) {
        for (col in 3..5) {
            a[row * 9 + col] = 5
        }
    }
}

/** 仕:九宫四角(2,0)(6,0)(2,2)(6,2)和中心(4,1)给 +5。 */
private val ADVISOR_PST: IntArray = IntArray(90).also { a ->
    val pts = listOf(2 to 0, 6 to 0, 4 to 1, 2 to 2, 6 to 2)
    for ((col, row) in pts) a[row * 9 + col] = 5
}

/** 相:本方 7 个合法田字点给 +5。 */
private val BISHOP_PST: IntArray = IntArray(90).also { a ->
    val pts = listOf(0 to 0, 0 to 4, 2 to 2, 2 to 6, 6 to 2, 6 to 6, 4 to 0, 4 to 4, 8 to 0, 8 to 4)
    for ((col, row) in pts) a[row * 9 + col] = 5
}

/** 马:中央 +5,边角 -10,过河(敌阵)+8。 */
private val KNIGHT_PST: IntArray = IntArray(90).also { a ->
    val grid = arrayOf(
        // col: 0   1   2   3   4   5   6   7   8
        intArrayOf(-10, -5,  0,  5,  5,  5,  0, -5, -10),  // row 0(红方底线)
        intArrayOf(-5,  0,  5,  10, 10, 10, 5,  0, -5),    // row 1
        intArrayOf(0,   5,  10, 15, 15, 15, 10, 5,  0),    // row 2
        intArrayOf(5,  10,  15, 18, 20, 18, 15, 10, 5),    // row 3
        intArrayOf(8,  13,  18, 21, 23, 21, 18, 13, 8),    // row 4(本方河界边)
        intArrayOf(10, 15,  20, 23, 25, 23, 20, 15, 10),   // row 5(过河第 1 格)
        intArrayOf(8,  13,  18, 21, 23, 21, 18, 13, 8),    // row 6
        intArrayOf(5,  10,  15, 18, 20, 18, 15, 10, 5),    // row 7
        intArrayOf(0,   5,  10, 15, 15, 15, 10, 5,  0),    // row 8
        intArrayOf(-10,-5,  0,  5,  5,  5,  0, -5, -10),   // row 9(黑方底线)
    )
    for (row in 0..9) for (col in 0..8) a[row * 9 + col] = grid[row][col]
}

/** 车:首行 +0(待出车);敌阵深处分高;河沿(本方 row=4)+6。 */
private val ROOK_PST: IntArray = IntArray(90).also { a ->
    val grid = arrayOf(
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(5,  5,  5,  5,  5,  5,  5,  5,  5),
        intArrayOf(5,  5, 10, 10, 10, 10, 10, 5,  5),
        intArrayOf(8,  8, 13, 15, 15, 15, 13, 8,  8),
        intArrayOf(6,  6, 11, 13, 13, 13, 11, 6,  6),
        intArrayOf(8,  8, 13, 15, 15, 15, 13, 8,  8),
        intArrayOf(5,  5, 10, 10, 10, 10, 10, 5,  5),
        intArrayOf(5,  5,  5,  5,  5,  5,  5,  5,  5),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),
    )
    for (row in 0..9) for (col in 0..8) a[row * 9 + col] = grid[row][col]
}

/** 炮:首行中央(col=4 row=0)+5(炮二平五威胁);河沿 +6。 */
private val CANNON_PST: IntArray = IntArray(90).also { a ->
    val grid = arrayOf(
        intArrayOf(0,  0,  0,  3,  5,  3,  0,  0,  0),
        intArrayOf(3,  3,  3,  3,  3,  3,  3,  3,  3),
        intArrayOf(5,  5,  5,  5,  5,  5,  5,  5,  5),
        intArrayOf(3,  3,  3,  3,  3,  3,  3,  3,  3),
        intArrayOf(6,  6,  6,  6,  6,  6,  6,  6,  6),
        intArrayOf(3,  3,  3,  3,  3,  3,  3,  3,  3),
        intArrayOf(5,  5,  5,  5,  5,  5,  5,  5,  5),
        intArrayOf(3,  3,  3,  3,  3,  3,  3,  3,  3),
        intArrayOf(0,  0,  0,  3,  5,  3,  0,  0,  0),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),
    )
    for (row in 0..9) for (col in 0..8) a[row * 9 + col] = grid[row][col]
}

/** 兵:未过河基本 0;过河后(敌阵)每深入加分;中路 col=4 额外 +5。 */
private val PAWN_PST: IntArray = IntArray(90).also { a ->
    val grid = arrayOf(
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),   // row 0 红方底线:不会有兵
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),   // row 3 红方未过河兵位
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),   // row 4 红方本方河界边
        intArrayOf(10, 10, 10, 12, 17, 12, 10, 10, 10),  // row 5 过河第 1 格
        intArrayOf(15, 15, 15, 18, 23, 18, 15, 15, 15),  // row 6
        intArrayOf(20, 20, 20, 23, 28, 23, 20, 20, 20),  // row 7
        intArrayOf(30, 30, 30, 33, 38, 33, 30, 30, 30),  // row 8 深入敌阵
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0,  0),   // row 9 黑方底线
    )
    for (row in 0..9) for (col in 0..8) a[row * 9 + col] = grid[row][col]
}
