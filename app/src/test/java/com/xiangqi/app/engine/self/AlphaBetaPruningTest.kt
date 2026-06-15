package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.MoveLegality
import org.junit.Test

/**
 * Alpha-Beta 剪枝收益验证。
 *
 * 原理:对同一局面同一深度,negamax 在宽窗口(-INF..INF)下退化为纯 Negamax,
 * 在窄窗口下生效 AB 剪枝。窄窗口的 nodes 应显著少于宽窗口。
 */
class AlphaBetaPruningTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)
    private val checkDetector = CheckDetector(gen)
    private val eval = Evaluation()
    private val ordering = MoveOrdering(gen, checkDetector)
    private val tt = TranspositionTable(1 shl 18)
    private val search = Search(gen, legality, eval, checkDetector, ordering, tt)

    @Test
    fun `ab prunes nodes on depth 3 from initial position`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board

        // 宽窗口:等同纯 Negamax(不会触发 beta cutoff,因为窗口永远装得下)
        search.nodes = 0L
        val movesWide = legality.legalMoves(board, gen.movesFor(board, Side.RED))
        for (mv in movesWide) {
            val after = board.applyMove(mv)
            search.negamax(after, Side.BLACK, depth = 2, Int.MIN_VALUE + 1, Int.MAX_VALUE, ply = 1)
        }
        val wideNodes = search.nodes

        // 窄窗口(Alpha-Beta 通过 searchRoot 触发)
        search.nodes = 0L
        search.searchRoot(board, Side.RED, maxDepth = 3)
        val abNodes = search.nodes

        // AB 应使节点数显著减少(经验至少 30%+)
        assertThat(abNodes).isLessThan(wideNodes * 7 / 10)
    }
}
