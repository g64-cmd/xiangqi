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
