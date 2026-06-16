# 中国象棋 Xiangqi

一个支持 Android 11+ 的中国象棋(象棋)人机对战 App。基于 Jetpack Compose + Hilt,内置自研简化引擎与皮卡鱼(Pikafish)UCI 引擎,支持难度选择、悔棋、提示、局势分析等对战功能。

## 状态

🚧 开发中 —— 当前处于 **M6 高级对战功能** 完成阶段(提示 / 求和 / 局势分析曲线)。详见 [doc/roadmap.md](doc/roadmap.md)。

## 功能规划

- 人机对战(暂不含双人对战 / 联机 / 棋谱库)
- 玩家选择执红 / 执黑
- 4 档难度:新手 / 初级 / 中级 / 高级
- 双引擎:自研 Minimax+AB(低难度)+ 皮卡鱼(中高难度)
- 对战辅助:悔棋、重开、求和、提示、局势分析
- 中国风视觉设计

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

## 准备皮卡鱼引擎二进制(必读)

App 的"皮卡鱼引擎"模式需要在 `app/src/main/assets/pikafish/` 放置两个二进制文件:

```
app/src/main/assets/pikafish/
├── pikafish           # arm64-v8a 可执行文件(~1.77 MB)
└── pikafish.nnue      # NNUE 网络权重(~50.7 MB)
```

这两个文件因体积过大被 `.gitignore` 忽略,**不会随 git clone 自动获取**。第一次构建前请手动准备:

1. 从 [official-pikafish/Pikafish Releases](https://github.com/official-pikafish/Pikafish/releases) 下载最新 release 包(当前使用 2026.01.31)
2. 解压后:
   - 把 `Android/pikafish-armv8` 复制为 `app/src/main/assets/pikafish/pikafish`
   - 把 `pikafish.nnue` 复制为 `app/src/main/assets/pikafish/pikafish.nnue`
3. 确认 SHA-256 与 `app/build.gradle.kts` 中 `PIKAFISH_SHA` / `PIKAFISH_NNUE_SHA` 一致;若不一致,Installer 会自动校验失败并拒绝启动

未准备这两个文件时,App 仍可构建、运行,但**人机模式选"皮卡鱼"开局后会抛 IOException**;选"自研引擎"则不受影响。

详见 [doc/engine-integration.md](doc/engine-integration.md)。

## 目录结构

```
xiangqi/
├── app/                    # Android 应用主模块
│   └── src/
│       ├── main/           # 主源码
│       ├── test/           # JVM 单元测试
│       └── androidTest/    # 插桩测试
├── doc/                    # 项目文档(架构、构建环境、CI、约定等)
├── gradle/                 # 版本目录 + wrapper
└── .github/workflows/      # GitHub Actions CI 配置
```

详细架构见 [doc/architecture.md](doc/architecture.md)。

## 文档

| 文件 | 用途 |
|---|---|
| [doc/roadmap.md](doc/roadmap.md) | 里程碑路线图 |
| [doc/architecture.md](doc/architecture.md) | 架构与分层 |
| [doc/build-environment.md](doc/build-environment.md) | 构建环境与命令 |
| [doc/engine-integration.md](doc/engine-integration.md) | 引擎集成方案 |
| [doc/testing.md](doc/testing.md) | 测试约定 |
| [doc/ci.md](doc/ci.md) | CI 配置 |
| [doc/conventions.md](doc/conventions.md) | 代码与提交约定 |
| [doc/release.md](doc/release.md) | 发布流程 |
| [doc/dev-log.md](doc/dev-log.md) | 开发日志 |

## 致谢

- [Pikafish](https://github.com/official-pikafish/Pikafish) —— 强大的开源中国象棋 UCI 引擎(GPL-3.0)
- Jetpack Compose、Hilt 等 Android Jetpack 库

## License

GPL-3.0,详见 [LICENSE](LICENSE)。
