# 约定

> 本文件描述项目的代码风格、命名、commit message、分支命名等约定。

## 分支命名

- `main` —— 主干,只接受 PR 合并,所有 PR 必须通过 CI
- `feature/<里程碑>-<子任务>` —— 功能开发,如 `feature/M1-fen-parser`、`feature/M3-board-canvas`
- `fix/<里程碑>-<bug 描述>` —— bug 修复,如 `fix/M2-ab-pruning-bug`
- `docs/<主题>` —— 仅文档变更,如 `docs/architecture-update`

## Commit Message

遵循 Conventional Commits 风格:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### type

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | bug 修复 |
| `refactor` | 重构(无功能变化) |
| `test` | 添加/修改测试 |
| `docs` | 仅文档 |
| `build` | 构建脚本、依赖、CI 配置变更 |
| `ci` | CI 配置变更 |
| `chore` | 杂项(不修改源码也不修改测试) |
| `revert` | 回滚之前的 commit |

### scope

里程碑编号(里程碑内 commit),如 `M0`、`M1`、`M5`;持续修复阶段可用主题名
(`pikafish`、`ui`、`test`)。

### 示例

```
feat(M1): add FEN parser
test(M1): add Perft test for initial position
fix(M2): correct alpha-beta pruning in quiescence
build(M0): add Hilt and Navigation dependencies
docs(M0): document engine integration plan
ci(M0): set up GitHub Actions workflow

# 持续修复阶段(M0-M6 合并后)
chore(pikafish): move ELF to jniLibs as libpikafish.so
fix(pikafish): catch engine failure and degrade to toast instead of crash
fix(ui): use round instead of floor for board tap coordinate mapping
fix(test): set executable bit on fake .so for cross-platform verifyExecutable
```

### 单 commit 颗粒度

- 单 commit 净变更 ≤ ~250 行(允许例外,如大型自动生成)。
- 一个 commit 只做一件事(不要混合功能 + 重构 + 格式化)。
- 跨多个文件的相关变更放在同一 commit。

## Kotlin 风格

遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)。要点:

- 4 空格缩进,不用 Tab。
- 行宽 ≤ 120 字符。
- 类与函数 PascalCase;变量与属性 camelCase;常量 UPPER_SNAKE_CASE。
- `data class` 优先用于纯数据载体。
- 不可变优先:`val` 优于 `var`,`data class` 用 `val` 属性。
- 公共 API 必须 KDoc 注释;私有可不写。

### Compose 特定

- Composable 函数 PascalCase,以名词或形容词结尾(如 `GameScreen`、`RedPiece`)。
- 用 `Modifier` 作为最后一个可选参数(默认 `Modifier = Modifier`)。
- State hoisting:状态上提到 ViewModel 或父 Composable,子 Composable 尽量无状态。

### 协程

- 用 `viewModelScope` 启动协程,不要用 `GlobalScope`。
- 切换 Dispatcher 用 `withContext`,不要用 `launch(Dispatchers.X)`。
- 长时间运行任务用 `Flow` 而非 suspend fun。

## 命名约定

| 元素 | 约定 | 示例 |
|---|---|---|
| 包 | 全小写,不用下划线 | `com.xiangqi.app.domain.movegen` |
| 类 | PascalCase | `MoveGenerator` |
| 接口 | PascalCase,不加 `I` 前缀 | `Engine`(不是 `IEngine`) |
| 函数 | camelCase | `generateMoves()` |
| 常量 | UPPER_SNAKE_CASE | `INITIAL_FEN` |
| 枚举值 | UPPER_SNAKE_CASE | `BEGINNER`、`EASY` |
| 资源 ID | snake_case | `game_top_bar` |

## 测试约定

- 测试类名:`<被测类>Test`,如 `FenParserTest`。
- 测试函数名:用反引号包描述性文字,如 `` `parse initial position correctly`() `` 或 `givenX_whenY_thenZ`。
- 一个测试只验证一个行为。
- 用 `@BeforeEach` 重置状态,不要在测试间共享可变状态。

详细测试约定见 `doc/testing.md`。

## 文档约定

- Markdown 文件使用 LF 换行,UTF-8 编码。
- 中文文档使用全角标点。
- 行尾不留空格。
- 长行优先(段落内换行只在段落结束时)。

## 禁止事项

- ❌ 提交 `local.properties`、签名密钥(`*.jks` / `*.keystore`)、IDE 配置(`.idea/`)。
- ❌ 在 `domain/` 层 import `android.*`。
- ❌ 在 Compose 中使用 `GlobalScope`。
- ❌ 在主线程做 CPU 密集型计算(自研引擎搜索必须切 `Dispatchers.Default`)。
- ❌ 直接修改 Pikafish 源码(避免衍生作品问题,只编译官方 release tag)。
