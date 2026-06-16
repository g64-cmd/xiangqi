# 架构

> 本文件描述 App 的整体架构、分层原则、数据流。

## 总体架构

单 Activity + Jetpack Compose + Hilt + Repository 模式。

```
┌──────────────────────────────────────────────┐
│  UI 层 (ui/)                                  │
│  Compose 屏幕 + ViewModel + UiState          │
│  MainActivity → NavHost → Screen             │
└──────────────────┬───────────────────────────┘
                   │ StateFlow / 事件
┌──────────────────▼───────────────────────────┐
│  Data 层 (data/)                              │
│  GameRepository / SettingsRepository         │
│  持有 GameHistory、调用 Engine               │
└──────┬─────────────────────┬─────────────────┘
       │                     │
┌──────▼──────────┐  ┌──────▼───────────────┐
│  Domain 层      │  │  Engine 层           │
│  (domain/)      │  │  (engine/)           │
│  纯 Kotlin,    │  │  Engine 接口 +        │
│  无 Android 依赖│  │  SelfEngine +        │
│  规则、FEN、模型│  │  PikafishEngine      │
└─────────────────┘  └──────────────────────┘
```

## 分层原则

1. **`domain/` 纯 Kotlin,零 Android 依赖**。可被纯 JVM 单测覆盖。任何 `android.*` 包导入都视为违规。
2. **`engine/` 接口位于顶层,实现按引擎分目录**。`Engine` 接口本身不依赖 Android,但 `engine/pikafish/` 内部可用 Android API(因为要读写 filesDir、启动进程)。
3. **`data/` 是 Repository 层**。协调 domain 与 engine,持有跨屏幕共享状态。
4. **`ui/` 是 Compose 表现层 + ViewModel**。ViewModel 持有 StateFlow,UI 通过 `collectAsStateWithLifecycle()` 订阅。

## 数据流

```
用户点击 → ViewModel.onEvent(Event) → ViewModel 更新 UiState
                                      ↓
                                  GameRepository.applyMove(move)
                                      ↓
                                  Engine.search(position) (Dispatchers.Default/IO)
                                      ↓
                                  EngineResult(bestMove, score)
                                      ↓
                                  GameRepository.applyMove(aiMove)
                                      ↓
                                  UiState 更新 → Compose 重组
```

## 状态管理

- **单一 `UiState` data class** 每屏一个。
- 用 `StateFlow<UiState>`(不要用多个 `MutableLiveData`)。
- UI 用 `collectAsStateWithLifecycle()`,Activity 后台时不收集。
- 引擎思考结果通过 `SharedFlow` 推送(支持多订阅/节流)。

## 依赖注入

Hilt 提供:
- `@HiltAndroidApp XiangqiApplication` —— 入口
- `@AndroidEntryPoint MainActivity` —— Activity 注入
- `@HiltViewModel` —— 每个 ViewModel
- `@Module @InstallIn(SingletonComponent::class) object GameModule` —— 提供
  SelfEngine / PikafishEngine / EngineProvider(按 `@SelfEngineQual` /
  `@PikafishEngineQual` qualifier 区分)
- Repository(`GameRepository`)、`PikafishInstaller` 等通过 `@Inject constructor`
  自动注入,无需 @Provides

## 线程模型

| Dispatcher | 用途 |
|---|---|
| `Dispatchers.Main` | UI 重组、ViewModel 事件入口 |
| `engineDispatcher`(默认 `Dispatchers.Default`,ViewModel 内 `@VisibleForTesting`) | 引擎搜索 / analyze;测试可注入 StandardTestDispatcher |
| `Dispatchers.IO` | 文件读写、子进程 IO(皮卡鱼 stdin/stdout) |

`GameViewModel.engineDispatcher` 用 `@VisibleForTesting internal var` 暴露,
单测注入 `StandardTestDispatcher` 让 `advanceUntilIdle` 能控制协程进度。

## 后续待补充

- [x] 完整状态机图 —— 见 `ui/game/GameViewModel.kt` KDoc(选择状态机 / 互斥门控 /
  engine 序列化不变量)
- [x] 主题与配色系统 —— 见 `ui/theme/`(Material 3 + 中国风木纹 / 朱砂 / 墨黑)
