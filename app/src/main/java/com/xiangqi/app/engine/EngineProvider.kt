package com.xiangqi.app.engine

import com.xiangqi.app.di.PikafishEngineQual
import com.xiangqi.app.di.SelfEngineQual
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 引擎分发器。按 [EngineType] 选择 [Engine] 实现,供 [com.xiangqi.app.ui.game.GameViewModel]
 * 在 launchAiMove 时按 [com.xiangqi.app.data.game.GameConfig.engineType] 取用。
 *
 * 与"直接注入 Engine"相比,允许同一 Hilt 图同时持有两个引擎实现而不冲突。
 */
fun interface EngineProvider {
    fun provide(type: EngineType): Engine
}

@Singleton
class EngineProviderImpl @Inject constructor(
    @SelfEngineQual private val self: Engine,
    @PikafishEngineQual private val pikafish: Engine,
) : EngineProvider {
    override fun provide(type: EngineType): Engine = when (type) {
        EngineType.SELF -> self
        EngineType.PIKAFISH -> pikafish
    }
}
