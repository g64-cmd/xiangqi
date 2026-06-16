package com.xiangqi.app.ui.game

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.SearchInfo

/**
 * 渲染 GameScreen 所需的全部状态。
 *
 * 这是 [com.xiangqi.app.data.game.GameState] 的"UI 视图"——把仓库状态投影成
 * Compose 友好的形状,并叠加选择高亮、合法目标点、AI 思考指示等纯 UI 状态。
 *
 * @property board 当前棋盘。
 * @property sideToMove 当前轮走方,用于"红方走/黑方走"提示。
 * @property selected 当前选中的格子,null = 未选中。
 * @property legalTargets 选中格棋子的合法目标集合。未选中时或不可交互时为空。
 * @property lastMove 最近一步走法,用于"上一步高亮"。
 * @property result 当前对局结果(终局时 GameViewModel 自动停手)。
 * @property canUndo 是否还有历史可悔,且当前 AI 不在思考。
 * @property orientation 哪方在屏幕底部。HOT_SEAT=RED;HUMAN_VS_AI=humanSide。
 *
 * M4 新增字段:
 * @property mode 对局模式。
 * @property humanSide 人机模式下玩家执棋方。
 * @property isAiThinking AI 是否正在思考。
 * @property searchInfo AI 思考过程中的最新 SearchInfo 快照,思考结束后清空。
 * @property canInteract UI 是否接受玩家点击(AI 思考中 / 游戏结束 / AI 回合都禁用)。
 *
 * M6 新增字段:
 * @property suggestedMove 引擎提示走法(Hint 按钮触发),null = 无提示。在棋盘上
 *   画半透明箭头,玩家 onTap / onUndo / onRestart 时清空。
 * @property canHint 是否可以触发提示(进行中且引擎空闲且轮到玩家方)。
 * @property canOfferDraw 是否可以请求求和(同 canHint 条件)。
 * @property currentScore 当前局面评估(红方视角 centipawn);走子后自动更新。
 *   null 表示尚未评估或评估失败。展示在 TopBar 副标题。
 * @property evalHistory 整局评估序列(红方视角 cp),索引对齐 history。用于曲线图。
 * @property canAnalyze 是否可手动触发分析(进行中、引擎空闲)。M7/9 commit 用。
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
    val mode: GameMode = GameMode.HOT_SEAT,
    val humanSide: Side = Side.RED,
    val isAiThinking: Boolean = false,
    val searchInfo: SearchInfo? = null,
    val canInteract: Boolean = true,
    val suggestedMove: Move? = null,
    val canHint: Boolean = false,
    val canOfferDraw: Boolean = false,
    val currentScore: Float? = null,
    val evalHistory: List<Float> = emptyList(),
    val canAnalyze: Boolean = false,
    val showAnalysisDialog: Boolean = false,
)
