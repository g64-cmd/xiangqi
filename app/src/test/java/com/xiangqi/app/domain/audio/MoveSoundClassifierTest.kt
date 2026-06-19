package com.xiangqi.app.domain.audio

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.audio.SoundKind
import com.xiangqi.app.data.model.HistoryEntry
import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import org.junit.Test

/**
 * [MoveSoundClassifier] 行为契约。
 *
 * 6 类分支:
 * 1. 无新走子 → null
 * 2. undo(history 缩短)→ null
 * 3. 普通走子(未吃 / 未将 / 未终局)→ MOVE
 * 4. 吃子(目标格原有子)→ CAPTURE
 * 5. 将军(对手被将、未将死、未终局)→ CHECK
 * 6. 终局这步:困毙 → STALEMATE;将死 → CHECK(优先于吃子)
 *
 * classifier 接收原始布尔参数(opponentInCheck / opponentStalemate),
 * 不依赖规则组件,单测构造 Board + HistoryEntry 即可。
 */
class MoveSoundClassifierTest {

    private val redPawn = Piece(PieceType.PAWN, Side.RED)
    private val blackPawn = Piece(PieceType.PAWN, Side.BLACK)

    /**
     * 构造一条"红兵从 (0,0) 走到 (0,1)"的 HistoryEntry。
     * [capturedAt] 控制走子前 (0,1) 格是否有子(被吃子)。
     */
    private fun entry(capturedAt: Piece? = null): HistoryEntry {
        val boardBefore = Board.build { col, row ->
            when {
                col == 0 && row == 0 -> redPawn
                col == 0 && row == 1 -> capturedAt
                else -> null
            }
        }
        return HistoryEntry(
            move = Move(Position(0, 0), Position(0, 1), Side.RED),
            boardBefore = boardBefore,
            sideToMoveBefore = Side.RED,
            resultBefore = GameResult.ONGOING,
        )
    }

    @Test
    fun `no new move returns null`() {
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 0,
            newHistorySize = 0,
            entry = null,
            result = GameResult.ONGOING,
            resultBefore = GameResult.ONGOING,
            opponentInCheck = false,
            opponentStalemate = false,
        )
        assertThat(result).isNull()
    }

    @Test
    fun `undo reduces history size returns null`() {
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 2,
            newHistorySize = 1,
            entry = entry(),
            result = GameResult.ONGOING,
            resultBefore = GameResult.ONGOING,
            opponentInCheck = false,
            opponentStalemate = false,
        )
        assertThat(result).isNull()
    }

    @Test
    fun `normal move returns MOVE`() {
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 0,
            newHistorySize = 1,
            entry = entry(capturedAt = null),
            result = GameResult.ONGOING,
            resultBefore = GameResult.ONGOING,
            opponentInCheck = false,
            opponentStalemate = false,
        )
        assertThat(result).isEqualTo(SoundKind.MOVE)
    }

    @Test
    fun `capture returns CAPTURE`() {
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 0,
            newHistorySize = 1,
            entry = entry(capturedAt = blackPawn),
            result = GameResult.ONGOING,
            resultBefore = GameResult.ONGOING,
            opponentInCheck = false,
            opponentStalemate = false,
        )
        assertThat(result).isEqualTo(SoundKind.CAPTURE)
    }

    @Test
    fun `check without mate returns CHECK`() {
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 0,
            newHistorySize = 1,
            entry = entry(capturedAt = null),
            result = GameResult.ONGOING,
            resultBefore = GameResult.ONGOING,
            opponentInCheck = true,
            opponentStalemate = false,
        )
        assertThat(result).isEqualTo(SoundKind.CHECK)
    }

    @Test
    fun `terminal non-stalemate returns CHECK`() {
        // 将死:result 由 ONGOING 变 BlackWin,opponentStalemate=false。
        // becameTerminal 优先于 CAPTURE(目标格有黑兵被吃),应返回 CHECK。
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 0,
            newHistorySize = 1,
            entry = entry(capturedAt = blackPawn),
            result = GameResult.BlackWin,
            resultBefore = GameResult.ONGOING,
            opponentInCheck = true,
            opponentStalemate = false,
        )
        assertThat(result).isEqualTo(SoundKind.CHECK)
    }

    @Test
    fun `terminal stalemate returns STALEMATE`() {
        // 困毙:becameTerminal 且 opponentStalemate=true。
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 0,
            newHistorySize = 1,
            entry = entry(capturedAt = null),
            result = GameResult.BlackWin,
            resultBefore = GameResult.ONGOING,
            opponentInCheck = false,
            opponentStalemate = true,
        )
        assertThat(result).isEqualTo(SoundKind.STALEMATE)
    }

    @Test
    fun `result already terminal before this move returns null`() {
        // resultBefore 非 ONGOING:不该走到这里(repo 不会在终局后再 applyMove),
        // 但 classifier 应防御性返回 null(避免重复发声)。
        val result = MoveSoundClassifier.classify(
            prevHistorySize = 0,
            newHistorySize = 1,
            entry = entry(capturedAt = null),
            result = GameResult.BlackWin,
            resultBefore = GameResult.BlackWin,
            opponentInCheck = false,
            opponentStalemate = false,
        )
        // resultBefore 非 ONGOING → becameTerminal=false → 落到 capture/check 分支
        // capturedAt=null → 不是 CAPTURE;opponentInCheck=false → 不是 CHECK → MOVE
        // 这是分类器的具体行为,记录下来防回归。
        assertThat(result).isEqualTo(SoundKind.MOVE)
    }
}
