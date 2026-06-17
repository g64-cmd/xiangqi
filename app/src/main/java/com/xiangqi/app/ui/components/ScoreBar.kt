package com.xiangqi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.xiangqi.app.ui.theme.Cinnabar
import com.xiangqi.app.ui.theme.InkBlack
import com.xiangqi.app.ui.theme.InkGray
import kotlin.math.abs

/**
 * 棋盘下方局势带(指针式进度条)。
 *
 * 0 轴居中,左右两侧分别表示红方占优(左)与黑方占优(右)。指针位置由
 * [scoreToFraction] 决定,文字标签 [scoreToLabel] 显示当前局势文字。
 *
 * 评分准则(红方视角 cp):
 * - |s| < 30  -> 均势(中线)
 * - 30..80    -> 微优(15..35%)
 * - 80..200   -> 明显占优(35..60%)
 * - 200..600  -> 大优(60..90%)
 * - >= 600    -> 决定性(90..100%)
 *
 * **视觉约定**:红方占优指针偏左,黑方占优偏右。原因是"红方在屏幕底部"时,
 * 玩家视角下自己的优势在身体一侧(左),对手在右。这与评分准则文字标签
 * "红方 +X.X" 形成空间映射。
 *
 * @param scoreCp 红方视角 centipawn。null 表示尚未评估,指针停中线、文字"评估中"。
 */
@Composable
fun ScoreBar(
    scoreCp: Float?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(44.dp)) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val trackY = centerY

        // 轨道背景:左半 Cinnabar(红优),右半 InkBlack(黑优),中间过渡
        val halfW = w / 2f
        drawLine(
            color = Cinnabar.copy(alpha = 0.25f),
            start = Offset(0f, trackY),
            end = Offset(halfW, trackY),
            strokeWidth = h * 0.45f,
        )
        drawLine(
            color = InkBlack.copy(alpha = 0.25f),
            start = Offset(halfW, trackY),
            end = Offset(w, trackY),
            strokeWidth = h * 0.45f,
        )

        // 中线刻度(均势分界)
        drawLine(
            color = InkGray,
            start = Offset(halfW, trackY - h * 0.4f),
            end = Offset(halfW, trackY + h * 0.4f),
            strokeWidth = 2f,
        )

        // 文字标签
        val label = scoreToLabel(scoreCp)
        val labelTextPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = InkBlack.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = h * 0.32f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD,
            )
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText(label, halfW, trackY + labelTextPaint.textSize * 0.35f, labelTextPaint)
        }

        // 指针(scoreCp=null 时停中线)
        val fraction = scoreToFraction(scoreCp) // [-1, +1]
        // fraction = +1 表示红方极优 -> 屏幕最左
        // fraction = -1 表示黑方极优 -> 屏幕最右
        val pointerX = halfW - fraction * halfW
        val pointerColor = when {
            scoreCp == null -> InkGray
            scoreCp > 30f -> Cinnabar
            scoreCp < -30f -> InkBlack
            else -> InkGray
        }
        drawCircle(
            color = pointerColor,
            radius = h * 0.18f,
            center = Offset(pointerX, trackY),
        )
        drawLine(
            color = pointerColor,
            start = Offset(pointerX, trackY - h * 0.45f),
            end = Offset(pointerX, trackY + h * 0.45f),
            strokeWidth = 3f,
        )
    }
}

/**
 * 把红方视角 cp 映射到 [-1, +1] 区间。
 *
 * - 正分(红优)返回正
 * - 负分(黑优)返回负
 * - |score| >= [DECISIVE_CP] 时 clamp 到 ±1
 *
 * 对应分数区间映射到分数比例:
 * - |score| < 30 -> 0(均势)
 * - 30..80 -> 0.15..0.35
 * - 80..200 -> 0.35..0.6
 * - 200..600 -> 0.6..0.9
 * - >= 600 -> 1.0(决定性)
 */
internal fun scoreToFraction(scoreCp: Float?): Float {
    if (scoreCp == null) return 0f
    val abs = abs(scoreCp)
    val fraction = when {
        abs < EVEN_CP -> 0f
        abs < SLIGHT_CP -> lerpFraction(abs, EVEN_CP, SLIGHT_CP, 0.15f, 0.35f)
        abs < CLEAR_CP -> lerpFraction(abs, SLIGHT_CP, CLEAR_CP, 0.35f, 0.60f)
        abs < BIG_CP -> lerpFraction(abs, CLEAR_CP, BIG_CP, 0.60f, 0.90f)
        else -> 1f
    }
    return if (scoreCp >= 0) fraction else -fraction
}

/** 把 [scoreToFraction] 的结果转成局势文字。 */
internal fun scoreToLabel(scoreCp: Float?): String {
    if (scoreCp == null) return "评估中"
    val abs = abs(scoreCp)
    val side = if (scoreCp > 0) "红方" else "黑方"
    return when {
        abs < EVEN_CP -> "均势"
        abs < SLIGHT_CP -> "$side 微优"
        abs < CLEAR_CP -> "$side 占优"
        abs < BIG_CP -> "$side 大优"
        else -> "$side 决定性优势"
    }
}

private fun lerpFraction(
    abs: Float,
    fromCp: Int,
    toCp: Int,
    fromF: Float,
    toF: Float,
): Float {
    val t = ((abs - fromCp) / (toCp - fromCp)).coerceIn(0f, 1f)
    return fromF + (toF - fromF) * t
}

internal const val EVEN_CP: Int = 30
internal const val SLIGHT_CP: Int = 80
internal const val CLEAR_CP: Int = 200
internal const val BIG_CP: Int = 600
internal const val DECISIVE_CP: Int = 600
