package com.xiangqi.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiangqi.app.ui.about.AboutScreen
import com.xiangqi.app.ui.game.GameScreen
import com.xiangqi.app.ui.setup.SetupScreen

/**
 * 应用顶层导航图。三个目的地:
 * - "setup":开局设置(模式 / 执棋方 / 难度 / 引擎)
 * - "game":对局棋盘
 * - "about":关于页(GPL 合规、引擎版本)
 *
 * 起始目的地 = "setup"。SetupScreen "开始对局"时把配置写入 GameConfigHolder(单例),
 * 然后 navigate("game")。GameViewModel 通过 Hilt 注入同一个 Holder 读取配置。
 *
 * GameScreen "返回"调用 `navController.popBackStack()` 回 Setup。
 * SetupScreen "关于" → navigate("about"),AboutScreen "返回" popBackStack。
 */
@Composable
fun XiangqiNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = "setup",
        modifier = modifier,
    ) {
        composable("setup") {
            SetupScreen(
                onStart = {
                    navController.navigate("game") {
                        launchSingleTop = true
                    }
                },
                onAbout = { navController.navigate("about") },
            )
        }
        composable("game") {
            GameScreen(
                onExit = { navController.popBackStack() },
            )
        }
        composable("about") {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
