package com.xiangqi.app.data.model

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side

/**
 * 悔棋栈中的一条记录。
 *
 * 每当 [com.xiangqi.app.data.game.GameRepository.applyMove] 被调用时,把"走子前"的完整
 * 快照(board / sideToMove / result)压栈。`undo()` 弹出栈顶即可恢复到走子前。
 *
 * 注意:这里存的是"快照引用",而不是 diff;Board 是不可变的,所以引用安全。
 *
 * @property move 本次执行的走法(便于 UI 显示"上一步")。
 * @property boardBefore 走子前的棋盘快照。
 * @property sideToMoveBefore 走子前的轮走方。
 * @property resultBefore 走子前的对局结果(几乎总是 ONGOING;但如果撤销最后一步杀棋,
 *   这里记录的是杀棋之前的 ONGOING)。
 */
data class HistoryEntry(
    val move: Move,
    val boardBefore: Board,
    val sideToMoveBefore: Side,
    val resultBefore: GameResult,
)
