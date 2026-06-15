package com.xiangqi.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiangqi.app.ui.game.GameScreen
import com.xiangqi.app.ui.setup.SetupScreen

/**
 * 应用顶层导航图。两个目的地:
 * - "setup":开局设置(模式 / 执棋方 / 难度)
 * - "game":对局棋盘
 *
 * 起始目的地 = "setup"。SetupScreen "开始对局"时把配置写入 GameConfigHolder(单例),
 * 然后 navigate("game")。GameViewModel 通过 Hilt 注入同一个 Holder 读取配置。
 *
 * GameScreen "返回"调用 `navController.popBackStack()` 回 Setup。
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
            )
        }
        composable("game") {
            GameScreen(
                onExit = { navController.popBackStack() },
            )
        }
    }
}
