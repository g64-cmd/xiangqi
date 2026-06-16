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
 * 皮卡鱼 NNUE 权重的 assets → filesDir 安装器。
 *
 * **可执行文件**(`libpikafish.so`)不再由 Installer 复制 —— 它通过 AGP jniLibs
 * 机制在 APK 安装时解压到 `applicationInfo.nativeLibraryDir`(SELinux
 * `apk_data_file` 域,允许 execute_no_trans)。Installer 通过 [executablePath]
 * 暴露该路径给 [PikafishEngine]。
 *
 * **NNUE 权重**(`pikafish.nnue`)是数据文件,不走 exec,无 SELinux 问题,
 * 仍由 Installer 从 assets 复制到 filesDir,与历史一致。
 *
 * 路径约定:
 * - 可执行:`${applicationInfo.nativeLibraryDir}/libpikafish.so`
 * - NNUE:`filesDir/pikafish/pikafish.nnue`
 * - 工作目录:`filesDir/pikafish/`(pikafish 从 cwd 找 pikafish.nnue)
 *
 * 校验:每次 [install] 时检查 NNUE 文件的 SHA-256 是否匹配
 * [BuildConfig.PIKAFISH_NNUE_SHA],不匹配则重装。可执行文件的 SHA 由 Installer
 * 在 [verifyExecutable] 单独提供(只读检查,不复制);不一致时抛
 * [IllegalStateException] —— 因为可执行文件来自 APK,失败说明 APK 被篡改。
 */
@Singleton
class PikafishInstaller @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {

    /** 一次安装产出的文件引用集合。 */
    data class Install(
        /** 可执行文件绝对路径,在 `nativeLibraryDir/libpikafish.so`。 */
        val executablePath: String,
        val workingDir: File,
        val nnueFile: File,
    )

    /**
     * 期望的 SHA-256 值。默认从 [BuildConfig] 读取,测试时可覆盖。
     */
    var expectedExecSha: String = BuildConfig.PIKAFISH_SHA
        internal set
    var expectedNnueSha: String = BuildConfig.PIKAFISH_NNUE_SHA
        internal set

    /** assets 中的 NNUE 资产路径,测试可改为占位路径。 */
    internal var nnueAssetName: String = NNUE_ASSET

    private val rootDir: File get() = File(ctx.filesDir, "pikafish")
    private val nnueFile: File get() = File(rootDir, "pikafish.nnue")

    /** 可执行文件路径:`nativeLibraryDir/libpikafish.so`。 */
    val executablePath: String
        get() = File(ctx.applicationInfo.nativeLibraryDir, "libpikafish.so").absolutePath

    /**
     * 确保 NNUE 已安装且 SHA 通过,返回 [Install] 引用。
     *
     * 不复制可执行文件(由 AGP/PackageManager 在 APK 安装时处理);只校验其存在。
     */
    fun install(): Install {
        rootDir.mkdirs()
        if (!nnueFile.exists() || sha256(nnueFile) != expectedNnueSha) {
            copyFromAssets(nnueAssetName, nnueFile)
            val actual = sha256(nnueFile)
            check(actual == expectedNnueSha) {
                "pikafish.nnue SHA-256 不匹配:expected=$expectedNnueSha, actual=$actual"
            }
        }
        return Install(executablePath, rootDir, nnueFile)
    }

    /**
     * 校验可执行文件存在于 nativeLibraryDir 且 SHA 匹配。
     * 失败时抛 [IllegalStateException],调用方应降级或向用户报错。
     */
    fun verifyExecutable() {
        val file = File(executablePath)
        check(file.exists()) {
            "libpikafish.so 不存在于 nativeLibraryDir: $executablePath"
        }
        check(file.canExecute()) {
            "libpikafish.so 不可执行: $executablePath"
        }
        val actual = sha256(file)
        check(actual == expectedExecSha) {
            "libpikafish.so SHA-256 不匹配:expected=$expectedExecSha, actual=$actual"
        }
    }

    /** NNUE 是否已安装(不校验 SHA,不校验可执行)。 */
    fun isInstalled(): Boolean = nnueFile.exists() && File(executablePath).exists()

    private fun copyFromAssets(assetName: String, dest: File) {
        dest.parentFile?.mkdirs()
        ctx.assets.open(assetName).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    companion object {
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
