package com.xiangqi.app.domain.eval

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Side
import org.junit.Test

class EvaluationTest {

    private val eval = Evaluation()

    @Test
    fun `empty board scores zero`() {
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/9 w - - 0 1").board
        assertThat(eval.materialAndPst(board)).isEqualTo(0)
    }

    @Test
    fun `symmetric initial position scores zero for red perspective`() {
        // 开局对称,红黑双方子力/PST 完全镜像,红方视角总分应为 0。
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        assertThat(eval.materialAndPst(board)).isEqualTo(0)
    }

    @Test
    fun `single red rook at center scores positive for red`() {
        // 红车单独在 (4,5)——过河到敌阵中央,PST 加分 +15
        // FEN 段 N 对应 row=9-N;row 5 → 段 4
        val board = FenParser.parse("9/9/9/9/4R4/9/9/9/9/9 w - - 0 1").board
        val redScore = eval.materialAndPst(board)
        assertThat(redScore).isGreaterThan(Evaluation.PIECE_VALUE[PieceType.ROOK]!!)
    }

    @Test
    fun `evaluate flips sign for black to move`() {
        val board = FenParser.parse("9/4R4/9/9/9/9/9/9/9/9 w - - 0 1").board
        val redView = eval.evaluate(board, Side.RED)
        val blackView = eval.evaluate(board, Side.BLACK)
        assertThat(blackView).isEqualTo(-redView)
        assertThat(redView).isGreaterThan(0)
    }

    @Test
    fun `extra pawn yields score difference`() {
        // 加一个红兵:红方视角应该至少 +10
        val noPawn = FenParser.parse("9/9/9/9/9/9/9/9/9/3K5 w - - 0 1").board
        val withPawn = FenParser.parse("9/9/9/9/9/9/9/9/9/3KP4 w - - 0 1").board
        val diff = eval.materialAndPst(withPawn) - eval.materialAndPst(noPawn)
        assertThat(diff).isAtLeast(Evaluation.PIECE_VALUE[PieceType.PAWN]!!)
    }

    @Test
    fun `piece values are configured correctly`() {
        val v = Evaluation.PIECE_VALUE
        assertThat(v[PieceType.KING]).isEqualTo(10_000)
        assertThat(v[PieceType.ROOK]).isEqualTo(90)
        assertThat(v[PieceType.CANNON]).isEqualTo(45)
        assertThat(v[PieceType.KNIGHT]).isEqualTo(40)
        assertThat(v[PieceType.ADVISOR]).isEqualTo(20)
        assertThat(v[PieceType.BISHOP]).isEqualTo(20)
        assertThat(v[PieceType.PAWN]).isEqualTo(10)
    }

    @Test
    fun `crossing-river pawn earns pst bonus`() {
        // 同一枚红兵,未过河(row 3) vs 过河(row 5),过河后 PST 显著加分
        // FEN 段 N 对应 row=9-N;row 3 → 段 6;row 5 → 段 4
        val notCrossed = FenParser.parse("9/9/9/9/9/9/4P4/9/9/3K5 w - - 0 1").board
        val crossed = FenParser.parse("9/9/9/9/4P4/9/9/9/9/3K5 w - - 0 1").board
        val before = eval.materialAndPst(notCrossed)
        val after = eval.materialAndPst(crossed)
        assertThat(after).isGreaterThan(before)
    }
}
