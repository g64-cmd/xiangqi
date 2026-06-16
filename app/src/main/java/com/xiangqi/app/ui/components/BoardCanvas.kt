package com.xiangqi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.ui.theme.Cinnabar
import com.xiangqi.app.ui.theme.CinnabarLight
import com.xiangqi.app.ui.theme.InkBlack
import com.xiangqi.app.ui.theme.InkGray
import com.xiangqi.app.ui.theme.WoodLight
import com.xiangqi.app.ui.theme.WoodMid
import kotlin.math.roundToInt

/**
 * 棋盘上一次动画状态。null 表示当前不在动画。
 *
 * @property movingPiece 正在移动的棋子。
 * @property fromView 起点视图坐标(像素)。
 * @property toView 终点视图坐标(像素)。
 * @property progress 0..1,动画进度。
 */
data class BoardAnimation(
    val movingPiece: Piece,
    val fromView: Offset,
    val toView: Offset,
    val progress: Float,
)

/**
 * 棋盘画布。负责绘制所有视觉元素:背景 / 外框 / 网格 / 河界 / 九宫 / 位置标记 /
 * 上一步高亮 / 选中高亮 / 合法目标点 / 棋子 / 动画棋子覆盖。
 *
 * 不持任何状态;所有渲染输入都通过参数传入。点击通过 [onTap] 回调抛出域坐标。
 *
 * @param orientation 哪方在屏幕底部,M3 固定 [Side.RED];M4 setup screen 可改。
 * @param animation 当前动画状态,null = 无动画。
 */
@Composable
fun BoardCanvas(
    board: Board,
    orientation: Side,
    selected: Position?,
    legalTargets: Set<Position>,
    lastMove: Move?,
    onTap: (Position) -> Unit,
    animation: BoardAnimation? = null,
    hintMove: Move? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val layout = computeLayout(widthPx, heightPx)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layout, orientation, onTap) {
                    detectTapGestures { offset ->
                        // 用 round 找最近的交叉点,而非 floor 截断。
                        // floor 在边界附近(0.7..0.9)会取下界,用户点视觉格点中心时
                        // 偶尔偏到上一格;round 让"在格点 N 的视觉区域内点击"稳定命中 N。
                        val viewCol = ((offset.x - layout.marginX) / layout.cell).roundToInt()
                        val viewRow = ((offset.y - layout.marginY) / layout.cell).roundToInt()
                        if (viewCol in 0..Position.COL_MAX && viewRow in 0..Position.ROW_MAX) {
                            onTap(viewToModel(viewCol, viewRow, orientation))
                        }
                    }
                },
        ) {
            drawBackground()
            drawOuterBorder(layout)
            drawGrid(layout)
            val textSizePx = layout.cell * 0.45f
            drawRiverText(layout, textSizePx)
            drawPalaces(layout, orientation)
            drawPositionMarkers(layout, orientation)
            drawLastMoveHighlight(layout, lastMove, orientation)
            drawSelectionHighlight(layout, selected, orientation)
            drawLegalTargets(layout, legalTargets, orientation)
            drawHintArrow(layout, hintMove, orientation)
            drawPieces(layout, board, orientation, lastMove, animation)
            drawAnimationOverlay(layout, animation)
        }
    }
}

/** 画布布局:cell 单元边长 + 外边距,使 9×10 交叉点居中。 */
internal data class BoardLayout(
    val cell: Float,
    val marginX: Float,
    val marginY: Float,
    val width: Float,
    val height: Float,
) {
    /** 视图坐标 (viewCol, viewRow) 的交叉点像素中心。 */
    fun centerOf(viewCol: Int, viewRow: Int): Offset =
        Offset(marginX + viewCol * cell, marginY + viewRow * cell)
}

/** 计算使 8 列宽 × 9 行高都能完整放下的布局,余量居中。 */
internal fun computeLayout(widthPx: Float, heightPx: Float): BoardLayout {
    val cell = minOf(widthPx / 9f, heightPx / 10f)
    val marginX = (widthPx - cell * 8f) / 2f
    val marginY = (heightPx - cell * 9f) / 2f
    return BoardLayout(cell, marginX, marginY, widthPx, heightPx)
}

// ---- 静态绘制层(从背到前) ----

private fun DrawScope.drawBackground() {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(WoodLight, WoodMid),
            startY = 0f,
            endY = size.height,
        ),
        size = size,
    )
}

private fun DrawScope.drawOuterBorder(layout: BoardLayout) {
    drawRect(
        color = InkBlack,
        topLeft = Offset(layout.marginX, layout.marginY),
        size = Size(layout.width - 2 * layout.marginX, layout.height - 2 * layout.marginY),
        style = Stroke(width = layout.cell * 0.06f),
    )
}

private fun DrawScope.drawGrid(layout: BoardLayout) {
    val strokeWidth = layout.cell * 0.015f
    // 10 条横线(完整宽度)
    for (viewRow in 0..9) {
        val y = layout.marginY + viewRow * layout.cell
        drawLine(
            color = InkBlack,
            start = Offset(layout.marginX, y),
            end = Offset(layout.width - layout.marginX, y),
            strokeWidth = strokeWidth,
        )
    }
    // 9 条竖线:外侧 2 条(col 0/8)完整,中间 7 条(col 1..7)在河界处断开
    for (viewCol in 0..8) {
        val x = layout.marginX + viewCol * layout.cell
        if (viewCol == 0 || viewCol == 8) {
            drawLine(
                color = InkBlack,
                start = Offset(x, layout.marginY),
                end = Offset(x, layout.height - layout.marginY),
                strokeWidth = strokeWidth,
            )
        } else {
            val yTop1 = layout.marginY + 4 * layout.cell
            val yBot0 = layout.marginY + 5 * layout.cell
            drawLine(InkBlack, Offset(x, layout.marginY), Offset(x, yTop1), strokeWidth)
            drawLine(InkBlack, Offset(x, yBot0), Offset(x, layout.marginY + 9 * layout.cell), strokeWidth)
        }
    }
}

private fun DrawScope.drawRiverText(layout: BoardLayout, textSizePx: Float) {
    val y = layout.marginY + 4.5f * layout.cell
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = InkGray.toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = textSizePx
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SERIF,
            android.graphics.Typeface.BOLD,
        )
    }
    drawContext.canvas.nativeCanvas.apply {
        drawText("楚 河", layout.marginX + 2f * layout.cell, y + textSizePx * 0.35f, paint)
        drawText("漢 界", layout.marginX + 6f * layout.cell, y + textSizePx * 0.35f, paint)
    }
}

private fun DrawScope.drawPalaces(layout: BoardLayout, orientation: Side) {
    // 红宫:model col 3..5、row 0..2;黑宫:model col 3..5、row 7..9。
    for (side in listOf(Side.RED, Side.BLACK)) {
        val modelRowTop = if (side == Side.RED) 0 else 7
        val modelRowBot = modelRowTop + 2
        val (vcL, vrT) = modelToView(Position(3, modelRowTop), orientation)
        val (vcR, vrB) = modelToView(Position(5, modelRowBot), orientation)
        val topLeft = layout.centerOf(vcL, vrT)
        val bottomRight = layout.centerOf(vcR, vrB)
        val topRight = layout.centerOf(vcR, vrT)
        val bottomLeft = layout.centerOf(vcL, vrB)
        val w = layout.cell * 0.015f
        drawLine(InkBlack, topLeft, bottomRight, w)
        drawLine(InkBlack, topRight, bottomLeft, w)
    }
}

private fun DrawScope.drawPositionMarkers(layout: BoardLayout, orientation: Side) {
    // 炮位 col 1/4、row 2/7;兵位 col 0/2/4/6/8、row 3/6。
    val cannonPositions = listOf(
        Position(1, 2), Position(4, 2),
        Position(1, 7), Position(4, 7),
    )
    val pawnPositions = mutableListOf<Position>()
    for (c in listOf(0, 2, 4, 6, 8)) {
        pawnPositions += Position(c, 3)
        pawnPositions += Position(c, 6)
    }
    for (p in cannonPositions + pawnPositions) {
        drawCrosshair(layout, p, orientation)
    }
}

/** 在交叉点四角画 4 段 L 形描边(传统炮位/兵位标记)。 */
private fun DrawScope.drawCrosshair(
    layout: BoardLayout,
    modelPos: Position,
    orientation: Side,
) {
    val (vc, vr) = modelToView(modelPos, orientation)
    val c = layout.centerOf(vc, vr)
    val arm = layout.cell * 0.10f
    val gap = layout.cell * 0.06f
    val w = layout.cell * 0.02f
    val color = InkBlack
    val corners = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
    for ((dx, dy) in corners) {
        // 边界:col 0 不画左侧、col 8 不画右侧
        if (modelPos.col == 0 && dx == -1) continue
        if (modelPos.col == Position.COL_MAX && dx == 1) continue
        val cornerX = c.x + dx * gap
        val cornerY = c.y + dy * gap
        drawLine(color, Offset(cornerX, cornerY), Offset(cornerX + dx * arm, cornerY), w)
        drawLine(color, Offset(cornerX, cornerY), Offset(cornerX, cornerY + dy * arm), w)
    }
}

private fun DrawScope.drawAnimationOverlay(
    layout: BoardLayout,
    animation: BoardAnimation?,
) {
    if (animation == null) return
    val radius = layout.cell * 0.42f
    val fontSizePx = layout.cell * 0.5f
    val t = animation.progress.coerceIn(0f, 1f)
    val cx = animation.fromView.x + (animation.toView.x - animation.fromView.x) * t
    val cy = animation.fromView.y + (animation.toView.y - animation.fromView.y) * t
    with(PiecePainter) {
        drawPiece(Offset(cx, cy), radius, animation.movingPiece, fontSizePx)
    }
}

private fun DrawScope.drawSelectionHighlight(
    layout: BoardLayout,
    selected: Position?,
    orientation: Side,
) {
    if (selected == null) return
    val (vc, vr) = modelToView(selected, orientation)
    val c = layout.centerOf(vc, vr)
    val rectSize = layout.cell * 0.7f
    drawRect(
        color = Cinnabar.copy(alpha = 0.35f),
        topLeft = Offset(c.x - rectSize / 2, c.y - rectSize / 2),
        size = Size(rectSize, rectSize),
    )
}

private fun DrawScope.drawLegalTargets(
    layout: BoardLayout,
    legalTargets: Set<Position>,
    orientation: Side,
) {
    val radius = layout.cell * 0.12f
    for (p in legalTargets) {
        val (vc, vr) = modelToView(p, orientation)
        val c = layout.centerOf(vc, vr)
        drawCircle(Cinnabar, radius, c)
    }
}

/**
 * 提示箭头(M6 Hint):半透明 Cinnabar 线 + 小三角箭头,画在棋子之下、合法目标点之上。
 */
private fun DrawScope.drawHintArrow(
    layout: BoardLayout,
    hintMove: Move?,
    orientation: Side,
) {
    if (hintMove == null) return
    val (fc, fr) = modelToView(hintMove.from, orientation)
    val (tc, tr) = modelToView(hintMove.to, orientation)
    val from = layout.centerOf(fc, fr)
    val to = layout.centerOf(tc, tr)

    val color = Cinnabar.copy(alpha = 0.55f)
    val strokeWidth = layout.cell * 0.10f
    val arrowSize = layout.cell * 0.22f

    // 箭头主体:从 from 到 to,但 to 点缩进 arrowSize 让箭头不超出格点
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
    val ux = dx / len
    val uy = dy / len
    val tip = Offset(to.x - ux * arrowSize * 0.5f, to.y - uy * arrowSize * 0.5f)
    drawLine(color, from, tip, strokeWidth = strokeWidth)

    // 箭头小三角:与方向轴垂直的两个底点 + 尖点
    val perpX = -uy
    val perpY = ux
    val base = Offset(to.x - ux * arrowSize, to.y - uy * arrowSize)
    val left = Offset(base.x + perpX * arrowSize * 0.6f, base.y + perpY * arrowSize * 0.6f)
    val right = Offset(base.x - perpX * arrowSize * 0.6f, base.y - perpY * arrowSize * 0.6f)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(to.x, to.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawLastMoveHighlight(
    layout: BoardLayout,
    lastMove: Move?,
    orientation: Side,
) {
    if (lastMove == null) return
    val highlight = CinnabarLight.copy(alpha = 0.25f)
    val rectSize = layout.cell * 0.7f
    for (p in listOf(lastMove.from, lastMove.to)) {
        val (vc, vr) = modelToView(p, orientation)
        val c = layout.centerOf(vc, vr)
        drawRect(
            color = highlight,
            topLeft = Offset(c.x - rectSize / 2, c.y - rectSize / 2),
            size = Size(rectSize, rectSize),
        )
    }
}

private fun DrawScope.drawPieces(
    layout: BoardLayout,
    board: Board,
    orientation: Side,
    lastMove: Move?,
    animation: BoardAnimation?,
) {
    val radius = layout.cell * 0.42f
    val fontSizePx = layout.cell * 0.5f
    for (row in 0..Position.ROW_MAX) {
        for (col in 0..Position.COL_MAX) {
            val piece = board[col, row] ?: continue
            val pos = Position(col, row)
            // 动画期间跳过 lastMove.from(由 drawAnimationOverlay 单独画)
            if (animation != null && lastMove != null && pos == lastMove.from) continue
            val (vc, vr) = modelToView(pos, orientation)
            val center = layout.centerOf(vc, vr)
            with(PiecePainter) { drawPiece(center, radius, piece, fontSizePx) }
        }
    }
}
