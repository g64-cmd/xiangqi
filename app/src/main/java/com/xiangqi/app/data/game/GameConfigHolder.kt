package com.xiangqi.app.data.game

import com.xiangqi.app.ui.game.GameMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局对局配置持有者。
 *
 * 用 Singleton StateFlow 而非路由参数,避免对 GameConfig 自定义类型做 NavHost
 * 序列化。代价:同 App 同时只能有 1 局(M4 接受;M6 多局管理时再优化)。
 *
 * 写入侧:[com.xiangqi.app.ui.setup.SetupViewModel.onStart]
 * 读取侧:[com.xiangqi.app.ui.game.GameViewModel] 的 `combine` 流
 */
@Singleton
class GameConfigHolder @Inject constructor() {
    private val _config = MutableStateFlow(GameConfig(mode = GameMode.HOT_SEAT))
    val config: StateFlow<GameConfig> = _config.asStateFlow()

    /** 设置新的开局配置。SetupScreen "开始对局"时调用。 */
    fun set(config: GameConfig) {
        _config.value = config
    }
}
