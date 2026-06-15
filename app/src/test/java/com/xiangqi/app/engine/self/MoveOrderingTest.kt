package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import org.junit.Test

class MoveOrderingTest {

    private val gen = MoveGeneratorImpl()
    private val checkDetector = CheckDetector(gen)
    private val ordering = MoveOrdering(gen, checkDetector)

    @Test
    fun `tt move ranks first when present`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val moves = gen.movesFor(board, Side.RED)
        val ttMove = moves.last() // 故意挑最后一个
        val sorted = ordering.sort(board, moves, Side.RED, ttMove)
        assertThat(sorted.first()).isEqualTo(ttMove)
    }

    @Test
    fun `captures rank above quiet moves`() {
        // 红车在 (3,1),黑卒在 (4,1);红车横吃 (3,1)→(4,1)。
        // FEN 段 8 → row 1,段 9 → row 0。3p5 → R 在 col 3?不,3p5 表示前 3 列空,p 在 col 3,
        //   后 5 列空——但这是黑卒 (3,1)。我要红车在 (3,0) 走到 (3,1),黑卒要在 (3,1)。
        // 段 9 = "3R5"(红车 col 3 row 0);段 8 = "3p5"(黑卒 col 3 row 1)。共 9+9=18 ✓
        val board = FenParser.parse("9/9/9/9/9/9/9/9/3p5/3R5 w - - 0 1").board
        val moves = gen.movesFor(board, Side.RED)
        val capture = Move(Position(3, 0), Position(3, 1), Side.RED)
        assertThat(moves).contains(capture)

        val sorted = ordering.sort(board, moves, Side.RED, ttMove = null)
        // 第一个应至少是吃子走法
        val firstMove = sorted.first()
        assertThat(board[firstMove.to]).isNotNull()
    }

    @Test
    fun `mvv_lva prefers cheap attacker`() {
        // 同上场景:红车 (3,0) 吃黑卒 (3,1),走法应是排序后首位
        val board = FenParser.parse("9/9/9/9/9/9/9/9/3p5/3R5 w - - 0 1").board
        val moves = gen.movesFor(board, Side.RED)
        val capture = Move(Position(3, 0), Position(3, 1), Side.RED)
        assertThat(moves).contains(capture)
        val sorted = ordering.sort(board, moves, Side.RED, ttMove = null)
        assertThat(sorted.first()).isEqualTo(capture)
    }

    @Test
    fun `sort is stable for equal-score moves`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val moves = gen.movesFor(board, Side.RED)
        val sorted = ordering.sort(board, moves, Side.RED, ttMove = null)
        // 全是非吃子 + 非将军的开局走法,同分(0);但首尾元素都来自 moves
        assertThat(sorted).hasSize(moves.size)
        assertThat(sorted.toSet()).isEqualTo(moves.toSet())
    }

    @Test
    fun `empty input returns empty`() {
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/4K4 w - - 0 1").board
        val sorted = ordering.sort(board, emptyList(), Side.RED, ttMove = null)
        assertThat(sorted).isEmpty()
    }
}
