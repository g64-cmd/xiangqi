package com.xiangqi.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PositionTest {

    @Test
    fun `packing is row-major`() {
        val p = Position(3, 5)
        assertThat(p.col).isEqualTo(3)
        assertThat(p.row).isEqualTo(5)
        assertThat(p.packed).isEqualTo(5 * 9 + 3)
    }

    @Test
    fun `col bounds 0 to 8`() {
        assertThrows(IllegalArgumentException::class.java) {
            Position(-1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Position(9, 0)
        }
    }

    @Test
    fun `row bounds 0 to 9`() {
        assertThrows(IllegalArgumentException::class.java) {
            Position(0, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Position(0, 10)
        }
    }

    @Test
    fun `corners are valid`() {
        Position(0, 0)
        Position(8, 0)
        Position(0, 9)
        Position(8, 9)
    }

    @Test
    fun `offsetBy returns null out of bounds`() {
        val corner = Position(0, 0)
        assertThat(corner.offsetBy(-1, 0)).isNull()
        assertThat(corner.offsetBy(0, -1)).isNull()
        assertThat(corner.offsetBy(1, 1)).isEqualTo(Position(1, 1))
    }

    @Test
    fun `value class equals by packed`() {
        assertThat(Position(3, 5)).isEqualTo(Position(3, 5))
        assertThat(Position(3, 5)).isNotEqualTo(Position(4, 5))
    }

    @Test
    fun `minus returns col row delta`() {
        val a = Position(5, 7)
        val b = Position(3, 2)
        assertThat(a - b).isEqualTo(2 to 5)
    }
}
