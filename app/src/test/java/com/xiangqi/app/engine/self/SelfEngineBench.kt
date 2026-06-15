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
import org.junit.Ignore
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * 性能基准测试。
 *
 * **不进 CI**(标 [Ignore] 跳过),由开发者本地手动执行:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "com.xiangqi.app.engine.self.SelfEngineBench" \
 *     -PunignoreBench=true
 * ```
 *
 * 验证 INTERMEDIATE(depth=3)在桌面 JVM 上 < 1s,ADVANCED(depth=4)在 ~2s 量级。
 * 实测数据记入 `doc/dev-log.md`。
 */
@Ignore("性能基准,本地手动运行")
class SelfEngineBench {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)
    private val checkDetector = CheckDetector(gen)
    private val eval = Evaluation()
    private val ordering = MoveOrdering(gen, checkDetector)
    private val checkmate = CheckmateDetector(gen)
    private val engine = SelfEngine(gen, legality, eval, checkDetector, checkmate, ordering)

    @Test
    fun `depth 3 from initial position under 1 second`() = runBlocking {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val ms = measureTimeMillis {
            val result = engine.search(board, Side.RED, Difficulty.INTERMEDIATE)
            println("[Bench] depth=3 bestMove=${result.bestMove} score=${result.score} nodes=${result.nodesSearched}")
        }
        println("[Bench] depth=3 用时 ${ms}ms")
        assertThat(ms).isLessThan(1_000L)
    }

    @Test
    fun `depth 4 from initial position under 3 seconds`() = runBlocking {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val ms = measureTimeMillis {
            val result = engine.search(board, Side.RED, Difficulty.ADVANCED)
            println("[Bench] depth=4 bestMove=${result.bestMove} score=${result.score} nodes=${result.nodesSearched}")
        }
        println("[Bench] depth=4 用时 ${ms}ms")
        // depth=4 在桌面 JVM 经验 ~1-2s,设 3s 留余量
        assertThat(ms).isLessThan(3_000L)
    }
}
