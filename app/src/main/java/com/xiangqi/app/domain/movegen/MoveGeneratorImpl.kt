package com.xiangqi.app.domain.movegen

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/**
 * 默认走法生成器实现。按棋子类型分派到对应的内部生成器。
 *
 * 所有生成方法返回的是 **伪合法走法**——遵循各棋子几何走法,但**不**检查
 * "走完后己方将帅被将/对脸/送将"。后者由 [domain.rules.MoveLegality] 完成。
 *
 * 关于"飞将":本生成器**也**不过滤"将帅照面"的走法(走完一步后两个将帅在同列、
 * 中间无棋子),这同样归 [domain.rules.MoveLegality] 处理。
 *
 * 关于"吃己方":本生成器**会**过滤——同一目标格若有己方棋子,不产生走法。
 */
class MoveGeneratorImpl : MoveGenerator {

    override fun movesFrom(board: Board, from: Position): List<Move> {
        val piece = board[from] ?: return emptyList()
        return generate(board, from, piece)
    }

    override fun movesFor(board: Board, side: Side): List<Move> {
        val out = ArrayList<Move>()
        for ((pos, piece) in board) {
            if (piece != null && piece.side == side) {
                out += generate(board, pos, piece)
            }
        }
        return out
    }

    override fun attacks(board: Board, from: Position, target: Position): Boolean {
        val piece = board[from] ?: return false
        return when (piece.type) {
            PieceType.KING -> kingAttacks(board, from, target, piece.side)
            PieceType.ADVISOR -> advisorAttacks(board, from, target, piece.side)
            PieceType.BISHOP -> bishopAttacks(board, from, target, piece.side)
            PieceType.KNIGHT -> knightAttacks(board, from, target, piece.side)
            PieceType.ROOK -> rookAttacks(board, from, target, piece.side)
            PieceType.CANNON -> cannonAttacks(board, from, target, piece.side)
            PieceType.PAWN -> pawnAttacks(board, from, target, piece.side)
        }
    }

    private fun generate(board: Board, from: Position, piece: Piece): List<Move> = when (piece.type) {
        PieceType.KING -> kingMoves(board, from, piece.side)
        PieceType.ADVISOR -> advisorMoves(board, from, piece.side)
        PieceType.BISHOP -> bishopMoves(board, from, piece.side)
        PieceType.KNIGHT -> knightMoves(board, from, piece.side)
        PieceType.ROOK -> rookMoves(board, from, piece.side)
        PieceType.CANNON -> cannonMoves(board, from, piece.side)
        PieceType.PAWN -> pawnMoves(board, from, piece.side)
    }

    /** 帅/将:九宫内一步直走。 */
    private fun kingMoves(board: Board, from: Position, side: Side): List<Move> =
        orthogonalSteps(from)
            .filter { BoardRegions.isInPalace(it, side) }
            .filterNot { board[it]?.side == side }
            .map { Move(from, it, side) }

    /** 仕/士:九宫内一步斜走。 */
    private fun advisorMoves(board: Board, from: Position, side: Side): List<Move> =
        diagonalSteps(from)
            .filter { BoardRegions.isInPalace(it, side) }
            .filterNot { board[it]?.side == side }
            .map { Move(from, it, side) }

    /** 相/象:田字格,目标在本方半场,象眼(中间点)必须空。 */
    private fun bishopMoves(board: Board, from: Position, side: Side): List<Move> {
        val moves = ArrayList<Move>(4)
        val deltas = listOf(2 to 2, 2 to -2, -2 to 2, -2 to -2)
        for ((dc, dr) in deltas) {
            val to = from.offsetBy(dc, dr) ?: continue
            if (!BoardRegions.isOnOwnSide(to, side)) continue
            val eye = from.offsetBy(dc / 2, dr / 2) ?: continue
            if (board[eye] != null) continue
            if (board[to]?.side == side) continue
            moves += Move(from, to, side)
        }
        return moves
    }

    /** 马:日字,蹩马腿禁走。 */
    private fun knightMoves(board: Board, from: Position, side: Side): List<Move> {
        val moves = ArrayList<Move>(8)
        // 8 个目标与对应的蹩腿格偏移
        val candidates: List<KnightStep> = listOf(
            KnightStep(1, 2, 0, 1),
            KnightStep(-1, 2, 0, 1),
            KnightStep(1, -2, 0, -1),
            KnightStep(-1, -2, 0, -1),
            KnightStep(2, 1, 1, 0),
            KnightStep(2, -1, 1, 0),
            KnightStep(-2, 1, -1, 0),
            KnightStep(-2, -1, -1, 0),
        )
        for (step in candidates) {
            val legPos = from.offsetBy(step.legDc, step.legDr) ?: continue
            if (board[legPos] != null) continue
            val to = from.offsetBy(step.dc, step.dr) ?: continue
            if (board[to]?.side == side) continue
            moves += Move(from, to, side)
        }
        return moves
    }

    private data class KnightStep(val dc: Int, val dr: Int, val legDc: Int, val legDr: Int)

    /** 车:四方向直线,被任意棋子阻挡。 */
    private fun rookMoves(board: Board, from: Position, side: Side): List<Move> {
        val moves = ArrayList<Move>(17)
        for ((dc, dr) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            var pos = from
            while (true) {
                val next = pos.offsetBy(dc, dr) ?: break
                val occ = board[next]
                if (occ == null) {
                    moves += Move(from, next, side)
                } else {
                    if (occ.side != side) moves += Move(from, next, side)
                    break
                }
                pos = next
            }
        }
        return moves
    }

    /** 炮:移动同车;吃子时中间必须恰好有一子(翻山)。 */
    private fun cannonMoves(board: Board, from: Position, side: Side): List<Move> {
        val moves = ArrayList<Move>(17)
        for ((dc, dr) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            var pos = from
            var jumped = false
            while (true) {
                val next = pos.offsetBy(dc, dr) ?: break
                val occ = board[next]
                if (!jumped) {
                    if (occ == null) {
                        moves += Move(from, next, side)
                    } else {
                        jumped = true
                    }
                } else {
                    if (occ != null) {
                        if (occ.side != side) moves += Move(from, next, side)
                        break
                    }
                }
                pos = next
            }
        }
        return moves
    }

    /** 兵/卒:未过河只前;过河后可左/右/前。 */
    private fun pawnMoves(board: Board, from: Position, side: Side): List<Move> {
        val moves = ArrayList<Move>(3)
        val forward = if (side == Side.RED) 1 else -1

        // 向前
        from.offsetBy(0, forward)?.let { to ->
            if (board[to]?.side != side) moves += Move(from, to, side)
        }

        if (BoardRegions.isAcrossRiver(from, side)) {
            for (dc in listOf(-1, 1)) {
                from.offsetBy(dc, 0)?.let { to ->
                    if (board[to]?.side != side) moves += Move(from, to, side)
                }
            }
        }
        return moves
    }

    private fun orthogonalSteps(from: Position): List<Position> =
        listOfNotNull(
            from.offsetBy(1, 0),
            from.offsetBy(-1, 0),
            from.offsetBy(0, 1),
            from.offsetBy(0, -1),
        )

    private fun diagonalSteps(from: Position): List<Position> =
        listOfNotNull(
            from.offsetBy(1, 1),
            from.offsetBy(1, -1),
            from.offsetBy(-1, 1),
            from.offsetBy(-1, -1),
        )

    // ---- 单点攻击判定(供 isInCheck 反查,避免分配 List)----

    /** 帅/将攻击 target:相邻直走(九宫外不判定,因为"将军"必然在九宫内)。 */
    private fun kingAttacks(board: Board, from: Position, target: Position, side: Side): Boolean {
        val (dc, dr) = target - from
        if (abs(dc) + abs(dr) != 1) return false
        return board[target]?.side != side
    }

    /** 仕/士攻击 target:相邻斜走。 */
    private fun advisorAttacks(board: Board, from: Position, target: Position, side: Side): Boolean {
        val (dc, dr) = target - from
        if (abs(dc) != 1 || abs(dr) != 1) return false
        return board[target]?.side != side
    }

    /** 相/象攻击 target:田字且不塞眼。 */
    private fun bishopAttacks(board: Board, from: Position, target: Position, side: Side): Boolean {
        val (dc, dr) = target - from
        if (abs(dc) != 2 || abs(dr) != 2) return false
        if (!BoardRegions.isOnOwnSide(target, side)) return false
        val eye = from.offsetBy(dc / 2, dr / 2) ?: return false
        if (board[eye] != null) return false
        return board[target]?.side != side
    }

    /** 马攻击 target:日字且不蹩腿。 */
    private fun knightAttacks(board: Board, from: Position, target: Position, side: Side): Boolean {
        val (dc, dr) = target - from
        // 8 个有效步幅:(±1, ±2) 或 (±2, ±1);对应蹩腿格偏移
        val (lc, lr) = when {
            abs(dc) == 1 && abs(dr) == 2 -> 0 to dr / 2
            abs(dc) == 2 && abs(dr) == 1 -> dc / 2 to 0
            else -> return false
        }
        val legPos = from.offsetBy(lc, lr) ?: return false
        if (board[legPos] != null) return false
        return board[target]?.side != side
    }

    /** 车攻击 target:同行/同列直射,中间无子。 */
    private fun rookAttacks(board: Board, from: Position, target: Position, side: Side): Boolean {
        val (dc, dr) = target - from
        if (dc != 0 && dr != 0) return false
        val (stepC, stepR) = when {
            dc > 0 -> 1 to 0
            dc < 0 -> -1 to 0
            dr > 0 -> 0 to 1
            dr < 0 -> 0 to -1
            else -> return false
        }
        var pos = from
        while (true) {
            val next = pos.offsetBy(stepC, stepR) ?: return false
            if (next == target) return true
            if (board[next] != null) return false
            pos = next
        }
    }

    /** 炮攻击 target:同行/同列,中间恰好一子(翻山),target 处有对方棋子。 */
    private fun cannonAttacks(board: Board, from: Position, target: Position, side: Side): Boolean {
        val (dc, dr) = target - from
        if (dc != 0 && dr != 0) return false
        val (stepC, stepR) = when {
            dc > 0 -> 1 to 0
            dc < 0 -> -1 to 0
            dr > 0 -> 0 to 1
            dr < 0 -> 0 to -1
            else -> return false
        }
        var pos = from
        var jumped = false
        while (true) {
            val next = pos.offsetBy(stepC, stepR) ?: return false
            if (next == target) {
                return jumped && board[target]?.side != side
            }
            if (board[next] != null) {
                if (jumped) return false
                jumped = true
            }
            pos = next
        }
    }

    /** 兵/卒攻击 target:未过河只前;过河后可左/右/前。 */
    private fun pawnAttacks(board: Board, from: Position, target: Position, side: Side): Boolean {
        val (dc, dr) = target - from
        val forward = if (side == Side.RED) 1 else -1
        if (dc == 0 && dr == forward) return board[target]?.side != side
        if (BoardRegions.isAcrossRiver(from, side) && dr == 0 && abs(dc) == 1) {
            return board[target]?.side != side
        }
        return false
    }

    private fun abs(x: Int): Int = if (x < 0) -x else x
}
