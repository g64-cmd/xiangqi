package com.xiangqi.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [formatScoreCp] 纯 JVM 测试。
 *
 * 用户约定:0 显示"均势";其他保留 1 位小数始终显示具体数值,
 * 让玩家看到开局不同应招间的细微分差。
 */
class GameTopBarScoreFormatTest {

    @Test
    fun `正分显示红方带加号`() {
        assertThat(formatScoreCp(150f)).isEqualTo("红方 +1.5")
    }

    @Test
    fun `负分显示黑方带加号`() {
        assertThat(formatScoreCp(-80f)).isEqualTo("黑方 +0.8")
    }

    @Test
    fun `0 显示均势`() {
        assertThat(formatScoreCp(0f)).isEqualTo("均势")
    }

    @Test
    fun `小分数也显示具体数值`() {
        // 不再归一到"均势",让开局不同应招的细微分差可见
        assertThat(formatScoreCp(8f)).isEqualTo("红方 +0.1")
        assertThat(formatScoreCp(-12f)).isEqualTo("黑方 +0.1")
    }

    @Test
    fun `大分数保留一位小数`() {
        assertThat(formatScoreCp(1234f)).isEqualTo("红方 +12.3")
        assertThat(formatScoreCp(-567f)).isEqualTo("黑方 +5.7")
    }
}
