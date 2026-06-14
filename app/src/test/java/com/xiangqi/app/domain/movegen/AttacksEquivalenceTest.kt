package com.xiangqi.app.domain.movegen

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Position
import org.junit.Test

/**
 * 验证 `MoveGenerator.attacks(board, from, target)` 与 `movesFrom(board, from).any { it.to == target }`
 * 在多个局面上完全等价。
 *
 * 这是 [MoveGeneratorImpl] 重构(单点反查 vs 列表枚举)的双向保险:
 * 任何一种棋子的几何规则在两条实现路径里必须报告一致结果。
 */
class AttacksEquivalenceTest {

    private val gen = MoveGeneratorImpl()

    /**
     * 局面样本:开局、中局、残局、各种棋子分散。
     */
    private val fens = listOf(
        FenParser.INITIAL_FEN,
        // 中炮横车
        "r3kabr1/9/9/9/4C4/9/9/9/9/2BAKABR1 b - - 0 1",
        // 散乱残局
        "2bak4/9/4b4/9/9/9/9/4N4/4C4/3AKA3 w - - 0 1",
        // 全空 + 单兵过河
        "9/9/9/9/4P4/9/9/9/9/4K4 b - - 0 1",
        // 黑方即将被将
        "4k4/9/9/9/9/9/9/9/4R4/9 b - - 0 1",
    )

    @Test
    fun `attacks equals movesFrom for all from-target pairs across positions`() {
        for (fen in fens) {
            val board = FenParser.parse(fen).board
            for (rowFrom in 0..Position.ROW_MAX) {
                for (colFrom in 0..Position.COL_MAX) {
                    val from = Position(colFrom, rowFrom)
                    val moves = gen.movesFrom(board, from)
                    val moveTargets = moves.map { it.to.packed }.toSet()
                    for (rowTo in 0..Position.ROW_MAX) {
                        for (colTo in 0..Position.COL_MAX) {
                            val target = Position(colTo, rowTo)
                            val expected = target.packed in moveTargets
                            val actual = gen.attacks(board, from, target)
                            if (actual != expected) {
                                throw AssertionError(
                                    "fen=$fen from=$from target=$target " +
                                        "expected=$expected actual=$actual"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
