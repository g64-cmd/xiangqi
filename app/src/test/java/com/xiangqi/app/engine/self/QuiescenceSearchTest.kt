package com.xiangqi.app.engine.self

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.eval.Evaluation
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.domain.movegen.MoveGeneratorImpl
import com.xiangqi.app.domain.rules.CheckDetector
import com.xiangqi.app.domain.rules.MoveLegality
import org.junit.Test

class QuiescenceSearchTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)
    private val checkDetector = CheckDetector(gen)
    private val eval = Evaluation()
    private val ordering = MoveOrdering(gen, checkDetector)
    private val qsearch = QuiescenceSearch(gen, legality, eval, ordering)

    @Test
    fun `quiet position returns material evaluation`() {
        // 初始局面没有可吃的子,standPat 立即返回
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        val score = qsearch.qSearch(board, Side.RED, alpha = Int.MIN_VALUE + 1, beta = Int.MAX_VALUE, ply = 0)
        val expected = eval.evaluate(board, Side.RED)
        assertThat(score).isEqualTo(expected)
    }

    @Test
    fun `qSearch resolves hanging piece capture`() {
        // 黑车在 (3,1),被红车 (3,0) 攻击且黑车无保护(红车吃了不会有报应)。
        // qSearch 应识别"红车能吃黑车"的走法,估值显著 > standPat
        // FEN:9/9/9/9/9/9/9/9/3r5/3R5 —— 红车(3,0) vs 黑车(3,1)
        val board = FenParser.parse("9/9/9/9/9/9/9/9/3r5/3R5 w - - 0 1").board
        val raw = eval.evaluate(board, Side.RED)
        val qs = qsearch.qSearch(board, Side.RED, alpha = Int.MIN_VALUE + 1, beta = Int.MAX_VALUE, ply = 0)
        // qSearch 应识别可吃黑车的机会,估值 > 静态评估
        assertThat(qs).isGreaterThan(raw)
    }

    @Test
    fun `qSearch bounded by max ply`() {
        // 连续吃子链不应失控;maxPly=6 是兜底
        // 这里只验证不抛栈溢出/超时
        val board = FenParser.parse(FenParser.INITIAL_FEN).board
        qsearch.qSearch(board, Side.RED, Int.MIN_VALUE + 1, Int.MAX_VALUE, ply = 0)
        // 没有抛异常即通过
    }
}
