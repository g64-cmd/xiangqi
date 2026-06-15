package com.xiangqi.app.engine

/**
 * 分数相关常量。所有引擎实现(自研 / 皮卡鱼)与 [EngineResult] / [SearchInfo]
 * 共享同一套语义。
 *
 * - 一般分(centipawn):约 1/100 兵,正数利于当前走子方。
 * - 杀棋分:|score| 在 [[MATE_THRESHOLD], [MATE]] 区间,距离 [MATE] 越近越早杀。
 */
object Score {
    /** 杀棋分上限(绝对值)。远大于全盘子力总和(~250)。 */
    const val MATE = 30_000

    /** 小于此阈值视为杀棋分。 */
    const val MATE_THRESHOLD = MATE - 1_000

    /** 给定"距离将死的半回合数",返回当前走子方视角的杀棋分。 */
    fun mateScore(pliesToMate: Int): Int = MATE - pliesToMate

    /** 给定 [score],若为杀棋分则返回距离将死的半回合数,否则 null。 */
    fun mateInPlies(score: Int): Int? =
        if (score > MATE_THRESHOLD) MATE - score
        else if (score < -MATE_THRESHOLD) MATE + score
        else null
}
