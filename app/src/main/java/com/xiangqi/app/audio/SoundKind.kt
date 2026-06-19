package com.xiangqi.app.audio

/**
 * 走子事件触发的音效种类。
 *
 * 占位音由 [SoundManager] 用合成 PCM 生成,后续可替换为 res/raw 下的 .ogg,
 * 接口零改动。
 *
 * @property MOVE 普通走子(未吃子、未将军、未终局)。
 * @property CAPTURE 吃子。
 * @property CHECK 走完将军对方(含将死,将死用 CHECK 音复用,声音更"重")。
 * @property STALEMATE 困毙(走完对方无子可动且未被将)。
 */
enum class SoundKind {
    MOVE,
    CAPTURE,
    CHECK,
    STALEMATE,
}
