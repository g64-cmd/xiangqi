package com.xiangqi.app.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.xiangqi.app.ui.components.BoardAnimation
import com.xiangqi.app.ui.components.BoardCanvas
import com.xiangqi.app.ui.components.BoardLayout
import com.xiangqi.app.ui.components.GameBottomBar
import com.xiangqi.app.ui.components.GameTopBar
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
        onDrawOffer = viewModel::onDrawOffer,
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
    onDrawOffer: () -> Unit,
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
            )
        },
    ) { padding ->
        BoardArea(
            state = state,
            onTap = onTap,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

@Composable
private fun BoardArea(
    state: GameUiState,
    onTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastMove = state.lastMove
    var animatedMove by remember { mutableStateOf<Move?>(null) }
    var animProgressTarget by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(lastMove) {
        if (lastMove != null && lastMove != animatedMove) {
            animatedMove = lastMove
            animProgressTarget = 0f
        }
    }

    val progress by animateFloatAsState(
        targetValue = animProgressTarget,
        animationSpec = tween(durationMillis = 200),
        label = "piece-move",
    )

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
            hintMove = state.suggestedMove,
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
