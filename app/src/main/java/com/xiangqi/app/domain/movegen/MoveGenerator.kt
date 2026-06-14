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

    /**
     * [from] 处棋子按几何规则是否攻击 [target](即 from→target 是一条伪合法走法)。
     *
     * 与 [movesFrom] 等价于 `movesFrom(board, from).any { it.to == target }`,
     * 但**不分配 List**,性能更优。供 [com.xiangqi.app.domain.rules.CheckDetector]
     * 反查"将位是否被攻击"时使用——把 N² 遍历降为 N。
     */
    fun attacks(board: Board, from: Position, target: Position): Boolean
}
