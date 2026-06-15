package com.xiangqi.app.ui.components

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
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
 * 棋子绘制器:用传统繁体字 + 木质圆盘渲染一颗棋子。
 *
 * 红方字色 = [Cinnabar],黑方字色 = [InkBlack];圆盘底色 = [PaperCream]。
 *
 * 这不是 Composable,而是一个 [DrawScope] 扩展函数,以便直接在 `Canvas` 内调用。
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
        drawCircle(
            color = PaperCream,
            radius = radius,
            center = center,
        )
        drawCircle(
            color = ringColor,
            radius = radius * 0.92f,
            center = center,
            style = Stroke(width = radius * 0.06f),
        )
        drawContext.canvas.nativeCanvas.drawText(
            charFor(piece.type, piece.side),
            center.x,
            center.y + fontSizePx * 0.36f,  // baseline 经验偏移
            android.graphics.Paint().apply {
                isAntiAlias = true
                color = ringColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = fontSizePx
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            },
        )
    }
}
