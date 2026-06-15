package com.xiangqi.app.ui.game

/**
 * 对局模式。
 *
 * - [HOT_SEAT]:双人本地,两位玩家共享同一设备轮流走子。
 * - [HUMAN_VS_AI]:人机对战,一方由玩家操控,另一方由 [com.xiangqi.app.engine.Engine] 自动应招。
 */
enum class GameMode { HOT_SEAT, HUMAN_VS_AI }
