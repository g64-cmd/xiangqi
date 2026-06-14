package com.xiangqi.app.domain.model

/**
 * 对战方。
 *
 * RED 执红、BLACK 执黑,红方先行。具体哪方在哪一行由初始 FEN 摆放决定,
 * 模型层不做硬性约定。在主流 UCI / FEN 规范里,红方通常摆在 row=0 一侧
 * (坐标字符与 row 直接对应),黑方摆在 row=9 一侧。
 */
enum class Side {
    RED,
    BLACK;

    /** 对手方。 */
    val opponent: Side
        get() = if (this == RED) BLACK else RED
}
