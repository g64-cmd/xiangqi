package com.xiangqi.app.domain.fen

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import org.junit.Assert.assertThrows
import org.junit.Test

class FenParserTest {

    @Test
    fun `parses initial position pieces`() {
        val fen = FenParser.parse(FenParser.INITIAL_FEN)

        // 黑方底线 row=9:r n b a k a b n r
        assertThat(fen.board[0, 9]).isEqualTo(Piece(PieceType.ROOK, Side.BLACK))
        assertThat(fen.board[1, 9]).isEqualTo(Piece(PieceType.KNIGHT, Side.BLACK))
        assertThat(fen.board[2, 9]).isEqualTo(Piece(PieceType.BISHOP, Side.BLACK))
        assertThat(fen.board[3, 9]).isEqualTo(Piece(PieceType.ADVISOR, Side.BLACK))
        assertThat(fen.board[4, 9]).isEqualTo(Piece(PieceType.KING, Side.BLACK))
        assertThat(fen.board[5, 9]).isEqualTo(Piece(PieceType.ADVISOR, Side.BLACK))
        assertThat(fen.board[6, 9]).isEqualTo(Piece(PieceType.BISHOP, Side.BLACK))
        assertThat(fen.board[7, 9]).isEqualTo(Piece(PieceType.KNIGHT, Side.BLACK))
        assertThat(fen.board[8, 9]).isEqualTo(Piece(PieceType.ROOK, Side.BLACK))

        // 黑方炮 row=7,col=1 和 7
        assertThat(fen.board[1, 7]).isEqualTo(Piece(PieceType.CANNON, Side.BLACK))
        assertThat(fen.board[7, 7]).isEqualTo(Piece(PieceType.CANNON, Side.BLACK))

        // 黑方卒 row=6,偶数列
        for (col in listOf(0, 2, 4, 6, 8)) {
            assertThat(fen.board[col, 6]).isEqualTo(Piece(PieceType.PAWN, Side.BLACK))
        }

        // 红方对称
        assertThat(fen.board[4, 0]).isEqualTo(Piece(PieceType.KING, Side.RED))
        assertThat(fen.board[1, 2]).isEqualTo(Piece(PieceType.CANNON, Side.RED))
        assertThat(fen.board[0, 3]).isEqualTo(Piece(PieceType.PAWN, Side.RED))

        assertThat(fen.sideToMove).isEqualTo(Side.RED)
        assertThat(fen.halfMoveClock).isEqualTo(0)
        assertThat(fen.fullMoveNumber).isEqualTo(1)
    }

    @Test
    fun `parses empty board with side black`() {
        val fen = FenParser.parse("9/9/9/9/9/9/9/9/9/9 b - - 3 7")
        for (row in 0..9) {
            for (col in 0..8) {
                assertThat(fen.board[col, row]).isNull()
            }
        }
        assertThat(fen.sideToMove).isEqualTo(Side.BLACK)
        assertThat(fen.halfMoveClock).isEqualTo(3)
        assertThat(fen.fullMoveNumber).isEqualTo(7)
    }

    @Test
    fun `parses when only board field given`() {
        val fen = FenParser.parse("9/9/9/9/9/9/9/9/9/9")
        assertThat(fen.sideToMove).isEqualTo(Side.RED)
        assertThat(fen.halfMoveClock).isEqualTo(0)
        assertThat(fen.fullMoveNumber).isEqualTo(1)
    }

    @Test
    fun `round-trips through toFen`() {
        val original = FenParser.INITIAL_FEN
        val parsed = FenParser.parse(original)
        assertThat(parsed.toFen()).isEqualTo(original)
    }

    @Test
    fun `round-trips several endgame positions`() {
        val endgames = listOf(
            // 仅双将
            "4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1",
            // 双车对单将
            "3rk4/4a4/9/9/9/9/9/9/4A4/3RK4 b - - 0 1",
            // 马后炮残局
            "2bak4/9/4b4/9/9/9/9/4N4/4C4/3AKA3 w - - 0 1",
            // 中炮横车
            "r3kabr1/9/9/9/4C4/9/9/9/9/2BAKABR1 b - - 0 1",
        )
        for (fen in endgames) {
            val reparsed = FenParser.parse(fen).toFen()
            assertThat(reparsed).isEqualTo(fen)
        }
    }

    @Test
    fun `parses digits as empty runs`() {
        // "1c5c1" -> col 0 空, col 1 黑炮, col 2..6 空(5), col 7 黑炮, col 8 空(1)
        val fen = FenParser.parse("9/9/9/9/9/9/9/1c5c1/9/9 w - - 0 1")
        assertThat(fen.board[1, 2]).isEqualTo(Piece(PieceType.CANNON, Side.BLACK))
        assertThat(fen.board[7, 2]).isEqualTo(Piece(PieceType.CANNON, Side.BLACK))
        assertThat(fen.board[2, 2]).isNull()
        assertThat(fen.board[6, 2]).isNull()
        assertThat(fen.board[8, 2]).isNull()
    }

    @Test
    fun `rejects empty string`() {
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("   ")
        }
    }

    @Test
    fun `rejects wrong number of board rows`() {
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("9/9/9 w - - 0 1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("9/9/9/9/9/9/9/9/9/9/9 w - - 0 1")
        }
    }

    @Test
    fun `rejects wrong cells per row`() {
        // 第 1 行格数太少
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("8/9/9/9/9/9/9/9/9/9 w - - 0 1")
        }
        // 第 1 行格数太多
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("10/9/9/9/9/9/9/9/9/9 w - - 0 1")
        }
    }

    @Test
    fun `rejects illegal piece char`() {
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("xnbakabnr/9/9/9/9/9/9/9/9/RNBAKABNR w - - 0 1")
        }
    }

    @Test
    fun `rejects illegal sideToMove`() {
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("9/9/9/9/9/9/9/9/9/9 x - - 0 1")
        }
    }

    @Test
    fun `rejects negative halfmove`() {
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("9/9/9/9/9/9/9/9/9/9 w - - -1 1")
        }
    }

    @Test
    fun `rejects zero fullmove`() {
        assertThrows(IllegalArgumentException::class.java) {
            FenParser.parse("9/9/9/9/9/9/9/9/9/9 w - - 0 0")
        }
    }

    @Test
    fun `toFen of custom position matches expected layout`() {
        // 红车在 (0,0),红帅在 (4,0),其它空 -> 第 10 段 "R3K4",其余全 "9"
        val pos = FenParser.parse("9/9/9/9/9/9/9/9/9/R3K4 w - - 0 1")
        assertThat(pos.board[0, 0]).isEqualTo(Piece(PieceType.ROOK, Side.RED))
        assertThat(pos.board[4, 0]).isEqualTo(Piece(PieceType.KING, Side.RED))
        assertThat(pos.toFen()).isEqualTo("9/9/9/9/9/9/9/9/9/R3K4 w - - 0 1")
    }
}
