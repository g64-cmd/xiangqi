package com.xiangqi.app.domain.notation

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Piece
import com.xiangqi.app.domain.model.PieceType
import com.xiangqi.app.domain.model.Side

/**
 * 把 [Move] 翻译成中文棋谱(如"炮二平五"、"前马退八"、"卒 7 进 1")。
 *
 * **列号约定**(标准中文象棋记谱):
 * - 红方视角:右起 九 八 七 六 五 四 三 二 一 → 对应内部 col 0..8(红方初始
 *   在棋盘下半,玩家视角下从右往左数)。所以 red col c → 汉字 `9 - c`。
 * - 黑方视角:左起 1 2 3 4 5 6 7 8 9 → 对应内部 col 0..8。black col c → 阿拉伯
 *   数字 `c + 1`。
 *
 * **走法动作**:
 * - 同列(横向移动):"平 N"(N 为目标列号,视角同上)
 * - 红进 / 黑退(row 变小对红方是"进",对黑方是"退"):"进 X"
 * - 红退 / 黑进:"退 X"
 * - X 的语义分两类:
 *   - 车炮兵卒帅将(直行类):走几行 = |Δrow|
 *   - 马仕相象(斜行类):走法目标列号(因为不能直行)
 *
 * **同列同种棋子**(典型:双车 / 双马 / 双炮同列):用 前 / 后 区分。
 *   红:row 小的在前(更靠近对方);黑:row 大的在前。简化:不实现三子同列
 *   场景(中国象棋不存在同方三子同列,兵卒理论可能但极少)。
 *
 * 输出格式:
 * - 普通棋子:`{棋子}{源列}{动作}{目标}`
 * - 同列双子:`{前|后}{棋子}{动作}{目标}`
 */
object ChineseNotation {

    /**
     * 把 [move] 翻译为中文棋谱。[boardBefore] 为走子前局面,用于判定棋子类型与
     * 同列同种棋子(前 / 后)。
     */
    fun format(move: Move, boardBefore: Board): String {
        val piece = boardBefore[move.from]
            ?: throw IllegalArgumentException("起点无棋子: ${move.from}")
        val isRed = piece.side == Side.RED
        val pieceName = pieceName(piece)
        val dc = move.to.col - move.from.col
        val dr = move.to.row - move.from.row
        val sameColumnPeers = peersInSameColumn(piece, move.from.col, boardBefore)
        val needsPrefix = sameColumnPeers.size >= 2

        if (needsPrefix) {
            val prefix = frontBackPrefix(piece.side, move.from, sameColumnPeers)
            val action = actionToken(piece.side, dr)
            val target = targetToken(piece.type, piece.side, dc, dr, move.to.col)
            return "$prefix$pieceName$action$target"
        }

        val srcColToken = columnToken(piece.side, move.from.col)
        val action = actionToken(piece.side, dr)
        val target = targetToken(piece.type, piece.side, dc, dr, move.to.col)
        return "$pieceName$srcColToken$action$target"
    }

    private fun pieceName(piece: Piece): String = when (piece.type) {
        PieceType.KING -> if (piece.side == Side.RED) "帅" else "将"
        PieceType.ADVISOR -> if (piece.side == Side.RED) "仕" else "士"
        PieceType.BISHOP -> if (piece.side == Side.RED) "相" else "象"
        PieceType.KNIGHT -> "马"
        PieceType.ROOK -> "车"
        PieceType.CANNON -> "炮"
        PieceType.PAWN -> if (piece.side == Side.RED) "兵" else "卒"
    }

    /**
     * 走子方视角下的"列号"字符。红方右起一二三...,黑方左起 1 2 3...。
     */
    private fun columnToken(side: Side, col: Int): String {
        val n = if (side == Side.RED) 9 - col else col + 1
        return if (side == Side.RED) redNumeral(n) else n.toString()
    }

    private fun redNumeral(n: Int): String = when (n) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "七"
        8 -> "八"
        9 -> "九"
        else -> throw IllegalArgumentException("非法列号: $n")
    }

    /**
     * 进 / 退 / 平。
     *
     * 棋盘 row 0 = 红方底线,row 9 = 黑方底线(见 [com.xiangqi.app.domain.model.Position])。
     * 红方:row 增大(向黑方推进)= 进,row 减小 = 退。
     * 黑方:row 减小(向红方推进)= 进,row 增大 = 退。
     * row 不变 = 平。
     */
    private fun actionToken(side: Side, dr: Int): String {
        if (dr == 0) return "平"
        val advances = if (side == Side.RED) dr > 0 else dr < 0
        return if (advances) "进" else "退"
    }

    /**
     * 走子动作后的"目标"信息。
     *
     * - 平:目标列号(视角同 source)
     * - 进 / 退 直行类(车炮兵卒帅将):走几行 = |Δrow|
     * - 进 / 退 斜行类(马仕相象):目标列号
     */
    private fun targetToken(
        type: PieceType,
        side: Side,
        dc: Int,
        dr: Int,
        toCol: Int,
    ): String {
        if (dr == 0) return columnToken(side, toCol)
        return if (isDiagonalPiece(type)) {
            columnToken(side, toCol)
        } else {
            val steps = kotlin.math.abs(dr)
            if (side == Side.RED) redNumeral(steps) else steps.toString()
        }
    }

    private fun isDiagonalPiece(type: PieceType): Boolean = when (type) {
        PieceType.KNIGHT, PieceType.ADVISOR, PieceType.BISHOP -> true
        else -> false
    }

    /**
     * 同列同方同种棋子位置列表(用于前 / 后判定)。包含 [origin] 自身。
     */
    private fun peersInSameColumn(
        piece: Piece,
        col: Int,
        board: Board,
    ): List<Int> {
        val rows = ArrayList<Int>()
        for (row in 0..9) {
            val p = board[col, row] ?: continue
            if (p.type == piece.type && p.side == piece.side) rows += row
        }
        return rows
    }

    /**
     * 红:row 大的在前(更靠黑方);黑:row 小的在前(更靠红方)。
     */
    private fun frontBackPrefix(side: Side, from: com.xiangqi.app.domain.model.Position, peers: List<Int>): String {
        val sortedAsc = peers.sorted()
        val isFirst = if (side == Side.RED) from.row == sortedAsc.last() else from.row == sortedAsc.first()
        return if (isFirst) "前" else "后"
    }
}
