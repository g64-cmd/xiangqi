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
 * onPlayHint 契约(M7):选中 HintBar 候选走子 + 清空候选列表,
 * 走完后 history 增加 1 步。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelHintCandidatesTest {

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

    private fun newVm(engine: Engine): GameViewModel {
        val gen = MoveGeneratorImpl()
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val checkmate = CheckmateDetector(gen, check, legality)
        val repo = GameRepository(gen, legality, checkmate)
        val holder = GameConfigHolder()
        holder.set(GameConfig(mode = GameMode.HOT_SEAT, enableAnalysis = false))
        val provider = EngineProvider { _ -> engine }
        return GameViewModel(repo, gen, legality, provider, holder).also {
            it.engineDispatcher = testDispatcher
        }
    }

    @Test
    fun `onHint 填充 suggestions 多候选`() = runTest {
        val engine = FakeMultiHintEngine()
        val vm = newVm(engine)
        vm.onHint()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.suggestions).hasSize(3)
    }

    @Test
    fun `onPlayHint 走候选且清空 suggestions`() = runTest {
        val engine = FakeMultiHintEngine()
        val vm = newVm(engine)
        vm.onHint()
        advanceUntilIdle()
        val beforeHistory = snapshot(vm).lastMove
        assertThat(beforeHistory).isNull() // 开局还未走子

        // 选第 0 个候选(主推荐)
        vm.onPlayHint(0)
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.suggestions).isEmpty()
        // history 应有 1 步:lastMove 非 null
        assertThat(s.lastMove).isNotNull()
    }

    @Test
    fun `onPlayHint 越界索引忽略`() = runTest {
        val engine = FakeMultiHintEngine()
        val vm = newVm(engine)
        vm.onHint()
        advanceUntilIdle()
        val candidates = snapshot(vm).suggestions
        // 索引超界不应走子也不应清空
        vm.onPlayHint(candidates.size + 5)
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.suggestions).isNotEmpty() // 保留候选
        assertThat(s.lastMove).isNull() // 没走子
    }
}

private class FakeMultiHintEngine : Engine {
    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        delay(1L)
        val move = firstLegalMove(board, sideToMove)
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

    override suspend fun hintCandidates(
        board: Board,
        sideToMove: Side,
        n: Int,
    ): List<Move> {
        delay(1L)
        val gen = MoveGeneratorImpl()
        val pseudo = gen.movesFor(board, sideToMove)
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val legal = legality.legalMoves(board, pseudo)
        return legal.take(n)
    }

    private fun firstLegalMove(board: Board, sideToMove: Side): Move {
        val gen = MoveGeneratorImpl()
        val pseudo = gen.movesFor(board, sideToMove)
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val legal = legality.legalMoves(board, pseudo)
        return legal.first()
    }
}
