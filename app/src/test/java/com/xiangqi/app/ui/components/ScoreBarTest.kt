package com.xiangqi.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [scoreToFraction] / [scoreToLabel] 纯 JVM 测试。
 *
 * 评分准则(红方视角 cp):
 * - |s| < 30  -> 均势
 * - 30..80    -> 微优
 * - 80..200   -> 明显占优
 * - 200..600  -> 大优
 * - >= 600    -> 决定性
 */
class ScoreBarTest {

    @Test
    fun `null 分数映射到中线`() {
        assertThat(scoreToFraction(null)).isEqualTo(0f)
        assertThat(scoreToLabel(null)).isEqualTo("评估中")
    }

    @Test
    fun `0 分均势`() {
        assertThat(scoreToFraction(0f)).isEqualTo(0f)
        assertThat(scoreToLabel(0f)).isEqualTo("均势")
    }

    @Test
    fun `边界均势`() {
        assertThat(scoreToLabel(29f)).isEqualTo("均势")
        assertThat(scoreToLabel(-29f)).isEqualTo("均势")
    }

    @Test
    fun `微优区间映射`() {
        // 30 -> 0.15, 80 -> 0.35
        assertThat(scoreToFraction(30f)).isWithin(0.01f).of(0.15f)
        assertThat(scoreToFraction(80f)).isWithin(0.01f).of(0.35f)
        assertThat(scoreToLabel(50f)).isEqualTo("红方 微优")
        assertThat(scoreToLabel(-50f)).isEqualTo("黑方 微优")
    }

    @Test
    fun `明显占优区间映射`() {
        // 80 -> 0.35, 200 -> 0.60
        assertThat(scoreToFraction(80f)).isWithin(0.01f).of(0.35f)
        assertThat(scoreToFraction(200f)).isWithin(0.01f).of(0.60f)
        assertThat(scoreToLabel(150f)).isEqualTo("红方 占优")
    }

    @Test
    fun `大优区间映射`() {
        // 200 -> 0.60, 599 -> 接近 0.90
        assertThat(scoreToFraction(200f)).isWithin(0.01f).of(0.60f)
        assertThat(scoreToFraction(400f)).isWithin(0.01f).of(0.75f)
        assertThat(scoreToLabel(400f)).isEqualTo("红方 大优")
        assertThat(scoreToLabel(599f)).isEqualTo("红方 大优")
    }

    @Test
    fun `决定性区间 clamp 到 1`() {
        // 600 及以上 -> 1f,标签为"决定性"
        assertThat(scoreToFraction(600f)).isEqualTo(1f)
        assertThat(scoreToFraction(700f)).isEqualTo(1f)
        assertThat(scoreToFraction(1200f)).isEqualTo(1f)
        assertThat(scoreToLabel(600f)).isEqualTo("红方 决定性优势")
        assertThat(scoreToLabel(1000f)).isEqualTo("红方 决定性优势")
        assertThat(scoreToLabel(-1000f)).isEqualTo("黑方 决定性优势")
    }

    @Test
    fun `负分取负向 fraction`() {
        // -50 -> lerp(30..80 → 0.15..0.35) = -0.23
        assertThat(scoreToFraction(-50f)).isWithin(0.01f).of(-0.23f)
        assertThat(scoreToFraction(-300f)).isLessThan(0f)
    }
}
