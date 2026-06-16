# 引擎集成

> 本文件描述两个引擎的集成方式、难度映射、构建流程、SELinux 与降级、常见坑。

## 引擎清单

| 引擎 | 类型 | 协议 | 定位 |
|---|---|---|---|
| SelfEngine | 自研,纯 Kotlin | 内部接口调用 | 低难度档(新手/初级);也作 pikafish 不可用时的兜底 |
| PikafishEngine | 集成第三方 | UCI 文本协议,子进程 | 高难度档(中级/高级/Hint/局势分析) |

两者都实现 `engine/Engine.kt` 接口。

## Engine 接口

```kotlin
interface Engine {
    val type: EngineType
    val info: StateFlow<SearchInfo?>
    suspend fun search(board: Board, sideToMove: Side, difficulty: Difficulty): EngineResult
    suspend fun analyze(board: Board, sideToMove: Side): AnalysisScore  // 默认实现走 search(ELEMENTARY).score
}
```

返回 `EngineResult(bestMove, score, depth, pv, ...)`,思考过程通过 `info`
StateFlow 推送。`analyze` 返回 `AnalysisScore(scoreCp, isMate, matePlies)`。

## 难度档位(`Difficulty` 枚举)

`engine/pikafish/PikafishEngine.kt:pikafishSkill` 映射:

| 档位 | 自研引擎(depth/movetime) | 皮卡鱼(Skill Level / movetime) |
|---|---|---|
| 新手 (BEGINNER) | depth=1, movetime=100ms | Skill=0, movetime=100ms |
| 初级 (ELEMENTARY) | depth=2, movetime=300ms | Skill=5, movetime=300ms |
| 中级 (INTERMEDIATE) | depth=3, movetime=800ms | Skill=12, movetime=800ms |
| 高级 (ADVANCED) | depth=4, movetime=1500ms | Skill=20, movetime=1500ms |
| 提示 (HINT,M6 起) | depth=2, movetime=400ms | Skill=10, movetime=400ms |

> HINT 是内部档位,不在 SetupScreen 暴露给玩家选择,仅供"提示"按钮调用一次
> 浅搜给玩家建议。

皮卡鱼 Threads 固定为 1。

## 局势分析(M6):`Engine.analyze` 接口与 `eval` 命令

M6 起 `Engine` 接口新增 `suspend fun analyze(board, sideToMove): AnalysisScore`,
返回 sideToMove 视角的 centipawn 分数与 mate 标记。两种实现:

- **SelfEngine** 继承默认实现(走 `search(ELEMENTARY).score` 转 `AnalysisScore`),
  无需额外代码。
- **PikafishEngine** 覆盖为发皮卡鱼独有的 **`eval`** 命令做 NNUE 静态评估,
  瞬时返回一行 `Final evaluation +0.23 (white side) [with scaled NNUE, ...]`,
  `parseEvalLine` 用正则 `Final evaluation\s+([+-]?\d+\.\d+)\s+\((white|black) side\)`
  提取分数,float × 100 转 cp,`(black side)` 标签时取负转 white 视角。

参考 chinese-chess-fish-android 的 `ComputerPlayer.java`。差异:象棋鱼不做 POV
规范化(eval 永远 white 视角,凑巧 Red=White 看起来对);我们在
`GameViewModel.maybeAutoEval` 里规范化到红方视角(`sideToMove == BLACK` 时取负),
让曲线在 sideToMove 切换后仍连贯。

**调用约定**:走子后 `GameViewModel.maybeAutoEval` 自动触发一次 `analyze`,
结果 append 到 `_evalHistory` 并刷新 TopBar `currentScore`。analyze 跑在独立
协程(不设 `_isEngineBusy` 避免与 AI 应招互锁),内部 `aiJob?.join()` 等 AI
应招完成才跑,保证 UCI 会话单线程串行。

## 引擎序列化不变量(M6 起)

单引擎实例(`PikafishEngine` 缓存 UCI session / `SelfEngine` 共享 TT)**不
支持并发 search**。`GameViewModel.launchEngine` 是所有手动 engine 入口
(AI 自动应招 / Hint / 求和评估)的统一通道,通过 `_isEngineBusy` + `aiJob?.cancel()`
保证序列化。`requireEngineIdle()` 把分散判断收敛为一处:`_isEngineBusy` 与
effectiveResult(_drawn / _resigned overlay 优先)都满足才允许调用。

auto-eval 是唯一的例外:它**不**走 `launchEngine`(否则会与 AI 应招死锁),
而是跑独立 `viewModelScope.launch(engineDispatcher)` + `aiJob?.join()`。

## 皮卡鱼集成方案

### 总体路径(jniLibs 改名方案,M5 起改为此路径)

```
官方 release ELF → 改名 libpikafish.so 放入 app/src/main/jniLibs/arm64-v8a/
                  → AGP 在 APK 安装时解压到 nativeLibraryDir (apk_data_file 域)
                  → PikafishProcess 通过 applicationInfo.nativeLibraryDir 拿路径
                  → ProcessBuilder 启动子进程
                  → UCI 文本协议通信(stdin/stdout)
```

**NNUE 权重** `pikafish.nnue` 是数据文件,无 SELinux 问题,仍走
`assets → filesDir` 复制(`PikafishInstaller`);启动后通过
`setoption name EvalFile value <绝对路径>` 显式传给引擎。

### 为什么走 jniLibs 而非 assets?

**根因**:Android 10+ (API 29+) SELinux 策略禁止从 `app_data_file` 域执行
二进制。`filesDir/<file>` 属于 `app_data_file` 域,`ProcessBuilder.start()` 后
内核 `execute_no_trans` 检查会被 SELinux 拒绝(`error=13, Permission denied`)。
M5 集成时未做真机/模拟器验证,这个雷一直埋着,M6 加了走子后自动 `analyze()`
才暴露。

**绕过**:把 ELF 改名为 `lib*.so` 放进 `app/src/main/jniLibs/<abi>/`,AGP 把
它当作原生库,APK 安装时解压到 `nativeLibraryDir`(即
`/data/app/<pkg>/lib/arm64/`),该目录属于 `apk_data_file` SELinux 域,
**允许 `execute_no_trans`**。

参考实现:`chinese-chess-fish-android` 的 `PikafishExternalEngine.java` 通过
`applicationInfo.nativeLibraryDir` 拿解压后路径,然后 `ProcessBuilder`。

### 关键配置(三件套)

1. **`app/src/main/jniLibs/arm64-v8a/libpikafish.so`** —— ELF 二进制(改名即可,
   内容不变)。被 `.gitignore` 显式 unignore(默认忽略 `*.so` 防误提交临时构建),
   跟随 git 分发,用户 clone 后即可跑。
2. **`app/build.gradle.kts` 的 `packaging.jniLibs.useLegacyPackaging = true`** ——
   让 AGP 把 .so 解压到 nativeLibraryDir 而不是从 APK 内 mmap;否则 ProcessBuilder
   拿不到真实文件路径。
3. **`AndroidManifest.xml` 的 `<application android:extractNativeLibs="true">`** ——
   与 Gradle useLegacyPackaging 双保险,跨 AGP 版本一致。

### PikafishInstaller 职责

- 不再复制 ELF(AGP 处理),只复制 NNUE:`assets/pikafish/pikafish.nnue` →
  `filesDir/pikafish/pikafish.nnue`,SHA-256 校验
- `verifyExecutable()` 校验 `nativeLibraryDir/libpikafish.so` 存在 + 可执行 + SHA
  匹配;失败抛 `IllegalStateException`(可执行文件来自 APK,失败说明 APK 被篡改)
- `executablePath: String` getter,返回
  `${ctx.applicationInfo.nativeLibraryDir}/libpikafish.so`

### 启动子进程(`PikafishProcess`)

```kotlin
class PikafishProcess(executablePath: String, workingDir: File) : AutoCloseable {
    private val process = ProcessBuilder(executablePath)
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()
    // stdin / stdout 通过 BufferedWriter / BufferedReader 包装
}
```

`PikafishEngine.ensureSession()` 启动后发送:
- `uci` + `isready`(握手)
- `setoption name EvalFile value <绝对路径>`(NNUE 路径,防 cwd 误解)

后续每次 search/analyze 复用 session,直到协程取消或 `close()` 销毁进程。

### UCI 协议交互示例

```
GUI -> Engine:  uci
Engine -> GUI:  id name Pikafish ...
Engine -> GUI:  uciok
GUI -> Engine:  isready
Engine -> GUI:  readyok
GUI -> Engine:  setoption name EvalFile value /data/.../pikafish.nnue
GUI -> Engine:  setoption name Threads value 1
GUI -> Engine:  setoption name Skill Level value 12
GUI -> Engine:  ucinewgame
GUI -> Engine:  position fen rnbakabnr/9/...
GUI -> Engine:  go movetime 800
Engine -> GUI:  info depth 10 score cp 35 pv ...
Engine -> GUI:  bestmove h2e2
```

eval 命令(局势分析):
```
GUI -> Engine:  position fen <fen>
GUI -> Engine:  eval
Engine -> GUI:  Final evaluation +0.23 (white side) [with scaled NNUE, ...]
```

## 引擎失败降级(M6 修复后)

`PikafishEngine` 任何环节失败(进程崩溃 / SELinux 拒绝 / 输出异常)都包装为
`EngineUnavailableException`(RuntimeException 子类):

- `ensureSession`:`IOException` / `IllegalStateException` 包装
- `search`:"未返回 bestmove"包装(子进程崩溃的常见表现)
- `analyze`:已有 `catch (_: Throwable)` 返回 0cp 兜底,无变化

`GameViewModel.launchEngine` 在 catch `CancellationException` 之后增加
`catch (e: EngineUnavailableException)`,emit toast"引擎不可用:<原因>",
**不闪退**,棋盘状态不变。玩家可重开 / 切换 SelfEngine / 检查 NNUE 安装。

**模拟器已知问题**:x86_64 AVD 通过 `ndk_translation_program_runner` 翻译 arm64
ELF,pikafish 启动时 SIGSEGV(NNUE 加载/SIMD 指令在翻译下出问题)。修复后 app
不闪退,改走 toast 提示。**真机 arm64 原生跑无此问题**。

## 自研引擎搜索框架

`engine/self/` 目录:

| 文件 | 职责 |
|---|---|
| `SelfEngine.kt` | 实现 `Engine` 接口,协调其他组件 |
| `Search.kt` | Minimax + Alpha-Beta 主循环 |
| `MoveOrdering.kt` | 走法排序(MVV-LVA、killer move) |
| `QuiescenceSearch.kt` | 静态搜索(只搜吃子,避免视野效应) |
| `TranspositionTable.kt` | Zobrist 置换表 |

## NNUE 版本与体积基线

| 项 | 值 |
|---|---|
| Pikafish 版本 | 2026.01.31 |
| NNUE 网络版本 | 2026.01.31(随 release) |
| 可执行文件大小 | 1.81 MB(libpikafish.so,arm64-v8a ELF) |
| NNUE 文件大小 | 50.7 MB(pikafish.nnue) |
| APK 增量 | ~52 MB(libpikafish.so + nnue) |
| ABI | arm64-v8a |
| PIKAFISH_SHA | 971b979c970a92d413d8f53c9ea4d3296a37dee8fe9cfcc133ebca98c831801a |
| PIKAFISH_NNUE_SHA | c4026370d7516d9b0f668447f9ca1931241538bdc689cde6fec6a991ac4d5f77 |

## 准备皮卡鱼二进制(本地一次性,详见 README)

`libpikafish.so` 已随仓库分发,无需本地准备。**NNUE** `pikafish.nnue` 因体积
过大(50.7 MB)被 `.gitignore` 忽略,首次构建前需手动准备:

1. 从 [official-pikafish/Pikafish Releases](https://github.com/official-pikafish/Pikafish/releases)
   下载 release 包(当前使用 2026.01.31)
2. 解压后把 `pikafish.nnue` 复制为 `app/src/main/assets/pikafish/pikafish.nnue`
3. 确认 SHA-256 与 `app/build.gradle.kts` 中 `PIKAFISH_NNUE_SHA` 一致

## 常见坑

- **NNUE 与引擎版本必须匹配**。Pikafish 文档明确警告 "latest master network release
  may not work well with older engine versions"。
- **`useLegacyPackaging` / `extractNativeLibs` 必须开**。否则 AGP 把 .so 留在 APK
  内 mmap,ProcessBuilder 拿不到真实文件路径,exec 失败。
- **`targetSdk=36` 时 `useLegacyPackaging` 已 deprecated**,但 `extractNativeLibs`
  在 manifest 层面仍然有效。两条都加最保险。Google 文档未明确何时移除 legacy path,
  M7 前留意。
- **模拟器 + arm64 翻译 SIGSEGV**:x86_64 AVD 通过 `ndk_translation` 跑 arm64
  ELF 不稳定,pikafish 启动崩。用 arm64 AVD 镜像或真机测。
- **子进程可能被系统杀掉**(低内存场景)。`PikafishEngine.ensureSession` 在
  session 不 alive 时重启,但当前实现没主动检测;协程取消时 `closeSession()`
  销毁进程。
- **GPL-3.0 合规**:App 必须以 GPL-3.0 发布,About 页需明示使用 Pikafish +
  仓库链接 + GPL 声明。

## 后续待补充

- [ ] 多 ABI 支持(x86_64 pikafish 让模拟器原生跑,APK 增 ~1.8 MB)
- [ ] 16 KB page size 对齐(pikafish ELF LOAD 段重新链接到 0x4000,
  Google 2025-11-01 起 targetSdk=15+ 强制;当前是 0x1000,需要 NDK 重编译或
  拉新版 release)
- [ ] JNI 嵌入(若 lib/ 改名方案未来 targetSdk 出问题,迁移到进程内 JNI 调用,
  详见会话 plan 文件"未来工作"节)

## License 与 GPL 合规

App 整体 GPL-3.0,因使用 Pikafish(GPL-3.0)。AboutScreen 已就绪,显示:
- Pikafish 仓库链接
- GPL-3.0 声明
- 致谢 jetpack/compose/hilt 等

修改 Pikafish 源码会触发衍生作品条款;当前路径只编译官方 release tag 不改源,
保持上游兼容。未来若做 JNI 嵌入需要改 `main()` 入口,届时需审 GPL 合规。
