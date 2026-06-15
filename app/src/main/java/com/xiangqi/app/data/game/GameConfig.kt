package com.xiangqi.app.data.game

import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.ui.game.GameMode

/**
 * 跨屏传输的开局配置。
 *
 * Setup 屏写入、Game 屏读取。默认值用于冷启动(GameConfigHolder 的初始值)与
 * [GameMode.HOT_SEAT] 时人机相关字段的占位。
 *
 * @property mode 对局模式。
 * @property humanSide 人机模式下玩家执棋方;[GameMode.HOT_SEAT] 时无意义但保留默认。
 * @property difficulty 人机模式下 AI 难度档位;[GameMode.HOT_SEAT] 时无意义。
 * @property orientation 屏幕底部哪方。人机 = 玩家方;双人本地 = RED(M3 兼容)。
 * @property engineType 人机模式下使用的 AI 引擎。HOT_SEAT 时无意义。
 */
data class GameConfig(
    val mode: GameMode,
    val humanSide: Side = Side.RED,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val orientation: Side = Side.RED,
    val engineType: EngineType = EngineType.PIKAFISH,
)
