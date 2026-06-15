package com.xiangqi.app.engine

import com.xiangqi.app.domain.model.Move

/**
 * 引擎单次 [Engine.search] 的最终结果。
 *
 * @property bestMove 推荐走法。
 * @property score 当前走子方视角下的最终分数。
 * @property depth 完整搜索的最大深度(迭代加深最后一次完成的深度)。
 * @property pv Principal Variation;M2 自研引擎从根节点搜索的 best line。
 * @property nodesSearched 整次搜索的总节点数(供 bench / 调试用)。
 * @property timeMs 整次搜索的总耗时(毫秒)。
 * @property isMate 是否为杀棋分(|score| > [Score.MATE_THRESHOLD])。
 * @property mateInPlies 若 [isMate] 为 true,表示距离将死的半回合数;否则 null。
 */
data class EngineResult(
    val bestMove: Move,
    val score: Int,
    val depth: Int,
    val pv: List<Move>,
    val nodesSearched: Long,
    val timeMs: Long,
    val isMate: Boolean,
    val mateInPlies: Int?,
)
