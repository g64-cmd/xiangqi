package com.xiangqi.app.ui.setup

import androidx.lifecycle.ViewModel
import com.xiangqi.app.data.game.GameConfig
import com.xiangqi.app.data.game.GameConfigHolder
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.ui.game.GameMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * SetupScreen 的状态机。
 *
 * 仅维护一个 [SetupUiState] StateFlow;每次事件(onModeChange 等)同步 update。
 * "开始对局"时把当前 UI 状态写入 [GameConfigHolder],供 GameViewModel 读取。
 *
 * orientation 派生规则:
 * - HOT_SEAT:固定 RED(M3 兼容)
 * - HUMAN_VS_AI:玩家执棋方在底(`humanSide`)
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val configHolder: GameConfigHolder,
) : ViewModel() {

    private val _ui = MutableStateFlow(SetupUiState())
    val ui: StateFlow<SetupUiState> = _ui.asStateFlow()

    fun onModeChange(mode: GameMode) {
        _ui.update { it.copy(mode = mode) }
    }

    fun onSideChange(side: com.xiangqi.app.domain.model.Side) {
        _ui.update { it.copy(humanSide = side) }
    }

    fun onDifficultyChange(difficulty: com.xiangqi.app.engine.Difficulty) {
        _ui.update { it.copy(difficulty = difficulty) }
    }

    fun onEngineTypeChange(engineType: EngineType) {
        _ui.update { it.copy(engineType = engineType) }
    }

    fun onAnalysisToggle(enabled: Boolean) {
        _ui.update { it.copy(enableAnalysis = enabled) }
    }

    /**
     * 把当前选择写入 [GameConfigHolder],然后触发 [onConfigured](通常是导航到 GameScreen)。
     */
    fun onStart(onConfigured: () -> Unit) {
        val s = _ui.value
        configHolder.set(
            GameConfig(
                mode = s.mode,
                humanSide = s.humanSide,
                difficulty = s.difficulty,
                orientation = if (s.mode == GameMode.HUMAN_VS_AI) s.humanSide
                else com.xiangqi.app.domain.model.Side.RED,
                engineType = s.engineType,
                enableAnalysis = s.enableAnalysis,
            )
        )
        onConfigured()
    }
}
