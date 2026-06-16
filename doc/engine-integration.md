# 引擎集成

> 本文件描述两个引擎的集成方式、难度映射、构建流程、常见坑。

## 引擎清单

| 引擎 | 类型 | 协议 | 定位 |
|---|---|---|---|
| SelfEngine | 自研,纯 Kotlin | 内部接口调用 | 低难度档(新手/初级) |
| PikafishEngine | 集成第三方 | UCI 文本协议,子进程 | 高难度档(中级/高级) |

两者都实现 `engine/Engine.kt` 接口。

## Engine 接口

```kotlin
interface Engine {
    val type: EngineType
    suspend fun search(
        position: Position,
        difficulty: Difficulty,
        onInfo: (EngineInfo) -> Unit = {}
    ): EngineResult
}
```

返回 `EngineResult(bestMove, score, depth, pv)`,思考过程通过 `onInfo` 回调。

## 难度档位(`Difficulty` 枚举)

M5 已实现映射(见 `engine/pikafish/PikafishEngine.kt:pikafishSkill`):

| 档位 | 自研引擎(depth/movetime) | 皮卡鱼(Skill Level / movetime) |
|---|---|---|
| 新手 (BEGINNER) | depth=1, movetime=100ms | Skill=0, movetime=100ms |
| 初级 (ELEMENTARY) | depth=2, movetime=300ms | Skill=5, movetime=300ms |
| 中级 (INTERMEDIATE) | depth=3, movetime=800ms | Skill=12, movetime=800ms |
| 高级 (ADVANCED) | depth=4, movetime=1500ms | Skill=20, movetime=1500ms |
| 提示 (HINT) | depth=2, movetime=400ms | Skill=10, movetime=400ms |

> HINT(M6 起新增)是内部档位,不在 SetupScreen 暴露给玩家选择,仅供
> "提示"按钮调用一次浅搜给玩家建议。

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

参考 chinese-chess-fish-android 的 `ComputerPlayer.java:368-378`(发送)+ 
`514-533`(parse)流程。差异:象棋鱼不做 POV 规范化(eval 永远 white 视角,
凑巧 Red=White 看起来对);我们在 `GameViewModel.maybeAutoEval` 里规范化到
红方视角(`sideToMove == BLACK` 时取负),让曲线在 sideToMove 切换后仍连贯。

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
而是跑独立 `viewModelScope.launch(Default)` + `aiJob?.join()`。

## 皮卡鱼集成方案

### 总体路径

```
本地 NDK 编译 → assets/ 内嵌 → 首次启动复制到 filesDir → ProcessBuilder 启动子进程 → UCI 文本协议通信
```

详细步骤:

1. **本地编译** (M5 准备时):
   ```bash
   # 前提:NDK r27c+ 已配置
   cd <pikafish-source>/src
   make -j build ARCH=armv8 COMP=ndk
   ```
   产出 `pikafish` 可执行文件 + `pikafish.nnue` 网络文件。

2. **放入 assets**:
   - `app/src/main/assets/pikafish/arm64-v8a/pikafish` (可执行)
   - `app/src/main/assets/pikafish/pikafish.nnue` (网络文件)
   - 这两个文件被 `.gitignore` 忽略,需要本地准备;CI 不内置。

3. **首次启动复制** (`PikafishInstaller`):
   - 检查 `filesDir/pikafish/bin/pikafish` 是否存在且 SHA-256 匹配 `BuildConfig.PIKAFISH_SHA`。
   - 不存在 → 从 assets 复制到 `filesDir/pikafish/bin/pikafish` 与 `filesDir/pikafish/pikafish.nnue`。
   - `File.setExecutable(true, true)`。

4. **启动子进程** (`PikafishProcess`):
   ```kotlin
   ProcessBuilder("bin/pikafish")
       .directory(File(filesDir, "pikafish"))
       .redirectErrorStream(true)
       .start()
   ```
   - stdin: 发送 UCI 命令
   - stdout: 接收 UCI 响应
   - 读循环在 `Dispatchers.IO`

5. **UCI 协议交互** (`UciProtocol`):
   ```
   GUI -> Engine:  uci
   Engine -> GUI:  id name ...
   Engine -> GUI:  option name Threads type spin ...
   Engine -> GUI:  uciok
   GUI -> Engine:  setoption name Threads value 1
   GUI -> Engine:  setoption name Skill Level value 12
   GUI -> Engine:  isready
   Engine -> GUI:  readyok
   GUI -> Engine:  ucinewgame
   GUI -> Engine:  position fen rnbakabnr/9/...
   GUI -> Engine:  go movetime 900
   Engine -> GUI:  info depth 10 score cp 35 pv ...
   Engine -> GUI:  bestmove h2e2
   ```

## 自研引擎搜索框架

`engine/self/` 目录:

| 文件 | 职责 |
|---|---|
| `SelfEngine.kt` | 实现 `Engine` 接口,协调其他组件 |
| `Search.kt` | Minimax + Alpha-Beta 主循环 |
| `MoveOrdering.kt` | 走法排序(MVV-LVA、killer move) |
| `QuiescenceSearch.kt` | 静态搜索(只搜吃子,避免视野效应) |
| `TranspositionTable.kt` | Zobrist 置换表(可选) |

## NNUE 版本与体积基线

> M5 完成时记录。当前空白。

| 项 | 值 |
|---|---|
| Pikafish 版本 | 2026.01.31 |
| NNUE 网络版本 | 2026.01.31(随 release) |
| 可执行文件大小 | 1.77 MB(pikafish-armv8) |
| NNUE 文件大小 | 50.7 MB(pikafish.nnue) |
| APK 增量 | ~52 MB(assets 内嵌) |
| ABI | arm64-v8a |
| PIKAFISH_SHA | 971b979c970a92d413d8f53c9ea4d3296a37dee8fe9cfcc133ebca98c831801a |
| PIKAFISH_NNUE_SHA | c4026370d7516d9b0f668447f9ca1931241538bdc689cde6fec6a991ac4d5f77 |

## scripts/build-pikafish.sh

M5 采用"从官方 release 包提取预编译二进制"路径(非 NDK 自编译),
故未生成构建脚本。如未来需要从源码自编译,流程:

```bash
# 前提:NDK r27c+ 已配置
git clone https://github.com/official-pikafish/Pikafish
cd Pikafish/src
make -j build ARCH=armv8 COMP=ndk
# 产出 pikafish + pikafish.nnue,放入 app/src/main/assets/pikafish/
```

## 常见坑

- **NNUE 与引擎版本必须匹配**。Pikafish 文档明确警告 "latest master network release may not work well with older engine versions"。
- **子进程可能被系统杀掉**(低内存场景)。`PikafishEngine` 需有断线检测与自动重启机制。
- **UCI 输出行缓冲**。某些引擎在管道模式下默认行缓冲,需检查 `bestmove` 是否正确分行。
- **GPL-3.0 合规**:App 必须以 GPL-3.0 发布,About 页需明示使用 Pikafish + 仓库链接 + GPL 声明。

## 后续待补充

- [x] NNUE 体积基线(M5 完成时,见上)
- [x] `BuildConfig.PIKAFISH_SHA` 配置(M5,见 `app/build.gradle.kts:defaultConfig.buildConfigField`)
- [x] `BuildConfig.PIKAFISH_NNUE_SHA` 配置(M5)
- [x] `BuildConfig.PIKAFISH_VERSION` 配置(M5)
- [ ] scripts/build-pikafish.sh(若改用 NDK 自编译时补充)
