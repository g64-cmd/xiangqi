package com.xiangqi.app.domain.movegen

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.rules.MoveLegality
import org.junit.Test

/**
 * Perft (Performance Test) —— 验证走法生成器正确性。
 *
 * 给定一个局面,在深度 d 下,所有合法走法序列的总数应该等于已知参考值。
 * 若不等,说明某种棋子的走法生成有 bug。
 *
 * 中国象棋初始局面 Perft 参考值(主流开源引擎一致):
 *
 * | 深度 | 节点数 |
 * |---|---|
 * | 1 | 44 |
 * | 2 | 1,920 |
 * | 3 | 79,666 |
 *
 * 参考实现:[ElephantArt](https://github.com/CGLemon/ElephantArt)、xqbase 规范。
 */
class PerftTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)

    @Test
    fun `perft depth 1 from initial position equals 44`() {
        assertThat(perft(FenParser.parse(FenParser.INITIAL_FEN).board, Side.RED, 1)).isEqualTo(44L)
    }

    @Test
    fun `perft depth 2 from initial position equals 1920`() {
        assertThat(perft(FenParser.parse(FenParser.INITIAL_FEN).board, Side.RED, 2)).isEqualTo(1920L)
    }

    @Test
    fun `perft depth 3 from initial position equals 79666`() {
        assertThat(perft(FenParser.parse(FenParser.INITIAL_FEN).board, Side.RED, 3)).isEqualTo(79666L)
    }

    private fun perft(board: Board, side: Side, depth: Int): Long {
        if (depth == 0) return 1L
        val pseudo = gen.movesFor(board, side)
        val legal = legality.legalMoves(board, pseudo)
        if (depth == 1) return legal.size.toLong()
        var total = 0L
        for (move in legal) {
            total += perft(board.applyMove(move), side.opponent, depth - 1)
        }
        return total
    }
}
