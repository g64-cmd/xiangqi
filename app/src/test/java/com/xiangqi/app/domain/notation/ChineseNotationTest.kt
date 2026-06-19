package com.xiangqi.app.domain.notation

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import org.junit.Test

/**
 * [ChineseNotation.format] 翻译测试。
 *
 * 列号约定:
 * - 红:col 0 = 九, col 8 = 一(col c → 9-c)
 * - 黑:col 0 = 1,  col 8 = 9(col c → c+1)
 *
 * 行号约定(row 0..9 从红到黑):
 * - 红进 = row 减小,红退 = row 增大
 * - 黑进 = row 增大,黑退 = row 减小
 */
class ChineseNotationTest {

    @Test
    fun `开局红炮二平五`() {
        // 标准开局 FEN,红方右侧炮(col 7)平到 col 4(中)
        // 红方 col 7 → 9 - 7 = 2 → "二";col 4 → 9 - 4 = 5 → "五"
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val move = Move(Position(7, 2), Position(4, 2), Side.RED)
        assertThat(ChineseNotation.format(move, board)).isEqualTo("炮二平五")
    }

    @Test
    fun `开局红炮八平五`() {
        // 红方左侧炮(col 1)平到 col 4:col 1 → 9 - 1 = 8 → "八"
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val move = Move(Position(1, 2), Position(4, 2), Side.RED)
        assertThat(ChineseNotation.format(move, board)).isEqualTo("炮八平五")
    }

    @Test
    fun `红车进 6 等于 6 行`() {
        // 红方 col 0 车,从 row 0 进到 row 6(6 行)
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val move = Move(Position(0, 0), Position(0, 6), Side.RED)
        // 内部 col 0 → 红方"九",行差 6 → 红方"六",row 增大对红 = 进
        assertThat(ChineseNotation.format(move, board)).isEqualTo("车九进六")
    }

    @Test
    fun `黑方炮 2 平 5 用阿拉伯数字`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        // 黑方 col 7 内部 = "8"(col+1);col 4 = "5"。左侧炮为 col 1 = "2"
        val move = Move(Position(1, 7), Position(4, 7), Side.BLACK)
        assertThat(ChineseNotation.format(move, board)).isEqualTo("炮2平5")
    }

    @Test
    fun `红方马八进七`() {
        // 红方 col 1 = "八"(9-1),目标 col 2 = "七"(9-2),斜行类用目标列号
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val move = Move(Position(1, 0), Position(2, 2), Side.RED)
        assertThat(ChineseNotation.format(move, board)).isEqualTo("马八进七")
    }

    @Test
    fun `黑方进表现为 row 减小`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        // 黑车 col 0 从 row 9 进到 row 5:row 减小,黑方为"进"
        val move = Move(Position(0, 9), Position(0, 5), Side.BLACK)
        // 黑 col 0 = "1",行差 4 → "4"
        assertThat(ChineseNotation.format(move, board)).isEqualTo("车1进4")
    }

    @Test
    fun `兵卒进 1 翻译`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        // 红兵 col 0 row 3 进 1 到 row 4:row 增大对红 = 进
        val move = Move(Position(0, 3), Position(0, 4), Side.RED)
        // 红 col 0 = "九",行差 1
        assertThat(ChineseNotation.format(move, board)).isEqualTo("兵九进一")
    }

    @Test
    fun `同列双车用前后`() {
        // 红方 col 0 有两辆车,row 0(后)和 row 5(前)
        val board = Board.build { col, row ->
            when {
                col == 0 && row == 0 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.ROOK,
                    Side.RED,
                )
                col == 0 && row == 5 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.ROOK,
                    Side.RED,
                )
                col == 4 && row == 0 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.KING,
                    Side.RED,
                )
                col == 4 && row == 9 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.KING,
                    Side.BLACK,
                )
                else -> null
            }
        }
        // 前车(row 5,更靠黑方)退到 row 4:row 减小对红 = 退
        val move = Move(Position(0, 5), Position(0, 4), Side.RED)
        assertThat(ChineseNotation.format(move, board)).isEqualTo("前车退一")
    }

    @Test
    fun `黑方同列双卒前后判定相反`() {
        // 黑方 col 0 row 5(前)和 row 9(后)两卒;黑"前"= row 小者(更靠红方)
        val board = Board.build { col, row ->
            when {
                col == 0 && row == 5 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.PAWN,
                    Side.BLACK,
                )
                col == 0 && row == 9 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.PAWN,
                    Side.BLACK,
                )
                col == 4 && row == 9 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.KING,
                    Side.BLACK,
                )
                col == 4 && row == 0 -> com.xiangqi.app.domain.model.Piece(
                    com.xiangqi.app.domain.model.PieceType.KING,
                    Side.RED,
                )
                else -> null
            }
        }
        // 黑方"前卒" = row 5(更靠红方),从 row 5 进 1 到 row 4:row 减小对黑 = 进
        val move = Move(Position(0, 5), Position(0, 4), Side.BLACK)
        assertThat(ChineseNotation.format(move, board)).isEqualTo("前卒进1")
    }

    @Test
    fun `红相飞斜用目标列`() {
        // 红相 col 2 row 0(开局位置)飞到 col 0 row 2:斜行类,row +2 = 进
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val move = Move(Position(2, 0), Position(0, 2), Side.RED)
        // 红 col 2 = "七"(9-2),目标 col 0 = "九"(9-0)
        assertThat(ChineseNotation.format(move, board)).isEqualTo("相七进九")
    }
}
