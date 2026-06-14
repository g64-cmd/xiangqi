package com.xiangqi.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BoardTest {

    private val redKing = Piece(PieceType.KING, Side.RED)
    private val blackKing = Piece(PieceType.KING, Side.BLACK)
    private val redKnight = Piece(PieceType.KNIGHT, Side.RED)

    @Test
    fun `empty board has no pieces`() {
        val b = Board.EMPTY
        for (row in 0..9) {
            for (col in 0..8) {
                assertThat(b[col, row]).isNull()
            }
        }
    }

    @Test
    fun `with places piece and returns new board`() {
        val b1 = Board.EMPTY
        val b2 = b1.with(Position(4, 9), redKing)
        assertThat(b1[4, 9]).isNull()
        assertThat(b2[4, 9]).isEqualTo(redKing)
    }

    @Test
    fun `with null removes piece`() {
        val b = Board.EMPTY.with(Position(4, 9), redKing).with(Position(4, 9), null)
        assertThat(b[4, 9]).isNull()
    }

    @Test
    fun `with returns same instance when no change`() {
        val b = Board.EMPTY.with(Position(4, 9), redKing)
        assertThat(b.with(Position(4, 9), redKing)).isSameInstanceAs(b)
    }

    @Test
    fun `applyMove moves piece and clears source`() {
        val b = Board.EMPTY.with(Position(7, 9), redKnight)
        val moved = b.applyMove(Move(Position(7, 9), Position(6, 7), Side.RED))
        assertThat(moved[7, 9]).isNull()
        assertThat(moved[6, 7]).isEqualTo(redKnight)
    }

    @Test
    fun `applyMove captures target piece`() {
        val b = Board.EMPTY
            .with(Position(7, 9), redKnight)
            .with(Position(6, 7), blackKing)
        val moved = b.applyMove(Move(Position(7, 9), Position(6, 7), Side.RED))
        assertThat(moved[7, 9]).isNull()
        assertThat(moved[6, 7]).isEqualTo(redKnight)
    }

    @Test
    fun `applyMove throws when source empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            Board.EMPTY.applyMove(Move(Position(0, 0), Position(1, 1), Side.RED))
        }
    }

    @Test
    fun `find returns positions of matching pieces`() {
        val b = Board.EMPTY
            .with(Position(0, 9), Piece(PieceType.ROOK, Side.RED))
            .with(Position(8, 9), Piece(PieceType.ROOK, Side.RED))
            .with(Position(0, 0), Piece(PieceType.ROOK, Side.BLACK))
        assertThat(b.find(PieceType.ROOK, Side.RED))
            .containsExactly(Position(0, 9), Position(8, 9))
        assertThat(b.find(PieceType.ROOK, Side.BLACK))
            .containsExactly(Position(0, 0))
    }

    @Test
    fun `kingPosition returns first king found`() {
        val b = Board.EMPTY.with(Position(4, 9), redKing)
        assertThat(b.kingPosition(Side.RED)).isEqualTo(Position(4, 9))
    }

    @Test
    fun `equals compares cells by content`() {
        val a = Board.EMPTY.with(Position(4, 9), redKing)
        val b = Board.EMPTY.with(Position(4, 9), redKing)
        val c = Board.EMPTY.with(Position(4, 9), redKnight)
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }

    @Test
    fun `build constructs board from coordinate function`() {
        val b = Board.build { col, row ->
            when (col to row) {
                4 to 9 -> redKing
                4 to 0 -> blackKing
                else -> null
            }
        }
        assertThat(b[4, 9]).isEqualTo(redKing)
        assertThat(b[4, 0]).isEqualTo(blackKing)
        assertThat(b[0, 0]).isNull()
    }
}
