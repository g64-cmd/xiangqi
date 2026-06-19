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

    /**
     * 提示档(M6 起):用于"提示"按钮给玩家一步建议。
     * 不在 SetupScreen 暴露给用户选择,仅供内部调用。
     *
     * 历史 HINT(2, 400) 是"浅快"档,提示质量差;调整为 (3, 1000) 让提示
     * 质量接近 INTERMEDIATE 但仍远低于 ADVANCED。皮卡鱼 skill 见
     * [com.xiangqi.app.engine.pikafish.PikafishEngine.pikafishSkill]。
     */
    HINT(3, 1000),

    /**
     * 局势评估专用内部档(M7 起):auto-eval / Hint 候选评估用。
     * 皮卡鱼 movetime=2000ms、自研深度=6。不向玩家暴露,SetupScreen 通过
     * filter 排除。皮卡鱼 skill 不影响 score 精度(只影响走子噪声),
     * 故 [com.xiangqi.app.engine.pikafish.PikafishEngine.pikafishSkill] 把
     * ANALYZE 映射到 skill=20(最高精度)。
     *
     * 用户决策:玩家有耐心,2 秒延迟可接受,换取真实深搜反映局势(吃子后被
     * 反扑能正确显示劣势,而非静态 eval 那样的子力噪声)。自研深度上限调到
     * 6 反映实际可达深度(3s 跑不到 12,2s 更跑不到;6 是诚实上限)。
     */
    ANALYZE(6, 2000),
}
