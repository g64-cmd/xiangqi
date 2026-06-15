package com.xiangqi.app.engine.pikafish

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.model.Side
import com.xiangqi.app.engine.Score
import org.junit.Test

/**
 * 仅测 [PikafishEngine.parseInfo] 解析逻辑,不涉及子进程。
 *
 * 真实 UCI 输出来自皮卡鱼文档与样本:每行可能含 depth / score cp|mate / nodes /
 * time / pv / string / hashfull 等多种字段顺序。
 */
class PikafishEngineParseInfoTest {

    private val side = Side.RED

    @Test
    fun `典型 info 行解析 depth+score cp+nodes+time`() {
        val line = "info depth 12 seldepth 14 multipv 1 score cp 35 nodes 15234 nps 200000 time 76 pv h2e2 h9g7"
        val info = PikafishEngine.parseInfo(line, side)

        assertThat(info).isNotNull()
        assertThat(info!!.depth).isEqualTo(12)
        assertThat(info.score).isEqualTo(35)
        assertThat(info.nodes).isEqualTo(15234)
        assertThat(info.timeMs).isEqualTo(76)
    }

    @Test
    fun `score mate 正映射到 Score mateScore`() {
        val line = "info depth 8 score mate 5 nodes 1000 time 50"
        val info = PikafishEngine.parseInfo(line, side)

        assertThat(info).isNotNull()
        assertThat(info!!.score).isEqualTo(Score.mateScore(5))
    }

    @Test
    fun `score mate 负数也映射成将死分`() {
        val line = "info depth 6 score mate -3 nodes 500 time 30"
        val info = PikafishEngine.parseInfo(line, side)

        assertThat(info).isNotNull()
        assertThat(info!!.score).isEqualTo(Score.mateScore(-3))
    }

    @Test
    fun `info string 等无 score 字段返回 null`() {
        val line = "info string Pikafish 2026.01.31"
        val info = PikafishEngine.parseInfo(line, side)
        assertThat(info).isNull()
    }

    @Test
    fun `字段乱序仍正确解析`() {
        val line = "info nodes 100 time 10 score cp -7 depth 3 pv b7e7"
        val info = PikafishEngine.parseInfo(line, side)
        assertThat(info).isNotNull()
        assertThat(info!!.depth).isEqualTo(3)
        assertThat(info.score).isEqualTo(-7)
        assertThat(info.nodes).isEqualTo(100)
        assertThat(info.timeMs).isEqualTo(10)
    }

    @Test
    fun `seldepth 与 multipv 等额外字段不干扰主字段`() {
        val line = "info depth 10 seldepth 14 multipv 1 score cp 12 hashfull 234 nodes 9999 time 80 pv h2e2"
        val info = PikafishEngine.parseInfo(line, side)
        assertThat(info).isNotNull()
        assertThat(info!!.depth).isEqualTo(10)
        assertThat(info.score).isEqualTo(12)
        assertThat(info.nodes).isEqualTo(9999)
        assertThat(info.timeMs).isEqualTo(80)
    }
}
