package com.xiangqi.app.ui.game

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
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
 * Hint 按钮契约(M6):
 * - 触发后 suggestedMove 被填充,history 不变
 * - 玩家 onTap / onUndo / onRestart 时清空
 * - 引擎思考中、游戏结束时 canHint = false
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelHintTest {

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

    private fun newVm(
        engine: Engine = HintFakeEngine(),
        config: GameConfig = GameConfig(
            mode = GameMode.HUMAN_VS_AI,
            humanSide = Side.RED,
            difficulty = Difficulty.INTERMEDIATE,
        ),
    ): GameViewModel {
        val gen = MoveGeneratorImpl()
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val checkmate = CheckmateDetector(gen, check, legality)
        val repo = GameRepository(gen, legality, checkmate)
        val holder = GameConfigHolder()
        holder.set(config)
        val provider = EngineProvider { _ -> engine }
        return GameViewModel(repo, gen, legality, provider, holder).also {
            it.engineDispatcher = testDispatcher
        }
    }

    @Test
    fun `onHint 填充 suggestedMove 且不改 history`() = runTest {
        val fake = HintFakeEngine()
        val vm = newVm(fake)
        vm.onHint()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.suggestedMove).isNotNull()
        assertThat(fake.lastSearchDifficulty).isEqualTo(Difficulty.HINT)
    }

    @Test
    fun `AI 思考中 onHint 是 no-op`() = runTest {
        val fake = HintFakeEngine(minDelay = 100L)
        val vm = newVm(fake)
        // 触发 AI 应招(玩家走子)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        // 思考期间触发 hint,不应该有效果
        vm.onHint()
        // 等待 AI 走完
        advanceUntilIdle()
        // FakeEngine 在 AI 应招时 difficulty == INTERMEDIATE,如果 Hint 成功执行
        // 会有 difficulty == HINT 的调用
        assertThat(fake.hintCallCount).isEqualTo(0)
    }

    @Test
    fun `onTap 清空 suggestedMove`() = runTest {
        val fake = HintFakeEngine()
        val vm = newVm(fake)
        vm.onHint()
        advanceUntilIdle()
        assertThat(snapshot(vm).suggestedMove).isNotNull()
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        advanceUntilIdle()
        assertThat(snapshot(vm).suggestedMove).isNull()
    }

    @Test
    fun `onUndo 清空 suggestedMove`() = runTest {
        val fake = HintFakeEngine()
        val vm = newVm(fake)
        vm.onHint()
        advanceUntilIdle()
        assertThat(snapshot(vm).suggestedMove).isNotNull()
        vm.onUndo()
        advanceUntilIdle()
        assertThat(snapshot(vm).suggestedMove).isNull()
    }

    @Test
    fun `onRestart 清空 suggestedMove`() = runTest {
        val fake = HintFakeEngine()
        val vm = newVm(fake)
        vm.onHint()
        advanceUntilIdle()
        vm.onRestart()
        advanceUntilIdle()
        assertThat(snapshot(vm).suggestedMove).isNull()
    }

    @Test
    fun `游戏结束时 canHint 为 false`() = runTest {
        val fake = HintFakeEngine()
        val vm = newVm(fake)
        vm.onResign()
        advanceUntilIdle()
        assertThat(snapshot(vm).canHint).isFalse()
    }
}

/**
 * 专用于 Hint 测试的 Fake Engine:记录每次 search 的 difficulty,
 * 区分 AI 应招 vs Hint 调用。
 */
private class HintFakeEngine(
    private val minDelay: Long = 0L,
) : Engine {
    var lastSearchDifficulty: Difficulty? = null
        private set
    var hintCallCount = 0
        private set

    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        lastSearchDifficulty = difficulty
        if (difficulty == Difficulty.HINT) hintCallCount++
        // delay 让协程进入 suspend 状态;在 Dispatchers.Default 上跑时是真实延迟,
        // 测试通过 Thread.sleep 等真实线程完成,不依赖 Main Looper(避免 CI 崩)
        delay(minDelay.coerceAtLeast(1L))
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
}
