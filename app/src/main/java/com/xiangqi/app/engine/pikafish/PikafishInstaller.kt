package com.xiangqi.app.engine.pikafish

import android.content.Context
import com.xiangqi.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 皮卡鱼二进制与 NNUE 权重的 assets → filesDir 安装器。
 *
 * 路径约定:
 * - 可执行文件:`filesDir/pikafish/bin/pikafish`
 * - NNUE 权重:`filesDir/pikafish/pikafish.nnue`
 * - 工作目录:`filesDir/pikafish/`(pikafish 从 cwd 找 `pikafish.nnue`)
 *
 * 校验:每次 [install] 时检查可执行文件的 SHA-256 是否匹配 [BuildConfig.PIKAFISH_SHA]。
 * 不匹配(老版本残留 / 损坏)则重装。SHA 不可达时(assets 缺失)抛 [IllegalStateException],
 * 调用方需向用户报错。
 */
@Singleton
class PikafishInstaller @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {

    /** 一次安装产出的文件引用集合。 */
    data class Install(
        val executable: File,
        val workingDir: File,
        val nnueFile: File,
    )

    /**
     * 期望的 SHA-256 值。默认从 [BuildConfig] 读取,测试时可覆盖
     * (典型用法:把 assets 占位文件的真实 SHA 注入进来)。
     */
    var expectedExecSha: String = BuildConfig.PIKAFISH_SHA
        internal set
    var expectedNnueSha: String = BuildConfig.PIKAFISH_NNUE_SHA
        internal set

    /** assets 中的资产路径,测试可改为占位路径以避开 main 的真二进制。 */
    internal var execAssetName: String = EXEC_ASSET
    internal var nnueAssetName: String = NNUE_ASSET

    private val rootDir: File get() = File(ctx.filesDir, "pikafish")
    private val binDir: File get() = File(rootDir, "bin")
    private val executableFile: File get() = File(binDir, "pikafish")
    private val nnueFile: File get() = File(rootDir, "pikafish.nnue")

    /**
     * 确保 pikafish 与 NNUE 已安装且校验通过,返回 [Install] 引用。
     *
     * 多次调用幂等:已安装且 SHA 匹配时直接返回;否则覆盖安装。
     */
    fun install(): Install {
        binDir.mkdirs()
        if (!executableFile.exists() || sha256(executableFile) != expectedExecSha) {
            copyFromAssets(execAssetName, executableFile)
            executableFile.setExecutable(true, true)
            val actual = sha256(executableFile)
            check(actual == expectedExecSha) {
                "pikafish 可执行文件 SHA-256 不匹配:expected=$expectedExecSha, actual=$actual"
            }
        }
        if (!nnueFile.exists() || sha256(nnueFile) != expectedNnueSha) {
            copyFromAssets(nnueAssetName, nnueFile)
            val actual = sha256(nnueFile)
            check(actual == expectedNnueSha) {
                "pikafish.nnue SHA-256 不匹配:expected=$expectedNnueSha, actual=$actual"
            }
        }
        return Install(executableFile, rootDir, nnueFile)
    }

    /** 是否已安装(不校验 SHA)。 */
    fun isInstalled(): Boolean = executableFile.exists() && nnueFile.exists()

    private fun copyFromAssets(assetName: String, dest: File) {
        dest.parentFile?.mkdirs()
        ctx.assets.open(assetName).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    companion object {
        private const val EXEC_ASSET = "pikafish/pikafish"
        private const val NNUE_ASSET = "pikafish/pikafish.nnue"

        /** 计算 [file] 的 SHA-256,小写十六进制。 */
        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** assets 读取失败抛出。 */
class PikafishAssetMissingException(asset: String) :
    IOException("assets 中找不到 pikafish 资产: $asset")
