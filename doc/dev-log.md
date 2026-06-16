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

## 2026-06-17 — 皮卡鱼 SELinux / 引擎降级 / 点击 round 修复(分支 fix/pikafish-selinux-execute,PR #10)

**改动**
- **走子动画状态机 bug 修复**(commit 8416deb):`animProgressTarget` 设为 0
  后再没推回 1,导致 progress 永远停在 0,`computeAnimation` 一直返回非 null,
  drawPieces 跳过 lastMove.from + drawAnimationOverlay 在 from 画动画棋子 +
  drawPieces 在 to 画已落子棋子 = 视觉双炮 + 后续点击错位。改用
  `Animatable.snapTo(0f).animateTo(1f, 200ms)`,动画完成后 progress>=1f
  自动归位
- **皮卡鱼二进制 SELinux `execute_no_trans` 拒绝**:
  Android 10+ (API 29+) SELinux 禁止从 `app_data_file` 域 exec 二进制,
  `filesDir/pikafish/bin/pikafish` 启动失败。参考 chinese-chess-fish-android 实践,
  把 pikafish ELF 改名为 `libpikafish.so` 迁到 `app/src/main/jniLibs/arm64-v8a/`,
  AGP 在 APK 安装时解压到 `nativeLibraryDir`(`apk_data_file` 域,允许 exec)。
  Gradle `packaging.jniLibs.useLegacyPackaging=true` + Manifest
  `extractNativeLibs="true"` 双保险
- **PikafishInstaller 重构**:删除 ELF assets→filesDir 复制逻辑,改为读
  `applicationInfo.nativeLibraryDir/libpikafish.so`;新增 `verifyExecutable()`
  校验存在 + 可执行 + SHA;`Install` data class `executable: File` 改为
  `executablePath: String`;NNUE 仍走 assets→filesDir(数据文件无 SELinux 问题)
- **PikafishEngine.ensureSession**:`install.executablePath` 替代 `executable`,
  启动后发 `setoption name EvalFile value <绝对路径>` 显式传 NNUE
- **PikafishProcess**:构造参数 `File executable` → `String executablePath`
  (ProcessBuilder 接收 String,内部不变)
- **引擎崩溃降级**:新增 `EngineUnavailableException`(RuntimeException 子类)。
  PikafishEngine.ensureSession 把 IOException / IllegalStateException 包装成
  EngineUnavailableException;`search` "未返回 bestmove" 也改抛 EngineUnavailableException。
  GameViewModel.launchEngine 增加 catch EngineUnavailableException → emit toast
  "引擎不可用:<原因>",不闪退,棋盘状态不变
- **点击坐标映射 round**:`((offset-margin)/cell).toInt()` 用 floor 截断,边界
  附近(0.7..0.9)会取下界,用户精准点交叉点偶尔命中上一格。改用 `roundToInt`
  找最近交叉点。真机 logcat 验证:用户点 (627, 1316) 原本算 viewRow=6(实际距
  viewRow=7 中心 28.8px,距 viewRow=6 中心 104.5px),改 round 后 viewRow=7

**关键决策**
- **方案选 lib/ 改名而非 JNI 嵌入**:象棋鱼已验证 lib/ 改名是业界主流,1 天
  工作量 vs JNI 3-5 天,且保留崩溃隔离(pikafish 崩 ≠ app 崩)。plan 末尾留
  JNI 迁移入口,未来 targetSdk 进一步提升时考虑
- **EngineUnavailableException 用 RuntimeException 而非 checked**:引擎崩溃是
  运行时故障,调用方无法通过类型签名预知;但 ViewModel 显式 try-catch 降级
- **catch 在 launchEngine 而非 PikafishEngine 内部**:让降级策略集中在
  ViewModel 层,Engine 实现保持"失败即抛异常"的语义清晰

**验证**
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 三 job 全绿
- 新增测试:GameViewModelEngineFailureTest(2)+ PikafishInstallerTest
  verifyExecutable 通过/失败 2 个 case
- 真机(Honor arm64)实测:皮卡鱼走子 + AI 应招 + TopBar 分数显示都正常
- 模拟器(x86_64 + ndk_translation):pikafish ELF 仍 SIGSEGV,但 app 不闪退,
  弹 toast"引擎不可用"
- 真机点击体验:精准点交叉点和边界附近都能稳定命中视觉格点

**备注**
- 5 commits(改名 ELF / useLegacyPackaging / Installer+Engine+Process 合并 /
  异常处理 + toast / tap round)+ 1 commit CI fix(setExecutable 在 Linux 测试
  缺 +x)
- **未来待办**:
  - 16 KB page size 对齐(pikafish ELF LOAD 段重新链接到 0x4000,Google 2025-11-01
    起 targetSdk=15+ 强制)
  - 多 ABI 支持(若要支持 x86_64 模拟器原生跑,需加 x86_64 pikafish 二进制)
  - JNI 嵌入(若 lib/ 改名方案在更高 targetSdk 出问题)

---

## 2026-06-16 — M6 高级对战功能(分支 feature/M6-advanced-play)

**改动**
- 抽出 `GameViewModel.launchEngine(s, difficulty, kind, onResult)` 统一通道,
  AI 自动应招 / Hint / 求和评估 共享 `_isEngineBusy` + `aiJob` 单引擎序列化
- 新增 `Difficulty.HINT(2, 400)` 内部档(pikafishSkill=10),仅供"提示"按钮
  浅搜一次。SetupScreen 两处 `when(Difficulty)` 加 `HINT -> "提示"` 分支
- `Engine` 接口加 `analyze(board, sideToMove): AnalysisScore`(默认实现走
  `search(ELEMENTARY).score`);`PikafishEngine` 覆盖为发皮卡鱼独有的 `eval`
  命令做 NNUE 静态评估,parse `Final evaluation X.XX (white|black) side`
  提取分数 × 100 转 cp,(black side) 时取负。失败兜底 0cp
- **Hint**:`onHint` 走 HINT 浅搜,结果填入 `_suggestedMove: StateFlow<Move?>`,
  `BoardCanvas` 新增 `drawHintArrow` 画半透明 Cinnabar 箭头(from→to + 三角
  箭头)。onTap / onUndo / onRestart 清空
- **求和**:`onDrawOffer` HOT_SEAT 直接 `repo.setDraw(AGREED)`;HUMAN_VS_AI
  走 ELEMENTARY 浅搜,`|score| < DRAW_ACCEPT_CP(30)` 接受设 `_drawn`
  overlay,否则 `_toast.tryEmit("对方拒绝了求和")` 由 SnackbarHost 显示。
  GameRepository 加 `setDraw(reason)`(history 不变)
- **局势分析 双引擎适配 + 曲线**:`maybeAutoEval` 走子后自动触发 analyze,
  红方视角规范化(`sideToMove == BLACK` 时取负)。`_evalHistory` 累积 + 
  `_currentScore` 实时刷 TopBar(`formatScoreCp` -> "红方 +1.50"/"黑方 +0.80"/
  "均势")。`AnalysisDialog` 用 AlertDialog + Compose Canvas 自绘折线,
  X=ply,Y=红方视角 cp,clamp 到 ±1000,0 轴中线,折线 + 半透明填充。
  不引入 MPAndroidChart 依赖

**关键决策**
- **Hint 走 search 而非 eval**:eval 不返回 bestmove,Hint 需要 bestMove
  做箭头,所以走浅搜;局势分析是分数评估,走 eval 即时
- **auto-eval 不设 `_isEngineBusy`**:走独立协程 + `aiJob?.join()` 等 AI 应招
  完成,避免互锁。这是序列化不变量的唯一例外
- **POV 规范化**:`analyze` 返回 sideToMove 视角,`GameViewModel` 把"刚走完方
  是红方"(即 sideToMove == BLACK)的分数取负,让 `_evalHistory` 统一红方视角
- **曲线 clamp ±1000**:超出的 mate 等极端局面 clamp 到上下边界,避免折线
  压缩到一根线;后续 M7 可加 mate 点红/绿标记
- **SnackbarHost 复用 Scaffold**:GameScreen 已有 Scaffold,加 snackbarHost
  参数 + LaunchedEffect 收 `_toast`,零额外依赖

**验证**
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 三 job
  全绿
- M6 新增测试:PikafishEngineEvalParseTest(6)+ GameViewModelHintTest(6)+
  GameViewModelDrawOfferTest(4)+ GameViewModelAutoEvalTest(6)+
  GameTopBarScoreFormatTest(4)+ AnalysisDialogScoreMapperTest(6)+
  GameRepositoryTest+3 = 35 新用例
- 既有 M0-M5 测试无回归
- 真机冒烟待用户在合并 PR 后做

**备注**
- 单 PR 全部 M6 子任务,共 11 commits(launchEngine 抽取 / HINT 档 /
  Engine.analyze + eval / Hint + 箭头 / setDraw / DrawOffer / auto-eval /
  TopBar 分数 / AnalysisDialog / requireEngineIdle 统一 / 文档)
- vs 象棋鱼:我们做对了 POV 规范化(它没做)和 clamp(它没做);
  MPAndroidChart 改为 Compose Canvas 自绘,零依赖
- M7 待办:多局管理(打破 GameConfigHolder 单局限制)、复盘回放、连续分析

---

## 2026-06-16 — M5 皮卡鱼引擎集成(分支 feature/M5-pikafish)

**改动**
- 提取 Pikafish 2026.01.31 release 的 `Android/pikafish-armv8`(1.77 MB)+ `pikafish.nnue`(50.7 MB)
  到 `app/src/main/assets/pikafish/`(.gitignore 忽略,本机准备)
- 新增 `engine/pikafish/`:
  - `PikafishProcess.kt`:`ProcessBuilder` 包装,stdin/stdout/close
  - `UciSession.kt`:`Dispatchers.IO` 后台读行 + Channel,`send`/`waitFor`/`consume`
  - `PikafishInstaller.kt`:assets→filesDir 复制,SHA-256 校验,`setExecutable(true, true)`
  - `PikafishEngine.kt`:实现 `Engine` 接口,`ucinewgame`+`setoption Skill Level`+`position fen`+
    `go movetime`+`bestmove`/`info` 解析,score mate 通过 `Score.mateScore` 映射
- 新增 `engine/EngineProvider.kt`(`fun interface`)+ `di/EngineQualifiers.kt`(@SelfEngineQual/@PikafishEngineQual),
  `GameModule` 重构为按 EngineType 选择实现,GameViewModel 改注入 `EngineProvider`
- `data/game/GameConfig.kt` 加 `engineType` 字段;SetupUiState/SetupViewModel 加 `onEngineTypeChange`;
  SetupScreen 加"AI 引擎"SegmentedButton(皮卡鱼 / 自研),难度 label 按引擎切换显示 Skill vs 深度
- 新增 `ui/about/AboutScreen.kt` + NavHost `about` 路由:SetupScreen 顶部"关于"入口,
  展示 GPL-3.0 协议、Pikafish 版本、NNUE 权重授权、源码链接,满足 GPL 合规
- `app/build.gradle.kts` 加 `buildConfigField` 三项:PIKAFISH_SHA / PIKAFISH_NNUE_SHA / PIKAFISH_VERSION
- `gradle/libs.versions.toml` 加 `androidx.test:core` 别名(用于 Installer 测试)

**关键决策**
- **二进制来源**:用 release 包预编译而非 NDK 自编译,避免工具链/源码同步成本
- **测试策略**:PikafishInstaller 用 mockk 模拟 Context + AssetManager 而非 Robolectric,
  避开 Robolectric SDK 36 不支持 + android-all jar 网络下载问题;Installer 把 expectedSha
  与 assetName 改为可注入字段以便测试
- **EngineProvider 接口**:避免 Hilt 同接口两 binding 歧义,ViewModel 在 launchAiMove 时
  按 `cfg.engineType` 拿对应实现
- **UCI info 解析**:抽 `parseInfo` 静态方法,纯 JVM 测覆盖 6 用例(典型行、mate 正负、
  字段乱序、info string 跳过)

**验证**
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 三 job 全绿
- M5 新增测试:PikafishEngineParseInfoTest 6 用例 + PikafishInstallerTest 4 用例 + SetupViewModelTest +2 用例 = 12 新用例
- 既有 M0-M4 测试无回归
- 真机冒烟待用户在合并 PR 后做(`./gradlew :app:installDebug`),需先确认 assets/pikafish/
  内二进制已就位

**备注**
- M5 验证无法在 CI 自动跑(assets 不进 git);本地端到端依赖开发者本机二进制
- 引擎进程长存,App 退出时依赖 Android 进程清理;后续可加 `@PreDestroy` 显式关闭
- 单 PR 全部 M5 子任务,共 8 commits(PikafishProcess / UciSession / Installer+测试 /
  PikafishEngine+测试 / EngineProvider+GameConfig+Setup+GameViewModel / SetupScreen
  引擎开关 / AboutScreen / 文档同步)

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

## 2026-06-15 — M2 自研搜索引擎完成(分支 feature/M2)

**改动**
- `domain/eval/Evaluation.kt`:子力 + 7 兵种 PST,当前走子方视角(供 Negamax 直接用)。
  棋子价值:K=10000 / R=90 / C=45 / N=40 / A,B=20 / P=10。
- `engine/`:Engine 接口(suspend search + StateFlow<SearchInfo?>)、
  EngineResult、EngineType(SELF/PIKAFISH)、Difficulty(4 档)、Score、SearchInfo。
- `engine/self/`:
  - TranspositionTable:64-bit Zobrist(90×14 棋子键 + 走子方键),
    固定种子,always-replace,256K 槽。storeMateScore/retrieveMateScore 做 ply 视角转换。
  - MoveOrdering:TT move > 吃子(CAPTURE_BASE + MVV-LVA)> 将军走法。
  - QuiescenceSearch:depth=0 后展开吃子走法,最大 6 ply,解决地平线效应。
  - Search:Negamax + Alpha-Beta + TT probe/write + QSearch 叶节点。
  - SelfEngine:迭代加深 + 协程取消(coroutineContext[Job].isActive)+ movetime
    (deadline 检查),超时回退上次完整深度结果。
- 依赖:加 `kotlinx-coroutines-core`(M1 仅有 -test)。

**关键决策**
- **Zobrist** 优先于 `Board.hashCode()`:50 行换 64-bit 无碰撞 + ~5x hash 速度。
- **不可变 Board 沿用 M1**:M2 沿用 applyMove(copyOf 90 元素),
  make/unmake 推迟到 M3 视性能需要。
- **SelfEngine.checkCancel** 走 `coroutineContext[Job].isActive`,而非 `ensureActive()`
  (后者 suspend,无法在 negamax 同步循环内调用)。
- Truth 的 `named()` 在 1.4 被移除,断言失败信息改用 `throw AssertionError`。

**验证**
- `./gradlew :app:testDebugUnitTest` ✅
  - `domain/eval/`:7 个测试
  - `engine/self/`:TranspositionTableTest(11)、MoveOrderingTest(5)、
    SearchMateTest(4)、AlphaBetaPruningTest(1)、QuiescenceSearchTest(3)、
    TranspositionTableIntegrationTest(1)、SelfEngineTest(5)、
    SelfEngineBalanceTest(3)、DifficultyGradientTest(3)
- `./gradlew :app:lintDebug` ✅
- `./gradlew :app:assembleDebug` ✅
- **AB 剪枝**:初始局面 depth=3,AB 节点数 < 宽窗口 70%(实测通过)
- **TT 集成**:第二次相同搜索节点数 < 第一次的 10%
- **性能基准**(`SelfEngineBench`,@Ignore,本地 JBR 21 桌面 JVM):
  - depth=3(INTERMEDIATE):**273ms**,2138 nodes
  - depth=4(ADVANCED):**448ms**,8373 nodes,nps ≈ 18.7K
  - 远低于目标(depth=3 < 1s, depth=4 < 3s)

**备注**
- 期望棋力:depth=4 在桌面 JVM ~0.5s,约业余 1-2 级
- M3 视性能/UX 需要再决定是否引入 make/unmake 增量 Zobrist 更新
- SelfEngineBench 在 PR 中 @Ignore 跳过,本地手动跑

---

## 2026-06-16 — M3 棋盘 UI 完成(分支 feature/M3-board-ui)

**改动**
- `di/GameModule.kt`:第一个 Hilt `@Module`,提供 MoveGenerator / CheckDetector /
  MoveLegality / CheckmateDetector / GameRepository 五个 Singleton。显式传全部依赖,
  避免 Hilt 走带默认参数的构造器(`MoveLegality(gen)` 默认会 new 一个新 `CheckDetector`,
  与 Singleton 不一致)。
- `data/model/HistoryEntry.kt` + `data/game/GameRepository.kt`:GameState 持有者,
  StateFlow<GameState> 单一真相源。applyMove / undo / restart 全同步。
  **关键修正**:`Board.applyMove` 是纯搬子不做几何校验,Repository 必须先用
  `moveGenerator.movesFrom` 验证 from 处棋子能走到 to,再交给 MoveLegality。
- `ui/game/GameUiState.kt` + `GameViewModel.kt`:`combine(repo.state, _selected,
  _legalTargets).stateIn(...)`。onTap 选择状态机(6 分支),onUndo/onRestart 全同步。
  orientation 参数化(RED 固定,M4 setup screen 可改)。
- `ui/components/BoardGeometry.kt`:纯 view↔model 坐标转换,无 Compose 依赖,
  JVM 可测。RED 翻 row、BLACK 翻 col(180° 旋转)。两套映射都是对合。
- `ui/components/PiecePainter.kt`:`DrawScope` 扩展,画一颗棋子(木盘 + 朱红/墨黑
  环 + 繁体字)。RED 用文体(帥仕相傌車炮兵),BLACK 用白体(將士象馬車砲卒)。
- `ui/components/BoardCanvas.kt`:单 `Canvas` 内 11 层绘制
  (背景渐变 → 外框 → 网格(中间列河界处断开)→ 楚河漢界文字 → 双方九宫 ×
  → 炮位/兵位 L 形十字标 → 上一步高亮 → 选中高亮 → 合法目标点 → 棋子 →
  动画覆盖)。BoxWithConstraints 在 Composable 层算 layout 一次,点击映射走
  `detectTapGestures` + `(offset - margin) / cell` 的 `toInt()` 自然半格容差。
- `ui/components/GameTopBar.kt` + `GameBottomBar.kt`:无状态 Composable,
  红黑走子提示 / 悔棋(条件禁用)/ 重开。
- `ui/game/GameScreen.kt`:`Scaffold` + `hiltViewModel` + `BoardArea`(动画 owner)。
  `LaunchedEffect(lastMove)` + `animateFloatAsState(tween(200ms))`,新走子触发 200ms
  平移;undo/restart 不触发动画。
- `MainActivity.kt`:删 PlaceholderScreen,直接渲染 GameScreen。
- `androidTest/.../BoardCanvasTest.kt`:Compose UI 测试,`@Ignore` 给 CI 跳。

**关键决策**
- **坐标映射的"对合"性质**:RED 翻 row、BLACK 翻 col,使得 `viewToModel ∘ modelToView
  = identity`。BoardGeometryTest 对全 90 格 round-trip 自检。
- **BoardCanvas 单 Canvas 11 层**:每层一个私有 helper(`drawBackground` /
  `drawGrid` / ...),`Canvas { }` 主体可读。
- **动画 owner 在 BoardArea 而非 ViewModel**:`animateFloatAsState` 必须在 composition 里,
  ViewModel 只暴露 lastMove,UI 自己 drive 动画进度。
- **GameViewModelTest 用 `Dispatchers.setMain(StandardTestDispatcher)`**:
  viewModelScope 默认绑 Main,runTest 必须把 Main 替换为 testDispatcher,否则
  `WhileSubscribed` 不会启动 combine。`snapshot()` helper launch collector +
  `advanceUntilIdle()` 拿当前值。
- **`@Suppress("UNUSED_PARAMETER")` 临时标记**:BoardCanvas 前 3 个 commit(commit 9-11)
  的 board/selected/etc 参数未用到,加 suppress 直到 commit 12 全部接通才移除。

**验证**
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` ✅
  - `data/game/`:GameRepositoryTest(8)
  - `ui/game/`:GameViewModelTest(8)
  - `ui/components/`:BoardGeometryTest(8)
  - 加 M0-M2 既有测试,共 100+ 单测全绿
- 真机冒烟(用户在合并 PR 后做):
  - 红方在底、32 颗起始棋子就位
  - 点红子 → 出现合法目标点(Cinnabar 实心圆)
  - 点目标 → 200ms 平移动画 → 轮次翻为"黑方走"
  - 点悔棋 → 上一步回退(无动画)
  - 点重开 → 棋盘回初始 FEN

**备注**
- M3 = UI + 双人手动走(无引擎);M4 接 SelfEngine 完成完整人机循环
- 动画期间跳过 `lastMove.from` 的常规绘制(避免双绘)
- BoardCanvasTest 标 `@Ignore`,真机/模拟器跑 `connectedDebugAndroidTest`
- 后续 M6 优化:`Paint` 实例每帧 alloc,可 hoist 到顶层 `remember`

## 2026-06-16 — M4 游戏流程完成(分支 feature/M4-game-flow)

**改动**
- `ui/game/GameMode.kt`:HOT_SEAT / HUMAN_VS_AI 两档枚举。
- `data/game/GameConfig.kt` + `GameConfigHolder.kt`:跨屏配置载体(Singleton
  StateFlow),避免对自定义类型做 NavHost 序列化。代价:同 App 同时只能 1 局,
  M6 多局管理时再优化。
- `ui/setup/`:SetupUiState + SetupViewModel + SetupScreen。模式 / 执棋方 /
  难度(SegmentedButton + RadioButton)。开始对局时写入 Holder,orientation 按
  模式派生(HOT_SEAT=RED、HUMAN_VS_AI=humanSide)。
- `di/GameModule.kt`:补 4 个 @Provides —— Evaluation / MoveOrdering /
  TranspositionTable / SelfEngine。返回 `Engine` 接口,M5 接 Pikafish 时按
  @Qualifier 切换。
- `ui/game/GameUiState.kt`:扩展 mode / humanSide / isAiThinking / searchInfo /
  canInteract 五个字段。
- `ui/game/GameViewModel.kt`:构造追加 `engine: Engine` + `configHolder`。
  `init { repo.state.collect { maybeLaunchAi } }` 监听对手回合;人机模式下
  `launchAiMove(Dispatchers.Default)` + engine.info collector + finally 清状态。
  onUndo 人机模式悔两步(玩家总是面对"自己刚走完,AI 还没应")。
  onResign 用本地 `_resigned` 叠加 uiState.result,优先于 repo.result。
  canInteract 派生:思考中 / 游戏结束 / AI 回合都禁用。
- `ui/components/GameTopBar.kt`:加返回按钮("← 返回" 文字,M4 不引入
  material-icons 依赖)、AI 思考副标题(深度 / 分数 / 时间)。
- `ui/components/GameBottomBar.kt`:加"认输"按钮;悔棋在 isAiThinking 时禁用。
- `ui/nav/XiangqiNavHost.kt`:Navigation Compose,setup → game 两路由,起始
  目的地 setup。
- `MainActivity.kt`:改为渲染 XiangqiNavHost。

**关键决策**
- **GameConfigHolder 而非路由参数**:避免对自定义类型做 NavHost Bundle
  序列化;Setup 写、Game 读。简单可靠。
- **AI 思考走 Dispatchers.Default**:CPU 密集。viewModelScope 自动绑 Main,
  协程上下文额外指定 Default 跑搜索。
- **engine.info 独立 collector + finally cancel**:防止前一次搜索的 SearchInfo
  串到下一次。
- **maybeLaunchAi 双重 guard**:`_isAiThinking` + `sideToMove != opponent`,
  避免 ViewModel 创建时误启动 AI(init block 首次 emit 时玩家还在自己回合)。
- **onResign 不污染 Repository**:repo.result 来自 CheckmateDetector.decide;
  认输是 UI 级别的"主动放弃",用 `_resigned: GameResult?` 叠加,优先级最高。
- **NoopEngine / FakeEngine 测试策略**:既有 GameViewModelTest 用 NoopEngine
  (HOT_SEAT 永不调用);GameViewModelAiTest 用 FakeEngine(delay + 第一个
  合法走法),避免真进 Dispatchers.Default 导致 TestDispatcher 失控。
- **KDoc 内 `engine/self/*` 导致嵌套注释解析失败**:Kotlin 支持 KDoc 嵌套块
  注释,`/*` 在 KDoc 里被当新注释起点。改写为 `engine/self 下的`。

**验证**
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` ✅
  - `data/game/`:GameConfigHolderTest(3)
  - `ui/setup/`:SetupViewModelTest(5)
  - `ui/game/`:GameViewModelTest(8,既有)+ GameViewModelAiTest(5,新)
  - 加 M0-M3 既有测试,共 120+ 单测全绿
- 真机冒烟(用户在合并 PR 后做):
  - 启动 → SetupScreen(默认人机 / 红方 / 中级)
  - 切双人本地 → 开始 → 棋盘出现(M3 行为不变)
  - 返回 Setup → 切人机 / 黑方 / 高级 → 开始 → 红方(AI)先走,
    TopBar 显示思考深度 / 分数 / 时间 → 红子动画 → 轮到玩家
  - 玩家走 → 黑方思考 → 黑子动画
  - 点认输 → 对方胜;点悔棋 → 退 2 ply(人机);点重开 → 回开局

**备注**
- M4 = Setup + Game 两屏 + 人机循环;M5 才接 Pikafish 引擎(NDK + UCI)
- material-icons 依赖未引入,GameTopBar 返回按钮用文字"← 返回";M7 视觉
  打磨时再加依赖 + Icon
- SelfEngine 已 @Singleton,256K 槽 TT 全 App 复用
- GameConfigHolder 是 Singleton,App 同时只能 1 局;M6 多局管理时拆掉

---

<!-- 后续记录在此行上方,保持倒序 -->
