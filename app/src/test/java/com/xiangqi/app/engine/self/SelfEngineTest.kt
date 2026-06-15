package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Difficulty
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class SelfEngineTest {

    private fun buildEngine(): SelfEngine {
        val gen = MoveGeneratorImpl()
        val legality = MoveLegality(gen)
        val checkDetector = CheckDetector(gen)
        val eval = Evaluation()
        val ordering = MoveOrdering(gen, checkDetector)
        val checkmate = CheckmateDetector(gen)
        return SelfEngine(gen, legality, eval, checkDetector, checkmate, ordering)
    }

    @Test
    fun `search returns non-null bestMove on initial position`() = runBlocking {
        val engine = buildEngine()
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val result = engine.search(board, Side.RED, Difficulty.INTERMEDIATE)
        assertThat(result.bestMove).isNotNull()
        assertThat(result.depth).isAtLeast(1)
        assertThat(result.nodesSearched).isGreaterThan(0L)
    }

    @Test
    fun `search info flow is updated during iteration`() = runBlocking {
        val engine = buildEngine()
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        engine.search(board, Side.RED, Difficulty.INTERMEDIATE)
        // 完成后 info 应至少有一次更新(depth >= 1)
        val info = engine.info.value
        assertThat(info).isNotNull()
        assertThat(info!!.depth).isAtLeast(1)
    }

    @Test
    fun `short movetime still completes one iteration`() = runBlocking {
        val engine = buildEngine()
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        // BEGINNER depth=1, movetime=100ms;应能完成 depth=1
        val result = engine.search(board, Side.RED, Difficulty.BEGINNER)
        assertThat(result.bestMove).isNotNull()
        assertThat(result.depth).isAtLeast(1)
    }

    @Test
    fun `search completes within wall-clock timeout`() = runBlocking {
        val engine = buildEngine()
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        // 5s 整次搜索的上限保护(实际 movetime 上限是 1500ms)
        withTimeout(5_000L) {
            val result = engine.search(board, Side.RED, Difficulty.ADVANCED)
            assertThat(result.bestMove).isNotNull()
        }
    }

    @Test
    fun `engine type is SELF`() {
        val engine = buildEngine()
        assertThat(engine.type).isEqualTo(com.xiangqi.app.engine.EngineType.SELF)
    }
}
