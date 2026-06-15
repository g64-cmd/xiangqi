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
 * 底部栏:悔棋(条件禁用)+ 重开。
 */
@Composable
fun GameBottomBar(
    canUndo: Boolean,
    result: GameResult,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onUndo,
            enabled = canUndo && result is GameResult.ONGOING,
            modifier = Modifier.weight(1f),
        ) {
            Text("悔棋")
        }
        Button(
            onClick = onRestart,
            modifier = Modifier.weight(1f),
        ) {
            Text("重开", style = MaterialTheme.typography.labelLarge)
        }
    }
}
