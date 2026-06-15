package com.xiangqi.app.ui.components

import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/**
 * 域坐标 ↔ 视图坐标 转换。
 *
 * **域坐标**(`Position(col, row)`):RED 初始在 row=0..4,BLACK 在 row=5..9。
 *
 * **视图坐标**:`(viewCol, viewRow)` 都是从屏幕左上角开始,(0,0) 在顶部,
 * row 增大向下、col 增大向右。
 *
 * **orientation = RED**(M3 默认,红方在底):
 * - col 不变(viewCol == modelCol)。
 * - row 翻转:viewRow = `ROW_MAX - modelRow`。
 *
 * **orientation = BLACK**(M4 起,黑方在底):
 * - row 不变(viewRow == modelRow)。
 * - col 翻转:viewCol = `COL_MAX - modelCol`。
 *
 * 这样定义让两套映射都是**对合**(`viewToModel ∘ modelToView = identity`),
 * tap round-trip 测试可断言全 90 格成立。
 *
 * 这些函数都是纯 Kotlin 无 Android 依赖,可在 JVM 单测中验证。
 */

fun modelToViewRow(modelRow: Int, orientation: Side): Int =
    if (orientation == Side.RED) Position.ROW_MAX - modelRow else modelRow

fun viewToModelRow(viewRow: Int, orientation: Side): Int =
    if (orientation == Side.RED) Position.ROW_MAX - viewRow else viewRow

fun modelToViewCol(modelCol: Int, orientation: Side): Int =
    if (orientation == Side.RED) modelCol else Position.COL_MAX - modelCol

fun viewToModelCol(viewCol: Int, orientation: Side): Int =
    if (orientation == Side.RED) viewCol else Position.COL_MAX - viewCol

/** 把视图坐标 (viewCol, viewRow) 映射为域 [Position]。 */
fun viewToModel(viewCol: Int, viewRow: Int, orientation: Side): Position =
    Position(viewToModelCol(viewCol, orientation), viewToModelRow(viewRow, orientation))

/** 把域 [Position] 映射为视图坐标对 (viewCol, viewRow)。 */
fun modelToView(p: Position, orientation: Side): Pair<Int, Int> =
    modelToViewCol(p.col, orientation) to modelToViewRow(p.row, orientation)
