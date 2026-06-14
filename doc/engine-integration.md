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

| 档位 | 自研引擎 | 皮卡鱼 |
|---|---|---|
| 新手 (BEGINNER) | depth=1, movetime=100ms | Skill=0, movetime=200ms |
| 初级 (EASY) | depth=2, movetime=300ms | Skill=5, movetime=400ms |
| 中级 (MEDIUM) | depth=3, movetime=800ms | Skill=12, movetime=900ms |
| 高级 (HARD) | depth=4, movetime=1500ms | Skill=20, Threads=2, Hash=128, movetime=3000ms |

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
| Pikafish 版本 | TBD |
| NNUE 网络版本 | TBD |
| 可执行文件大小 | TBD |
| NNUE 文件大小 | TBD |
| APK 增量 | TBD |

## scripts/build-pikafish.sh

M5 准备时补充脚本与使用说明。

## 常见坑

- **NNUE 与引擎版本必须匹配**。Pikafish 文档明确警告 "latest master network release may not work well with older engine versions"。
- **子进程可能被系统杀掉**(低内存场景)。`PikafishEngine` 需有断线检测与自动重启机制。
- **UCI 输出行缓冲**。某些引擎在管道模式下默认行缓冲,需检查 `bestmove` 是否正确分行。
- **GPL-3.0 合规**:App 必须以 GPL-3.0 发布,About 页需明示使用 Pikafish + 仓库链接 + GPL 声明。

## 后续待补充

- [ ] `scripts/build-pikafish.sh` 脚本(M5 启动时)
- [ ] NNUE 体积基线(M5 完成时)
- [ ] `BuildConfig.PIKAFISH_SHA` 配置(M5)
