package com.xiangqi.app.engine

/**
 * 引擎实现类型。
 *
 * - [SELF]:M2 自研 Negamax + Alpha-Beta 引擎(低强度档位)。
 * - [PIKAFISH]:M5 皮卡鱼引擎(高强度档位,通过 UCI 子进程调用)。
 */
enum class EngineType {
    SELF,
    PIKAFISH,
}
