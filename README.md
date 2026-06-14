# 中国象棋 Xiangqi

一个支持 Android 11+ 的中国象棋(象棋)人机对战 App。基于 Jetpack Compose + Hilt,内置自研简化引擎与皮卡鱼(Pikafish)UCI 引擎,支持难度选择、悔棋、提示、局势分析等对战功能。

## 状态

🚧 开发中 —— 当前处于 **M0 工程化基线** 阶段。详见 [doc/roadmap.md](doc/roadmap.md)。

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
