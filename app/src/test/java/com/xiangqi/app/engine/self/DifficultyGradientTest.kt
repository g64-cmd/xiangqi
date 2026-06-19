package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Difficulty
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 4 档难度梯度验证。
 *
 * **行为契约**:
 * 1. 同一局面,深度越深的难度搜索的节点数应越多(depth 单调)
 * 2. 各档产出的 bestMove 必须合法
 * 3. 各档产出的搜索深度等于该档 Difficulty.depth
 *
 * 注:BEGINNER 与 ADVANCED 在简单局面下可能产出相同 bestMove
 * (depth=1 已能找到"明显最优"),这是合法行为,不强求差异。
 */
class DifficultyGradientTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)
    private val checkDetector = CheckDetector(gen)
    private val eval = Evaluation()
    private val ordering = MoveOrdering(gen, checkDetector)
    private val checkmate = CheckmateDetector(gen)

    private val fens = listOf(
        FenParser.INITIAL_FEN,
        "r1bakab1r/9/1cn4c1/p1p1p1p1p/9/9/P1P1P1P1P/1C2C1N2/9/RNBAKAB1R w - - 0 1",
        "2bak4/9/4b4/9/9/9/9/4N4/4C4/3AKA3 w - - 0 1",
        "r3kabr1/9/9/9/4C4/9/9/9/9/2BAKABR1 w - - 0 1",
    )

    @Test
    fun `node count increases monotonically with difficulty depth`() = runBlocking {
        for (fen in fens) {
            val parsed = FenParser.parse(fen)
            val beginnerNodes = searchOnce(parsed.board, parsed.sideToMove, Difficulty.BEGINNER)
            val intermediateNodes = searchOnce(parsed.board, parsed.sideToMove, Difficulty.INTERMEDIATE)
            val advancedNodes = searchOnce(parsed.board, parsed.sideToMove, Difficulty.ADVANCED)

            if (beginnerNodes > intermediateNodes) {
                throw AssertionError("FEN=$fen BEGINNER($beginnerNodes) > INTERMEDIATE($intermediateNodes)")
            }
            if (intermediateNodes > advancedNodes) {
                throw AssertionError("FEN=$fen INTERMEDIATE($intermediateNodes) > ADVANCED($advancedNodes)")
            }
        }
    }

    @Test
    fun `bestMove is legal at all difficulties`() = runBlocking {
        for (fen in fens) {
            val parsed = FenParser.parse(fen)
            for (d in Difficulty.entries) {
                // ANALYZE 是内部评估档,movetime 3000ms 在测试环境跑会超时,跳过
                if (d == Difficulty.ANALYZE) continue
                val result = newEngine().search(parsed.board, parsed.sideToMove, d)
                if (!legality.isLegal(parsed.board, result.bestMove)) {
                    throw AssertionError("FEN=$fen difficulty=$d bestMove=${result.bestMove} 不合法")
                }
            }
        }
    }

    @Test
    fun `result depth equals difficulty depth`() = runBlocking {
        for (fen in fens) {
            val parsed = FenParser.parse(fen)
            for (d in Difficulty.entries) {
                // ANALYZE 内部档 movetime 3000ms 在测试环境实际只跑到 4-6 层
                // (depth=12 是上限,迭代加深到 movetime 截止返回 lastComplete 深度)
                if (d == Difficulty.ANALYZE) continue
                val result = newEngine().search(parsed.board, parsed.sideToMove, d)
                if (result.depth != d.depth) {
                    throw AssertionError("FEN=$fen expected depth=${d.depth} actual=${result.depth}")
                }
            }
        }
    }

    @Test
    fun `HINT is deeper than INTERMEDIATE with bounded movetime`() {
        // 历史 HINT(2, 400) 是"浅快"档;现在调整为 (3, 1000) 让提示质量更接近
        // INTERMEDIATE 但仍远低于 ADVANCED。契约:
        // - depth >= INTERMEDIATE.depth(3) 保证 hint 价值
        // - movetime <= ADVANCED(1500) 避免提示按钮等太久
        assertThat(Difficulty.HINT.depth).isAtLeast(Difficulty.INTERMEDIATE.depth)
        assertThat(Difficulty.HINT.moveTimeMs).isAtMost(Difficulty.ADVANCED.moveTimeMs)
    }

    private suspend fun searchOnce(
        board: com.xiangqi.app.domain.model.Board,
        sideToMove: com.xiangqi.app.domain.model.Side,
        difficulty: Difficulty,
    ): Long = newEngine().search(board, sideToMove, difficulty).nodesSearched

    private fun newEngine(): SelfEngine =
        SelfEngine(gen, legality, eval, checkDetector, checkmate, ordering)
}
