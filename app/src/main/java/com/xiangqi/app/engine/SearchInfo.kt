package com.xiangqi.app.engine

import com.xiangqi.app.domain.model.Move

/**
 * 引擎思考过程的实时快照。搜索期间通过 [Engine.info] StateFlow 持续推送,
 * UI 可在思考动画 / 局势分析条上消费。
 *
 * @property depth 当前已完成的迭代深度。
 * @property score 当前走子方视角下的分数(单位:centipawn,约等于 1/100 兵)。
 *                 绝对值超过 [Score.MATE_THRESHOLD] 时为杀棋分。
 * @property pv Principal Variation,从根节点到叶节点的最佳走法序列。
 * @property nodes 截至当前迭代已搜索的节点总数。
 * @property timeMs 当前迭代的累计耗时(毫秒)。
 */
data class SearchInfo(
    val depth: Int,
    val score: Int,
    val pv: List<Move>,
    val nodes: Long,
    val timeMs: Long,
)
