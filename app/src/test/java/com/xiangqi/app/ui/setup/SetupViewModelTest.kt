package com.xiangqi.app.ui.setup

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.ui.game.GameMode
import org.junit.Test

class SetupViewModelTest {

    private fun newVm(): SetupViewModel = SetupViewModel(GameConfigHolder())

    @Test
    fun `默认状态为人机 红方 中级`() {
        val vm = newVm()
        val s = vm.ui.value
        assertThat(s.mode).isEqualTo(GameMode.HUMAN_VS_AI)
        assertThat(s.humanSide).isEqualTo(Side.RED)
        assertThat(s.difficulty).isEqualTo(Difficulty.INTERMEDIATE)
    }

    @Test
    fun `切到双人本地后 configHolder 持久化`() {
        val holder = GameConfigHolder()
        val vm = SetupViewModel(holder)
        var configured = false

        vm.onModeChange(GameMode.HOT_SEAT)
        vm.onStart { configured = true }

        assertThat(configured).isTrue()
        val cfg = holder.config.value
        assertThat(cfg.mode).isEqualTo(GameMode.HOT_SEAT)
        assertThat(cfg.orientation).isEqualTo(Side.RED)
    }

    @Test
    fun `选黑方后 config humanSide == BLACK 且 orientation == BLACK`() {
        val holder = GameConfigHolder()
        val vm = SetupViewModel(holder)

        vm.onSideChange(Side.BLACK)
        vm.onStart {}

        val cfg = holder.config.value
        assertThat(cfg.humanSide).isEqualTo(Side.BLACK)
        assertThat(cfg.orientation).isEqualTo(Side.BLACK)
    }

    @Test
    fun `选高级后 config difficulty == ADVANCED`() {
        val holder = GameConfigHolder()
        val vm = SetupViewModel(holder)

        vm.onDifficultyChange(Difficulty.ADVANCED)
        vm.onStart {}

        assertThat(holder.config.value.difficulty).isEqualTo(Difficulty.ADVANCED)
    }

    @Test
    fun `onStart 触发回调`() {
        val vm = newVm()
        var calls = 0
        vm.onStart { calls++ }
        vm.onStart { calls++ }
        assertThat(calls).isEqualTo(2)
    }
}
