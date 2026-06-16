package com.xiangqi.app.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.xiangqi.app.ui.theme.Cinnabar
import com.xiangqi.app.ui.theme.InkBlack
import com.xiangqi.app.ui.theme.InkGray

/**
 * 局势分析曲线 Dialog(M6):用 Compose Canvas 画整局分数走势图。
 *
 * - X 轴:ply 索引(0..N)
 * - Y 轴:score(红方视角 cp),clamp 到 [-CLAMP_CP, +CLAMP_CP]
 * - 0 轴居中横线
 * - 红色折线 + 半透明填充
 *
 * 不引入 MPAndroidChart 依赖,自绘最简折线足够。
 *
 * @param scores 红方视角 centipawn 序列,索引对齐走子历史。
 * @param onDismiss 关闭回调。
 */
@Composable
fun AnalysisDialog(
    scores: List<Float>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("局势分析") },
        text = {
            Column {
                Text(
                    text = "共 ${scores.size} 步  红方视角(centipawn)",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkGray,
                )
                if (scores.isEmpty()) {
                    Text(
                        text = "尚未走子,暂无评估数据",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(top = 12.dp),
                    ) {
                        EvalChart(scores = scores)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("红优 ←", style = MaterialTheme.typography.labelSmall, color = Cinnabar)
                    Text("均势", style = MaterialTheme.typography.labelSmall, color = InkGray)
                    Text("→ 黑优", style = MaterialTheme.typography.labelSmall, color = InkBlack)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun EvalChart(scores: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        // 0 轴中线
        drawLine(
            color = InkGray.copy(alpha = 0.4f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1f,
        )

        if (scores.size < 2) {
            // 单点:画一个圆
            val y = if (scores.isNotEmpty()) mapScoreToY(scores[0], h) else centerY
            drawCircle(Cinnabar, radius = 6f, center = Offset(w / 2f, y))
            return@Canvas
        }

        val stepX = w / (scores.size - 1).toFloat()
        val points = scores.mapIndexed { i, s ->
            Offset(i * stepX, mapScoreToY(s, h))
        }

        // 半透明填充
        val fillPath = Path().apply {
            moveTo(0f, centerY)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, centerY)
            close()
        }
        drawPath(fillPath, Cinnabar.copy(alpha = 0.20f))

        // 折线
        for (i in 1 until points.size) {
            drawLine(
                color = Cinnabar,
                start = points[i - 1],
                end = points[i],
                strokeWidth = 3f,
            )
        }

        // 终点标记
        drawCircle(Cinnabar, radius = 5f, center = points.last())
    }
}

/**
 * 把分数映射到 Canvas Y 坐标。
 *
 * - score == 0 -> 中线(height/2)
 * - score > 0 -> 上方(红方好,分数正向)
 * - score < 0 -> 下方(黑方好)
 * - |score| > [CLAMP_CP] -> clamp 到上下边界
 *
 * @param scoreCp 红方视角 centipawn。
 * @param height Canvas 像素高度。
 * @return Y 坐标。
 */
internal fun mapScoreToY(scoreCp: Float, height: Float): Float {
    val clamped = scoreCp.coerceIn(-CLAMP_CP.toFloat(), CLAMP_CP.toFloat())
    // 把 [-CLAMP, +CLAMP] 映射到 [height, 0](上正下负)
    val normalized = clamped / CLAMP_CP.toFloat() // [-1, +1]
    return height / 2f - normalized * (height / 2f)
}

/** Y 轴 clamp 边界。±1000 cp = ±10 兵,足够覆盖绝大多数非杀棋局面。 */
internal const val CLAMP_CP: Int = 1000
