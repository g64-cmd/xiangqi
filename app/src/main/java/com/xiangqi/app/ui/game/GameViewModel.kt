package com.xiangqi.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.data.game.GameState
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.DrawReason
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.EngineProvider
import com.xiangqi.app.engine.EngineResult
import com.xiangqi.app.engine.SearchInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * `_isEngineBusy` / `_searchInfo` + `configHolder.config` → combine 成 [uiState]。
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
 * **引擎序列化不变量(M6 起)**:同一时刻只有 0 或 1 个 `engine.search` 在执行。
 * `launchEngine` 是所有 engine 入口(AI 自动应招 / Hint / 求和评估 / 手动 Analysis)
 * 的统一通道:设置 `_isEngineBusy=true` → 跑 `engine.search` on `Dispatchers.Default`
 * → `finally` 清旗标。`aiJob?.cancel()` 保证后到的覆盖前到的。
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
    private val _isEngineBusy = MutableStateFlow(false)
    private val _searchInfo = MutableStateFlow<SearchInfo?>(null)
    private val _resigned = MutableStateFlow<GameResult?>(null)
    private val _drawn = MutableStateFlow<GameResult?>(null)
    private val _suggestedMove = MutableStateFlow<Move?>(null)
    private val _evalHistory = MutableStateFlow<List<Float>>(emptyList())
    private val _currentScore = MutableStateFlow<Float?>(null)
    private val _showAnalysisDialog = MutableStateFlow(false)

    /** 短暂 UI 消息(典型:AI 拒绝求和的 toast)。extraBufferCapacity 防止背压丢消息。 */
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast = _toast
    private var aiJob: Job? = null
    /** 上次观察到 history 长度,用于检测新走子触发自动 eval。 */
    private var lastSeenHistorySize = 0

    val uiState: StateFlow<GameUiState> =
        combine(
            repo.state,
            _selected,
            _legalTargets,
            configHolder.config,
            _isEngineBusy,
        ) { gs, sel, targets, cfg, thinking ->
            mapUiState(gs, sel, targets, cfg, thinking)
        }.combine(_searchInfo) { state, info ->
            state.copy(searchInfo = info)
        }.combine(_resigned) { state, resigned ->
            if (resigned != null) state.copy(result = resigned) else state
        }.combine(_drawn) { state, drawn ->
            if (drawn != null) state.copy(result = drawn) else state
        }.combine(_suggestedMove) { state, hint ->
            state.copy(suggestedMove = hint)
        }.combine(_evalHistory) { state, history ->
            state.copy(evalHistory = history)
        }.combine(_currentScore) { state, score ->
            state.copy(currentScore = score)
        }.combine(_showAnalysisDialog) { state, show ->
            state.copy(showAnalysisDialog = show)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    init {
        viewModelScope.launch {
            repo.state.collect { s ->
                maybeLaunchAi(s)
                maybeAutoEval(s)
            }
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
        // result overlay:认输 / 求和让游戏"视为结束",所有派生门控都按终局处理
        val effectiveResult: GameResult = _drawn.value ?: _resigned.value ?: gs.result
        val canHintNow = !thinking &&
            effectiveResult is GameResult.ONGOING &&
            (cfg.mode == GameMode.HOT_SEAT || gs.sideToMove == cfg.humanSide)
        val canOfferDrawNow = !thinking &&
            effectiveResult is GameResult.ONGOING &&
            (cfg.mode == GameMode.HOT_SEAT || gs.sideToMove == cfg.humanSide)
        val canAnalyzeNow = !thinking && effectiveResult is GameResult.ONGOING
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
            suggestedMove = _suggestedMove.value,
            canHint = canHintNow,
            canOfferDraw = canOfferDrawNow,
            currentScore = _currentScore.value,
            evalHistory = _evalHistory.value,
            canAnalyze = canAnalyzeNow,
            showAnalysisDialog = _showAnalysisDialog.value,
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
        if (_isEngineBusy.value) return
        launchAiMove(s)
    }

    /**
     * 走子后自动评估当前局面,append 到 [_evalHistory] 并刷新 [_currentScore]。
     *
     * **不设** `_isEngineBusy`(否则会和 AI 应招互锁),走独立协程;内部
     * `aiJob?.join()` 等 AI 应招完成后才跑 analyze,避免 UCI 会话并发损坏。
     *
     * **POV 规范化**:analyze 返回 sideToMove 视角;走子后 sideToMove 是
     * "刚走完方的对手",所以如果走完的是红方(sideToMove=BLACK),分数取负
     * 让存储统一为红方视角。
     */
    private fun maybeAutoEval(s: GameState) {
        if (s.history.size <= lastSeenHistorySize) {
            // 没有新走子(可能 undo / restart 触发 emit),不重复跑 eval
            lastSeenHistorySize = s.history.size
            return
        }
        lastSeenHistorySize = s.history.size
        val cfg = configHolder.config.value
        val engine = engineProvider.provide(cfg.engineType)
        viewModelScope.launch(Dispatchers.Default) {
            // 等 AI 应招(若刚刚是人机模式)走完,保证 engine 单线程串行
            aiJob?.join()
            try {
                val raw = engine.analyze(s.board, s.sideToMove)
                // POV 规范化到红方视角:analyze 返回 sideToMove 视角,
                // sideToMove == BLACK(即红方刚走完)时取负转红方视角;
                // sideToMove == RED(黑方刚走完)时已是红方视角直接保留
                val redPov = if (s.sideToMove == Side.BLACK) -raw.scoreCp else raw.scoreCp
                _evalHistory.value = _evalHistory.value + redPov
                _currentScore.value = redPov
            } catch (_: CancellationException) {
                // 取消时静默
            } catch (_: Throwable) {
                // analyze 失败(子进程异常 / parse 失败)不崩溃,保留上次分数
            }
        }
    }

    private fun launchAiMove(s: GameState) {
        val cfg = configHolder.config.value
        launchEngine(s, cfg.difficulty, EngineKind.AI_MOVE) { result ->
            repo.applyMove(result.bestMove)
        }
    }

    /**
     * 所有 engine.search 入口的统一通道(M6 抽出)。设置 `_isEngineBusy=true`,
     * 在 Dispatchers.Default 跑 `engine.search`,完成后调用 [onResult](典型:
     * AI move → applyMove;Hint → 写入 _suggestedMove;求和评估 → 阈值判定)。
     * `aiJob?.cancel()` 保证序列化,后到的覆盖前到的。
     */
    private fun launchEngine(
        s: GameState,
        difficulty: Difficulty,
        @Suppress("UNUSED_PARAMETER") kind: EngineKind,
        onResult: (EngineResult) -> Unit,
    ) {
        aiJob?.cancel()
        _isEngineBusy.value = true
        val cfg = configHolder.config.value
        val engine = engineProvider.provide(cfg.engineType)
        aiJob = viewModelScope.launch(Dispatchers.Default) {
            val infoCollector = launch { engine.info.collect { _searchInfo.value = it } }
            try {
                val result = engine.search(
                    board = s.board,
                    sideToMove = s.sideToMove,
                    difficulty = difficulty,
                )
                onResult(result)
            } catch (_: CancellationException) {
                // 取消时静默退出
            } finally {
                infoCollector.cancel()
                _searchInfo.value = null
                _isEngineBusy.value = false
            }
        }
    }

    fun onTap(position: Position) {
        val s = repo.state.value
        if (s.result !is GameResult.ONGOING) return
        if (_isEngineBusy.value) return
        val cfg = configHolder.config.value
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove != cfg.humanSide) return

        // 玩家任何点击都视为"看过提示",清掉 Hint 箭头
        if (_suggestedMove.value != null) _suggestedMove.value = null

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
        if (_isEngineBusy.value) return
        clearSelection()
        _suggestedMove.value = null
        val cfg = configHolder.config.value
        val historySize = repo.state.value.history.size
        val undoCount = if (cfg.mode == GameMode.HUMAN_VS_AI && historySize >= 2) 2 else 1
        repeat(undoCount) { repo.undo() }
        // eval history 与 move history 对齐,同步 drop
        _evalHistory.value = _evalHistory.value.dropLast(undoCount.coerceAtMost(_evalHistory.value.size))
        _currentScore.value = _evalHistory.value.lastOrNull()
        lastSeenHistorySize = repo.state.value.history.size
    }

    fun onRestart() {
        aiJob?.cancel()
        clearSelection()
        _resigned.value = null
        _drawn.value = null
        _suggestedMove.value = null
        _evalHistory.value = emptyList()
        _currentScore.value = null
        _showAnalysisDialog.value = false
        lastSeenHistorySize = 0
        repo.restart()
    }

    /** 显示局势分析曲线 Dialog(数据来自 _evalHistory 走子后自动 eval 累积)。 */
    fun onShowAnalysis() {
        _showAnalysisDialog.value = true
    }

    fun onDismissAnalysis() {
        _showAnalysisDialog.value = false
    }

    /**
     * 玩家请求一步提示。仅当引擎空闲、对局进行中、轮到玩家方时可用。
     * 走 [Difficulty.HINT] 档浅搜,结果填入 [_suggestedMove] 由 BoardCanvas 画箭头。
     */
    fun onHint() {
        val s = repo.state.value
        if (s.result !is GameResult.ONGOING) return
        if (_isEngineBusy.value) return
        val cfg = configHolder.config.value
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove != cfg.humanSide) return
        launchEngine(s, Difficulty.HINT, EngineKind.HINT) { result ->
            _suggestedMove.value = result.bestMove
        }
    }

    /**
     * 玩家请求求和。
     *
     * - HOT_SEAT:对方就在旁边,直接 `repo.setDraw(AGREED)`。
     * - HUMAN_VS_AI:引擎走 ELEMENTARY 难度浅搜一次,`|score| < DRAW_ACCEPT_CP`
     *   视为均势接受求和(`_drawn = Draw(AGREED)`);否则发 `_toast` 提示拒绝。
     *
     * **Score POV**:engine.search 返回 sideToMove 视角,玩家方走完后 sideToMove
     * 仍是玩家方(因为求和请求是玩家在己方回合发起,还没走子),所以 score 直接是
     * 玩家视角,|score| < 30 即"对玩家而言均势"。
     */
    fun onDrawOffer() {
        val s = repo.state.value
        if (s.result !is GameResult.ONGOING) return
        if (_isEngineBusy.value) return
        val cfg = configHolder.config.value
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove != cfg.humanSide) return
        if (cfg.mode == GameMode.HOT_SEAT) {
            repo.setDraw(DrawReason.AGREED)
            return
        }
        launchEngine(s, Difficulty.ELEMENTARY, EngineKind.DRAW_EVAL) { r ->
            if (kotlin.math.abs(r.score) < DRAW_ACCEPT_CP) {
                _drawn.value = GameResult.Draw(DrawReason.AGREED)
            } else {
                _toast.tryEmit("对方拒绝了求和")
            }
        }
    }

    /**
     * 玩家主动认输。用本地 _resigned 字段叠加在 uiState.result,不污染 repo;
     * 优先级最高。重启时清空。
     */
    fun onResign() {
        if (_isEngineBusy.value) return
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

    /**
     * 标识当前 `launchEngine` 调用的语义入口。仅用于日志/调试,M6 起所有 engine
     * 入口共用一个序列化通道。
     */
    private enum class EngineKind { AI_MOVE, HINT, DRAW_EVAL, MANUAL_ANALYZE }

    companion object {
        /**
         * AI 接受求和的分数阈值(centipawn)。|score| < 30 ≈ "对玩家而言均势"。
         */
        const val DRAW_ACCEPT_CP = 30
    }
}
