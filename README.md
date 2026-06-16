# 中国象棋 Xiangqi

一个支持 Android 11+ 的中国象棋(象棋)App。基于 Jetpack Compose + Hilt,内置自研
简化引擎与皮卡鱼(Pikafish)UCI 引擎,支持人机对战 / 双人本地对战,以及难度选择、
悔棋、提示、求和、局势分析曲线等对战功能。

## 状态

🚧 开发中 —— **M0-M6 已合并**,当前处于持续修复与验证阶段(根据真机反馈修 bug +
完善 UX)。详见 [doc/roadmap.md](doc/roadmap.md)。

最近合并的 fix(PR #10):
- 皮卡鱼 SELinux `execute_no_trans` 拒绝 → ELF 迁到 jniLibs/libpikafish.so
- 引擎崩溃让 app 闪退 → EngineUnavailableException + toast 降级
- 走子点击位置错位 → `.toInt()` 改 `.roundToInt()`
- 走子动画状态机卡死导致双炮渲染 → Animatable 重写

## 功能

- **人机对战**(HUMAN_VS_AI)+ **双人本地对战**(HOT_SEAT)
- 玩家选择执红 / 执黑 + 屏幕方向(orientation)
- 4 档难度:新手 / 初级 / 中级 / 高级
- **双引擎**:
  - 自研 Minimax + Alpha-Beta + Zobrist TT + QSearch(纯 Kotlin,低难度档)
  - 皮卡鱼 Pikafish(UCI 子进程,中高难度档 + Hint + 局势分析 NNUE eval)
- **对战辅助**:悔棋、重开、认输、**求和**(HOT_SEAT 直接和 / HUMAN_VS_AI 引擎
  评估均势接受)、**提示**(半透明箭头显示建议走法)、**局势分析曲线**
  (Compose Canvas 自绘折线,红方视角 cp 序列)
- 中国风视觉设计(木纹背景 / 朱砂红 / 墨黑)

## 技术栈

| 项 | 版本 |
|---|---|
| AGP | 9.2.1 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| Gradle Wrapper | 9.5.0 |
| minSdk | 30(Android 11) |
| targetSdk | 36 |
| Java source/target | 11 |
| Gradle daemon JDK | 21 |
| ABI | arm64-v8a |

## 构建命令

使用 Gradle wrapper(Windows `gradlew.bat`,其他平台 `./gradlew`):

```bash
./gradlew :app:assembleDebug           # 构建 Debug APK
./gradlew :app:installDebug             # 安装到设备/模拟器
./gradlew :app:testDebugUnitTest        # JVM 单元测试
./gradlew :app:connectedDebugAndroidTest # 插桩测试(需设备/模拟器)
./gradlew :app:lintDebug                # Lint 检查
./gradlew clean                         # 清理
```

详见 [doc/build-environment.md](doc/build-environment.md)。

## 准备皮卡鱼 NNUE 权重(必读)

App 的"皮卡鱼引擎"模式需要 NNUE 网络权重文件:

```
app/src/main/assets/pikafish/
└── pikafish.nnue      # NNUE 网络权重(~50.7 MB)
```

> **可执行文件** `libpikafish.so` 已随仓库分发(`app/src/main/jniLibs/arm64-v8a/`),
> 无需本地准备。AGP 在 APK 安装时自动解压到 `nativeLibraryDir`。
>
> **NNUE 权重**因体积过大被 `.gitignore` 忽略,**不会随 git clone 自动获取**,
> 首次构建前请手动准备:

1. 从 [official-pikafish/Pikafish Releases](https://github.com/official-pikafish/Pikafish/releases)
   下载最新 release 包(当前使用 2026.01.31)
2. 解压后把 `pikafish.nnue` 复制为 `app/src/main/assets/pikafish/pikafish.nnue`
3. 确认 SHA-256 与 `app/build.gradle.kts` 中 `PIKAFISH_NNUE_SHA` 一致;若不一致,
   Installer 会自动校验失败并拒绝启动

未准备 NNUE 文件时,App 可构建、运行,但**人机模式选"皮卡鱼"开局后引擎启动失败,
弹 toast 提示"引擎不可用"**;选"自研引擎"则不受影响。

> **关于 SELinux**:Android 10+ 禁止从 `app_data_file` 域 exec 二进制,因此
> 可执行文件不能放在 `filesDir`,必须放在 `nativeLibraryDir`(属于 `apk_data_file`
> 域,允许 exec)。详见 [doc/engine-integration.md](doc/engine-integration.md)。

## 模拟器与真机

- **arm64 真机**:皮卡鱼引擎原生跑,所有功能正常。
- **x86_64 模拟器**:通过 `ndk_translation` 翻译 arm64 ELF 跑 pikafish 仍会
  SIGSEGV(NNUE 加载/SIMD 翻译不稳定);app **不会闪退**,弹 toast 提示。
  要在 x86_64 模拟器上原生跑皮卡鱼,需加 x86_64 ABI 的 pikafish 二进制(M7 待办)。
- 自研引擎在所有环境正常。

## 目录结构

```
xiangqi/
├── app/                              # Android 应用主模块
│   └── src/
│       ├── main/
│       │   ├── java/com/xiangqi/app/
│       │   │   ├── ui/               # Compose 屏幕 + ViewModel + UiState
│       │   │   ├── data/             # GameRepository / SettingsRepository
│       │   │   ├── domain/           # 纯 Kotlin:模型 / 走法 / 规则 / FEN
│       │   │   ├── engine/           # Engine 接口 + SelfEngine + PikafishEngine
│       │   │   └── di/               # Hilt GameModule + Qualifiers
│       │   ├── assets/pikafish/      # pikafish.nnue(本地准备)
│       │   ├── jniLibs/arm64-v8a/    # libpikafish.so(随仓库分发)
│       │   └── AndroidManifest.xml
│       ├── test/                     # JVM 单元测试
│       └── androidTest/              # 插桩测试
├── doc/                              # 项目文档
├── gradle/                           # 版本目录 + wrapper
└── .github/workflows/                # GitHub Actions CI
```

详细架构见 [doc/architecture.md](doc/architecture.md)。

## 文档

| 文件 | 用途 |
|---|---|
| [doc/roadmap.md](doc/roadmap.md) | 里程碑路线图 |
| [doc/architecture.md](doc/architecture.md) | 架构与分层 |
| [doc/build-environment.md](doc/build-environment.md) | 构建环境与命令 |
| [doc/engine-integration.md](doc/engine-integration.md) | 引擎集成方案(SELinux jniLibs 路径 / UCI 协议 / 难度映射) |
| [doc/testing.md](doc/testing.md) | 测试约定 |
| [doc/ci.md](doc/ci.md) | CI 配置 |
| [doc/conventions.md](doc/conventions.md) | 代码与提交约定 |
| [doc/release.md](doc/release.md) | 发布流程(M7 占位) |
| [doc/dev-log.md](doc/dev-log.md) | 开发日志 |

## 致谢

- [Pikafish](https://github.com/official-pikafish/Pikafish) —— 强大的开源中国象棋
  UCI 引擎(GPL-3.0)
- [chinese-chess-fish-android](https://github.com/zfdang/chinese-chess-fish-android) ——
  Android 上 jniLibs 改名方案的参考实现
- Jetpack Compose、Hilt 等 Android Jetpack 库

## License

GPL-3.0,详见 [LICENSE](LICENSE)。App 整体 GPL-3.0 是因使用 Pikafish(GPL-3.0)。
