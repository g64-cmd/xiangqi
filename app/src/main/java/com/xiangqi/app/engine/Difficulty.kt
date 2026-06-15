package com.xiangqi.app.engine

/**
 * 难度档位。同时驱动自研引擎(深度 + movetime)与皮卡鱼(Skill + movetime)。
 *
 * @property depth 自研引擎的最大搜索深度(迭代加深上限)。
 * @property moveTimeMs 单次思考的软性时间上限(毫秒)。超时则返回上次完整深度结果。
 */
enum class Difficulty(val depth: Int, val moveTimeMs: Long) {
    BEGINNER(1, 100),
    ELEMENTARY(2, 300),
    INTERMEDIATE(3, 800),
    ADVANCED(4, 1500),
}
