package com.xiangqi.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SideTest {
    @Test
    fun `red opponent is black`() {
        assertThat(Side.RED.opponent).isEqualTo(Side.BLACK)
    }

    @Test
    fun `black opponent is red`() {
        assertThat(Side.BLACK.opponent).isEqualTo(Side.RED)
    }
}
