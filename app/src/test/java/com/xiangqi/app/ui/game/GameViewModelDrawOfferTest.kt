package com.xiangqi.app.ui.game

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.GameResult
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
 * Draw Offer 契约(M6):
 * - HOT_SEAT:点求和立即和棋
 * - HUMAN_VS_AI:引擎浅搜,|score| < 30 接受、否则 _toast 拒绝
 * - AI 思考中 no-op
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelDrawOfferTest {

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
        config: GameConfig,
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
    fun `HOT_SEAT onDrawOffer 直接和棋`() = runTest {
        val engine = DrawFakeEngine(score = 0)
        val vm = newVm(
            engine,
            GameConfig(mode = GameMode.HOT_SEAT),
        )
        vm.onDrawOffer()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.result).isEqualTo(GameResult.Draw(com.xiangqi.app.domain.model.DrawReason.AGREED))
    }

    @Test
    fun `HUMAN_VS_AI 均势时 onDrawOffer 接受求和`() = runTest {
        val engine = DrawFakeEngine(score = 10) // |10| < 30,接受
        val vm = newVm(
            engine,
            GameConfig(
                mode = GameMode.HUMAN_VS_AI,
                humanSide = Side.RED,
                difficulty = Difficulty.INTERMEDIATE,
            ),
        )
        vm.onDrawOffer()
        advanceUntilIdle()
        val s = snapshot(vm)
        assertThat(s.result).isEqualTo(GameResult.Draw(com.xiangqi.app.domain.model.DrawReason.AGREED))
    }

    @Test
    fun `HUMAN_VS_AI 优势时 onDrawOffer 拒绝且 result 保持 ONGOING`() = runTest {
        val engine = DrawFakeEngine(score = 500) // |500| > 30,拒绝
        val vm = newVm(
            engine,
            GameConfig(
                mode = GameMode.HUMAN_VS_AI,
                humanSide = Side.RED,
                difficulty = Difficulty.INTERMEDIATE,
            ),
        )
        vm.onDrawOffer()
        advanceUntilIdle()
        // 拒绝后 _drawn 没被设,result 仍 ONGOING
        val s = snapshot(vm)
        assertThat(s.result).isInstanceOf(GameResult.ONGOING::class.java)
    }

    @Test
    fun `HUMAN_VS_AI AI 思考中 onDrawOffer 是 no-op`() = runTest {
        // 用 launchEngine 把 _isEngineBusy 占住
        val engine = DrawFakeEngine(score = 0, minDelay = 200L)
        val vm = newVm(
            engine,
            GameConfig(
                mode = GameMode.HUMAN_VS_AI,
                humanSide = Side.RED,
                difficulty = Difficulty.INTERMEDIATE,
            ),
        )
        // 触发 AI 思考(玩家走子)
        vm.onTap(com.xiangqi.app.domain.model.Position(7, 2))
        vm.onTap(com.xiangqi.app.domain.model.Position(4, 2))
        vm.onDrawOffer() // 思考中,应该被拦下
        advanceUntilIdle()
        // 思考期间调 onDrawOffer,DrawFakeEngine score=0 若被调用会触发求和;
        // 但 onDrawOffer 被 _isEngineBusy 拦下,result 应是 ONGOING 或 RedWin/BlackWin(正常走子),
        // 而不是 Draw AGREED
        val s = snapshot(vm)
        assertThat(s.result).isNotInstanceOf(GameResult.Draw::class.java)
    }
}

private class DrawFakeEngine(
    private val score: Int,
    private val minDelay: Long = 0L,
) : Engine {
    private val _info = MutableStateFlow<SearchInfo?>(null)
    override val type: EngineType = EngineType.SELF
    override val info: StateFlow<SearchInfo?> = _info.asStateFlow()

    override suspend fun search(
        board: Board,
        sideToMove: Side,
        difficulty: Difficulty,
    ): EngineResult {
        delay(minDelay.coerceAtLeast(1L))
        val gen = MoveGeneratorImpl()
        val pseudo = gen.movesFor(board, sideToMove)
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val legal = legality.legalMoves(board, pseudo)
        val move = legal.first()
        return EngineResult(
            bestMove = move,
            score = score,
            depth = 1,
            pv = listOf(move),
            nodesSearched = 1L,
            timeMs = 0L,
            isMate = false,
            mateInPlies = null,
        )
    }
}
