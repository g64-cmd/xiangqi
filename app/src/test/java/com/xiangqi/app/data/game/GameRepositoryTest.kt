package com.xiangqi.app.data.game

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.model.GameResult
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.model.DrawReason
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.CheckmateDetector
import com.xiangqi.app.domain.rules.MoveLegality
import org.junit.Test

/**
 * [GameRepository] 行为契约:
 * - 初始状态 = 标准 FEN 开局,RED 走。
 * - applyMove 翻转 sideToMove、压栈 history。
 * - applyMove 拒绝非法走法 / 错方走子 / 游戏已结束。
 * - undo 恢复上一步状态。
 * - restart 回到初始 FEN。
 */
class GameRepositoryTest {

    private fun newRepo(): GameRepository {
        val gen = MoveGeneratorImpl()
        val check = CheckDetector(gen)
        val legality = MoveLegality(gen, check)
        val checkmate = CheckmateDetector(gen, check, legality)
        return GameRepository(gen, legality, checkmate)
    }

    @Test
    fun `initial state is standard opening with RED to move`() {
        val s = newRepo().state.value
        val initial = FenParser.parse(FenParser.INITIAL_FEN)
        assertThat(s.board).isEqualTo(initial.board)
        assertThat(s.sideToMove).isEqualTo(Side.RED)
        assertThat(s.history).isEmpty()
        assertThat(s.result).isEqualTo(GameResult.ONGOING)
    }

    @Test
    fun `applyMove advances sideToMove and records history`() {
        val repo = newRepo()
        // 红炮 h2e2(七路炮平中路),合法首招。
        val ok = repo.applyMove(Move.fromUci("h2e2", Side.RED))
        assertThat(ok).isTrue()
        val s = repo.state.value
        assertThat(s.sideToMove).isEqualTo(Side.BLACK)
        assertThat(s.history).hasSize(1)
        assertThat(s.history.first().move.toUci()).isEqualTo("h2e2")
    }

    @Test
    fun `applyMove rejects illegal move and leaves state unchanged`() {
        val repo = newRepo()
        // a0b1 = 红车斜走一格。MoveGenerator 不会产出这种走法(车只能直线);
        // Board.applyMove 是纯搬子,不校验几何,所以 Repository 必须先用
        // moveGenerator.movesFrom 验证 from 处棋子能走到 to。
        val before = repo.state.value
        val ok = repo.applyMove(Move.fromUci("a0b1", Side.RED))
        assertThat(ok).isFalse()
        assertThat(repo.state.value).isEqualTo(before)
        assertThat(repo.state.value.history).isEmpty()
    }

    @Test
    fun `applyMove rejects move from wrong side`() {
        val repo = newRepo()
        val before = repo.state.value
        val ok = repo.applyMove(Move.fromUci("h2e2", Side.BLACK))
        assertThat(ok).isFalse()
        assertThat(repo.state.value).isEqualTo(before)
    }

    @Test
    fun `undo restores previous state and history`() {
        val repo = newRepo()
        repo.applyMove(Move.fromUci("h2e2", Side.RED))
        val undone = repo.undo()
        assertThat(undone).isTrue()
        val s = repo.state.value
        assertThat(s.sideToMove).isEqualTo(Side.RED)
        assertThat(s.history).isEmpty()
        val initial = FenParser.parse(FenParser.INITIAL_FEN)
        assertThat(s.board).isEqualTo(initial.board)
    }

    @Test
    fun `undo after multiple moves pops only last ply`() {
        val repo = newRepo()
        repo.applyMove(Move.fromUci("h2e2", Side.RED))
        repo.applyMove(Move.fromUci("h9g7", Side.BLACK))  // 黑马跳
        assertThat(repo.state.value.history).hasSize(2)
        repo.undo()
        assertThat(repo.state.value.history).hasSize(1)
        assertThat(repo.state.value.sideToMove).isEqualTo(Side.BLACK)
    }

    @Test
    fun `undo with empty history returns false`() {
        val repo = newRepo()
        assertThat(repo.undo()).isFalse()
        assertThat(repo.state.value.history).isEmpty()
    }

    @Test
    fun `restart resets to initial FEN`() {
        val repo = newRepo()
        repo.applyMove(Move.fromUci("h2e2", Side.RED))
        repo.applyMove(Move.fromUci("h9g7", Side.BLACK))
        repo.restart()
        val s = repo.state.value
        val initial = FenParser.parse(FenParser.INITIAL_FEN)
        assertThat(s.board).isEqualTo(initial.board)
        assertThat(s.sideToMove).isEqualTo(Side.RED)
        assertThat(s.history).isEmpty()
        assertThat(s.result).isEqualTo(GameResult.ONGOING)
    }

    @Test
    fun `setDraw sets result to Draw AGREED while ONGOING`() {
        val repo = newRepo()
        repo.applyMove(Move.fromUci("h2e2", Side.RED))
        val ok = repo.setDraw(DrawReason.AGREED)
        assertThat(ok).isTrue()
        val s = repo.state.value
        assertThat(s.result).isEqualTo(GameResult.Draw(DrawReason.AGREED))
    }

    @Test
    fun `setDraw rejected after game over`() {
        val repo = newRepo()
        repo.setDraw(DrawReason.AGREED)
        val ok = repo.setDraw(DrawReason.AGREED)
        assertThat(ok).isFalse()
    }

    @Test
    fun `setDraw does not modify history`() {
        val repo = newRepo()
        repo.applyMove(Move.fromUci("h2e2", Side.RED))
        repo.applyMove(Move.fromUci("h9g7", Side.BLACK))
        val historyBefore = repo.state.value.history
        repo.setDraw(DrawReason.AGREED)
        assertThat(repo.state.value.history).isEqualTo(historyBefore)
    }
}
