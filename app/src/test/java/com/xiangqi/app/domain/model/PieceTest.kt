package com.xiangqi.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PieceTest {

    @Test
    fun `red pieces use uppercase fen chars`() {
        assertThat(Piece(PieceType.KING, Side.RED).fenChar()).isEqualTo('K')
        assertThat(Piece(PieceType.ADVISOR, Side.RED).fenChar()).isEqualTo('A')
        assertThat(Piece(PieceType.BISHOP, Side.RED).fenChar()).isEqualTo('B')
        assertThat(Piece(PieceType.KNIGHT, Side.RED).fenChar()).isEqualTo('N')
        assertThat(Piece(PieceType.ROOK, Side.RED).fenChar()).isEqualTo('R')
        assertThat(Piece(PieceType.CANNON, Side.RED).fenChar()).isEqualTo('C')
        assertThat(Piece(PieceType.PAWN, Side.RED).fenChar()).isEqualTo('P')
    }

    @Test
    fun `black pieces use lowercase fen chars`() {
        assertThat(Piece(PieceType.KING, Side.BLACK).fenChar()).isEqualTo('k')
        assertThat(Piece(PieceType.KNIGHT, Side.BLACK).fenChar()).isEqualTo('n')
        assertThat(Piece(PieceType.CANNON, Side.BLACK).fenChar()).isEqualTo('c')
    }

    @Test
    fun `fromFenChar round-trips all 14 piece chars`() {
        val chars = "KABNRCPkabnrcp"
        for (c in chars) {
            val p = Piece.fromFenChar(c)
            assertThat(p.fenChar()).isEqualTo(c)
        }
    }

    @Test
    fun `fromFenChar throws on non-piece char`() {
        assertThrows(IllegalArgumentException::class.java) {
            Piece.fromFenChar('.')
        }
        assertThrows(IllegalArgumentException::class.java) {
            Piece.fromFenChar('1')
        }
        assertThrows(IllegalArgumentException::class.java) {
            Piece.fromFenChar('x')
        }
    }

    @Test
    fun `piece equality by type and side`() {
        assertThat(Piece(PieceType.KING, Side.RED))
            .isEqualTo(Piece(PieceType.KING, Side.RED))
        assertThat(Piece(PieceType.KING, Side.RED))
            .isNotEqualTo(Piece(PieceType.KING, Side.BLACK))
    }
}
