package com.xiangqi.app.ui.game

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.AnalysisScore
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.Engine
import com.xiangqi.app.engine.EngineProvider
import com.xiangqi.app.engine.EngineResult
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.engine.SearchInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * 局势评估开关(M7):关闭后进入"快打模式",auto-eval 完全跳过,
 * currentScore / evalHistory 保持空,玩家不再承担 ANALYZE 深搜延迟。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelAnalysisToggleTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private suspend fun snapshot(vm: GameViewModel): GameUiState {
        val collected = mutableListOf<GameUiState>()
        kotlinx.coroutines.coroutineScope {
            val inner = launch { vm.uiState.collect { collected += it } }
            testDispatcher.scheduler.advanceUntilIdle()
            inner.cancel()
            inner.join()
        }
        return collected.last()
    }

    private fun newVm(engine: Engine, enableAnalysis: Boolean): GameViewModel {
        val gen = MoveGeneratorImpl()
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val checkmate = CheckmateDetector(gen, check, legality)
        val repo = GameRepository(gen, legality, checkmate)
        val holder = GameConfigHolder()
        holder.set(GameConfig(mode = GameMode.HOT_SEAT, enableAnalysis = enableAnalysis))
        val provider = EngineProvider { _ -> engine }
        return GameViewModel(repo, gen, legality, provider, holder).also {
            it.engineDispatcher = testDispatcher
        }
    }

    @Test
    fun `关闭评估时走子不触发 analyze`() = runTest {
        val engine = AnalyzeCountingEngine()
        val vm = newVm(engine, enableAnalysis = false)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.currentScore).isNull()
        assertThat(s.evalHistory).isEmpty()
        assertThat(engine.analyzeCalls).isEqualTo(0)
    }

    @Test
    fun `开启评估时走子触发 analyze`() = runTest {
        val engine = AnalyzeCountingEngine()
        val vm = newVm(engine, enableAnalysis = true)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(engine.analyzeCalls).isGreaterThan(0)
        assertThat(s.currentScore).isNotNull()
        assertThat(s.evalHistory).hasSize(1)
    }
}

/**
 * 计数式 fake engine:只记录 analyze 被调用的次数,不依赖具体分数,
 * 用于断言"开关关闭时 analyze 根本不被调用"。
 */
private class AnalyzeCountingEngine : Engine {
    var analyzeCalls = 0

    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        delay(1L)
        val gen = MoveGeneratorImpl()
        val pseudo = gen.movesFor(board, sideToMove)
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val legal = legality.legalMoves(board, pseudo)
        val move = legal.first()
        return EngineResult(
            bestMove = move,
            score = 0,
            depth = 1,
            pv = listOf(move),
            nodesSearched = 1L,
            timeMs = 0L,
            isMate = false,
            mateInPlies = null,
        )
    }

    override suspend fun analyze(board: Board, sideToMove: Side): AnalysisScore {
        analyzeCalls += 1
        delay(1L)
        return AnalysisScore(0f, false, null)
    }
}
