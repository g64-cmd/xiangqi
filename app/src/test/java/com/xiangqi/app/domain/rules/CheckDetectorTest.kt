package com.xiangqi.app.domain.rules

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import org.junit.Test

class CheckDetectorTest {

    private val gen = MoveGeneratorImpl()
    private val detector = CheckDetector(gen)

    @Test
    fun `kings facing each other on clear column counts as in check`() {
        // 双将同列,中间空:红帅 (4,0),黑将 (4,9)
        val board = FenParser.parse("4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1").board
        assertThat(detector.areKingsFacing(board)).isTrue()
        // isInCheck 双方都为 true(因为飞将)
        assertThat(detector.isInCheck(board, Side.RED)).isTrue()
        assertThat(detector.isInCheck(board, Side.BLACK)).isTrue()
    }

    @Test
    fun `kings facing blocked by piece is not flying general`() {
        // 中间放一颗棋子
        val board = FenParser.parse("4k4/9/9/9/9/9/4P4/9/9/4K4 w - - 0 1").board
        assertThat(detector.areKingsFacing(board)).isFalse()
    }

    @Test
    fun `kings on different columns do not face`() {
        val board = FenParser.parse("3k5/9/9/9/9/9/9/9/9/4K4 w - - 0 1").board
        assertThat(detector.areKingsFacing(board)).isFalse()
    }

    @Test
    fun `rook attacking king is in check`() {
        // 红车在 (4,8) 攻击黑将 (4,9),中间空
        val board = FenParser.parse("4k4/4R4/9/9/9/9/9/9/9/9 b - - 0 1").board
        assertThat(detector.isInCheck(board, Side.BLACK)).isTrue()
        assertThat(detector.isInCheck(board, Side.RED)).isFalse()
    }

    @Test
    fun `no check in quiet position`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        assertThat(detector.isInCheck(board, Side.RED)).isFalse()
        assertThat(detector.isInCheck(board, Side.BLACK)).isFalse()
    }
}
