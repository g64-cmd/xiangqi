package com.xiangqi.app.engine.self

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Score
import java.util.Random
import kotlin.math.absoluteValue

/**
 * 转置表(Transposition Table)+ Zobrist 哈希。
 *
 * **Zobrist 哈希**:启动时用固定种子生成 90×14 个棋子键 + 1 个走子方键。
 * 给定局面,把所有占位棋子的键 XOR 起来,再 XOR 走子方键,得到 64-bit 局面标识。
 * M2 暂用"每次全量重算"(O(N),N=棋子数);M3 引入 make/unmake 后可改增量更新。
 *
 * **TT 结构**:固定槽数(必须为 2 的幂),`index = (key & mask).toInt()`。
 * 写入采用 **always-replace** 替换策略——简单,M2 容量足够。
 *
 * **Flag 语义**(在以 [alpha]/[beta] 边界调用 negamax 后写入):
 * - [Flag.EXACT]:score 在 (alpha, beta) 内,精确。
 * - [Flag.LOWER_BOUND]:score >= beta(fail-high),真实值至少这么大。
 * - [Flag.UPPER_BOUND]:score <= alpha(fail-low),真实值至多这么大。
 *
 * **杀棋分修正**:杀棋分形如 `±(MATE - distance)`,distance = "从当前节点算起还需多少
 * 半回合到达将死"。同一个杀棋在 TT 里被另一个节点读到时,distance 需要按 ply 差平移。
 * 见 [retrieveMateScore] / [storeMateScore]。
 */
class TranspositionTable(capacity: Int) {

    init {
        require(capacity > 0 && capacity and (capacity - 1) == 0) {
            "capacity 必须为 2 的幂,实际: $capacity"
        }
    }

    private val table: Array<Entry?> = arrayOfNulls(capacity)
    private val mask: Int = capacity - 1

    var entries: Int = 0
        private set

    fun get(key: Long): Entry? {
        val e = table[(key and mask.toLong()).toInt()] ?: return null
        return if (e.key == key) e else null
    }

    fun put(key: Long, depth: Int, flag: Flag, score: Int, bestMove: Move?) {
        val idx = (key and mask.toLong()).toInt()
        if (table[idx] == null) entries++
        table[idx] = Entry(key, depth, flag, score, bestMove)
    }

    fun clear() {
        table.fill(null)
        entries = 0
    }

    /** 计算局面的 Zobrist 哈希(全量重算,M3 改增量)。 */
    fun hash(board: Board, sideToMove: Side): Long {
        var h = 0L
        for ((pos, piece) in board) {
            if (piece == null) continue
            h = h xor pieceKey(pos, piece)
        }
        if (sideToMove == Side.BLACK) h = h xor sideKey
        return h
    }

    /** TT 表项。score 已按存储时 ply 视角规范化(见 [storeMateScore])。 */
    data class Entry(
        val key: Long,
        val depth: Int,
        val flag: Flag,
        val score: Int,
        val bestMove: Move?,
    )

    /** 命中类型。 */
    enum class Flag { EXACT, LOWER_BOUND, UPPER_BOUND }

    private fun pieceKey(pos: Position, piece: Piece): Long =
        pieceKeys[pos.packed * 14 + piece.ordinalIndex()]

    private fun Piece.ordinalIndex(): Int = type.ordinal * 2 + side.ordinal

    companion object {
        // 固定种子,保证每次启动 Zobrist 表一致(便于测试可复现)。
        private const val SEED = 0x1234_5678_9ABC_DEF0L

        private val pieceKeys: LongArray = LongArray(Position.CELL_COUNT * 14) {
            Random(SEED xor it.toLong()).nextLong()
        }
        private val sideKey: Long = Random(SEED xor pieceKeys.size.toLong()).nextLong()

        /**
         * 写入 TT 前规范化杀棋分:让分数变成"以 ply=0 视角"的固定值,
         * 这样不管之后从哪个 ply 读出,只需按 [retrieveMateScore] 还原即可。
         *
         * 规则:stored = ±(MATE - distanceStored),其中 distanceStored = (MATE - |score|) + ply。
         */
        fun storeMateScore(score: Int, ply: Int): Int {
            if (score.absoluteValue <= Score.MATE_THRESHOLD) return score
            val sign = if (score > 0) 1 else -1
            val distanceAtCurrentPly = Score.MATE - score.absoluteValue
            val distanceStored = distanceAtCurrentPly + ply
            return sign * (Score.MATE - distanceStored)
        }

        /**
         * 从 TT 读出后,把"ply=0 视角"的分数还原为"当前 ply 视角"。
         *
         * 规则:retrieved = ±(MATE - distanceAtCurrentPly),其中
         * distanceAtCurrentPly = (MATE - |stored|) - retrievePly。
         */
        fun retrieveMateScore(score: Int, retrievePly: Int): Int {
            if (score.absoluteValue <= Score.MATE_THRESHOLD) return score
            val sign = if (score > 0) 1 else -1
            val distanceStored = Score.MATE - score.absoluteValue
            val distanceAtCurrentPly = distanceStored - retrievePly
            return sign * (Score.MATE - distanceAtCurrentPly)
        }
    }
}
