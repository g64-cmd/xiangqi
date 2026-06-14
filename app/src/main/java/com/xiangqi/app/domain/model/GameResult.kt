package com.xiangqi.app.domain.model

/**
 * 对局结束状态。
 *
 * - [RED_WIN] 红方胜(黑方被将死 / 黑方无子可动)。
 * - [BLACK_WIN] 黑方胜。
 * - [DRAW] 和棋(双方协议、长将循环、僵死等)。
 * - [ONGOING] 未结束。
 *
 * @property winner 胜方,仅当 RED_WIN / BLACK_WIN 时非空。
 */
sealed interface GameResult {
    val winner: Side?

    data object ONGOING : GameResult {
        override val winner: Side? = null
    }

    data object RedWin : GameResult {
        override val winner: Side = Side.RED
    }

    data object BlackWin : GameResult {
        override val winner: Side = Side.BLACK
    }

    data class Draw(val reason: DrawReason) : GameResult {
        override val winner: Side? = null
    }
}

/** 和棋原因。 */
enum class DrawReason {
    AGREED,
    STALEMATE,
    REPETITION,
    INSUFFICIENT_MATERIAL,
}
