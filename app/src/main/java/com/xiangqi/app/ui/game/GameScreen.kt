package com.xiangqi.app.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.ui.components.BoardAnimation
import com.xiangqi.app.ui.components.BoardCanvas
import com.xiangqi.app.ui.components.BoardLayout
import com.xiangqi.app.ui.components.GameBottomBar
import com.xiangqi.app.ui.components.GameTopBar
import com.xiangqi.app.ui.components.computeLayout
import com.xiangqi.app.ui.components.modelToView

/**
 * GameScreen:整个 M3 入口。组装 [GameTopBar] + [BoardArea] + [GameBottomBar]。
 *
 * 状态全部来自 [GameViewModel] 的 [GameViewModel.uiState],本 Composable 自身无状态。
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GameScreenContent(
        state = state,
        onTap = viewModel::onTap,
        onUndo = viewModel::onUndo,
        onRestart = viewModel::onRestart,
        modifier = modifier,
    )
}

@Composable
private fun GameScreenContent(
    state: GameUiState,
    onTap: (Position) -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            GameTopBar(sideToMove = state.sideToMove, result = state.result)
        },
        bottomBar = {
            GameBottomBar(
                canUndo = state.canUndo,
                result = state.result,
                onUndo = onUndo,
                onRestart = onRestart,
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

/**
 * 棋盘区域。持有简单平移动画状态([animateFloatAsState]),
 * 把 [BoardAnimation] 喂给 [BoardCanvas]。
 */
@Composable
private fun BoardArea(
    state: GameUiState,
    onTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastMove = state.lastMove
    var animatedMove by remember { mutableStateOf<Move?>(null) }
    var animProgressTarget by remember { mutableFloatStateOf(1f) }

    // 新走子时(且与上次动画不同)启动一次动画。undo / restart 不会改 lastMove 到
    // 与 animatedMove 不同的值之外的情况—— restart 会把 lastMove 设为 null,
    // 此时 LaunchedEffect 触发但 branch 走不进去。
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
        )
    }
}

/** Constraints → BoardLayout:把 px 维度的 constraints 转成 BoardLayout。 */
private fun computeLayoutFromConstraints(
    constraints: Constraints,
    @Suppress("UNUSED_PARAMETER") density: Float,
): BoardLayout {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    return computeLayout(widthPx, heightPx)
}

/** 仅当动画进行中(progress < 1 且 lastMove 非空)时返回 [BoardAnimation]。 */
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
