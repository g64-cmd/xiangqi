# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指引。

## 项目

`xiangqi` 是一个 Android 应用（中国象棋 / 象棋），当前为 Android Studio 生成的全新 Jetpack Compose 脚手架。单 Gradle 模块（`:app`），包名 `com.example.xiangqi`。

工具链：AGP 9.2.1、Kotlin 2.2.10、Compose BOM 2026.02.01、Gradle 9.5.0 wrapper、JDK 21 daemon、`JavaVersion.VERSION_11` 源码/目标。`minSdk=30`、`targetSdk=36`、`compileSdk=36`（minor API level 1）。Release 构建设置了 `optimization.enable=false`（默认不做 R8 代码压缩）。

## 常用命令

使用 Gradle wrapper（Windows 下为 `gradlew.bat`，其他平台为 `./gradlew`）：

- 构建 debug APK：`./gradlew :app:assembleDebug`
- 安装到已连接设备/模拟器：`./gradlew :app:installDebug`
- 运行 JVM 单元测试（`app/src/test/`）：`./gradlew :app:testDebugUnitTest`
- 运行单个测试类：`./gradlew :app:testDebugUnitTest --tests "com.example.xiangqi.ExampleUnitTest"`
- 运行插桩测试（`app/src/androidTest/`，需连接设备/模拟器）：`./gradlew :app:connectedDebugAndroidTest`
- Lint 检查：`./gradlew :app:lintDebug`
- 清理：`./gradlew clean`

`gradle.properties` 中开启了 `org.gradle.configuration-cache=true` —— 若在修改构建脚本或设置后构建失败，可加 `--no-configuration-cache` 跑一次以使缓存失效。

`local.properties`（已 gitignore，由本机生成）指向 Android SDK 路径（`sdk.dir`）—— 不要提交。

## 架构

标准的单模块 Android Compose 应用。入口：

- `app/src/main/AndroidManifest.xml` —— 声明 `MainActivity` 为启动 Activity，主题 `@style/Theme.Xiangqi`。
- `app/src/main/java/com/example/xiangqi/MainActivity.kt` —— `ComponentActivity`，使用 `enableEdgeToEdge()` + `setContent { XiangqiTheme { Scaffold { Greeting(...) } } }`。将 `Greeting` 替换为真实的应用 UI 即可。
- `app/src/main/java/com/example/xiangqi/ui/theme/` —— `Theme.kt`（`XiangqiTheme` 支持 Material 3，并在 Android 12+ 上启用动态取色）、`Color.kt`、`Type.kt`。

版本目录（version catalog）：`gradle/libs.versions.toml` —— 所有依赖版本与库/插件别名均在此定义；在 `build.gradle.kts` 中通过 `libs.*` 引用，不要硬编码版本号。

R8 keep 规则：`app/src/main/keepRules/rules.keep` 会被 AGP 合并进 R8 配置 —— 引入反射、JNI 等需要保留的场景时，在此处添加项目专属的 keep 规则。
