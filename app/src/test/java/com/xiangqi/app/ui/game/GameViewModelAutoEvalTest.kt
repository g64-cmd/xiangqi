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
 * 走子后自动评估契约(M6):
 * - 走子后 currentScore 被填充
 * - 红方走完后(sideToMove=BLACK)analyze 返回黑方视角,存储取负转红方视角
 * - onUndo 同步 dropLast
 * - onRestart 清空
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelAutoEvalTest {

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
        engine: Engine,
        config: GameConfig = GameConfig(mode = GameMode.HOT_SEAT),
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
    fun `走子后 currentScore 被填充`() = runTest {
        val engine = AnalyzeFakeEngine(scoreFor = { 50f })
        val vm = newVm(engine)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.currentScore).isNotNull()
        assertThat(s.evalHistory).hasSize(1)
    }

    @Test
    fun `红方走完后 analyze 黑方视角, 存储取负成红方视角`() = runTest {
        // 红方走完 -> sideToMove=BLACK -> analyze 返回 BLACK 视角 +80(黑优)
        // 规范化取负 -> -80(红方视角,红劣)
        val engine = AnalyzeFakeEngine(scoreFor = { 80f })
        val vm = newVm(engine)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.currentScore).isEqualTo(-80f)
    }

    @Test
    fun `黑方走完后 analyze 红方视角保留`() = runTest {
        // 红方走 + 黑方走 -> 第二步后 sideToMove=RED -> analyze 返回 RED 视角 +80
        // 规范化保留 -> +80
        val engine = AnalyzeFakeEngine(scoreFor = { 80f })
        val vm = newVm(engine)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 7))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 7))
        advanceUntilIdle()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.currentScore).isEqualTo(80f)
        assertThat(s.evalHistory).hasSize(2)
        assertThat(s.evalHistory.first()).isEqualTo(-80f) // 第一步:红走后取负
        assertThat(s.evalHistory.last()).isEqualTo(80f) // 第二步:黑走后保留
    }

    @Test
    fun `onUndo 同步 dropLast`() = runTest {
        val engine = AnalyzeFakeEngine(scoreFor = { 30f })
        val vm = newVm(engine)
        // 红黑各走一步
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 7))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 7))
        advanceUntilIdle()
        advanceUntilIdle()
        val sizeBefore = snapshot(vm).evalHistory.size
        vm.onUndo()
        advanceUntilIdle()
        val sizeAfter = snapshot(vm).evalHistory.size
        assertThat(sizeAfter).isLessThan(sizeBefore)
    }

    @Test
    fun `onRestart 清空 evalHistory 与 currentScore`() = runTest {
        val engine = AnalyzeFakeEngine(scoreFor = { 30f })
        val vm = newVm(engine)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        vm.onRestart()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.evalHistory).isEmpty()
        assertThat(s.currentScore).isNull()
    }

    @Test
    fun `analyze 抛异常不崩溃且保留上次分数`() = runTest {
        val engine = AnalyzeFakeEngine(scoreFor = { 30f })
        val vm = newVm(engine)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        advanceUntilIdle()
        val before = snapshot(vm).currentScore
        // 让后续 ANALYZE search 抛 EngineUnavailableException(launchEngine 会 catch)
        engine.throwOnNext = true
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 7))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 7))
        advanceUntilIdle()
        advanceUntilIdle()
        val s = snapshot(vm)
        // 异常不崩溃,分数不变(launchEngine catch 后 _toast,不更新 _currentScore)
        assertThat(s.currentScore).isEqualTo(before)
    }
}

private class AnalyzeFakeEngine(
    private val scoreFor: (Side) -> Float,
) : Engine {
    var throwOnNext = false

    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        delay(1L)
        if (throwOnNext && difficulty == Difficulty.ANALYZE) {
            throw com.xiangqi.app.engine.EngineUnavailableException("fake analyze failure")
        }
        val gen = MoveGeneratorImpl()
        val pseudo = gen.movesFor(board, sideToMove)
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val legal = legality.legalMoves(board, pseudo)
        val move = legal.first()
        return EngineResult(
            bestMove = move,
            score = scoreFor(sideToMove).toInt(),
            depth = 1,
            pv = listOf(move),
            nodesSearched = 1L,
            timeMs = 0L,
            isMate = false,
            mateInPlies = null,
        )
    }
}
