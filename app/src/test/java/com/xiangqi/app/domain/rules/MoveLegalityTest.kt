package com.xiangqi.app.domain.rules

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import org.junit.Test

class MoveLegalityTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)

    @Test
    fun `move that does not resolve check is illegal`() {
        // 黑将 (3,9) 被红车 (3,8) 将军。黑卒 (0,6) 想走 (0,5),没解将 → 非法。
        // FEN:段 0 (row=9)= (3,9) 黑将;段 1 (row=8) = (3,8) 红车;段 3 (row=6) = (0,6) 黑卒
        val board = FenParser.parse("3k5/3R5/9/p8/9/9/9/9/9/9 b - - 0 1").board
        val pawnMove = Move(Position(0, 6), Position(0, 5), Side.BLACK)
        assertThat(legality.isLegal(board, pawnMove)).isFalse()
    }

    @Test
    fun `king moves away from check is legal`() {
        // 黑将 (3,9),红车 (3,8) 沿 col=3 攻击。黑将 → (4,9) 逃。
        val board = FenParser.parse("3k5/3R5/9/9/9/9/9/9/9/9 b - - 0 1").board
        val escape = Move(Position(3, 9), Position(4, 9), Side.BLACK)
        assertThat(legality.isLegal(board, escape)).isTrue()
    }

    @Test
    fun `move creating flying general is illegal`() {
        // 红帅 (4,0),黑将 (4,9),中间红车 (4,4)。红车从 (4,4) 走到 (3,4) → 形成飞将,非法。
        // FEN 段 5 = row=4
        val board = FenParser.parse("4k4/9/9/9/9/4R4/9/9/9/4K4 w - - 0 1").board
        val moveAside = Move(Position(4, 4), Position(3, 4), Side.RED)
        assertThat(legality.isLegal(board, moveAside)).isFalse()
    }

    @Test
    fun `ordinary move in quiet position is legal`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        // 红炮从 (1,2) 平移到 (4,2)(炮二平五)
        val cannonMove = Move(Position(1, 2), Position(4, 2), Side.RED)
        assertThat(legality.isLegal(board, cannonMove)).isTrue()
    }

    @Test
    fun `hasAnyLegalMove returns true in opening`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        assertThat(legality.hasAnyLegalMove(board, Side.RED)).isTrue()
        assertThat(legality.hasAnyLegalMove(board, Side.BLACK)).isTrue()
    }

    @Test
    fun `black king in corner with two rooks has no legal move`() {
        // 黑将 (0,9) 角落。
        // 段 8 (row=1) (1,1) 红车;段 9 (row=0) (0,0) 红车
        // 黑将 (0,9):
        //   去 (1,9)?被红车 (1,1) 沿 col=1 攻击 → illegal
        //   去 (0,8)?被红车 (0,0) 沿 col=0 攻击 → illegal
        // 无路 → hasAnyLegalMove 为 false
        val board = FenParser.parse("k8/9/9/9/9/9/9/9/1R7/R8 b - - 0 1").board
        assertThat(legality.hasAnyLegalMove(board, Side.BLACK)).isFalse()
    }
}
