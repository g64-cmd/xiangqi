package com.xiangqi.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import org.junit.Test

/**
 * [BoardGeometry] view↔model 转换契约:
 * - RED orientation 下 row 翻转、col 不变。
 * - BLACK orientation 下 col 翻转、row 不变。
 * - 两套映射都是对合(round-trip == identity),对全部 90 格成立。
 */
class BoardGeometryTest {

    @Test
    fun `RED orientation maps model row 0 to view row 9`() {
        assertThat(modelToViewRow(0, Side.RED)).isEqualTo(9)
        assertThat(modelToViewRow(9, Side.RED)).isEqualTo(0)
    }

    @Test
    fun `RED orientation leaves column unchanged`() {
        for (c in 0..Position.COL_MAX) {
            assertThat(modelToViewCol(c, Side.RED)).isEqualTo(c)
        }
    }

    @Test
    fun `BLACK orientation leaves row unchanged`() {
        for (r in 0..Position.ROW_MAX) {
            assertThat(modelToViewRow(r, Side.BLACK)).isEqualTo(r)
        }
    }

    @Test
    fun `BLACK orientation flips column`() {
        assertThat(modelToViewCol(0, Side.BLACK)).isEqualTo(Position.COL_MAX)
        assertThat(modelToViewCol(Position.COL_MAX, Side.BLACK)).isEqualTo(0)
    }

    @Test
    fun `RED round-trip holds for all 90 positions`() {
        for (row in 0..Position.ROW_MAX) {
            for (col in 0..Position.COL_MAX) {
                val p = Position(col, row)
                val (vc, vr) = modelToView(p, Side.RED)
                val back = viewToModel(vc, vr, Side.RED)
                assertThat(back).isEqualTo(p)
            }
        }
    }

    @Test
    fun `BLACK round-trip holds for all 90 positions`() {
        for (row in 0..Position.ROW_MAX) {
            for (col in 0..Position.COL_MAX) {
                val p = Position(col, row)
                val (vc, vr) = modelToView(p, Side.BLACK)
                val back = viewToModel(vc, vr, Side.BLACK)
                assertThat(back).isEqualTo(p)
            }
        }
    }

    @Test
    fun `modelToView red king position is at bottom of screen`() {
        // 红帅初始位 (col=4, row=0) 应映射到 view (4, 9) —— 屏幕底部正中。
        val (vc, vr) = modelToView(Position(4, 0), Side.RED)
        assertThat(vc).isEqualTo(4)
        assertThat(vr).isEqualTo(9)
    }

    @Test
    fun `modelToView black king position is at top of screen`() {
        // 黑将初始位 (col=4, row=9) 应映射到 view (4, 0) —— 屏幕顶部正中。
        val (vc, vr) = modelToView(Position(4, 9), Side.RED)
        assertThat(vc).isEqualTo(4)
        assertThat(vr).isEqualTo(0)
    }
}
