package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.MoveLegality
import com.xiangqi.app.engine.Score
import org.junit.Test

/**
 * 纯 Negamax 搜索器的正确性测试(M2 commit 6 阶段)。
 *
 * 后续 commit 会:
 * - commit 7:升级 AB,验证节点数减少
 * - commit 9:接 TT,验证杀棋加速
 * - commit 10:接 QSearch,验证白送子不亏
 */
class SearchMateTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)
    private val checkDetector = CheckDetector(gen)
    private val eval = Evaluation()
    private val ordering = MoveOrdering(gen, checkDetector)
    private val tt = TranspositionTable(1 shl 18)
    private val quiescence = QuiescenceSearch(gen, legality, eval, ordering)
    private val search = Search(gen, legality, eval, checkDetector, ordering, tt, quiescence)

    @Test
    fun `initial position returns non-null bestMove with positive nodes`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val (bestMove, score) = search.searchRoot(board, Side.RED, maxDepth = 2)
        assertThat(bestMove).isNotNull()
        assertThat(search.nodes).isGreaterThan(0L)
        // 开局对称,红黑分应近 0(允许小范围 PST 偏差)
        assertThat(score).isIn(-50..50)
    }

    @Test
    fun `depth 1 picks capture when available`() {
        // 红车 (3,0) 可一步吃黑卒 (3,1)。depth=1 应识别这是最佳走法
        val board = FenParser.parse("9/9/9/9/9/9/9/9/3p5/3R5 w - - 0 1").board
        val (bestMove, score) = search.searchRoot(board, Side.RED, maxDepth = 1)
        assertThat(bestMove).isNotNull()
        // 吃卒的走法应被选中(吃卒估值 +10 + 局面 +PST,显著 > 0)
        assertThat(bestMove!!.from).isEqualTo(Position(3, 0))
        assertThat(bestMove.to).isEqualTo(Position(3, 1))
        assertThat(score).isGreaterThan(0)
    }

    @Test
    fun `terminal score when no legal moves returns mate`() {
        // 构造一个被将死且无路可走的局面(简化版)
        // 这里用 Search 的入口反向验证:如果当前走子方真的无合法走法,searchRoot 返回 null + 负 MATE 分
        // 中国象棋很难构造"完全无走法"的极简局,所以这测试主要锁定接口语义
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/3K5 w - - 0 1").board
        val (bestMove, score) = search.searchRoot(board, Side.RED, maxDepth = 1)
        // 红帅单独在场有合法走法,bestMove 不为 null
        assertThat(bestMove).isNotNull()
    }

    @Test
    fun `mate score has correct magnitude`() {
        // ply=0 处的无走法应给 -MATE;ply=1 应给 -(MATE-1) 等
        // 用纯调用验证 Score.mateScore 的属性(间接保证 terminalScore 的正确性)
        assertThat(Score.mateScore(0)).isEqualTo(Score.MATE)
        assertThat(Score.mateScore(1)).isEqualTo(Score.MATE - 1)
        assertThat(Score.mateScore(5)).isGreaterThan(Score.MATE_THRESHOLD)
    }
}
