package com.xiangqi.app.engine

/**
 * 分数相关常量。所有引擎实现(自研 / 皮卡鱼)与 [EngineResult] / [SearchInfo]
 * 共享同一套语义。
 *
 * - 一般分(centipawn):约 1/100 兵,正数利于当前走子方。
 * - 杀棋分:|score| 在 [[MATE_THRESHOLD], [MATE]] 区间,距离 [MATE] 越近越早杀。
 *
 * **杀棋分符号约定**(与 UCI `score mate N` 一致):
 * - 正分:当前走子方 N 个半回合内将死对方(自己赢)。
 * - 负分:当前走子方 N 个半回合内被对方将死(自己输)。
 *
 * 即分数的符号表示"哪一方在赢",绝对值越接近 [MATE] 表示越早结束。这保证
 * 与普通 cp 分的符号语义一致(正=当前走子方占优)。
 */
object Score {
    /** 杀棋分上限(绝对值)。远大于全盘子力总和(~250)。 */
    const val MATE = 30_000

    /** 小于此阈值视为杀棋分。 */
    const val MATE_THRESHOLD = MATE - 1_000

    /**
     * 给定 UCI `score mate N` 中的 N(可正可负),返回当前走子方视角的杀棋分。
     *
     * - N > 0(走子方将杀对方):返回 `+(MATE - N)`
     * - N < 0(走子方被将杀):返回 `-(MATE - (-N)) = -(MATE + N)`
     * - N = 0(已绝杀):返回 `+MATE`
     */
    fun mateScore(pliesToMate: Int): Int =
        if (pliesToMate >= 0) MATE - pliesToMate
        else -MATE - pliesToMate

    /** 给定 [score],若为杀棋分则返回距离将死的半回合数(带符号),否则 null。 */
    fun mateInPlies(score: Int): Int? =
        if (score > MATE_THRESHOLD) MATE - score
        else if (score < -MATE_THRESHOLD) MATE + score
        else null
}
