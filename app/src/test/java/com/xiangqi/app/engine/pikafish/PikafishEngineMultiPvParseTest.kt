package com.xiangqi.app.engine.pikafish

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.model.Side
import org.junit.Test

/**
 * [PikafishEngine.parseInfoMultiPvFirstMove] 纯 JVM 测试。
 *
 * 皮卡鱼 MultiPV 输出形如:
 * `info depth 12 multipv 1 score cp 35 pv e3e4 h9g7 ...`
 *
 * 我们只关心 multipv 索引 + pv 首着 4 字符 UCI。
 */
class PikafishEngineMultiPvParseTest {

    @Test
    fun `解析 multipv 1 的首着`() {
        val line = "info depth 12 multipv 1 score cp 35 nodes 1234 time 100 pv e3e4 h9g7 c3c4"
        val pair = PikafishEngine.parseInfoMultiPvFirstMove(line, Side.RED)
        assertThat(pair).isNotNull()
        assertThat(pair!!.first).isEqualTo(1)
        // e3e4 -> col=e row=3 -> col=e row=4
        assertThat(pair.second.toUci()).isEqualTo("e3e4")
    }

    @Test
    fun `解析 multipv 2 与 multipv 3`() {
        val line2 = "info depth 12 multipv 2 score cp 12 pv h2e2"
        val line3 = "info depth 12 multipv 3 score cp 5 pv b7e7"
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove(line2, Side.RED)?.first).isEqualTo(2)
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove(line3, Side.RED)?.first).isEqualTo(3)
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove(line2, Side.RED)?.second?.toUci())
            .isEqualTo("h2e2")
    }

    @Test
    fun `非 info 行返回 null`() {
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove("bestmove e3e4", Side.RED)).isNull()
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove("uciok", Side.RED)).isNull()
    }

    @Test
    fun `缺 multipv 字段返回 null`() {
        // 单 PV 行没有 multipv 字段
        val line = "info depth 12 score cp 35 pv e3e4"
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove(line, Side.RED)).isNull()
    }

    @Test
    fun `缺 pv 字段返回 null`() {
        // info string 行没有 pv
        val line = "info string Depth 12 fully searched"
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove(line, Side.RED)).isNull()
    }

    @Test
    fun `pv 首 token 不是 4 字符返回 null`() {
        // 不合法 UCI(防止皮卡鱼异常输出)
        val line = "info depth 1 multipv 1 score cp 0 pv xxxxx"
        assertThat(PikafishEngine.parseInfoMultiPvFirstMove(line, Side.RED)).isNull()
    }
}
