package com.xiangqi.app.ui.game

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
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
 * [GameViewModel] 人机模式契约:AI 自动应招、思考门控、悔棋语义。
 *
 * 关键技巧:用 [StandardTestDispatcher] 替换 Main;[FakeEngine] 的 `search` 用
 * `delay` 切回 Main 调度器等待,从而让 `advanceUntilIdle` 能控制"思考进度"。
 * 实际生产环境里 engine 跑在 `Dispatchers.Default`,这里只测状态机转换,
 * 不真的进 Default 调度(深度搜索已有 `engine/self` 下的测试覆盖)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelAiTest {

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
        val j = kotlinx.coroutines.coroutineScope {
            val inner = launch { vm.uiState.collect { collected += it } }
            testDispatcher.scheduler.advanceUntilIdle()
            inner.cancel()
            inner.join()
        }
        return collected.last()
    }

    private fun newAiVm(
        engine: Engine = FakeEngine(),
        holderConfig: GameConfig = GameConfig(
            mode = GameMode.HUMAN_VS_AI,
            humanSide = Side.RED,
            difficulty = Difficulty.INTERMEDIATE,
        ),
    ): Pair<GameViewModel, GameConfigHolder> {
        val gen = MoveGeneratorImpl()
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val checkmate = CheckmateDetector(gen, check, legality)
        val repo = GameRepository(gen, legality, checkmate)
        val holder = GameConfigHolder()
        holder.set(holderConfig)
        // 用 FakeEngine 包装一个固定 EngineProvider,让所有 type 都返回同一 Fake
        val provider = EngineProvider { _ -> engine }
        return GameViewModel(repo, gen, legality, provider, holder, testSoundManager(), check, checkmate) to holder
    }

    @Test
    fun `AI 在玩家走完后自动应招`() = runTest {
        val fake = FakeEngine()
        val (vm, _) = newAiVm(fake)
        // 红方玩家走炮 h2e2
        vm.onTap(Position(7, 2))
        vm.onTap(Position(4, 2))
        advanceUntilIdle()
        // AI 应招后 fake.searchCount 应 >= 1,轮次回 RED
        assertThat(fake.searchCount).isAtLeast(1)
        val s = snapshot(vm)
        assertThat(s.sideToMove).isEqualTo(Side.RED)
    }

    @Test
    fun `AI 思考期间 canInteract == false`() = runTest {
        val fake = FakeEngine(minDelay = 50L) // 思考稍微拖延
        val (vm, _) = newAiVm(fake)
        vm.onTap(Position(7, 2))
        vm.onTap(Position(4, 2))
        // 不 advanceUntilIdle,先在思考期间订阅一次
        val collected = mutableListOf<GameUiState>()
        val j = launch { vm.uiState.collect { collected += it } }
        testDispatcher.scheduler.advanceUntilIdle()
        // 应该有 thinking=true 的状态出现
        assertThat(collected.any { it.isAiThinking }).isTrue()
        assertThat(collected.any { !it.canInteract }).isTrue()
        j.cancel()
        j.join()
    }

    @Test
    fun `AI 思考结束后 searchInfo 被清空`() = runTest {
        val fake = FakeEngine().apply {
            infoToPush = SearchInfo(depth = 2, score = 50, pv = emptyList(), nodes = 100, timeMs = 5)
        }
        val (vm, _) = newAiVm(fake)
        vm.onTap(Position(7, 2))
        vm.onTap(Position(4, 2))
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.searchInfo).isNull()
    }

    @Test
    fun `onUndo 在人机模式悔两步`() = runTest {
        val fake = FakeEngine()
        val (vm, _) = newAiVm(fake)
        // 玩家走炮 → AI 应招(共 2 ply)
        vm.onTap(Position(7, 2))
        vm.onTap(Position(4, 2))
        advanceUntilIdle()
        assertThat(snapshot(vm).sideToMove).isEqualTo(Side.RED) // 玩家又轮到走
        vm.onUndo()
        advanceUntilIdle()
        val s = snapshot(vm)
        // 悔两步回到开局
        assertThat(s.canUndo).isFalse()
        assertThat(s.lastMove).isNull()
    }

    @Test
    fun `onRestart 取消 aiJob 并清空 resigned`() = runTest {
        val fake = FakeEngine(minDelay = 100L)
        val (vm, _) = newAiVm(fake)
        vm.onTap(Position(7, 2))
        vm.onTap(Position(4, 2))
        // 思考中触发 onRestart
        vm.onRestart()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.isAiThinking).isFalse()
        assertThat(s.sideToMove).isEqualTo(Side.RED)
        assertThat(s.canUndo).isFalse()
    }
}

/**
 * 假 Engine:立即(或可选 delay)返回一个预设合法走法。
 *
 * 走法选择策略:从当前局面 MoveGenerator 给出 sideToMove 的第一个伪合法 + 合法走法。
 * 简单可靠,能通过 GameRepository.applyMove 的双重校验。
 */
private class FakeEngine(
    private val minDelay: Long = 0L,
) : Engine {
    private var _searchCount = 0
    val searchCount: Int get() = _searchCount
    var infoToPush: SearchInfo? = null

    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        _searchCount++
        infoToPush?.let { _info.value = it }
        if (minDelay > 0) delay(minDelay)
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
