package com.xiangqi.app.ui.game

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Position
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [GameViewModel] 行为契约(HOT_SEAT 路径)。
 *
 * 由于 [GameRepository] / [com.xiangqi.app.domain.movegen.MoveGenerator] /
 * [com.xiangqi.app.domain.rules.MoveLegality] 都已分别有测试覆盖,这里只验证 ViewModel
 * 自身的状态投影 + 选择状态机的行为。AI 自动应招的契约见 [GameViewModelAiTest]。
 *
 * `uiState` 使用 `SharingStarted.WhileSubscribed`,所以测试里必须主动订阅才能让
 * combine 运行;否则只能拿到 `initialState()`。`viewModelScope` 默认绑定 Main 调度,
 * 因此用 [StandardTestDispatcher] + [Dispatchers.setMain] 把 Main 替换为测试调度,
 * `advanceUntilIdle()` 让 viewModelScope 跑完所有挂起任务。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private fun newVm(): GameViewModel {
        val gen = MoveGeneratorImpl()
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val checkmate = CheckmateDetector(gen, check, legality)
        val repo = GameRepository(gen, legality, checkmate)
        val holder = GameConfigHolder()
        // HOT_SEAT 模式 + NoopEngine 包装的 Provider,既有用例不受 AI 干扰
        holder.set(GameConfig(mode = GameMode.HOT_SEAT, enableAnalysis = false))
        return GameViewModel(repo, gen, legality, NoopProvider, holder, testSoundManager(), check, checkmate)
    }

    /** 在 runTest 内订阅 uiState 并等待 viewModelScope 跑完一轮,返回当前值。 */
    private suspend fun snapshot(vm: GameViewModel): GameUiState {
        val collected = mutableListOf<GameUiState>()
        val job = kotlinx.coroutines.coroutineScope {
            val j = launch { vm.uiState.collect { collected += it } }
            testDispatcher.scheduler.advanceUntilIdle()
            j.cancel()
            j.join()
        }
        return collected.last()
    }

    @Test
    fun `initial uiState shows RED to move with empty selection`() = runTest {
        val vm = newVm()
        val s = snapshot(vm)
        assertThat(s.sideToMove).isEqualTo(Side.RED)
        assertThat(s.selected).isNull()
        assertThat(s.legalTargets).isEmpty()
        assertThat(s.lastMove).isNull()
        assertThat(s.canUndo).isFalse()
        assertThat(s.orientation).isEqualTo(Side.RED)
    }

    @Test
    fun `tapping own piece selects it and exposes legal targets`() = runTest {
        val vm = newVm()
        // 点红炮 h2 (col=7,row=2)
        vm.onTap(Position(7, 2))
        val s = snapshot(vm)
        assertThat(s.selected).isEqualTo(Position(7, 2))
        assertThat(s.legalTargets).isNotEmpty()
    }

    @Test
    fun `tapping empty square with no selection does nothing`() = runTest {
        val vm = newVm()
        // 点河界中间(col=4,row=5,开局为空)
        vm.onTap(Position(4, 5))
        val s = snapshot(vm)
        assertThat(s.selected).isNull()
        assertThat(s.legalTargets).isEmpty()
    }

    @Test
    fun `tapping selected square deselects it`() = runTest {
        val vm = newVm()
        val p = Position(7, 2)
        vm.onTap(p)
        assertThat(snapshot(vm).selected).isEqualTo(p)
        vm.onTap(p)
        assertThat(snapshot(vm).selected).isNull()
    }

    @Test
    fun `tapping a legal target applies move and flips sideToMove`() = runTest {
        val vm = newVm()
        // 选中红炮 h2,然后点 e2(col=4,row=2,平中)
        vm.onTap(Position(7, 2))
        val targets = snapshot(vm).legalTargets
        assertThat(targets).contains(Position(4, 2))
        vm.onTap(Position(4, 2))
        val s = snapshot(vm)
        assertThat(s.sideToMove).isEqualTo(Side.BLACK)
        assertThat(s.selected).isNull()
        assertThat(s.lastMove).isNotNull()
        assertThat(s.canUndo).isTrue()
    }

    @Test
    fun `tapping enemy piece with no selection does nothing`() = runTest {
        val vm = newVm()
        // 黑方棋子 row=7 col=7(黑马),开局 RED 走,选对方棋无效
        vm.onTap(Position(7, 7))
        assertThat(snapshot(vm).selected).isNull()
    }

    @Test
    fun `undo reverts last move and clears selection`() = runTest {
        val vm = newVm()
        vm.onTap(Position(7, 2))
        vm.onTap(Position(4, 2))
        vm.onUndo()
        val s = snapshot(vm)
        assertThat(s.sideToMove).isEqualTo(Side.RED)
        assertThat(s.canUndo).isFalse()
        assertThat(s.lastMove).isNull()
    }

    @Test
    fun `restart resets to initial state`() = runTest {
        val vm = newVm()
        vm.onTap(Position(7, 2))
        vm.onTap(Position(4, 2))
        vm.onRestart()
        val s = snapshot(vm)
        assertThat(s.sideToMove).isEqualTo(Side.RED)
        assertThat(s.canUndo).isFalse()
        assertThat(s.lastMove).isNull()
    }
}

/**
 * 不干活的 Engine + 包装 Provider:HOT_SEAT 模式下永远不会被调用。
 * 若意外被调用,抛异常以暴露误用。
 */
private object NoopEngine : Engine {
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = MutableStateFlow<SearchInfo?>(null).asStateFlow()
    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult = error("NoopEngine should never be invoked in HOT_SEAT mode")
}

private object NoopProvider : EngineProvider {
    override fun provide(type: EngineType): Engine = NoopEngine
}

