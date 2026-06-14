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
- **androidx.core-ktx 降级**:1.19.0 要求 compileSdk 37,本机 SDK 暂只到 36.1;降级到 1.16.0 以维持 compileSdk=36。后续升 SDK 时一并升级。

**验证**
- `./gradlew :app:testDebugUnitTest` ✅(1 个 smoke test 通过)
- `./gradlew :app:lintDebug` ✅
- `./gradlew :app:assembleDebug` ✅
- CI workflow 已就绪(尚未首次触发,等 PR 创建后由 GitHub Actions 实跑)

**备注**
- 真机启动验证待用户在合并 PR 后做(`./gradlew :app:installDebug`)
- M1 启动前需先在 GitHub 上确认 CI 真跑通过

---

## 2026-06-15 — Hilt + KSP 接入(分支 feature/M0-hilt-retry)

**背景**
最初尝试用 Hilt 2.57 + KSP 接入,但 Hilt 2.57 的 Gradle 插件初始化时执行 `project.extensions.getByType(BaseExtension::class.java)`,而 `BaseExtension` 已被 AGP 9 移除,导致 `Android BaseExtension not found`。当时选择把 Hilt 暂缓到独立 PR。后确认 **Hilt 2.59.2(2026-02 发布)** 已迁移到 AGP 新 Variant API,可兼容 AGP 9。

**改动**
- `libs.versions.toml`:`hilt = "2.59.2"`
- 根 `build.gradle.kts`:启用 `hilt`、`ksp` 插件 `apply false`
- `app/build.gradle.kts`:启用 `hilt`、`ksp` 插件,加 `implementation(hilt-android)` + `ksp(hilt-compiler)` + `implementation(hilt-navigation-compose)`,androidTest 加 `hilt-android-testing`
- `XiangqiApplication`:加 `@HiltAndroidApp`
- `MainActivity`:加 `@AndroidEntryPoint`
- `gradle.properties`:加 `android.disallowKotlinSourceSets=false`,绕开 AGP 9 内置 Kotlin + KSP 的 `kotlin.sourceSets` 冲突。详见 https://developer.android.com/r/tools/built-in-kotlin

**验证**
- `./gradlew :app:testDebugUnitTest` ✅(`kspDebugKotlin`、`hiltSyncDebug`、`hiltAggregateDepsDebug`、`hiltJavaCompileDebug`、`transformDebugClassesWithAsm` 等任务全部通过)
- `./gradlew :app:lintDebug` ✅
- `./gradlew :app:assembleDebug` ✅

**备注**
- `android.disallowKotlinSourceSets=false` 是临时 workaround,等 KSP 完全适配 AGP 9 内置 Kotlin 后可移除
- 后续每个 ViewModel 用 `@HiltViewModel` 注解,Repository 用 `@Inject constructor` 注解,EngineModule 提供 SelfEngine / PikafishEngine 绑定

---

## 2026-06-15 — M1 领域模型与规则引擎完成(分支 feature/M1)

**改动**
- `domain/model/`:Piece / Side / Position(value class) / Move / Board / GameResult。
  棋盘 9×10,row-major packed Int,坐标约定 row 0..9 直接对应 UCI 行字符
  (与 xqbase / Pikafish 主流规范一致:红方 row=0–4,黑方 row=5–9)。
- `domain/fen/`:FenParser + FenPosition,严格 round-trip。
- `domain/movegen/`:MoveGeneratorImpl 实现 7 种棋子伪合法走法,
  含蹩马腿 / 塞象眼 / 翻山 / 过河横移。
- `domain/rules/`:CheckDetector(将军+飞将)、MoveLegality(走完后己方不被将)、
  CheckmateDetector(将死 / 困毙判负 / decide 返回 GameResult)。

**验证**
- `./gradlew :app:testDebugUnitTest` ✅ 76 个测试全绿
- **Perft depth 1/2/3 = 44 / 1920 / 79666**,与开源中国象棋参考值完全对账一致
  (已记入 `doc/testing.md` 的 Perft 表)

**备注**
- 设计上把"伪合法走法"和"合法性过滤"分离,Perft 内部用 MoveLegality 过滤
- 自定义杀局 FEN 容易因人工算错格数出错,最终保留了一个简单可靠的双车夹角杀局
  + 生成式 sanity 检查(开局任一红走法走完后黑方仍 ONGOING)
- M1 不依赖任何 Android API,完全 JVM 可测;为 M2 自研搜索引擎打下基础

---

<!-- 后续记录在此行上方,保持倒序 -->
