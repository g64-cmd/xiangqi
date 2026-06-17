package com.xiangqi.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiangqi.app.BuildConfig

/**
 * 关于页:版本、AI 引擎、开源协议、源码链接、NNUE 权重授权。
 *
 * 满足 GPL-3.0 合规:互动界面展示 license notice + 仓库链接 + 使用的 GPL 软件版本。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "← 返回",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 4.dp),
            )

            Section("中国象棋", "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

            Section(
                title = "AI 引擎",
                body = """
                    本应用内置以下引擎,可在开局设置页选择:

                    • 皮卡鱼 Pikafish ${BuildConfig.PIKAFISH_VERSION}
                      官方仓库:github.com/official-pikafish/Pikafish
                      协议:GPL-3.0
                      NNUE 权重:pikafish.nnue(权重授权协议见 release 包内说明,
                      仅供合法用途,未经授权不得商用)

                    • 自研引擎(M2 Negamax + Alpha-Beta + Transposition Table)
                      本仓库自有代码,GPL-3.0
                """.trimIndent(),
            )

            Section(
                title = "开源协议",
                body = """
                    本应用以 GNU General Public License v3.0 发布,详见 LICENSE 文件。
                    使用了 GPL-3.0 的 Pikafish,故本应用整体以 GPL-3.0 授权。
                """.trimIndent(),
            )

            Section(
                title = "源代码",
                body = "github.com/g64-cmd/xiangqi",
            )
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
