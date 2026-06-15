package com.xiangqi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiangqi.app.domain.model.GameResult

/**
 * 底部栏:悔棋(条件禁用)+ 认输(进行中且非 AI 思考)+ 重开。
 */
@Composable
fun GameBottomBar(
    canUndo: Boolean,
    result: GameResult,
    isAiThinking: Boolean,
    onUndo: () -> Unit,
    onResign: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ongoing = result is GameResult.ONGOING
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onUndo,
            enabled = canUndo && ongoing && !isAiThinking,
            modifier = Modifier.weight(1f),
        ) {
            Text("悔棋")
        }
        OutlinedButton(
            onClick = onResign,
            enabled = ongoing && !isAiThinking,
            modifier = Modifier.weight(1f),
        ) {
            Text("认输")
        }
        Button(
            onClick = onRestart,
            modifier = Modifier.weight(1f),
        ) {
            Text("重开", style = MaterialTheme.typography.labelLarge)
        }
    }
}
