package com.xiangqi.app.domain.movegen

import com.xiangqi.app.domain.model.Board
import com.xiangqi.app.domain.model.Move
import com.xiangqi.app.domain.model.Position
import com.xiangqi.app.domain.model.Side

/**
 * 走法生成器。
 *
 * 职责:给定 [board] 与 [from] 位置,返回该处棋子的"伪合法走法"集合
 * (pseudo-legal moves)——即不检查"走完是否自将/送将/飞将"的走法。
 * 真正合法性判定(包括"飞将"——即双方将帅照面禁走)在 [domain.rules] 包,
 * 走法生成器只产出棋子按规则的几何走法。
 *
 * "飞将"判定有时也被放进走法生成器,本项目采取分离方案以便测试。
 */
interface MoveGenerator {
    /** 生成 [from] 处棋子的伪合法走法。若 from 为空,返回空列表。 */
    fun movesFrom(board: Board, from: Position): List<Move>

    /** 生成棋盘上 [side] 方所有棋子的伪合法走法,顺序无约定。 */
    fun movesFor(board: Board, side: Side): List<Move>
}
