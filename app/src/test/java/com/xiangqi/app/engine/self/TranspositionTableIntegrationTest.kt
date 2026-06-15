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
 * 验证 TranspositionTable 集成到 Search 后:
 * 同一局面 + 同一深度,有 TT 时节点数应 ≤ 无 TT 时。
 *
 * 实现方式:跑两次 searchRoot,第二次因 TT 已有上一深度(同 maxDepth)的精确结果,
 * 入口 probe 命中后直接返回 —— 等同于"零节点"搜索。
 */
class TranspositionTableIntegrationTest {

    private val gen = MoveGeneratorImpl()
    private val legality = MoveLegality(gen)
    private val checkDetector = CheckDetector(gen)
    private val eval = Evaluation()
    private val ordering = MoveOrdering(gen, checkDetector)
    private val tt = TranspositionTable(1 shl 18)
    private val search = Search(gen, legality, eval, checkDetector, ordering, tt)

    @Test
    fun `second identical search hits TT and reduces nodes`() {
        val board = FenParser.parse(FenParser.INITIAL_FEN).board

        // 第一次:正常搜索,所有节点都需要展开
        tt.clear()
        search.searchRoot(board, Side.RED, maxDepth = 3)
        val firstRunNodes = search.nodes

        // 第二次:同一局面同一深度,根节点 TT probe 命中 EXACT,直接返回
        // 节点数应远小于第一次(理论上只有根节点 probe + 1 个 entry 检查)
        search.searchRoot(board, Side.RED, maxDepth = 3)
        val secondRunNodes = search.nodes

        assertThat(secondRunNodes).isLessThan(firstRunNodes / 10)
    }
}
