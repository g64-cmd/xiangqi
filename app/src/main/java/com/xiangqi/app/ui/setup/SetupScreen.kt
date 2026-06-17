package com.xiangqi.app.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Difficulty
import com.xiangqi.app.engine.EngineType
import com.xiangqi.app.ui.game.GameMode

@Composable
fun SetupScreen(
    onStart: () -> Unit,
    onAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    SetupScreenContent(
        state = state,
        onModeChange = viewModel::onModeChange,
        onSideChange = viewModel::onSideChange,
        onDifficultyChange = viewModel::onDifficultyChange,
        onEngineTypeChange = viewModel::onEngineTypeChange,
        onStart = { viewModel.onStart(onStart) },
        onAbout = onAbout,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreenContent(
    state: SetupUiState,
    onModeChange: (GameMode) -> Unit,
    onSideChange: (Side) -> Unit,
    onDifficultyChange: (Difficulty) -> Unit,
    onEngineTypeChange: (EngineType) -> Unit,
    onStart: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "中国象棋",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "关于",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onAbout)
                    .padding(vertical = 4.dp),
            )

            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("模式", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GameMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = state.mode == m,
                            onClick = { onModeChange(m) },
                            shape = SegmentedButtonDefaults.itemShape(index, GameMode.entries.size),
                        ) { Text(if (m == GameMode.HOT_SEAT) "双人本地" else "人机") }
                    }
                }
            }

            if (state.mode == GameMode.HUMAN_VS_AI) {
                Column(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("执棋方", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        Side.entries.forEachIndexed { index, s ->
                            SegmentedButton(
                                selected = state.humanSide == s,
                                onClick = { onSideChange(s) },
                                shape = SegmentedButtonDefaults.itemShape(index, Side.entries.size),
                            ) { Text(if (s == Side.RED) "红方" else "黑方") }
                        }
                    }
                }

                Column(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("AI 引擎", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        EngineType.entries.forEachIndexed { index, t ->
                            SegmentedButton(
                                selected = state.engineType == t,
                                onClick = { onEngineTypeChange(t) },
                                shape = SegmentedButtonDefaults.itemShape(index, EngineType.entries.size),
                            ) { Text(engineLabel(t)) }
                        }
                    }
                }

                Column(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("难度", style = MaterialTheme.typography.titleMedium)
                    Difficulty.entries.forEach { d ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.difficulty == d,
                                    role = Role.RadioButton,
                                    onClick = { onDifficultyChange(d) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = state.difficulty == d,
                                onClick = null,
                            )
                            Text(
                                text = difficultyLabel(d, state.engineType),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(56.dp),
            ) { Text("开始对局", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

private fun difficultyLabel(d: Difficulty, engineType: EngineType): String {
    val suffix = when (engineType) {
        EngineType.SELF -> "深度 ${d.depth}"
        EngineType.PIKAFISH -> "Skill ${pikafishSkill(d)}"
    }
    val base = when (d) {
        Difficulty.BEGINNER -> "初学"
        Difficulty.ELEMENTARY -> "初级"
        Difficulty.INTERMEDIATE -> "中级"
        Difficulty.ADVANCED -> "高级"
        Difficulty.HINT -> "提示"
        Difficulty.ANALYZE -> "评估"
    }
    return "$base($suffix)"
}

private fun engineLabel(t: EngineType): String = when (t) {
    EngineType.PIKAFISH -> "皮卡鱼"
    EngineType.SELF -> "自研"
}

private fun pikafishSkill(d: Difficulty): Int = when (d) {
    Difficulty.BEGINNER -> 0
    Difficulty.ELEMENTARY -> 5
    Difficulty.INTERMEDIATE -> 12
    Difficulty.ADVANCED -> 20
    Difficulty.HINT -> 10
    Difficulty.ANALYZE -> 20
}
