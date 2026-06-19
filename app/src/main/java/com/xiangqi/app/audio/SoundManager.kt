package com.xiangqi.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 走子音效播放器。封装 [SoundPool] + 占位合成 PCM。
 *
 * **占位音生成**:init 时用纯函数 [synthPcm] 合成 4 段 8kHz mono 16-bit PCM,写入
 * `cacheDir/sound_<kind>.pcm`,`pool.load(path)` 加载;`setOnLoadCompleteListener`
 * 加载成功后删除临时文件,避免堆积。后续替换 `res/raw` 下的 .ogg 时,把 `load(path)`
 * 换成 `load(context, R.raw.move)` 即可,接口零改动。
 *
 * **并发**:SoundPool maxStreams=4,允许走子 + 吃子 + 将军快速连击不互相截断。
 *
 * **开关**:[enabled] 由 GameViewModel 每次播放前实时读 GameConfig.soundEnabled
 * 写入,关闭时 play 静默 no-op。
 *
 * **预热**:[warmUp] 在 GameScreen 进入时调一次 0 音量 play,避免首次播放因 SoundPool
 * 异步加载未完成而被丢弃。
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** soundId per [SoundKind];init 异步加载,未就绪时为 null。 */
    private val soundIds: MutableMap<SoundKind, Int> = mutableMapOf()

    /** 开关;由调用方(GameViewModel)实时写入。volatile 保证跨线程可见。 */
    @Volatile
    var enabled: Boolean = true

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                // 加载成功后清理临时文件。listener 拿不到 kind,简单起见遍历删除
                // 全部 4 个 .pcm,无副作用(已加载的 sample 已在 SoundPool 内存里)。
                SoundKind.entries.forEach {
                    File(ctx.cacheDir, "sound_${it.name.lowercase()}.pcm").delete()
                }
            }
        }
        for (kind in SoundKind.entries) {
            val bytes = synthPcm(kind)
            val file = File(ctx.cacheDir, "sound_${kind.name.lowercase()}.pcm")
            file.writeBytes(bytes)
            val id = pool.load(file.absolutePath, 0)
            soundIds[kind] = id
        }
    }

    /** 播放指定类型音效;[enabled] 为 false 时静默 no-op。 */
    fun play(kind: SoundKind) {
        if (!enabled) return
        val id = soundIds[kind] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun playMove() = play(SoundKind.MOVE)
    fun playCapture() = play(SoundKind.CAPTURE)
    fun playCheck() = play(SoundKind.CHECK)
    fun playStalemate() = play(SoundKind.STALEMATE)

    /**
     * 预热:GameScreen 进入时调用一次 0 音量播放,触发 SoundPool 把 sample 加载到内存,
     * 避免首次真实播放因异步加载未完成被丢弃。
     */
    fun warmUp() {
        val id = soundIds[SoundKind.MOVE] ?: return
        pool.play(id, 0f, 0f, 0, 0, 1f)
    }
}

/**
 * 为 [kind] 合成一段 8kHz mono 16-bit PCM(裸 .pcm,无 WAV 头)。
 *
 * 参数手调,目标是"短促、易分辨":
 * - MOVE:80ms 440Hz 纯音 + 指数衰减,清脆"啪"。
 * - CAPTURE:120ms 220+110Hz 叠加 + 更强衰减,厚重"咚"。
 * - CHECK:200ms 880→660Hz 线性频扫,警示感。
 * - STALEMATE:300ms 165Hz 长尾 + 慢衰减,沉闷。
 *
 * 返回 ByteArray 长度 = `sampleRate * durationMs / 1000 * 2`(16-bit = 2 字节/样本)。
 */
internal fun synthPcm(kind: SoundKind): ByteArray {
    val sampleRate = 8000
    val durationMs = when (kind) {
        SoundKind.MOVE -> 80
        SoundKind.CAPTURE -> 120
        SoundKind.CHECK -> 200
        SoundKind.STALEMATE -> 300
    }
    val samples = sampleRate * durationMs / 1000
    val out = ByteArray(samples * 2)
    var idx = 0
    for (i in 0 until samples) {
        val t = i / sampleRate.toDouble()
        val progress = i.toDouble() / samples
        val sample: Double = when (kind) {
            SoundKind.MOVE -> {
                val env = exp(-progress * 4.0)
                env * sin(2 * PI * 440.0 * t)
            }
            SoundKind.CAPTURE -> {
                val env = exp(-progress * 5.0)
                env * 0.6 * (sin(2 * PI * 220.0 * t) + sin(2 * PI * 110.0 * t)) / 2
            }
            SoundKind.CHECK -> {
                val env = exp(-progress * 2.5)
                val freq = 880.0 - (880.0 - 660.0) * progress
                env * sin(2 * PI * freq * t)
            }
            SoundKind.STALEMATE -> {
                val env = exp(-progress * 1.8)
                env * sin(2 * PI * 165.0 * t)
            }
        }
        // 16-bit little-endian,clamp 到 Short 范围
        val v = (sample * Short.MAX_VALUE * 0.7).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        out[idx++] = (v and 0xFF).toByte()
        out[idx++] = ((v shr 8) and 0xFF).toByte()
    }
    return out
}
