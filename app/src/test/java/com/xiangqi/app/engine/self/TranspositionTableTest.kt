package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Score
import org.junit.Test

class TranspositionTableTest {

    @Test
    fun `capacity must be power of two`() {
        expectIllegalArgument("capacity 必须为 2 的幂") { TranspositionTable(3) }
        expectIllegalArgument("capacity 必须为 2 的幂") { TranspositionTable(0) }
        TranspositionTable(4)
        TranspositionTable(1 shl 18)
    }

    @Test
    fun `hash is identical for identical position`() {
        val tt = TranspositionTable(16)
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val h1 = tt.hash(board, Side.RED)
        val h2 = tt.hash(board, Side.RED)
        assertThat(h1).isEqualTo(h2)
    }

    @Test
    fun `hash differs when side to move differs`() {
        val tt = TranspositionTable(16)
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val red = tt.hash(board, Side.RED)
        val black = tt.hash(board, Side.BLACK)
        assertThat(red).isNotEqualTo(black)
    }

    @Test
    fun `hash differs after a move`() {
        val tt = TranspositionTable(16)
        val before = FenParser.parse(FenParser.INITIAL_FEN).board
        val move = Move(Position(1, 2), Position(1, 4), Side.RED) // 红炮进二
        val after = before.applyMove(move)
        assertThat(tt.hash(before, Side.RED)).isNotEqualTo(tt.hash(after, Side.BLACK))
    }

    @Test
    fun `put and get round trip`() {
        val tt = TranspositionTable(16)
        val key = 0xDEADBEEFCAFEL
        val bestMove = Move(Position(0, 0), Position(1, 0), Side.RED)
        tt.put(key, depth = 4, flag = TranspositionTable.Flag.EXACT, score = 35, bestMove = bestMove)

        val e = tt.get(key)
        assertThat(e).isNotNull()
        assertThat(e!!.depth).isEqualTo(4)
        assertThat(e.flag).isEqualTo(TranspositionTable.Flag.EXACT)
        assertThat(e.score).isEqualTo(35)
        assertThat(e.bestMove).isEqualTo(bestMove)
    }

    @Test
    fun `get returns null for unknown key`() {
        val tt = TranspositionTable(16)
        tt.put(1L, 1, TranspositionTable.Flag.EXACT, 0, null)
        assertThat(tt.get(2L)).isNull()
    }

    @Test
    fun `clear empties the table`() {
        val tt = TranspositionTable(16)
        tt.put(1L, 1, TranspositionTable.Flag.EXACT, 0, null)
        assertThat(tt.entries).isEqualTo(1)
        tt.clear()
        assertThat(tt.entries).isEqualTo(0)
        assertThat(tt.get(1L)).isNull()
    }

    @Test
    fun `always-replace strategy overwrites conflicting slot`() {
        val tt = TranspositionTable(2) // 极小容量,强制冲突
        val k1 = 0x100000000L // 槽位 = (k1 & 1).toInt() = 0
        val k2 = 0x100000002L // 槽位 = (k2 & 1).toInt() = 0(同一槽,但 key 不同)
        tt.put(k1, 1, TranspositionTable.Flag.EXACT, 100, null)
        tt.put(k2, 2, TranspositionTable.Flag.EXACT, 200, null)
        // k1 已被覆盖
        assertThat(tt.get(k1)).isNull()
        assertThat(tt.get(k2)?.score).isEqualTo(200)
    }

    @Test
    fun `storeMateScore normalizes to ply-zero perspective`() {
        // 在 ply=3 找到的杀棋,MATE - distance = MATE - 0 = MATE(一步杀,distance=0)
        val score = Score.mateScore(0) // 当前 ply 视角的杀棋分 = MATE
        val stored = TranspositionTable.storeMateScore(score, ply = 3)
        // stored 应是 ply=0 视角下的"3 ply 后被杀"
        // stored = MATE - (0 + 3) = MATE - 3
        assertThat(stored).isEqualTo(Score.MATE - 3)
    }

    @Test
    fun `retrieveMateScore restores original ply perspective`() {
        val score = Score.mateScore(0)
        val stored = TranspositionTable.storeMateScore(score, ply = 3)
        val retrieved = TranspositionTable.retrieveMateScore(stored, retrievePly = 3)
        assertThat(retrieved).isEqualTo(score)
    }

    @Test
    fun `non-mate score is untouched by store and retrieve`() {
        val cp = 42
        assertThat(TranspositionTable.storeMateScore(cp, ply = 5)).isEqualTo(cp)
        assertThat(TranspositionTable.retrieveMateScore(cp, retrievePly = 5)).isEqualTo(cp)
    }

    private fun expectIllegalArgument(message: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("期望抛 IllegalArgumentException 含 \"$message\"")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message ?: "").contains(message)
        }
    }
}
