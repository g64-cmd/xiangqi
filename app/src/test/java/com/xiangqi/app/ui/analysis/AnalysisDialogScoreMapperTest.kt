package com.xiangqi.app.ui.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [mapScoreToY] 纯 JVM 测试。
 *
 * 0 -> 中线,正分向上(Y 小),负分向下(Y 大),超过 [CLAMP_CP] clamp。
 */
class AnalysisDialogScoreMapperTest {

    private val height = 200f

    @Test
    fun `0 分在中线`() {
        assertThat(mapScoreToY(0f, height)).isEqualTo(height / 2f)
    }

    @Test
    fun `正分向上 Y 减小`() {
        val y = mapScoreToY(500f, height)
        assertThat(y).isLessThan(height / 2f)
    }

    @Test
    fun `负分向下 Y 增大`() {
        val y = mapScoreToY(-500f, height)
        assertThat(y).isGreaterThan(height / 2f)
    }

    @Test
    fun `正负分关于中线对称`() {
        val up = mapScoreToY(500f, height)
        val down = mapScoreToY(-500f, height)
        assertThat(up + down).isWithin(0.01f).of(height)
    }

    @Test
    fun `超过 CLAMP_CP 的分数被 clamp 到边界`() {
        val maxY = mapScoreToY(CLAMP_CP.toFloat() * 2f, height)
        val minY = mapScoreToY(-CLAMP_CP.toFloat() * 2f, height)
        assertThat(maxY).isEqualTo(0f) // 顶
        assertThat(minY).isEqualTo(height) // 底
    }

    @Test
    fun `CLAMP_CP 单位为 1000 cp 即 10 兵`() {
        assertThat(CLAMP_CP).isEqualTo(1000)
    }
}
