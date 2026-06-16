package com.xiangqi.app.engine.pikafish

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.ByteArrayInputStream
import java.io.File

/**
 * [PikafishInstaller] 的纯 JVM 测试。
 *
 * 用 mockk 模拟 Context + AssetManager + ApplicationInfo,assets 字节流直接由
 * 测试提供。filesDir / nativeLibraryDir 用 [TemporaryFolder] 隔离。
 *
 * **可执行文件**不再走 assets 复制,而是放在 mockk 出来的 nativeLibraryDir,
 * 模拟 AGP jniLibs 安装时解压的场景。
 */
class PikafishInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var nativeLibraryDir: File
    private lateinit var ctx: Context
    private lateinit var installer: PikafishInstaller

    private val execBytes = "FAKE_PIKAFISH_BINARY_FOR_TESTING_ONLY".toByteArray()
    private val nnueBytes = "FAKE_PIKAFISH_NNUE_WEIGHT".toByteArray()

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        nativeLibraryDir = tmp.newFolder("nativeLibrary", "arm64")
        // 预置 libpikafish.so(模拟 AGP 安装时解压)
        File(nativeLibraryDir, "libpikafish.so").writeBytes(execBytes)

        ctx = mockk(relaxed = true)
        every { ctx.filesDir } returns filesDir
        // ApplicationInfo.nativeLibraryDir 是 final String 字段,直接 new 实例赋值
        // 比 mockk 字段更稳。注意 apply 内 this 是 appInfo,需用外层引用。
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = this@PikafishInstallerTest.nativeLibraryDir.absolutePath
        }
        every { ctx.applicationInfo } returns appInfo
        val assets = mockk<AssetManager>()
        every { assets.open("pikafish/pikafish.nnue") } answers { ByteArrayInputStream(nnueBytes) }
        every { ctx.assets } returns assets

        installer = PikafishInstaller(ctx)
        installer.expectedExecSha = sha256(execBytes)
        installer.expectedNnueSha = sha256(nnueBytes)
    }

    @Test
    fun `首次 install 从 assets 复制 NNUE,executablePath 指向 nativeLibraryDir`() {
        val install = installer.install()

        assertThat(install.nnueFile.exists()).isTrue()
        assertThat(install.workingDir.exists()).isTrue()
        assertThat(install.executablePath).isEqualTo(
            File(nativeLibraryDir, "libpikafish.so").absolutePath,
        )
    }

    @Test
    fun `第二次 install 跳过 NNUE 复制,幂等`() {
        val first = installer.install()
        val firstNnueMtime = first.nnueFile.lastModified()

        Thread.sleep(20)
        val second = installer.install()
        // 未触发覆盖,文件 lastModified 不变
        assertThat(second.nnueFile.lastModified()).isEqualTo(firstNnueMtime)
    }

    @Test
    fun `NNUE SHA 不匹配时重新覆盖安装`() {
        installer.install()
        val nnueFile = File(filesDir, "pikafish/pikafish.nnue")
        nnueFile.writeText("CORRUPTED_CONTENT")

        installer.install()
        // 重新安装后内容应该恢复
        val actual = PikafishInstaller.sha256(nnueFile)
        assertThat(actual).isEqualTo(installer.expectedNnueSha)
    }

    @Test
    fun `isInstalled 在 install 前为 false,之后为 true`() {
        assertThat(installer.isInstalled()).isFalse()
        installer.install()
        assertThat(installer.isInstalled()).isTrue()
    }

    @Test
    fun `verifyExecutable 通过 nativeLibraryDir 中的文件`() {
        installer.install()
        installer.verifyExecutable() // 不抛异常即通过
    }

    @Test
    fun `verifyExecutable 在可执行 SHA 不匹配时抛异常`() {
        installer.install()
        // 改 nativeLibraryDir 里的 .so 内容(模拟 APK 被篡改)
        File(nativeLibraryDir, "libpikafish.so").writeBytes("TAMPERED".toByteArray())
        try {
            installer.verifyExecutable()
            assert(false) { "应该抛 IllegalStateException" }
        } catch (e: IllegalStateException) {
            // 预期
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
