package com.xiangqi.app.ui.setup

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.ui.game.GameMode
import org.junit.Test

class SetupViewModelTest {

    private fun newVm(): SetupViewModel = SetupViewModel(GameConfigHolder())

    @Test
    fun `默认状态为人机 红方 中级 皮卡鱼`() {
        val vm = newVm()
        val s = vm.ui.value
        assertThat(s.mode).isEqualTo(GameMode.HUMAN_VS_AI)
        assertThat(s.humanSide).isEqualTo(Side.RED)
        assertThat(s.difficulty).isEqualTo(Difficulty.INTERMEDIATE)
        assertThat(s.engineType).isEqualTo(EngineType.PIKAFISH)
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
    fun `切到自研引擎后 config engineType == SELF`() {
        val holder = GameConfigHolder()
        val vm = SetupViewModel(holder)

        vm.onEngineTypeChange(EngineType.SELF)
        vm.onStart {}

        assertThat(holder.config.value.engineType).isEqualTo(EngineType.SELF)
    }

    @Test
    fun `HOT_SEAT 模式下 engineType 仍写入但不影响游戏`() {
        val holder = GameConfigHolder()
        val vm = SetupViewModel(holder)

        vm.onModeChange(GameMode.HOT_SEAT)
        vm.onEngineTypeChange(EngineType.SELF)
        vm.onStart {}

        // 引擎字段仍同步,但 ViewModel 会因 mode=HOT_SEAT 不启动 AI
        assertThat(holder.config.value.engineType).isEqualTo(EngineType.SELF)
        assertThat(holder.config.value.mode).isEqualTo(GameMode.HOT_SEAT)
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
