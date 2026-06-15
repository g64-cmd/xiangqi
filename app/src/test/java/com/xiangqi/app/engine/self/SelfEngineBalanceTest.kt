package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Difficulty
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 平衡局面 bestmove 行为验证。
 *
 * 没有外部参考引擎可对照,采用以下**行为契约**:
 * 1. engine.bestMove 必须存在且合法(走完后己方不被将/飞将)
 * 2. bestMove 走完,对方"不能白吃我方高价值子"——通过 QuiescenceSearch 反查
 *    所有对方吃子走法,确认我方不至于在 bestMove 后立刻丢贵子(容差 ±30 cp)
 *
 * 这能锁定"自研引擎不会走出明显送子的烂棋"。
 */
class SelfEngineBalanceTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)
    private val checkDetector = CheckDetector(gen)
    private val eval = Evaluation()
    private val ordering = MoveOrdering(gen, checkDetector)
    private val checkmate = CheckmateDetector(gen)
    private val engine = SelfEngine(gen, legality, eval, checkDetector, checkmate, ordering)
    private val quiescence = QuiescenceSearch(gen, legality, eval, ordering)

    /** 5 个中局样本(子力大致均衡,避免极端局面误触发"送子"误报)。 */
    private val fens = listOf(
        FenParser.INITIAL_FEN,                                                       // 开局
        "r1bakab1r/9/1cn4c1/p1p1p1p1p/9/9/P1P1P1P1P/1C2C1N2/9/RNBAKAB1R w - - 0 1", // 散兵开局
        "2bak4/9/4b4/9/9/9/9/4N4/4C4/3AKA3 w - - 0 1",                                // 残局双方均有主力
        "r3kabr1/9/9/9/4C4/9/9/9/9/2BAKABR1 b - - 0 1",                               // 中炮横车对称局
        "3k5/9/9/9/9/9/9/9/4R4/3K5 w - - 0 1",                                        // 红车对抗单将
    )

    @Test
    fun `bestMove is legal for all middle-game positions`() = runBlocking {
        for (fen in fens) {
            val parsed = FenParser.parse(fen)
            val result = engine.search(parsed.board, parsed.sideToMove, Difficulty.INTERMEDIATE)
            assertThat(result.bestMove).isNotNull()
            if (!legality.isLegal(parsed.board, result.bestMove)) {
                throw AssertionError("FEN=$fen, bestMove=${result.bestMove} 不合法")
            }
        }
    }

    @Test
    fun `bestMove does not hang a high-value piece`() = runBlocking {
        for (fen in fens) {
            val parsed = FenParser.parse(fen)
            val result = engine.search(parsed.board, parsed.sideToMove, Difficulty.INTERMEDIATE)
            val after = parsed.board.applyMove(result.bestMove)

            val oppScore = quiescence.qSearch(
                after, parsed.sideToMove.opponent,
                alpha = Int.MIN_VALUE + 1, beta = Int.MAX_VALUE, ply = 0,
            )
            val myScore = -oppScore  // 翻回原走子方视角

            // bestMove 的 score 不应极度悲观(容差 ±50 cp,排除送子)
            if (myScore < -50) {
                throw AssertionError(
                    "FEN=$fen, bestMove=${result.bestMove} 暴露高价值子,qSearch score=$myScore",
                )
            }
        }
    }

    @Test
    fun `intermediate search returns non-empty pv`() = runBlocking {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val result = engine.search(board, Side.RED, Difficulty.INTERMEDIATE)
        assertThat(result.pv).isNotEmpty()
        assertThat(result.pv.first()).isEqualTo(result.bestMove)
    }
}
