package com.xiangqi.app.domain.audio

import com.xiangqi.app.audio.SoundKind
import com.xiangqi.app.data.model.HistoryEntry
import com.xiangqi.app.domain.model.GameResult

/**
 * 把一次 [com.xiangqi.app.data.game.GameState] 变更归类为应播放的 [SoundKind]。
 *
 * 纯 object,所有判定输入(check / stalemate)都由调用方算好后作为布尔参数传入,
 * 不依赖 Android / 规则组件,便于 JVM 单测。
 *
 * 判定规则:
 * 1. `newHistorySize <= prevHistorySize` 或 `entry == null`
 *    → null(undo / restart / init 重放 / 同步重复 emit)。
 * 2. 终局这一步([resultBefore] ONGOING → [result] 非 ONGOING):
 *    - [opponentStalemate] = true → [SoundKind.STALEMATE]
 *    - 否则(将死)→ [SoundKind.CHECK](将死复用将军音,声音更"重")
 * 3. `entry.boardBefore[entry.move.to] != null` → [SoundKind.CAPTURE](目标格原有子被吃)。
 * 4. [opponentInCheck] = true → [SoundKind.CHECK](对手被将但未将死)。
 * 5. 否则 → [SoundKind.MOVE]。
 *
 * 顺序很重要:终局判定优先于吃子/将军,避免将死时同时触发 CAPTURE 音。
 */
object MoveSoundClassifier {

    /**
     * @param prevHistorySize 上次 emit 时观察到的 history 大小。
     * @param newHistorySize 本次 emit 的 history 大小。
     * @param entry history.lastOrNull();走子后非空,undo/restart/init 可能空。
     * @param result 本次 emit 的对局结果。
     * @param resultBefore entry 记录的走子前结果(几乎都是 ONGOING)。
     * @param opponentInCheck 走子后对手(sideToMoveAfter)是否被将(由调用方用
     *   CheckDetector.isInCheck(newBoard, sideToMoveAfter) 计算)。
     * @param opponentStalemate 走子后对手是否被困毙(由 CheckmateDetector.isStalemate
     *   计算)。仅在 becameTerminal 时有意义。
     * @return 应播放的 [SoundKind];null 表示不发声。
     */
    fun classify(
        prevHistorySize: Int,
        newHistorySize: Int,
        entry: HistoryEntry?,
        result: GameResult,
        resultBefore: GameResult,
        opponentInCheck: Boolean,
        opponentStalemate: Boolean,
    ): SoundKind? {
        if (newHistorySize <= prevHistorySize) return null
        if (entry == null) return null

        // 终局这一步:先看困毙,再看将死(将死 → CHECK)。
        val becameTerminal = resultBefore is GameResult.ONGOING && result !is GameResult.ONGOING
        if (becameTerminal) {
            return if (opponentStalemate) SoundKind.STALEMATE else SoundKind.CHECK
        }

        // 吃子:走子前目标格有子。Board 索引签名 operator fun get(Position)。
        val targetBefore = entry.boardBefore[entry.move.to]
        if (targetBefore != null) return SoundKind.CAPTURE

        // 将军:对手被将但未将死。
        if (opponentInCheck) return SoundKind.CHECK

        return SoundKind.MOVE
    }
}
