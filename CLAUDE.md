# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指引。

## 项目

`xiangqi` 是中国象棋 Android 应用。单 Gradle 模块(`:app`),包名 `com.xiangqi.app`。
基于 Jetpack Compose + Hilt + Navigation Compose,内置两个引擎:自研 Negamax+AB
(低难度)和皮卡鱼 Pikafish(高难度,UCI 协议子进程)。

工具链:AGP 9.2.1、Kotlin 2.2.10、Compose BOM 2026.02.01、Gradle 9.5.0 wrapper、
JDK 21 daemon、`JavaVersion.VERSION_11` 源码/目标。`minSdk=30`、`targetSdk=36`、
`compileSdk=36`(minor API level 1)。Release 构建设置 `optimization.enable=false`
(默认不做 R8 代码压缩,M7 启用)。

## 当前进展

里程碑路线见 [doc/roadmap.md](doc/roadmap.md)。
M0-M6 已合并到 main,M7(打磨与发布:R8 / 图标 / 签名 / Release)未启动。

当前处于**持续修复与验证阶段**:根据真机反馈修 bug + 完善 UX,**不预先 plan**,
保持细颗粒度 commit。

## 常用命令

使用 Gradle wrapper(Windows `gradlew.bat`,其他平台 `./gradlew`):

| 命令 | 用途 |
|---|---|
| `./gradlew :app:assembleDebug` | 构建 Debug APK |
| `./gradlew :app:installDebug` | 安装到设备/模拟器 |
| `./gradlew :app:testDebugUnitTest` | JVM 单元测试(`app/src/test/`) |
| `./gradlew :app:testDebugUnitTest --tests "com.xiangqi.app.ui.game.*"` | 按包/类过滤测试 |
| `./gradlew :app:connectedDebugAndroidTest` | 插桩测试(需设备/模拟器) |
| `./gradlew :app:lintDebug` | Lint 检查 |
| `./gradlew :app:compileDebugKotlin` | 快速类型检查(不发 APK) |
| `./gradlew clean` | 清理 |

`gradle.properties` 中开启 `org.gradle.configuration-cache=true`。若修改构建脚本后
构建异常,加 `--no-configuration-cache` 跑一次使缓存失效。

`local.properties`(已 gitignore)指向本机 Android SDK 路径(`sdk.dir`)—— 不要提交。

## 真机调试

无线 / USB adb:`adb devices`、`adb install -r app-debug.apk`、`adb logcat` 抓崩溃。

- **皮卡鱼引擎**:APK 安装时 AGP 把 `libpikafish.so` 解压到 `nativeLibraryDir`
  (`apk_data_file` SELinux 域,允许 exec);NNUE 权重 `pikafish.nnue` 仍走
  `assets → filesDir`(本地需准备,见 README)。**模拟器**:x86_64 AVD 通过
  ndk_translation 跑 arm64 ELF 仍会 SIGSEGV(已知),用 arm64 镜像或真机测。

## 架构

单 Activity(`MainActivity`)+ Navigation Compose + Hilt。

### 分层

```
ui/        Compose 屏幕 + ViewModel + UiState
data/      GameRepository / SettingsRepository(协调 domain 与 engine)
domain/    纯 Kotlin,无 Android 依赖(模型、走法、规则、FEN)
engine/    Engine 接口 + SelfEngine(纯 Kotlin)+ PikafishEngine(子进程 UCI)
```

关键约束:
- `domain/` 不得 import `android.*`(可在纯 JVM 单测覆盖)。
- `Engine` 接口在顶层,`SelfEngine` 和 `PikafishEngine` 实现它;`EngineProvider`
  按 `EngineType` 切换。
- 单引擎实例**不支持并发**(`PikafishEngine` 缓存 UCI session,UCI 单 stdin/stdout
  不可重入)。`GameViewModel.launchEngine` 是所有手动 engine 入口(AI 应招 / Hint /
  求和评估 / Analysis)的统一序列化通道;auto-eval 是唯一例外,跑独立协程。

### 关键模块

- `ui/game/GameViewModel.kt` —— 状态机核心,持有 `_isEngineBusy` / `_selected` /
  `_suggestedMove` / `_evalHistory` / `_toast` 等;所有 engine 入口通过
  `launchEngine` + `requireEngineIdle`。
- `engine/pikafish/PikafishEngine.kt` —— UCI 协议封装,`search` 发 ucinewgame+
  setoption+go movetime,`analyze` 发 eval 命令(NNUE 静态评估)。
- `engine/pikafish/PikafishInstaller.kt` —— NNUE 从 assets 复制到 filesDir,
  SHA-256 校验;可执行路径读 `applicationInfo.nativeLibraryDir/libpikafish.so`。
- `ui/components/BoardCanvas.kt` —— Canvas 棋盘,`detectTapGestures` 给出
  raw offset,坐标映射用 `roundToInt`(不是 toInt)找最近交叉点。

### 数据流

```
用户点击 → ViewModel.onTap(Position) → repo.applyMove(Move)
                                      → uiState 更新 → Compose 重组
                                      → (人机模式)launchAiMove → engine.search → repo.applyMove(aiMove)
                                      → maybeAutoEval → engine.analyze → TopBar currentScore
```

## 依赖注入

Hilt:
- `@HiltAndroidApp XiangqiApplication` —— 入口
- `@AndroidEntryPoint MainActivity`
- `@HiltViewModel` —— 每个 ViewModel
- `@Module @InstallIn(SingletonComponent::class) object GameModule` —— 提供
  SelfEngine / PikafishEngine / EngineProvider;Repository / Installer 通过
  `@Inject constructor` 自动注入。

## 线程模型

| Dispatcher | 用途 |
|---|---|
| `Dispatchers.Main` | UI 重组、ViewModel 事件入口 |
| `engineDispatcher`(默认 `Dispatchers.Default`) | 引擎搜索 / analyze;`@VisibleForTesting` 可注入便于单测 |
| `Dispatchers.IO` | 文件读写、子进程 IO(皮卡鱼 stdin/stdout) |

## 版本目录与构建配置

- `gradle/libs.versions.toml` —— 所有依赖版本与库/插件别名;在 `build.gradle.kts`
  中通过 `libs.*` 引用,**不要硬编码版本号**。
- `app/build.gradle.kts` —— `defaultConfig.ndk.abiFilters = ["arm64-v8a"]`;
  `packaging.jniLibs.useLegacyPackaging = true`(让 AGP 把 .so 解压到
  nativeLibraryDir,皮卡鱼 ProcessBuilder 才能拿到真实文件路径);BuildConfig
  含 `PIKAFISH_SHA` / `PIKAFISH_NNUE_SHA` / `PIKAFISH_VERSION`。
- `AndroidManifest.xml` —— `application.extractNativeLibs="true"`(与 Gradle
  useLegacyPackaging 双保险)。

## R8 keep 规则

`app/src/main/keepRules/rules.keep` 会被 AGP 合并进 R8 配置。当前 release 未开 R8
(`optimization.enable=false`),M7 启用压缩时需要为 Hilt 生成的类、反射用例、
JNI native 方法签名添加 keep 规则。

## 文档

| 文件 | 用途 |
|---|---|
| [doc/roadmap.md](doc/roadmap.md) | 里程碑路线图 |
| [doc/architecture.md](doc/architecture.md) | 架构与分层 |
| [doc/build-environment.md](doc/build-environment.md) | 构建环境与命令 |
| [doc/engine-integration.md](doc/engine-integration.md) | 引擎集成方案(SELinux jniLibs 路径、UCI 协议、难度映射) |
| [doc/testing.md](doc/testing.md) | 测试约定 |
| [doc/ci.md](doc/ci.md) | CI 配置 |
| [doc/conventions.md](doc/conventions.md) | 代码与提交约定 |
| [doc/release.md](doc/release.md) | 发布流程(M7 占位) |
| [doc/dev-log.md](doc/dev-log.md) | 开发日志 |

## 提交约定

见 [doc/conventions.md](doc/conventions.md)。要点:

- Conventional Commits:`feat(M6): ...`、`fix(pikafish): ...`、`refactor(ui): ...`
- 单 commit 净变更 ≤ ~250 行(允许例外)
- 一个 commit 只做一件事
- 中文本项目用户偏好中文 commit message + 中文注释(KDoc)

## License

GPL-3.0,因使用 Pikafish(GPL-3.0)。详见 [LICENSE](LICENSE) 与
[doc/engine-integration.md](doc/engine-integration.md#license-与-gpl-合规)。
