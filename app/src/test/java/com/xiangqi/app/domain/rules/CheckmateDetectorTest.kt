package com.xiangqi.app.domain.rules

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import org.junit.Test

class CheckmateDetectorTest {

    private val gen = MoveGeneratorImpl()
    private val detector = CheckmateDetector(gen)

    /**
     * 杀局:双车 + 同列夹将。
     *
     * 黑将 (0,9) 在角落,红车 (0,0) 沿 col=0 攻击黑将;红车 (1,1) 沿 col=1 攻击 (1,9)。
     * 黑将的两个逃格都被覆盖,无路可逃。
     */
    @Test
    fun `checkmate corner king trapped by two rooks`() {
        val board = FenParser.parse("k8/9/9/9/9/9/9/9/1R7/R8 b - - 0 1").board
        // 段 0 (row=9): (0,9) 黑将
        // 段 1 (row=8): 空
        // 段 8 (row=1): (1,1) 红车
        // 段 9 (row=0): (0,0) 红车
        // 黑将 (0,9):
        //   (1,9) 被红车 (1,1) 沿 col=1 攻击 → illegal
        //   (0,8) 被红车 (0,0) 沿 col=0 攻击 → illegal
        // 无路 → checkmate
        assertThat(detector.isCheckmate(board, Side.BLACK)).isTrue()
        assertThat(detector.decide(board, Side.BLACK)).isEqualTo(GameResult.RedWin)
    }

    /**
     * 非杀局:开局。
     */
    @Test
    fun `initial position is ongoing`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        assertThat(detector.decide(board, Side.RED)).isEqualTo(GameResult.ONGOING)
        assertThat(detector.decide(board, Side.BLACK)).isEqualTo(GameResult.ONGOING)
    }

    /**
     * 将军但未将死(将可逃)。
     *
     * 黑将 (4,9),红车 (4,0) 沿 col=4 攻击 → 将军。
     * 黑将可逃 (3,9) 或 (5,9),这两个位置不被红车攻击。
     */
    @Test
    fun `check but not mate`() {
        val board = FenParser.parse("4k4/9/9/9/9/9/9/9/9/4R4 b - - 0 1").board
        // 段 0 (row=9): (4,9) 黑将
        // 段 9 (row=0): (4,0) 红车
        // 黑将可逃 (3,9)(5,9),不被攻击
        assertThat(detector.isCheckmate(board, Side.BLACK)).isFalse()
        assertThat(detector.decide(board, Side.BLACK)).isEqualTo(GameResult.ONGOING)
    }

    /**
     * Sanity:开局红方所有合法走法走完后,黑方仍 ongoing(开局不可能一步杀)。
     */
    @Test
    fun `no first move produces checkmate or stalemate for black`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val legality = MoveLegality(gen)
        for (move in legality.legalMoves(board, gen.movesFor(board, Side.RED))) {
            val after = board.applyMove(move)
            val result = detector.decide(after, Side.BLACK)
            assertThat(result).isEqualTo(GameResult.ONGOING)
        }
    }
}
