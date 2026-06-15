package com.xiangqi.app.engine.pikafish

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * pikafish 子进程的轻量包装。
 *
 * 负责三件事:
 * 1. 用 [ProcessBuilder] 启动可执行文件,工作目录指向包含 `pikafish.nnue` 的目录。
 * 2. 提供阻塞的 [send](写 stdin 一行 + flush)与 [readLine](读 stdout 一行)。
 * 3. [close] 终止进程;[isAlive] 用于会话复用判断。
 *
 * 调用方([UciSession])负责在 `Dispatchers.IO` 上调度,避免阻塞主线程或 Default 调度器。
 *
 * @param executable 可执行文件,需提前 `setExecutable(true, true)`。
 * @param workingDir pikafish 的工作目录;NNUE 权重从该目录的 `pikafish.nnue` 加载。
 */
class PikafishProcess(
    val executable: File,
    val workingDir: File,
) : AutoCloseable {

    private val process: Process = ProcessBuilder(executable.absolutePath)
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()

    private val stdin: BufferedWriter =
        BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.US_ASCII))
    private val stdout: BufferedReader =
        BufferedReader(InputStreamReader(process.inputStream, Charsets.US_ASCII))

    /** 进程是否仍在运行。 */
    val isAlive: Boolean get() = process.isAlive

    /** 向子进程 stdin 写入一行 + 换行 + flush。 */
    @Throws(IOException::class)
    fun send(line: String) {
        stdin.write(line)
        stdin.newLine()
        stdin.flush()
    }

    /**
     * 阻塞读取 stdout 一行。返回 null 表示流结束(子进程退出 / pipe 关闭)。
     */
    @Throws(IOException::class)
    fun readLine(): String? = stdout.readLine()

    /**
     * 等待子进程退出至多 [timeoutMs] 毫秒。返回退出码;超时返回 null。
     */
    fun waitForExit(timeoutMs: Long): Int? {
        val ok = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        return if (ok) process.exitValue() else null
    }

    /** 强制终止子进程并关闭流。幂等。 */
    override fun close() {
        runCatching { stdin.close() }
        runCatching { stdout.close() }
        process.destroy()
        if (process.isAlive) {
            process.waitFor(500, TimeUnit.MILLISECONDS)
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
