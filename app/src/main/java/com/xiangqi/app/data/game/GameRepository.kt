package com.xiangqi.app.data.game

import com.xiangqi.app.data.model.HistoryEntry
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.DrawReason
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGenerator
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对局完整状态。
 *
 * @property board 当前棋盘(不可变)。
 * @property sideToMove 当前轮走方。
 * @property history 走子历史栈,最后一条 = 最近一步。空表示未走子。
 * @property result 当前对局结果(ONGOING / RedWin / BlackWin / Draw)。
 */
data class GameState(
    val board: Board,
    val sideToMove: Side,
    val history: List<HistoryEntry>,
    val result: GameResult,
)

/**
 * 对局状态持有者与变更入口。
 *
 * 职责:维护 [GameState] 单一真相源,暴露不可变 [StateFlow];变更操作全部同步(领域层
 * 都是微秒级,不需要切协程)。M4 才会在 GameViewModel 接入 SelfEngine,届时由
 * ViewModel 负责协程切换。
 *
 * 不可变性:`applyMove` 内部走 `Board.applyMove` 生成新棋盘,旧快照在 history 中保留;
 * `undo` 把栈顶快照恢复成当前状态。
 */
@Singleton
class GameRepository @Inject constructor(
    private val moveGenerator: MoveGenerator,
    private val moveLegality: MoveLegality,
    private val checkmateDetector: CheckmateDetector,
) {
    private val _state: MutableStateFlow<GameState> = MutableStateFlow(initialState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** 应用走法。返回 false 表示走法非法 / 游戏已结束 / 走子方不匹配,状态不变。 */
    fun applyMove(move: Move): Boolean {
        val s = _state.value
        if (s.result !is GameResult.ONGOING) return false
        if (move.side != s.sideToMove) return false
        // 必须先匹配 MoveGenerator 的伪合法走法(几何合法),否则 Board.applyMove 会
        // 无视几何规则搬运棋子(它是纯搬子,不做规则校验)。
        val pseudoLegal = moveGenerator.movesFrom(s.board, move.from)
        if (pseudoLegal.none { it.to == move.to }) return false
        if (!moveLegality.isLegal(s.board, move)) return false

        val newBoard = s.board.applyMove(move)
        val nextSide = s.sideToMove.opponent
        val newResult = checkmateDetector.decide(newBoard, nextSide)

        _state.value = GameState(
            board = newBoard,
            sideToMove = nextSide,
            history = s.history + HistoryEntry(move, s.board, s.sideToMove, s.result),
            result = newResult,
        )
        return true
    }

    /** 悔一步(一个半回合)。history 空 → 返回 false。 */
    fun undo(): Boolean {
        val s = _state.value
        val last = s.history.lastOrNull() ?: return false
        _state.value = GameState(
            board = last.boardBefore,
            sideToMove = last.sideToMoveBefore,
            history = s.history.dropLast(1),
            result = last.resultBefore,
        )
        return true
    }

    /** 重置到标准开局。 */
    fun restart() {
        _state.value = initialState()
    }

    /**
     * 直接设置和棋结果(不修改 history)。仅当对局仍 [GameResult.ONGOING] 时生效;
     * 返回 false 表示对局已结束,操作被忽略。
     *
     * 典型用途:玩家与对手协商一致(M6 求和按钮)。
     */
    fun setDraw(reason: DrawReason): Boolean {
        val s = _state.value
        if (s.result !is GameResult.ONGOING) return false
        _state.value = s.copy(result = GameResult.Draw(reason))
        return true
    }

    private fun initialState(): GameState {
        val f = FenParser.parse(FenParser.INITIAL_FEN)
        return GameState(
            board = f.board,
            sideToMove = f.sideToMove,
            history = emptyList(),
            result = GameResult.ONGOING,
        )
    }
}
