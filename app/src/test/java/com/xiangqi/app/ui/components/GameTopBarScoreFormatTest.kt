package com.xiangqi.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [formatScoreCp] 纯 JVM 测试。
 */
class GameTopBarScoreFormatTest {

    @Test
    fun `正分显示红方带加号`() {
        assertThat(formatScoreCp(150f)).isEqualTo("红方 +1.50")
    }

    @Test
    fun `负分显示黑方带加号`() {
        assertThat(formatScoreCp(-80f)).isEqualTo("黑方 +0.80")
    }

    @Test
    fun `小分数显示均势`() {
        assertThat(formatScoreCp(20f)).isEqualTo("均势")
        assertThat(formatScoreCp(-29f)).isEqualTo("均势")
    }

    @Test
    fun `大分数显示两位小数`() {
        assertThat(formatScoreCp(1234f)).isEqualTo("红方 +12.34")
        assertThat(formatScoreCp(-567f)).isEqualTo("黑方 +5.67")
    }
}
