package com.xiangqi.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.engine.Score
import org.junit.Test

/**
 * [scoreToLabel] 纯 JVM 测试。
 *
 * ScoreBar UI 改为折线图后,核心逻辑是分数→标签的映射:
 * - mate score(识别 |score| > [Score.MATE_THRESHOLD])→ "X 方将杀 N 步内"
 * - 普通分按档位:均势 / 微优 / 占优 / 大优 / 决定性优势
 */
class ScoreBarTest {

    @Test
    fun `null 分数显示评估中`() {
        assertThat(scoreToLabel(null)).isEqualTo("评估中")
    }

    @Test
    fun `0 分均势`() {
        assertThat(scoreToLabel(0f)).isEqualTo("均势")
    }

    @Test
    fun `边界均势`() {
        assertThat(scoreToLabel(29f)).isEqualTo("均势")
        assertThat(scoreToLabel(-29f)).isEqualTo("均势")
    }

    @Test
    fun `微优区间`() {
        assertThat(scoreToLabel(50f)).isEqualTo("红方 微优")
        assertThat(scoreToLabel(-50f)).isEqualTo("黑方 微优")
    }

    @Test
    fun `明显占优区间`() {
        assertThat(scoreToLabel(150f)).isEqualTo("红方 占优")
        assertThat(scoreToLabel(-150f)).isEqualTo("黑方 占优")
    }

    @Test
    fun `大优区间`() {
        assertThat(scoreToLabel(400f)).isEqualTo("红方 大优")
        assertThat(scoreToLabel(-400f)).isEqualTo("黑方 大优")
    }

    @Test
    fun `决定性优势区间`() {
        assertThat(scoreToLabel(700f)).isEqualTo("红方 决定性优势")
        assertThat(scoreToLabel(-700f)).isEqualTo("黑方 决定性优势")
    }

    @Test
    fun `mate 正分识别为红方将杀`() {
        // mate in 3 -> Score.mateScore(3) = 30000 - 3 = 29997
        val mateScore = Score.mateScore(3).toFloat()
        assertThat(scoreToLabel(mateScore)).isEqualTo("红方 将杀 3 步内")
    }

    @Test
    fun `mate 负分识别为黑方将杀`() {
        val mateScore = -Score.mateScore(5).toFloat()
        assertThat(scoreToLabel(mateScore)).isEqualTo("黑方 将杀 5 步内")
    }
}
