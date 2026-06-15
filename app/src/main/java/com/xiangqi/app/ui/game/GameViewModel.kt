package com.xiangqi.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiangqi.app.data.game.GameRepository
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.MoveLegality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * GameScreen 的状态机。
 *
 * 数据流:`repo.state` (单一真相源) + 两个本地 `_selected` / `_legalTargets` → combine 成
 * [uiState]。所有事件入口(`onTap` / `onUndo` / `onRestart`)同步执行(领域层都是微秒级)。
 *
 * **选择状态机** (onTap):
 * 1. 游戏已结束 → 忽略。
 * 2. 无选中 + 点己方棋 → 选中 + 算合法目标。
 * 3. 已选中 + 点同格 → 取消选中。
 * 4. 已选中 + 点合法目标 → 走子 + 清选中。
 * 5. 已选中 + 点另一颗己方棋 → 切换选中。
 * 6. 其他 → 清选中。
 *
 * **M3 范围**:hot-seat(双人手动走)。M4 才会加 SelfEngine 在 AI 回合自动走子。
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val repo: GameRepository,
    private val moveGenerator: MoveGenerator,
    private val moveLegality: MoveLegality,
) : ViewModel() {

    private val _selected = MutableStateFlow<Position?>(null)
    private val _legalTargets = MutableStateFlow<Set<Position>>(emptySet())

    val uiState: StateFlow<GameUiState> =
        combine(repo.state, _selected, _legalTargets) { gs, sel, targets ->
            GameUiState(
                board = gs.board,
                sideToMove = gs.sideToMove,
                selected = sel,
                legalTargets = if (sel != null) targets else emptySet(),
                lastMove = gs.history.lastOrNull()?.move,
                result = gs.result,
                canUndo = gs.history.isNotEmpty(),
                orientation = Side.RED,  // M3 固定;M4 setup screen 改
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    fun onTap(position: Position) {
        val s = repo.state.value
        if (s.result !is GameResult.ONGOING) return
        val sel = _selected.value
        val pieceAtTap = s.board[position]

        when {
            sel == null && pieceAtTap?.side == s.sideToMove ->
                select(position)
            sel != null && position == sel ->
                clearSelection()
            sel != null && position in _legalTargets.value -> {
                repo.applyMove(Move(sel, position, s.sideToMove))
                clearSelection()
            }
            sel != null && pieceAtTap?.side == s.sideToMove ->
                select(position)
            else ->
                clearSelection()
        }
    }

    fun onUndo() {
        clearSelection()
        repo.undo()
    }

    fun onRestart() {
        clearSelection()
        repo.restart()
    }

    private fun select(p: Position) {
        val s = repo.state.value
        val moves = moveGenerator.movesFrom(s.board, p)
        val legal = moveLegality.legalMoves(s.board, moves).map { it.to }.toSet()
        _selected.value = p
        _legalTargets.value = legal
    }

    private fun clearSelection() {
        _selected.value = null
        _legalTargets.value = emptySet()
    }

    private fun initialState(): GameUiState {
        val f = FenParser.parse(FenParser.INITIAL_FEN)
        return GameUiState(board = f.board, sideToMove = f.sideToMove)
    }
}
