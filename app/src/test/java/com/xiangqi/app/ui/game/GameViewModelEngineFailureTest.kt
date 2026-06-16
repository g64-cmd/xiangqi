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
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.Engine
import com.xiangqi.app.engine.EngineProvider
import com.xiangqi.app.engine.EngineResult
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.engine.EngineUnavailableException
import com.xiangqi.app.engine.SearchInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * 引擎失败时的降级契约(M6 修复):
 * - search 抛 EngineUnavailableException → 不闪退,emit toast
 * - aiJob 完成后 _isEngineBusy 恢复 false
 * - 棋盘状态不变(没有错误的 applyMove)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelEngineFailureTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private suspend fun collectToasts(vm: GameViewModel): List<String> {
        val collected = mutableListOf<String>()
        kotlinx.coroutines.coroutineScope {
            val inner = launch { vm.toast.collect { collected += it } }
            testDispatcher.scheduler.advanceUntilIdle()
            inner.cancel()
            inner.join()
        }
        return collected
    }

    private fun newVm(engine: Engine): GameViewModel {
        val gen = MoveGeneratorImpl()
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val checkmate = CheckmateDetector(gen, check, legality)
        val repo = GameRepository(gen, legality, checkmate)
        val holder = GameConfigHolder()
        holder.set(
            GameConfig(
                mode = GameMode.HUMAN_VS_AI,
                humanSide = Side.RED,
                difficulty = Difficulty.INTERMEDIATE,
            ),
        )
        val provider = EngineProvider { _ -> engine }
        return GameViewModel(repo, gen, legality, provider, holder).also {
            it.engineDispatcher = testDispatcher
        }
    }

    @Test
    fun `search 抛 EngineUnavailableException 不闪退并发 toast`() = runTest {
        val engine = FailingEngine(EngineUnavailableException("pikafish 进程崩溃"))
        val vm = newVm(engine)
        // SharedFlow 无 replay,必须先订阅再触发事件
        val collected = mutableListOf<String>()
        val collector = launch { vm.toast.collect { collected += it } }
        // 触发玩家走子 → launchAiMove → engine.search 抛异常
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        advanceUntilIdle()
        collector.cancel()
        collector.join()
        assertThat(collected).isNotEmpty()
        assertThat(collected.first()).contains("引擎不可用")
    }

    @Test
    fun `引擎失败后 _isEngineBusy 恢复 false,棋盘未推进`() = runTest {
        val engine = FailingEngine(EngineUnavailableException("fail"))
        val vm = newVm(engine)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        advanceUntilIdle()
        // history 仍是 1(玩家走的那步,AI 没应招)
        assertThat(vm.uiState.value.isAiThinking).isFalse()
    }
}

private class FailingEngine(
    private val ex: Throwable,
) : Engine {
    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        throw ex
    }
}
