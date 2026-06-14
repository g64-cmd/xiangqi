# 构建环境

> 本文件描述项目的工具链版本、常用命令、本地配置说明。后续如有变更需同步更新。

## 工具链版本(M0 基线)

| 工具 | 版本 | 备注 |
|---|---|---|
| Android Gradle Plugin (AGP) | 9.2.1 | 在 `gradle/libs.versions.toml` 中声明 |
| Kotlin | 2.2.10 | 同上 |
| Gradle Wrapper | 9.5.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Compose BOM | 2026.02.01 | 统一管理 Compose 库版本 |
| JDK | 21(daemon)/ 11(源码与目标兼容) | `gradle/gradle-daemon-jvm.properties` 与 `app/build.gradle.kts` 的 `compileOptions` |
| Android SDK | compileSdk=36 (minorApiLevel=1), targetSdk=36, minSdk=30 | Android 11+ |

## 常用命令

使用 Gradle wrapper(Windows `gradlew.bat`,其他平台 `./gradlew`):

| 命令 | 作用 |
|---|---|
| `./gradlew :app:assembleDebug` | 构建 Debug APK |
| `./gradlew :app:installDebug` | 安装到已连接设备/模拟器 |
| `./gradlew :app:testDebugUnitTest` | 运行 JVM 单元测试(`app/src/test/`) |
| `./gradlew :app:testDebugUnitTest --tests "com.xiangqi.app.domain.*"` | 运行指定测试类/包 |
| `./gradlew :app:connectedDebugAndroidTest` | 运行插桩测试(`app/src/androidTest/`,需设备/模拟器) |
| `./gradlew :app:lintDebug` | Lint 检查 |
| `./gradlew :app:assembleRelease` | 构建 Release APK(需签名配置) |
| `./gradlew clean` | 清理 |

## local.properties

`local.properties`(已 gitignore)由本机生成,内容形如:

```
sdk.dir=C\:\\Users\\<用户>\\AppData\\Local\\Android\\Sdk
```

**不要提交此文件**。CI 上通过 `android-actions/setup-android` 自动安装 SDK。

## configuration-cache

`gradle.properties` 中开启了 `org.gradle.configuration-cache=true`。若修改构建脚本后构建异常,加 `--no-configuration-cache` 跑一次以使缓存失效。

## ABI 过滤

`defaultConfig.ndk.abiFilters = ["arm64-v8a"]`。Android 11+ 主流真机为 arm64-v8a。x86_64 模拟器调试需要另起 debug-only flavor(M3+ 再考虑)。

## 已知坑

- AGP 9.2.1 是较新版本,某些第三方库插件可能尚未兼容;如遇插件冲突,优先升级第三方插件而非降级 AGP。
- Compose BOM 2026.02.01 是版本目录默认值,可能比当前实际稳定版新;遇到 API 不稳定时回退到上一个 BOM。
- Robolectric 4.x 对 SDK 36 支持不完整,Robolectric 测试统一固定 `@Config(sdk = [33])`。

## 后续待补充

- [ ] NDK r27c+ 安装路径与 `local.properties` 中 `ndk.dir` 配置(M5 准备时补充)
- [ ] 签名密钥管理流程(M7 准备时补充)
- [ ] ProGuard / R8 keep 规则(M7 准备时补充)
