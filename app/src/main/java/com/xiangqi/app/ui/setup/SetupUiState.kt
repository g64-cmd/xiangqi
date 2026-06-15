package com.xiangqi.app.ui.setup

import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.ui.game.GameMode

/**
 * SetupScreen 的 UI 状态。
 *
 * 由 [SetupViewModel] 持有,SetupScreen 通过 `collectAsStateWithLifecycle` 订阅。
 * 玩家每次修改选项后通过 `update` 推送新值。
 *
 * @property mode 对局模式,默认人机。
 * @property humanSide 人机模式下玩家执棋方,默认红方。
 * @property difficulty 人机模式下 AI 难度档位,默认中级。
 * @property engineType 人机模式下的 AI 引擎,默认皮卡鱼。
 */
data class SetupUiState(
    val mode: GameMode = GameMode.HUMAN_VS_AI,
    val humanSide: Side = Side.RED,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val engineType: EngineType = EngineType.PIKAFISH,
)
