package com.xiangqi.app.engine.pikafish

import android.content.Context
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
 * 用 mockk 模拟 Context + AssetManager,assets 字节流直接由测试提供。
 * filesDir 用 [TemporaryFolder] 隔离,避免污染。
 *
 * 不引入 Robolectric(无需 SDK 34 / android-all jar 下载),
 * 测试快且 CI 友好。
 */
class PikafishInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var ctx: Context
    private lateinit var installer: PikafishInstaller

    private val execBytes = "FAKE_PIKAFISH_BINARY_FOR_TESTING_ONLY".toByteArray()
    private val nnueBytes = "FAKE_PIKAFISH_NNUE_WEIGHT".toByteArray()

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        ctx = mockk(relaxed = true)
        every { ctx.filesDir } returns filesDir
        val assets = mockk<AssetManager>()
        // 用 answers 每次返回新的 ByteArrayInputStream,避免多次 install 时流已读完
        every { assets.open("pikafish/pikafish") } answers { ByteArrayInputStream(execBytes) }
        every { assets.open("pikafish/pikafish.nnue") } answers { ByteArrayInputStream(nnueBytes) }
        every { ctx.assets } returns assets

        installer = PikafishInstaller(ctx)
        installer.expectedExecSha = sha256(execBytes)
        installer.expectedNnueSha = sha256(nnueBytes)
    }

    @Test
    fun `首次 install 从 assets 复制文件并校验 SHA`() {
        val install = installer.install()

        assertThat(install.executable.exists()).isTrue()
        assertThat(install.nnueFile.exists()).isTrue()
        assertThat(install.executable.canExecute()).isTrue()
        assertThat(install.workingDir.exists()).isTrue()
    }

    @Test
    fun `第二次 install 跳过复制,幂等`() {
        val first = installer.install()
        val firstExecMtime = first.executable.lastModified()

        Thread.sleep(20)
        val second = installer.install()
        // 未触发覆盖,文件 lastModified 不变
        assertThat(second.executable.lastModified()).isEqualTo(firstExecMtime)
    }

    @Test
    fun `SHA 不匹配时重新覆盖安装`() {
        installer.install()
        val execFile = File(filesDir, "pikafish/bin/pikafish")
        execFile.writeText("CORRUPTED_CONTENT")

        installer.install()
        // 重新安装后内容应该恢复
        val actual = PikafishInstaller.sha256(execFile)
        assertThat(actual).isEqualTo(installer.expectedExecSha)
    }

    @Test
    fun `isInstalled 在 install 前为 false,之后为 true`() {
        assertThat(installer.isInstalled()).isFalse()
        installer.install()
        assertThat(installer.isInstalled()).isTrue()
    }

    private fun sha256(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
