package com.xiangqi.app.data.game

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.ui.game.GameMode
import org.junit.Test

class GameConfigHolderTest {

    @Test
    fun `初始值是 HOT_SEAT 模式`() {
        val holder = GameConfigHolder()
        assertThat(holder.config.value.mode).isEqualTo(GameMode.HOT_SEAT)
    }

    @Test
    fun `set 后 config value 同步更新`() {
        val holder = GameConfigHolder()
        val cfg = GameConfig(
            mode = GameMode.HUMAN_VS_AI,
            humanSide = Side.BLACK,
            difficulty = Difficulty.ADVANCED,
            orientation = Side.BLACK,
        )
        holder.set(cfg)
        assertThat(holder.config.value).isEqualTo(cfg)
    }

    @Test
    fun `多次 set 取最后一次`() {
        val holder = GameConfigHolder()
        holder.set(GameConfig(mode = GameMode.HOT_SEAT))
        holder.set(GameConfig(mode = GameMode.HUMAN_VS_AI, difficulty = Difficulty.BEGINNER))
        assertThat(holder.config.value.mode).isEqualTo(GameMode.HUMAN_VS_AI)
        assertThat(holder.config.value.difficulty).isEqualTo(Difficulty.BEGINNER)
    }
}
