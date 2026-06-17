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
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.notation.ChineseNotation

/**
 * Hint 候选条:横向最多 3 个 OutlinedButton,显示候选走子的中文棋谱。
 *
 * 候选**不显示分数**(走完任何候选后,UI 按 auto-eval 流程刷新真实局势)。
 * 首个候选(主推荐)在棋盘上画箭头,这里也对应按钮 1。
 *
 * **棋谱显示**:用 [ChineseNotation] 把走子翻译为"炮二平五" / "马八进七" /
 * "前车退一" 等。`boardBefore` 是当前未走子的局面,format 内部查起点棋子类型
 * 与同列同种棋子(前/后)。如果起点无棋子(异常),退化为 UCI 串。
 *
 * @param candidates 引擎给出的候选走子列表(可能少于 3)。
 * @param boardBefore 走子前的棋盘,用于中文棋谱翻译。
 * @param onPlay 选中某个候选时回调,参数是候选索引(0-based)。
 */
@Composable
fun HintBar(
    candidates: List<Move>,
    boardBefore: Board,
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
                Text(moveLabel(index, move, boardBefore))
            }
        }
    }
}

/**
 * 候选按钮显示文字。
 *
 * 第一个候选标注"主推"(对应棋盘箭头);其余标注"候选 N"。
 * 走子用中文棋谱;无法翻译(异常情况)时退化为 UCI 串。
 */
private fun moveLabel(index: Int, move: Move, boardBefore: Board): String {
    val tag = if (index == 0) "主推" else "候选 ${index + 1}"
    val notation = runCatching { ChineseNotation.format(move, boardBefore) }
        .getOrDefault(move.toUci())
    return "$tag $notation"
}
