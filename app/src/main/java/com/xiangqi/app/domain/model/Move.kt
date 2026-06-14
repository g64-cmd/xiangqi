package com.xiangqi.app.domain.model

/**
 * 一手走法 = 起点 + 终点 + 走子方。
 *
 * @property from 起点。
 * @property to 终点。
 * @property side 走子方。
 */
data class Move(val from: Position, val to: Position, val side: Side) {
    /** UCI 风格坐标(如 "h2e2")—— 列用 a–i,行用 0–9;供 UCI 引擎使用。 */
    fun toUci(): String {
        return "${colToChar(from.col)}${from.row}${colToChar(to.col)}${to.row}"
    }

    private fun colToChar(col: Int): Char = ('a'.code + col).toChar()

    companion object {
        /** 解析 UCI 字符串,如 "h2e2"。非法格式抛 [IllegalArgumentException]。 */
        fun fromUci(s: String, side: Side): Move {
            require(s.length == 4) { "UCI 走法长度必须为 4: '$s'" }
            val from = Position(colFromChar(s[0]), rowFromChar(s[1]))
            val to = Position(colFromChar(s[2]), rowFromChar(s[3]))
            return Move(from, to, side)
        }

        private fun colFromChar(c: Char): Int {
            require(c in 'a'..'i') { "UCI 列字符非法: '$c'" }
            return c.code - 'a'.code
        }

        private fun rowFromChar(c: Char): Int {
            require(c in '0'..'9') { "UCI 行字符非法: '$c'" }
            return c.code - '0'.code
        }
    }
}
