package com.xiangqi.app.engine.pikafish

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.engine.AnalysisScore
import org.junit.Test

/**
 * [PikafishEngine.parseEvalLine] 纯 JVM 解析测试。
 *
 * 行格式参考 chinese-chess-fish-android 实测:
 * `Final evaluation       +0.23 (white side) [with scaled NNUE, ...]`
 */
class PikafishEngineEvalParseTest {

    @Test
    fun `parses positive white-side eval`() {
        val r = PikafishEngine.parseEvalLine("Final evaluation       +0.23 (white side)")
        assertThat(r).isEqualTo(AnalysisScore(23.0f, false, null))
    }

    @Test
    fun `parses negative black-side eval and negates`() {
        val r = PikafishEngine.parseEvalLine("Final evaluation       -1.50 (black side)")
        // -1.50 × 100 = -150 cp,black side 取负 -> +150
        assertThat(r).isEqualTo(AnalysisScore(150.0f, false, null))
    }

    @Test
    fun `parses positive black-side eval and negates`() {
        val r = PikafishEngine.parseEvalLine("Final evaluation       +0.80 (black side)")
        // +0.80 × 100 = +80 cp,black side 取负 -> -80
        assertThat(r).isEqualTo(AnalysisScore(-80.0f, false, null))
    }

    @Test
    fun `parses line with scaled-NNUE suffix`() {
        val r = PikafishEngine.parseEvalLine(
            "Final evaluation       +1.23 (white side) [with scaled NNUE, ...]",
        )
        assertThat(r).isEqualTo(AnalysisScore(123.0f, false, null))
    }

    @Test
    fun `returns null for unrelated lines`() {
        assertThat(PikafishEngine.parseEvalLine("info depth 10 score cp 50")).isNull()
        assertThat(PikafishEngine.parseEvalLine("bestmove e2e4")).isNull()
        assertThat(PikafishEngine.parseEvalLine("")).isNull()
    }

    @Test
    fun `returns null for malformed Final evaluation line`() {
        assertThat(PikafishEngine.parseEvalLine("Final evaluation       NaN (white side)")).isNull()
    }
}
