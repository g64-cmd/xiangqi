package com.xiangqi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.SearchInfo

/**
 * 顶部栏:返回按钮 + 固定标题 + AI 思考副标题 / 轮走方 / 终局结果。
 *
 * - 进行中 + AI 思考:中央显示"AI 思考中…"+ SearchInfo(深度/分数/时间)。
 * - 进行中 + 非 AI:右显示"红方走/黑方走"。
 * - 终局:右显示结果。
 */
@Composable
fun GameTopBar(
    sideToMove: Side,
    result: GameResult,
    isAiThinking: Boolean,
    searchInfo: SearchInfo?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "← 返回",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clickable(onClick = onExit)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "中国象棋",
                style = MaterialTheme.typography.titleLarge,
            )
            if (isAiThinking) {
                val info = searchInfo
                val subtitle = if (info != null) {
                    "AI 思考中… 深度 ${info.depth}  分数 ${info.score}  ${info.timeMs}ms"
                } else {
                    "AI 思考中…"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        val indicator = when (result) {
            GameResult.ONGOING -> if (sideToMove == Side.RED) "红方走" else "黑方走"
            GameResult.RedWin -> "红方胜"
            GameResult.BlackWin -> "黑方胜"
            is GameResult.Draw -> "和棋"
        }
        Text(
            text = indicator,
            style = MaterialTheme.typography.labelLarge,
            color = if (sideToMove == Side.RED) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

