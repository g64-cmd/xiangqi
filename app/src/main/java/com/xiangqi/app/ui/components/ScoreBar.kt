package com.xiangqi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.xiangqi.app.engine.Score
import com.xiangqi.app.ui.theme.Cinnabar
import com.xiangqi.app.ui.theme.InkBlack
import com.xiangqi.app.ui.theme.InkGray
import kotlin.math.abs

/**
 * 棋盘下方局势折线图。
 *
 * 横轴:走子序号(0..N),每个评估点对应一次 auto-eval。
 * 纵轴:红方视角 cp,clamp 到 ±[CLAMP_CP],中线为均势(0)。
 * 折线 + 半透明填充,终点画大圆点。
 *
 * **mate 识别**:皮卡鱼返回 `score mate N` 时转成 [Score.mateScore](MATE-N,
 * 约 ±29997),不是普通 cp。需要先识别 mate:
 * - 正 mate(红方将杀)→ 折线 clamp 到顶端,标签"红方将杀 N 步"
 * - 负 mate(黑方将杀)→ clamp 到底端,标签"黑方将杀 N 步"
 * - 否则按 cp 分档:均势 / 微优 / 占优 / 大优 / 决定性优势
 *
 * @param scores 红方视角 cp(或 mate score)序列。
 * @param currentScore 当前最新分数(也来自序列末尾,但单独传便于 null 处理)。
 */
@Composable
fun ScoreBar(
    scores: List<Float>,
    currentScore: Float?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        // 中线(均势)
        drawLine(
            color = InkGray.copy(alpha = 0.4f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1f,
        )

        if (scores.isEmpty()) {
            // 无数据:显示"评估中"文字
            val paint = labelPaint(h, InkGray)
            drawContext.canvas.nativeCanvas.apply {
                drawText("评估中", w / 2f, centerY + paint.textSize * 0.35f, paint)
            }
            return@Canvas
        }

        if (scores.size == 1) {
            // 单点:画一个圆 + 标签
            val y = cpToY(scores[0], h)
            val color = sideColor(scores[0])
            drawCircle(color, radius = 8f, center = Offset(w / 2f, y))
        } else {
            val stepX = w / (scores.size - 1).toFloat()
            val points = scores.mapIndexed { i, s -> Offset(i * stepX, cpToY(s, h)) }

            // 半透明填充(从中线开始,折线,回到中线)
            val fillPath = Path().apply {
                moveTo(points.first().x, centerY)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, centerY)
                close()
            }
            val fillColor = if ((currentScore ?: scores.last()) >= 0f) {
                Cinnabar.copy(alpha = 0.18f)
            } else {
                InkBlack.copy(alpha = 0.18f)
            }
            drawPath(fillPath, fillColor)

            // 折线段
            var prevColor: Color? = null
            for (i in 1 until points.size) {
                val segColor = sideColor(scores[i])
                // 同色连续段用同一色;不同色单独画
                if (prevColor != null && prevColor != segColor) {
                    // 切换点单独画前一段
                }
                drawLine(segColor, points[i - 1], points[i], strokeWidth = 3f)
                prevColor = segColor
            }

            // 各点小圆
            for (p in points.dropLast(1)) {
                drawCircle(InkGray.copy(alpha = 0.5f), radius = 3f, center = p)
            }
            // 终点大圆
            drawCircle(sideColor(scores.last()), radius = 6f, center = points.last())
        }

        // 当前局势文字标签(右上角)
        val label = scoreToLabel(currentScore)
        val labelColor = sideColor(currentScore ?: 0f)
        val paint = labelPaint(h, labelColor).apply {
            textAlign = android.graphics.Paint.Align.RIGHT
            textSize = h * 0.22f
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText(label, w - 8f, paint.textSize + 4f, paint)
        }
    }
}

/**
 * 把红方视角 cp(或 mate score)映射到 Canvas Y 坐标。
 *
 * - 普通分:clamp 到 ±[CLAMP_CP],中线居中
 * - mate:clamp 到 ±[CLAMP_CP](顶/底)
 */
private fun cpToY(scoreCp: Float, height: Float): Float {
    val isMate = abs(scoreCp) > Score.MATE_THRESHOLD.toFloat()
    val effective = if (isMate) {
        // mate 直接打到顶/底
        if (scoreCp > 0) CLAMP_CP.toFloat() else -CLAMP_CP.toFloat()
    } else {
        scoreCp.coerceIn(-CLAMP_CP.toFloat(), CLAMP_CP.toFloat())
    }
    val normalized = effective / CLAMP_CP.toFloat() // [-1, +1]
    // 正分(红优)在上方(Y 小),负分(黑优)在下方(Y 大)
    return height / 2f - normalized * (height / 2f * 0.85f)
}

/** 根据分数符号返回主色:正=Cinnabar(红),负=InkBlack(黑),0=InkGray。 */
private fun sideColor(scoreCp: Float): Color = when {
    scoreCp > 1f -> Cinnabar
    scoreCp < -1f -> InkBlack
    else -> InkGray
}

private fun labelPaint(height: Float, color: Color) = android.graphics.Paint().apply {
    isAntiAlias = true
    this.color = color.toArgb()
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = height * 0.22f
    typeface = android.graphics.Typeface.create(
        android.graphics.Typeface.DEFAULT,
        android.graphics.Typeface.BOLD,
    )
}

/**
 * 把红方视角 cp(或 mate score)转成局势文字。
 *
 * - mate score(识别 |score| > [Score.MATE_THRESHOLD])→ "X 方将杀 N 步"
 * - 普通分按档位:均势 / 微优 / 占优 / 大优 / 决定性优势
 */
internal fun scoreToLabel(scoreCp: Float?): String {
    if (scoreCp == null) return "评估中"
    val abs = abs(scoreCp)
    if (abs > Score.MATE_THRESHOLD.toFloat()) {
        val side = if (scoreCp > 0) "红方" else "黑方"
        val plies = Score.MATE - abs.toInt()
        return "$side 将杀 $plies 步内"
    }
    val side = if (scoreCp > 0) "红方" else "黑方"
    return when {
        abs < EVEN_CP -> "均势"
        abs < SLIGHT_CP -> "$side 微优"
        abs < CLEAR_CP -> "$side 占优"
        abs < BIG_CP -> "$side 大优"
        else -> "$side 决定性优势"
    }
}

private const val EVEN_CP: Int = 30
private const val SLIGHT_CP: Int = 80
private const val CLEAR_CP: Int = 200
private const val BIG_CP: Int = 600

/** 折线 Y 轴 clamp 边界。±600 cp = ±6 兵,覆盖绝大多数非杀棋局面。 */
private const val CLAMP_CP: Int = 600

