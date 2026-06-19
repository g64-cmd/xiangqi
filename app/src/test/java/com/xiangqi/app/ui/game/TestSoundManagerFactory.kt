package com.xiangqi.app.ui.game

import com.xiangqi.app.audio.SoundManager
import io.mockk.mockk

/**
 * 测试用 SoundManager 工厂。返回 relaxed mock,所有 play* 调用 no-op,
 * 避免 JVM 单测环境实例化真实 SoundManager(会触 SoundPool.Builder Android API)。
 *
 * 多个 GameViewModel*Test 共用,降低每个测试构造点的样板代码。
 */
internal fun testSoundManager(): SoundManager = mockk(relaxed = true)
