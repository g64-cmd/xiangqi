package com.xiangqi.app.engine.pikafish

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UCI 会话高层封装。
 *
 * 在 [Dispatchers.IO] 上启动后台 reader 协程,从 [PikafishProcess.readLine] 阻塞读
 * 并通过 [Channel] 分发,避免调用方陷入阻塞。
 *
 * - [send]:写命令到子进程 stdin。
 * - [waitFor]:等待首个以 [prefix] 开头的响应行,超时返回 null。
 * - [lines]:暴露剩余行作为流,供 [PikafishEngine.search] 消费 info / bestmove。
 *
 * reader 协程遇到 EOF 或 IOException 自然结束。close 时取消 reader 并关闭进程。
 */
class UciSession(
    private val proc: PikafishProcess,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lineChannel = Channel<String>(capacity = 256)
    private val readerJob: Job

    init {
        readerJob = scope.launch {
            try {
                while (true) {
                    val line = proc.readLine() ?: break
                    lineChannel.send(line)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // close 时被取消,正常退出
            } catch (_: Throwable) {
                // 子进程崩溃,关闭 channel 让消费者收尾
            }
            lineChannel.close()
        }
    }

    val isAlive: Boolean get() = proc.isAlive && readerJob.isActive

    /** 写一行命令到子进程 stdin。 */
    fun send(cmd: String) {
        proc.send(cmd)
    }

    /**
     * 阻塞等待首个以 [prefix] 开头的行(如 "uciok" / "readyok" / "bestmove")。
     * 超时返回 null;等待期间到达的其他行会被丢弃。
     */
    suspend fun waitFor(prefix: String, timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val line = lineChannel.receiveCatching().getOrNull() ?: return@withTimeoutOrNull null
                if (line.startsWith(prefix)) return@withTimeoutOrNull line
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

    /**
     * 消费后续行(用于 info / bestmove 循环)。每行通过 [onLine] 回调,
     * 回调返回 true 时结束(典型:收到 "bestmove" 后)。
     */
    suspend fun consume(onLine: (String) -> Boolean) {
        while (true) {
            val line = lineChannel.receiveCatching().getOrNull() ?: return
            if (onLine(line)) return
        }
    }

    override fun close() {
        scope.cancel()
        proc.close()
    }
}
