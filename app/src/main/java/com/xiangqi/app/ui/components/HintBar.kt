package com.xiangqi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiangqi.app.domain.model.Move

/**
 * Hint 候选条:横向最多 3 个 OutlinedButton,显示候选走子的 UCI 串。
 *
 * 候选**不显示分数**(走完任何候选后,UI 按 auto-eval 流程刷新真实局势)。
 * 首个候选(主推荐)在棋盘上画箭头,这里也对应按钮 1。
 *
 * @param candidates 引擎给出的候选走子列表(可能少于 3)。
 * @param onPlay 选中某个候选时回调,参数是候选索引(0-based)。
 */
@Composable
fun HintBar(
    candidates: List<Move>,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        candidates.forEachIndexed { index, move ->
            OutlinedButton(
                onClick = { onPlay(index) },
                modifier = Modifier.weight(1f),
            ) {
                Text(moveLabel(index, move))
            }
        }
    }
}

/**
 * 候选按钮显示文字。
 *
 * 第一个候选标注"主推"(对应棋盘箭头);其余标注"候选 N"。
 * 走子用 UCI 串(列行列行,例如 `e3e4`)。后续可加中文棋谱显示。
 */
private fun moveLabel(index: Int, move: Move): String {
    val tag = if (index == 0) "主推" else "候选 ${index + 1}"
    return "$tag ${move.toUci()}"
}
