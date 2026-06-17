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
import com.xiangqi.app.engine.EngineUnavailableException
import com.xiangqi.app.engine.SearchInfo
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

    /**
     * Engine 调用使用的 dispatcher。生产环境用 [Dispatchers.Default](CPU 密集);
     * 测试可改为 [kotlinx.coroutines.test.StandardTestDispatcher] 让 advanceUntilIdle
     * 能控制协程进度,避免依赖 Thread.sleep / Main Looper。
     */
    @VisibleForTesting
    internal var engineDispatcher: CoroutineDispatcher = Dispatchers.Default

    private val _selected = MutableStateFlow<Position?>(null)
    private val _legalTargets = MutableStateFlow<Set<Position>>(emptySet())
    private val _isEngineBusy = MutableStateFlow(false)
    private val _searchInfo = MutableStateFlow<SearchInfo?>(null)
    private val _resigned = MutableStateFlow<GameResult?>(null)
    private val _drawn = MutableStateFlow<GameResult?>(null)
    private val _suggestions = MutableStateFlow<List<Move>>(emptyList())
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
        }.combine(_suggestions) { state, suggestions ->
            state.copy(suggestions = suggestions)
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
            suggestions = _suggestions.value,
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

    /**
     * 引擎空闲且对局仍进行中的统一守门(M6 commit 10 抽出)。
     *
     * `effectiveResult` 考虑认输 / 求和 overlay(它们让对局视为结束),让所有手动
     * engine 入口在终局或引擎繁忙时一致拒绝。
     */
    private fun requireEngineIdle(): Boolean {
        if (_isEngineBusy.value) return false
        val gs = repo.state.value
        val effective: GameResult = _drawn.value ?: _resigned.value ?: gs.result
        return effective is GameResult.ONGOING
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
     * **走 [launchEngine] 通道**(M7 修复):与 AI 应招共用 `aiJob` +
     * `_isEngineBusy`。这样既保证 UCI 单实例串行(AI 应招 → auto-eval →
     * 下一回合 AI 应招依次跑),又让 AI 应招能抢占未完成的 auto-eval
     *(`aiJob?.cancel()` 取消慢吞吞的 3 秒 ANALYZE)。
     *
     * **早期 bug**:原实现走独立协程 + `aiJob?.join()` ad-hoc 序列化,不设
     * `_isEngineBusy`。后果是 auto-eval 跑 ANALYZE 期间 `_isEngineBusy=false`,
     * 玩家走子触发 `maybeLaunchAi` 误判引擎空闲,启动 AI 应招,与 auto-eval
     * 在同一 UCI session 并发 → 子进程输出混乱 → search 永远等不到 bestmove
     * → 第 3 步卡死。
     *
     * **POV 规范化**:EngineResult.score 是 sideToMove 视角;走子后 sideToMove
     * 是"刚走完方的对手",所以如果走完的是红方(sideToMove=BLACK),分数取负
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
        // 快打模式:玩家关闭局势评估时跳过 ANALYZE 深搜,TopBar 与局势带保持空
        if (!cfg.enableAnalysis) return
        // 人机模式下,玩家走子后 sideToMove = AI 方。此时 maybeLaunchAi 已启动
        // AI 应招(occupy aiJob),maybeAutoEval 不能并发跑 ANALYZE(否则会
        // aiJob?.cancel() 取消 AI 应招)。等 AI 走完后 emit 时 sideToMove 变回
        // 玩家方,此时再 eval 即"玩家走完 + AI 应招完成"的局面。
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove == cfg.humanSide.opponent) return
        val sideToMove = s.sideToMove
        launchEngine(s, Difficulty.ANALYZE, EngineKind.AUTO_EVAL) { result ->
            val redPov = if (sideToMove == Side.BLACK) -result.score.toFloat() else result.score.toFloat()
            _evalHistory.value = _evalHistory.value + redPov
            _currentScore.value = redPov
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
     * AI move → applyMove;Hint → 写入 _suggestions;求和评估 → 阈值判定)。
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
        aiJob = viewModelScope.launch(engineDispatcher) {
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
            } catch (e: EngineUnavailableException) {
                // 引擎崩溃 / 启动失败 / 输出异常。降级为 toast,不闪退。
                // HUMAN_VS_AI 模式下 AI 应招失败,轮到对方的局面会"卡住":
                // 此时让玩家继续操作(切换到 SelfEngine 或重开)。GameViewModel
                // 不自动 fallback,避免静默走错引擎。
                _toast.tryEmit("引擎不可用:${e.message ?: "未知原因"}")
            } finally {
                infoCollector.cancel()
                _searchInfo.value = null
                _isEngineBusy.value = false
            }
        }
    }

    fun onTap(position: Position) {
        if (!requireEngineIdle()) {
            // 即使引擎忙,玩家的 tap 也应清掉 Hint 候选
            if (_suggestions.value.isNotEmpty()) _suggestions.value = emptyList()
            return
        }
        val s = repo.state.value
        val cfg = configHolder.config.value
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove != cfg.humanSide) {
            if (_suggestions.value.isNotEmpty()) _suggestions.value = emptyList()
            return
        }

        // 玩家任何点击都视为"看过提示",清掉 Hint 候选
        if (_suggestions.value.isNotEmpty()) _suggestions.value = emptyList()

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
        if (!requireEngineIdle()) return
        clearSelection()
        _suggestions.value = emptyList()
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
        _suggestions.value = emptyList()
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
     * 玩家请求多应着提示。仅当引擎空闲、对局进行中、轮到玩家方时可用。
     * 走 [Difficulty.HINT] 浅搜(MultiPV / searchRootTopN)拿 top-N 候选,
     * 结果填入 [_suggestions]:首个候选由 BoardCanvas 画箭头,其余由 HintBar 显示。
     * 走 launchEngine 通道与 AI 应招 / 求和评估串行,保证 UCI 单实例不变量。
     */
    fun onHint() {
        if (!requireEngineIdle()) return
        val s = repo.state.value
        val cfg = configHolder.config.value
        if (cfg.mode == GameMode.HUMAN_VS_AI && s.sideToMove != cfg.humanSide) return
        val engine = engineProvider.provide(cfg.engineType)
        aiJob?.cancel()
        _isEngineBusy.value = true
        aiJob = viewModelScope.launch(engineDispatcher) {
            try {
                val candidates = engine.hintCandidates(s.board, s.sideToMove, n = 3)
                _suggestions.value = candidates
            } catch (_: CancellationException) {
                // 取消时静默
            } catch (_: Throwable) {
                // hintCandidates 内部已兜底,这里再兜一次防御
                _suggestions.value = emptyList()
            } finally {
                _isEngineBusy.value = false
            }
        }
    }

    /**
     * 玩家在 HintBar 上选中某个候选走子。走该候选 + 清空候选列表;
     * 走完后 auto-eval 流程会刷新 TopBar 与 ScoreBar 的真实局势分数。
     */
    fun onPlayHint(index: Int) {
        val candidates = _suggestions.value
        if (index !in candidates.indices) return
        if (!requireEngineIdle()) return
        val move = candidates[index]
        _suggestions.value = emptyList()
        repo.applyMove(move)
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
        if (!requireEngineIdle()) return
        val s = repo.state.value
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
        if (!requireEngineIdle()) return
        val s = repo.state.value
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
    private enum class EngineKind { AI_MOVE, HINT, DRAW_EVAL, MANUAL_ANALYZE, AUTO_EVAL }

    companion object {
        /**
         * AI 接受求和的分数阈值(centipawn)。|score| < 30 ≈ "对玩家而言均势"。
         */
        const val DRAW_ACCEPT_CP = 30
    }
}
