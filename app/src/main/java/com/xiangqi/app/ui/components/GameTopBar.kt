package com.xiangqi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Side

/**
 * 顶部栏:固定标题 + 轮走方提示,终局时显示结果。
 */
@Composable
fun GameTopBar(
    sideToMove: Side,
    result: GameResult,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "中国象棋",
            style = MaterialTheme.typography.titleLarge,
        )
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
        )
    }
}
