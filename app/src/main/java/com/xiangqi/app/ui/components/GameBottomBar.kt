package com.xiangqi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
 * 底部栏:第一行悔棋(条件禁用)+ 认输(进行中且非 AI 思考)+ 重开;
 * 第二行(M6)提示 + 求和 + 分析。
 */
@Composable
fun GameBottomBar(
    canUndo: Boolean,
    result: GameResult,
    isAiThinking: Boolean,
    onUndo: () -> Unit,
    onResign: () -> Unit,
    onRestart: () -> Unit,
    canHint: Boolean = false,
    onHint: () -> Unit = {},
    canOfferDraw: Boolean = false,
    onDrawOffer: () -> Unit = {},
    canAnalyze: Boolean = false,
    onAnalyze: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ongoing = result is GameResult.ONGOING
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onHint,
                enabled = canHint,
                modifier = Modifier.weight(1f),
            ) {
                Text("提示")
            }
            OutlinedButton(
                onClick = onDrawOffer,
                enabled = canOfferDraw,
                modifier = Modifier.weight(1f),
            ) {
                Text("求和")
            }
            OutlinedButton(
                onClick = onAnalyze,
                enabled = canAnalyze,
                modifier = Modifier.weight(1f),
            ) {
                Text("分析")
            }
        }
    }
}
