package com.xiangqi.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MoveTest {

    @Test
    fun `toUci emits 4-char coordinates`() {
        val m = Move(Position(7, 1), Position(4, 1), Side.RED)
        assertThat(m.toUci()).isEqualTo("h1e1")
    }

    @Test
    fun `fromUci round-trips`() {
        val m = Move.fromUci("h1e1", Side.RED)
        assertThat(m.from).isEqualTo(Position(7, 1))
        assertThat(m.to).isEqualTo(Position(4, 1))
        assertThat(m.side).isEqualTo(Side.RED)
        assertThat(m.toUci()).isEqualTo("h1e1")
    }

    @Test
    fun `fromUci rejects wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            Move.fromUci("h1e1 ", Side.RED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Move.fromUci("h1", Side.RED)
        }
    }

    @Test
    fun `fromUci rejects out-of-range column`() {
        assertThrows(IllegalArgumentException::class.java) {
            Move.fromUci("z1e1", Side.RED)
        }
    }

    @Test
    fun `fromUci rejects out-of-range row`() {
        assertThrows(IllegalArgumentException::class.java) {
            Move.fromUci("h1ea", Side.RED)
        }
    }
}
