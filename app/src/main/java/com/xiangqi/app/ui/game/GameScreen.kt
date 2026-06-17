package com.xiangqi.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.ui.analysis.AnalysisDialog
import com.xiangqi.app.ui.components.BoardAnimation
import com.xiangqi.app.ui.components.BoardCanvas
import com.xiangqi.app.ui.components.BoardLayout
import com.xiangqi.app.ui.components.GameBottomBar
import com.xiangqi.app.ui.components.GameTopBar
import com.xiangqi.app.ui.components.HintBar
import com.xiangqi.app.ui.components.ScoreBar
import com.xiangqi.app.ui.components.computeLayout
import com.xiangqi.app.ui.components.modelToView

@Composable
fun GameScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.toast.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    GameScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onTap = viewModel::onTap,
        onUndo = viewModel::onUndo,
        onResign = viewModel::onResign,
        onRestart = viewModel::onRestart,
        onHint = viewModel::onHint,
        onPlayHint = viewModel::onPlayHint,
        onDrawOffer = viewModel::onDrawOffer,
        onAnalyze = viewModel::onShowAnalysis,
        onDismissAnalysis = viewModel::onDismissAnalysis,
        onExit = onExit,
        modifier = modifier,
    )
}

@Composable
private fun GameScreenContent(
    state: GameUiState,
    snackbarHostState: SnackbarHostState,
    onTap: (Position) -> Unit,
    onUndo: () -> Unit,
    onResign: () -> Unit,
    onRestart: () -> Unit,
    onHint: () -> Unit,
    onPlayHint: (Int) -> Unit,
    onDrawOffer: () -> Unit,
    onAnalyze: () -> Unit,
    onDismissAnalysis: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GameTopBar(
                sideToMove = state.sideToMove,
                result = state.result,
                isAiThinking = state.isAiThinking,
                searchInfo = state.searchInfo,
                currentScore = state.currentScore,
                onExit = onExit,
            )
        },
        bottomBar = {
            GameBottomBar(
                canUndo = state.canUndo,
                result = state.result,
                isAiThinking = state.isAiThinking,
                onUndo = onUndo,
                onResign = onResign,
                onRestart = onRestart,
                canHint = state.canHint,
                onHint = onHint,
                canOfferDraw = state.canOfferDraw,
                onDrawOffer = onDrawOffer,
                canAnalyze = state.canAnalyze,
                onAnalyze = onAnalyze,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            BoardArea(
                state = state,
                onTap = onTap,
                modifier = Modifier.weight(1f),
            )
            HintBar(
                candidates = state.suggestions,
                onPlay = onPlayHint,
            )
            ScoreBar(
                scores = state.evalHistory,
                currentScore = state.currentScore,
            )
        }
        if (state.showAnalysisDialog) {
            AnalysisDialog(
                scores = state.evalHistory,
                onDismiss = onDismissAnalysis,
            )
        }
    }
}

@Composable
private fun BoardArea(
    state: GameUiState,
    onTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastMove = state.lastMove
    // 动画状态机:每次 lastMove 变化时把 Animatable 从 0 渐变到 1。
    // progress == 1f 表示动画结束,computeAnimation 返回 null,from 格改由
    // drawPieces 绘制;动画期间 drawPieces 跳过 from 格,由 drawAnimationOverlay
    // 单独画移动中的棋子。
    val progressState = remember { Animatable(1f) }
    var animatedMove by remember { mutableStateOf<Move?>(null) }

    LaunchedEffect(lastMove) {
        if (lastMove != null && lastMove != animatedMove) {
            animatedMove = lastMove
            progressState.snapTo(0f)
            progressState.animateTo(1f, tween(durationMillis = 200))
        }
    }
    val progress = progressState.value

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layout = computeLayoutFromConstraints(constraints, density = LocalDensity.current.density)
        val animation = computeAnimation(state, lastMove, progress, layout)
        BoardCanvas(
            board = state.board,
            orientation = state.orientation,
            selected = state.selected,
            legalTargets = state.legalTargets,
            lastMove = state.lastMove,
            onTap = onTap,
            animation = animation,
            hintMove = state.suggestions.firstOrNull(),
        )
    }
}

private fun computeLayoutFromConstraints(
    constraints: Constraints,
    @Suppress("UNUSED_PARAMETER") density: Float,
): BoardLayout {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    return computeLayout(widthPx, heightPx)
}

private fun computeAnimation(
    state: GameUiState,
    lastMove: Move?,
    progress: Float,
    layout: BoardLayout,
): BoardAnimation? {
    if (lastMove == null || progress >= 1f) return null
    val movingPiece = state.board[lastMove.to] ?: return null
    val (fc, fr) = modelToView(lastMove.from, state.orientation)
    val (tc, tr) = modelToView(lastMove.to, state.orientation)
    return BoardAnimation(
        movingPiece = movingPiece,
        fromView = layout.centerOf(fc, fr),
        toView = layout.centerOf(tc, tr),
        progress = progress,
    )
}
