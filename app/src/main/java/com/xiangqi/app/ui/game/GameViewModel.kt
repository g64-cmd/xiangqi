package com.xiangqi.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.data.game.GameState
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.EngineProvider
import com.xiangqi.app.engine.SearchInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * GameScreen 的状态机。
 *
 * 数据流:`repo.state` (单一真相源) + 本地 `_selected` / `_legalTargets` /
 * `_isAiThinking` / `_searchInfo` + `configHolder.config` → combine 成 [uiState]。
 *
 * **选择状态机** (onTap):
 * 1. 游戏已结束 / AI 思考中 / AI 回合 → 忽略。
 * 2. 无选中 + 点己方棋 → 选中 + 算合法目标。
 * 3. 已选中 + 点同格 → 取消选中。
 * 4. 已选中 + 点合法目标 → 走子 + 清选中。
 * 5. 已选中 + 点另一颗己方棋 → 切换选中。
 * 6. 其他 → 清选中。
 *
 * **AI 自动应招**:[init] 启动 `repo.state` collector,人机模式下检测到对手回合
 * 即 `launchAiMove`。AI 走子通过 `repo.applyMove` 复用既有的合法性校验。
 *
 * **悔棋语义**:HOT_SEAT 退 1 ply;HUMAN_VS_AI 退 2 ply(玩家总是面对"自己刚
 * 走完,AI 还没应"的局面)。
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val repo: GameRepository,
    private val moveGenerator: MoveGenerator,
    private val moveLegality: MoveLegality,
    private val engineProvider: EngineProvider,
    private val configHolder: GameConfigHolder,
) : ViewModel() {

    private val _selected = MutableStateFlow<Position?>(null)
    private val _legalTargets = MutableStateFlow<Set<Position>>(emptySet())
    private val _isAiThinking = MutableStateFlow(false)
    private val _searchInfo = MutableStateFlow<SearchInfo?>(null)
    private val _resigned = MutableStateFlow<GameResult?>(null)
    private var aiJob: Job? = null

    val uiState: StateFlow<GameUiState> =
        combine(
            repo.state,
            _selected,
            _legalTargets,
            configHolder.config,
            _isAiThinking,
        ) { gs, sel, targets, cfg, thinking ->
            mapUiState(gs, sel, targets, cfg, thinking)
        }.combine(_searchInfo) { state, info ->
            state.copy(searchInfo = info)
        }.combine(_resigned) { state, resigned ->
            if (resigned != null) state.copy(result = resigned) else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    init {
        viewModelScope.launch {
            repo.state.collect { s -> maybeLaunchAi(s) }
        }
    }

    private fun mapUiState(
        gs: GameState,
        sel: Position?,
        targets: Set<Position>,
        cfg: GameConfig,
        thinking: Boolean,
    ): GameUiState {
        val interact = canInteract(gs, cfg, thinking)
        return GameUiState(
            board = gs.board,
            sideToMove = gs.sideToMove,
            selected = sel,
            legalTargets = if (sel != null && interact) targets else emptySet(),
            lastMove = gs.history.lastOrNull()?.move,
            result = gs.result,
            canUndo = gs.history.isNotEmpty() && !thinking,
            orientation = cfg.orientation,
            mode = cfg.mode,
            humanSide = cfg.humanSide,
            isAiThinking = thinking,
            searchInfo = _searchInfo.value,
            canInteract = interact,
        )
    }

    private fun canInteract(s: GameState, cfg: GameConfig, thinking: Boolean): Boolean {
        if (thinking) return false
        if (s.result !is GameResult.ONGOING) return false
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove != cfg.humanSide) return false
        return true
    }

    private fun maybeLaunchAi(s: GameState) {
        val cfg = configHolder.config.value
        if (cfg.mode != GameMode.HUMAN_VS_AI) return
        if (s.result !is GameResult.ONGOING) return
        if (s.sideToMove != cfg.humanSide.opponent) return
        if (_isAiThinking.value) return
        launchAiMove(s)
    }

    private fun launchAiMove(s: GameState) {
        aiJob?.cancel()
        _isAiThinking.value = true
        val cfg = configHolder.config.value
        val engine = engineProvider.provide(cfg.engineType)
        aiJob = viewModelScope.launch(Dispatchers.Default) {
            val infoCollector = launch { engine.info.collect { _searchInfo.value = it } }
            try {
                val result = engine.search(
                    board = s.board,
                    sideToMove = s.sideToMove,
                    difficulty = cfg.difficulty,
                )
                repo.applyMove(result.bestMove)
            } catch (_: CancellationException) {
                // 取消时静默退出
            } finally {
                infoCollector.cancel()
                _searchInfo.value = null
                _isAiThinking.value = false
            }
        }
    }

    fun onTap(position: Position) {
        val s = repo.state.value
        if (s.result !is GameResult.ONGOING) return
        if (_isAiThinking.value) return
        val cfg = configHolder.config.value
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove != cfg.humanSide) return

        val sel = _selected.value
        val pieceAtTap = s.board[position]

        when {
            sel == null && pieceAtTap?.side == s.sideToMove ->
                select(position)
            sel != null && position == sel ->
                clearSelection()
            sel != null && position in _legalTargets.value -> {
                repo.applyMove(Move(sel, position, s.sideToMove))
                clearSelection()
            }
            sel != null && pieceAtTap?.side == s.sideToMove ->
                select(position)
            else ->
                clearSelection()
        }
    }

    fun onUndo() {
        if (_isAiThinking.value) return
        clearSelection()
        val cfg = configHolder.config.value
        val historySize = repo.state.value.history.size
        if (cfg.mode == GameMode.HUMAN_VS_AI && historySize >= 2) {
            repo.undo(); repo.undo()
        } else {
            repo.undo()
        }
    }

    fun onRestart() {
        aiJob?.cancel()
        clearSelection()
        _resigned.value = null
        repo.restart()
    }

    /**
     * 玩家主动认输。用本地 _resigned 字段叠加在 uiState.result,不污染 repo;
     * 优先级最高。重启时清空。
     */
    fun onResign() {
        if (_isAiThinking.value) return
        val s = repo.state.value
        if (s.result !is GameResult.ONGOING) return
        _resigned.value = when (s.sideToMove) {
            Side.RED -> GameResult.BlackWin
            Side.BLACK -> GameResult.RedWin
        }
    }

    private fun select(p: Position) {
        val s = repo.state.value
        val moves = moveGenerator.movesFrom(s.board, p)
        val legal = moveLegality.legalMoves(s.board, moves).map { it.to }.toSet()
        _selected.value = p
        _legalTargets.value = legal
    }

    private fun clearSelection() {
        _selected.value = null
        _legalTargets.value = emptySet()
    }

    private fun initialState(): GameUiState {
        val f = FenParser.parse(FenParser.INITIAL_FEN)
        return GameUiState(board = f.board, sideToMove = f.sideToMove)
    }
}
