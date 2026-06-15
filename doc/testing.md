# 测试

> 本文件描述测试约定、运行方式、Perft 数据来源。

## 测试金字塔

```
            /\
           /  \    真机测试(用户做)
          /----\
         /      \  插桩测试(androidTest,Robolectric 或 connectedAndroidTest)
        /--------\
       /          \ 单元测试(test,JVM,无 Android 依赖)
      /____________\
```

绝大部分测试应在最底层(JVM 单元测试),速度快、覆盖率高。

## 三类测试

### 1. JVM 单元测试(`app/src/test/`)

- 路径:`app/src/test/java/com/xiangqi/app/...`
- 运行:`./gradlew :app:testDebugUnitTest`
- 范围:`domain/` 全部、`engine/self/`、`data/`(Repository)、`util/`。
- 依赖:JUnit 4 + Truth + MockK + kotlinx-coroutines-test。

### 2. Robolectric 测试(也在 `app/src/test/`)

- Robolectric 在 JVM 上模拟 Android 框架,适合需要 `Context` 但不需要真 UI 的测试。
- 配置:`@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])`(SDK 36 兼容问题)。
- 范围:`engine/pikafish/`(需要 `Context.filesDir`)、`data/settings/`(DataStore)。

### 3. 插桩测试(`app/src/androidTest/`)

- 路径:`app/src/androidTest/java/com/xiangqi/app/...`
- 运行:`./gradlew :app:connectedDebugAndroidTest`(需设备/模拟器)
- 范围:Compose UI 交互、完整端到端流程。
- 依赖:`androidx.compose.ui:ui-test-junit4` + `androidx.test.ext:junit`。

## 测试覆盖要求

| 模块 | 最低覆盖率 | 关键用例 |
|---|---|---|
| `domain/fen/` | 高(100%) | 标准开局、残局、序列化往返 |
| `domain/movegen/` | 高(100%) | 每种棋子、Perft、特殊规则(蹩马腿等) |
| `domain/rules/` | 高(100%) | 8 组经典杀局、飞将、送将 |
| `engine/self/` | 中(核心 80%) | 杀棋局面、性能、AB 剪枝 |
| `engine/pikafish/` | 中 | 启动、UCI 通信、超时降级 |
| `data/game/` | 中 | 悔棋栈、终局判定 |
| `ui/game/` | 低(关键路径) | 走子流程、状态切换 |

## Perft 测试

[Perft (Performance Test)](https://www.chessprogramming.org/Perft) 是验证走法生成正确性的标准方法。

### 原理

给定一个局面,在深度 d 下,**总走法节点数**应该是确定的。如果你的走法生成器算出的节点数与已知参考值不符,说明有 bug。

### 中国象棋 Perft 参考值(从初始局面)

> M1-c 完成时实际跑出,与开源参考对账一致。

| 深度 | 节点数(本实现) | 参考值 | 对账 |
|---|---|---|---|
| 1 | 44 | 44 | ✅ |
| 2 | 1,920 | 1,920 | ✅ |
| 3 | 79,666 | 79,666 | ✅ |

### 用例位置

`app/src/test/java/com/xiangqi/app/domain/movegen/PerftTest.kt`。

### 数据来源

- 主要参考:[xqbase.com 着法表示规范](https://www.xqbase.com/protocol/cchess_move.htm)
- 也可以用 [ElephantArt](https://github.com/CGLemon/ElephantArt) 等开源引擎的 Perft 实现对照(注意协议兼容)。

## 性能基准(Bench)

`SelfEngineBench.kt` 标 `@Ignore("手动跑")`,避免拖慢 CI。

**手动运行**:编辑 `SelfEngineBench.kt` 临时去掉 `@Ignore`,然后:

```bash
./gradlew :app:testDebugUnitTest --tests "com.xiangqi.app.engine.self.SelfEngineBench" --info
```

输出含 nps(nodes per second)与时间,记入 `doc/dev-log.md`。

**M2 基线(本地 JBR 21 桌面 JVM,Win11)**:

| 难度 | depth | movetime | 实测时间 | 节点数 | nps |
|---|---|---|---|---|---|
| INTERMEDIATE | 3 | 800ms | **273ms** | 2138 | ~7.8K |
| ADVANCED | 4 | 1500ms | **448ms** | 8373 | ~18.7K |

depth=4 因 TT / AB / MoveOrdering 共同作用,实际仅用 448ms 就跑完
8373 节点(理论上限 1500ms),说明迭代加深 + TT 命中收益显著。

## 模拟器 vs 真机

- CI 默认不跑 connectedAndroidTest(需要 emulator,资源消耗大)。
- Robolectric 测试在 CI 上跑(`ci.yml` 的 `unit-test` job 已包含 Robolectric)。
- 真机测试由用户做(开发计划中已明确)。

## 测试报告

- 本地:`app/build/reports/tests/testDebugUnitTest/index.html`
- CI:下载 `unit-test-results` artifact

## 常见坑

- Robolectric 在 AGP 9 + SDK 36 下可能不稳定,固定 `@Config(sdk = [33])`。
- `kotlinx-coroutines-test` 用 `runTest` 而非 `runBlocking`。
- 测试 suspend 函数:`@Test fun foo() = runTest { ... }`。
- MockK 的 `relaxed = true` 在 mock final 类时需要 `mockkStatic` 或 `MockKAgent` 初始化。

## 后续待补充

- [x] Perft 实际节点数(M1-c 完成,见上表)
- [x] SelfEngine 性能基线(M2 完成,见 Bench 节)
