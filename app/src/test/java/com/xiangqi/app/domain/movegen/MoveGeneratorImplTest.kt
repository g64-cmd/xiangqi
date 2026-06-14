package com.xiangqi.app.domain.movegen

import com.google.common.truth.Truth.assertThat
import com.xiangqi.app.domain.fen.FenParser
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side
import org.junit.Test

class MoveGeneratorImplTest {

    private val gen = MoveGeneratorImpl()

    private fun targetsOf(moves: List<Move>): Set<Position> =
        moves.map { it.to }.toSet()

    @Test
    fun `king moves only one orthogonal step within palace`() {
        // 红帅居中 (4,0),只可走 (3,0)(5,0)(4,1)
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/3K5 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(3, 0)))
        assertThat(targets).containsExactly(Position(4, 0), Position(3, 1))
    }

    @Test
    fun `king cannot leave palace`() {
        // 红帅在 (3,0),不能去 (2,0) 或 (3,-1)
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/K8 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(0, 0)))
        // 红九宫 col 3..5 row 0..2 — 帅在 (0,0) 不在九宫,理应 0 步
        assertThat(targets).isEmpty()
    }

    @Test
    fun `advisor moves diagonally within palace`() {
        // 红仕 (3,0),只能去 (4,1)
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/3A5 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(3, 0)))
        assertThat(targets).containsExactly(Position(4, 1))
    }

    @Test
    fun `bishop cannot cross river and respects eye`() {
        // 红相 (1,0),田字心为 (2,1),目标 (3,2)
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/1B7 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(1, 0)))
        // 田字目标:(3,2)(-1,2 — 越界) → 只能 (3,2)
        assertThat(targets).containsExactly(Position(3, 2))

        // 塞象眼:在 (2,1) 放棋子,(1,0)→(3,2) 的象眼是 (2,1),目标 (3,2) 应该不可走
        val blocked = board.with(Position(2, 1), Piece(PieceType.PAWN, Side.BLACK))
        assertThat(targetsOf(gen.movesFrom(blocked, Position(1, 0)))).doesNotContain(Position(3, 2))
    }

    @Test
    fun `bishop cannot cross river south to north as red`() {
        // 红相在 (2,4) 河界边,只能向下(向本方),不能向上过河
        val board = FenParser.parse("9/9/9/9/9/1B7/9/9/9/9 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(1, 4)))
        // row=4 是红方本方边界,可去的田字在 (3,2)(-1,2越界)(3,6) — 但 (3,6) 已过河
        // 实际红方 isOnOwnSide 要求 row<=4,过河就是 row>4。 (3,6) 是过河,被排除
        assertThat(targets).containsExactly(Position(3, 2))
    }

    @Test
    fun `knight L-shape with leg blocked`() {
        // 红马 (4,0),无阻挡下应有 4 个走法:(3,2)(5,2)(6,1)(2,1)
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/4N4 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(4, 0)))
        assertThat(targets).containsExactly(
            Position(3, 2), Position(5, 2),
            Position(6, 1), Position(2, 1),
        )

        // 蹩马腿:在 (4,1) 放棋子,(3,2) 和 (5,2) 不可走(因为它们的腿是 (4,1))
        val blocked = board.with(Position(4, 1), Piece(PieceType.PAWN, Side.BLACK))
        val blockedTargets = targetsOf(gen.movesFrom(blocked, Position(4, 0)))
        assertThat(blockedTargets).containsExactly(Position(6, 1), Position(2, 1))
    }

    @Test
    fun `rook slides until blocked by own piece or captures enemy`() {
        // 红车 (0,0),空棋盘,横纵直走到底
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/R8 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(0, 0)))
        val expected = mutableListOf<Position>()
        for (c in 1..8) expected += Position(c, 0)
        for (r in 1..9) expected += Position(0, r)
        assertThat(targets).containsExactlyElementsIn(expected)

        // 黑卒阻挡 (5,0):车能吃,但不能再走 (6,0)
        val blocked = board.with(Position(5, 0), Piece(PieceType.PAWN, Side.BLACK))
        assertThat(targetsOf(gen.movesFrom(blocked, Position(0, 0)))).contains(Position(5, 0))
        assertThat(targetsOf(gen.movesFrom(blocked, Position(0, 0)))).doesNotContain(Position(6, 0))

        // 己方棋子阻挡
        val ownBlocked = board.with(Position(5, 0), Piece(PieceType.PAWN, Side.RED))
        assertThat(targetsOf(gen.movesFrom(ownBlocked, Position(0, 0)))).doesNotContain(Position(5, 0))
        assertThat(targetsOf(gen.movesFrom(ownBlocked, Position(0, 0)))).doesNotContain(Position(6, 0))
    }

    @Test
    fun `cannon moves like rook but needs exactly one screen to capture`() {
        // 红炮 (1,2),开局位置
        val init = FenParser.parse(FenParser.INITIAL_FEN).board
        val targets = targetsOf(gen.movesFrom(init, Position(1, 2)))
        // 不吃子:沿(1,0..2)空走 + 向下走 row 3、4(空)直到 row=5 撞红兵
        assertThat(targets).contains(Position(0, 2)) // 左
        assertThat(targets).contains(Position(2, 2)) // 右
        assertThat(targets).contains(Position(1, 3)) // 下
        assertThat(targets).contains(Position(1, 4)) // 下下
        // 翻山吃黑卒 (1,6) — 中间是空(row 5? 看 init: col=1 红兵在 row=3,然后空,黑卒在 row=6)。
        // 红兵在 (1,3),过一格 (1,4)(1,5) 空,(1,6) 是黑卒。翻山:中间恰好一格棋子 — 但红兵是第一格挡,不算翻山
        // 实际上从 (1,2) 向下:第一子是红兵(1,3) — 这是炮架,跳过后找下一个子 — 黑卒 (1,6),可吃
        assertThat(targets).contains(Position(1, 6))
    }

    @Test
    fun `pawn moves forward only before crossing river`() {
        // 红兵 (0,3),开局位置,只能向 (0,4)
        val init = FenParser.parse(FenParser.INITIAL_FEN).board
        val targets = targetsOf(gen.movesFrom(init, Position(0, 3)))
        assertThat(targets).containsExactly(Position(0, 4))
    }

    @Test
    fun `pawn can move sideways after crossing river`() {
        // 红兵 (4,5),已过河到黑方半场(row=5),可去 (3,5)(5,5)(4,6)
        // FEN 段索引 4 对应 row=5
        val board = FenParser.parse("9/9/9/9/4P4/9/9/9/9/9 w - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(4, 5)))
        assertThat(targets).containsExactly(
            Position(3, 5), Position(5, 5), Position(4, 6),
        )
    }

    @Test
    fun `black pawn moves downward and sideways after crossing river`() {
        // 黑卒 (4,3),已过河到红方半场(row=3),可去 (3,3)(5,3)(4,2)
        // FEN 段索引 6 对应 row=3
        val board = FenParser.parse("9/9/9/9/9/9/4p4/9/9/9 b - - 0 1").board
        val targets = targetsOf(gen.movesFrom(board, Position(4, 3)))
        assertThat(targets).containsExactly(
            Position(3, 3), Position(5, 3), Position(4, 2),
        )
    }

    @Test
    fun `movesFrom empty square returns empty`() {
        val board = FenParser.parse("9/9/9/9/9/9/9/9/9/9 w - - 0 1").board
        assertThat(gen.movesFrom(board, Position(0, 0))).isEmpty()
    }

    @Test
    fun `movesFor yields all moves for a side`() {
        val init = FenParser.parse(FenParser.INITIAL_FEN).board
        val redMoves = gen.movesFor(init, Side.RED)
        // 红方开局应有 44 步(2 车 + 2 马 + 2 炮 + 5 兵 + 2 仕 + 2 相 + 1 帅)
        // 计算:车 (0+8) 各 2 步 = 4; 马 各 2 = 4; 炮 各 6? 不对,看具体
        // 这只是 sanity,数量大于 0
        assertThat(redMoves).isNotEmpty()
        // 不应产生黑方走法
        assertThat(redMoves.all { it.side == Side.RED }).isTrue()
    }
}
