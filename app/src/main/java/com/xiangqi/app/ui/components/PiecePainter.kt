package com.xiangqi.app.ui.components

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.ui.theme.Cinnabar
import com.xiangqi.app.ui.theme.InkBlack
import com.xiangqi.app.ui.theme.PaperCream

/**
 * 棋子绘制器:7 层绘制模拟实木圆盘质感。
 *
 * 红方字色 = [Cinnabar],黑方字色 = [InkBlack];圆盘底色 = [PaperCream]。
 *
 * 这不是 Composable,而是一个 [DrawScope] 扩展函数,以便直接在 `Canvas` 内调用。
 *
 * 绘制层(从底到顶):
 * 1. 落子阴影(Paint.setShadowLayer 在 nativeCanvas 上画一个"看不见的圆",让 shadow 渲染)
 * 2. 圆盘 PaperCream 实心填充
 * 3. 顶部高光弧(左上 1/4,白色 alpha 渐变,模拟玻璃光)
 * 4. 外圈描边(深 ringColor,粗)
 * 5. 内圈描边(浅 ringColor,细,让圆盘有"内凹"感)
 * 6. 字阴影(黑色 alpha,向下偏移 1px)
 * 7. 字本体(繁体字,SERIF BOLD)
 */
object PiecePainter {

    /** (PieceType, Side) → 传统繁体字符。 */
    private val chars: Map<Pair<PieceType, Side>, String> = mapOf(
        (PieceType.KING to Side.RED) to "帥",
        (PieceType.ADVISOR to Side.RED) to "仕",
        (PieceType.BISHOP to Side.RED) to "相",
        (PieceType.KNIGHT to Side.RED) to "傌",
        (PieceType.ROOK to Side.RED) to "車",
        (PieceType.CANNON to Side.RED) to "炮",
        (PieceType.PAWN to Side.RED) to "兵",
        (PieceType.KING to Side.BLACK) to "將",
        (PieceType.ADVISOR to Side.BLACK) to "士",
        (PieceType.BISHOP to Side.BLACK) to "象",
        (PieceType.KNIGHT to Side.BLACK) to "馬",
        (PieceType.ROOK to Side.BLACK) to "車",
        (PieceType.CANNON to Side.BLACK) to "砲",
        (PieceType.PAWN to Side.BLACK) to "卒",
    )

    /** 取该棋子的繁体字。 */
    fun charFor(type: PieceType, side: Side): String =
        chars.getValue(type to side)

    /**
     * 在 [DrawScope] 内 [center] 处画一颗 [piece]。
     *
     * @param radius 棋子半径(像素)。
     * @param fontSizePx 字号(像素)。
     */
    fun DrawScope.drawPiece(center: Offset, radius: Float, piece: Piece, fontSizePx: Float) {
        val ringColor = if (piece.side == Side.RED) Cinnabar else InkBlack
        val textColorArgb = ringColor.toArgb()

        // Layer 1: 落子阴影。setShadowLayer 必须配合实际 drawCircle 才会渲染 shadow,
        // 这里画一个"看不见的圆"(颜色就是 PaperCream,与圆盘同色)让 shadow 在圆盘外溢出。
        val shadowPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = PaperCream.toArgb()
            setShadowLayer(radius * 0.20f, 0f, radius * 0.10f, 0x66000000)
        }
        drawContext.canvas.nativeCanvas.drawCircle(
            center.x, center.y, radius, shadowPaint,
        )

        // Layer 2: 圆盘 PaperCream。
        drawCircle(
            color = PaperCream,
            radius = radius,
            center = center,
        )

        // Layer 3: 顶部高光弧。用 SweepEffect-like 的简单左上 1/4 弧 + 白色 alpha 渐变。
        // DrawScope.drawArc 画在直径 = radius*1.6 的椭圆上,只画顶部 90 度。
        val highlightSize = Size(radius * 1.6f, radius * 1.6f)
        drawArc(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
                center = Offset(center.x, center.y - radius * 0.4f),
                radius = radius * 0.9f,
            ),
            topLeft = Offset(center.x - highlightSize.width / 2, center.y - highlightSize.height / 2),
            size = highlightSize,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = true,
        )

        // Layer 4: 外圈描边。
        drawCircle(
            color = ringColor,
            radius = radius * 0.92f,
            center = center,
            style = Stroke(width = radius * 0.06f),
        )

        // Layer 5: 内圈描边(浅色,细),让圆盘有内凹感。
        drawCircle(
            color = ringColor.copy(alpha = 0.4f),
            radius = radius * 0.78f,
            center = center,
            style = Stroke(width = radius * 0.025f),
        )

        // Layer 6: 字阴影(向下偏移 1px,黑色 alpha 0.3)。
        val shadowTextPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = 0x4D000000
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = fontSizePx
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawText(
            charFor(piece.type, piece.side),
            center.x,
            center.y + fontSizePx * 0.36f + 1f,
            shadowTextPaint,
        )

        // Layer 7: 字本体。
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = textColorArgb
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = fontSizePx
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawText(
            charFor(piece.type, piece.side),
            center.x,
            center.y + fontSizePx * 0.36f,
            textPaint,
        )
    }
}
