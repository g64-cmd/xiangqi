# 开发日志

本文件按时间倒序记录每次主要变更。每条记录包含五栏:**日期 / commit / 改动 / 验证 / 备注**。

---

## 模板

```
### YYYY-MM-DD — 主题(commit: <短 hash 或 描述>)

**改动**
- ...

**验证**
- ...

**备注**
- ...
```

---

## 2026-06-15 — 项目初始化(commit: 3cea5fc)

**改动**
- 生成 Android Studio 默认脚手架(AGP 9.2.1 + Kotlin 2.2.10 + Compose BOM 2026.02.01)
- 配置 .gitignore(忽略 .idea/、build/、.gradle/、local.properties、签名文件等)
- 添加 GPL-3.0 LICENSE

**验证**
- `git status` 工作树干净
- 仅有一条初始化提交

**备注**
- 后续将按 M0–M7 路线图推进

---

## 2026-06-15 — M0 工程化基线完成(分支 feature/M0-baseline)

**改动**
- 创建 `doc/` 文档骨架:roadmap、dev-log、build-environment、architecture、engine-integration、ci、conventions、testing、release,共 9 份
- 完善 `.gitignore`:忽略 `*.nnue`、`/pikafish-build/`、`/pikafish-bin/`、`/app/src/main/assets/pikafish/`、`/.claude/`
- 扩展 `gradle/libs.versions.toml`:登记 Hilt、Navigation Compose、DataStore Preferences、Robolectric、Truth、MockK、kotlinx-coroutines-test、KSP 等
- **包名重命名**:`com.example.xiangqi` → `com.xiangqi.app`(目录树 + namespace + applicationId)
- 删除脚手架 `ExampleUnitTest` / `ExampleInstrumentedTest`,替换为 `SmokeTest`(JVM)与 `PackageNameTest`(插桩)
- 改造主题为中国风配色(Cinnabar 朱红、Ink Black 墨黑、Wood 木黄、Paper Cream 米白)
- `MainActivity` 改为 `XiangqiApplication` + 占位 UI(中国象棋标题)
- `app/build.gradle.kts`:加 `buildConfig=true`、`ndk.abiFilters="arm64-v8a"`、`testOptions.unitTests.isIncludeAndroidResources=true`
- 创建 `.github/workflows/ci.yml`:三个并行 job(unit-test / lint / build),JDK 21 temurin,`gradle/actions/setup-gradle@v4`,失败时上传报告 artifact
- 重写 `README.md`:项目状态、技术栈、构建命令、目录结构、文档导航

**关键调整(偏离原计划)**
- **Hilt + KSP 暂缓接入**:Hilt 2.57 的 Gradle 插件依赖已被 AGP 9 移除的 `BaseExtension`(报错 `Android BaseExtension not found`)。等 Dagger 发布兼容 AGP 9 的稳定版后,单开一个 PR 接入。已写注释占位。
- **androidx.core-ktx 降级**:1.19.0 要求 compileSdk 37,本机 SDK 暂只到 36.1;降级到 1.16.0 以维持 compileSdk=36。后续升 SDK 时一并升级。

**验证**
- `./gradlew :app:testDebugUnitTest` ✅(1 个 smoke test 通过)
- `./gradlew :app:lintDebug` ✅
- `./gradlew :app:assembleDebug` ✅
- CI workflow 已就绪(尚未首次触发,等 PR 创建后由 GitHub Actions 实跑)

**备注**
- 真机启动验证待用户在合并 PR 后做(`./gradlew :app:installDebug`)
- Hilt 接入涉及后续每个 ViewModel / Repository 的 DI 改造,等独立 PR 时统一推进
- M1 启动前需先在 GitHub 上确认 CI 真跑通过

---

<!-- 后续记录在此行上方,保持倒序 -->
