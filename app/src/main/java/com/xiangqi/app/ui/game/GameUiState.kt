package com.xiangqi.app.ui.game

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/**
 * 渲染 GameScreen 所需的全部状态。
 *
 * 这是 [com.xiangqi.app.data.game.GameState] 的"UI 视图"——把仓库状态投影成
 * Compose 友好的形状,并叠加选择高亮、合法目标点等纯 UI 状态。
 *
 * @property board 当前棋盘。
 * @property sideToMove 当前轮走方,用于"红方走/黑方走"提示。
 * @property selected 当前选中的格子,null = 未选中。
 * @property legalTargets 选中格棋子的合法目标集合。未选中时空。
 * @property lastMove 最近一步走法,用于"上一步高亮"。
 * @property result 当前对局结果(终局时 GameViewModel 自动停手)。
 * @property canUndo 是否还有历史可悔。
 * @property orientation 哪方在屏幕底部。M3 固定 RED;M4 SetupScreen 改为可配置。
 */
data class GameUiState(
    val board: Board,
    val sideToMove: Side,
    val selected: Position? = null,
    val legalTargets: Set<Position> = emptySet(),
    val lastMove: Move? = null,
    val result: GameResult = GameResult.ONGOING,
    val canUndo: Boolean = false,
    val orientation: Side = Side.RED,
)
