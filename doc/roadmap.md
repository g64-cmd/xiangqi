# 开发路线图

本文件跟踪 8 个里程碑(M0–M7)的进展。每个里程碑完成后,在状态列更新为 ✅。

## 当前阶段

🔄 **持续修复与验证**(M0-M6 全部合并后)

M6(提示 / 求和 / 局势分析曲线)与紧随其后的 SELinux / UI / 引擎降级 fix 已合并
到 main。当前阶段不预先 plan,根据真机反馈修 bug + 完善 UX,保持细颗粒度 commit。

已合并的关键 fix(见 dev-log.md 2026-06-16 / 2026-06-17 条目):
- 走子动画状态机卡死导致双炮渲染(Animatable 替代 animateFloatAsState)
- 皮卡鱼 SELinux `execute_no_trans` 拒绝(ELF 迁到 jniLibs/libpikafish.so)
- 引擎崩溃让 app 闪退(EngineUnavailableException + toast 降级)
- 点击位置错位(`.toInt()` 改 `.roundToInt()`)

下一步候选:
- **真机深度冒烟**:全功能(悔棋 / 提示 / 求和 / 局势分析 / Hint 箭头 / 双引擎切换)
- **JNI 嵌入**(若 lib/ 改名方案在更高 targetSdk 出问题,见 engine-integration.md 末尾)
- **M7 启动**:R8 / 图标 / 签名 / Release

## 里程碑清单

| ID | 名称 | 状态 | 关键交付 | 备注 |
|---|---|---|---|---|
| M0 | 工程化基线 | ✅ 完成 | CI、文档骨架、包名重命名、Android 风配色、Hilt+KSP | feature/M0-baseline + feature/M0-hilt-retry |
| M1 | 领域模型与规则引擎 | ✅ 完成 | 棋盘、棋子、走法生成、FEN、将军/将死判定 | feature/M1,76 单测全绿,Perft d1/d2/d3 = 44/1920/79666 |
| M2 | 自研搜索引擎 | ✅ 完成 | Negamax + AB + Zobrist TT + QSearch + 4 档难度 | feature/M2,bench d3=273ms / d4=448ms |
| M3 | 棋盘 UI | ✅ 完成 | Canvas 棋盘、棋子、点击交互、简单动画、双人手动走 | feature/M3-board-ui,9 新测试,BoardGeometry round-trip 全 90 格 |
| M4 | 游戏流程 | ✅ 完成 | SetupScreen、Navigation Compose、SelfEngine 接入、认输/悔棋门控 | feature/M4-game-flow,15 新提交,SetupViewModel+AiTest 共 13 用例 |
| M5 | 皮卡鱼引擎集成 | ✅ 完成 | PikafishEngine 子进程、UCI 协议、EngineProvider、SetupScreen 引擎开关、AboutScreen | feature/M5-pikafish,2026.01.31 release |
| M6 | 高级对战功能 | ✅ 完成 | 提示 / 求和 / 局势分析曲线 / 单引擎序列化 / 双引擎 analyze | feature/M6-advanced-play,35 新测试,Compose Canvas 曲线 |
| 持续修复 | 真机验证 fix | 🔄 进行中 | 走子动画 / SELinux / 引擎降级 / 点击 round | fix/M6-* / fix/pikafish-selinux-execute / fix(ui):* |
| M7 | 打磨与发布 | ⏳ 待启动 | R8、图标、签名、Release | feature/M7-* |

## 状态图例

- ⏳ 待启动
- 🔄 进行中
- ✅ 完成
- ⚠️ 阻塞
- ❌ 取消

## 详细计划

完整里程碑计划见会话内 plan 文件:
`C:\Users\x7145\.claude\plans\android11-ai-ai-feature-github-ci-pr-rippling-babbage.md`
(M6 实现计划 + 持续修复阶段的 SELinux fix 计划)。

## M7 待办清单(预演)

- R8 启用(`optimization.enable=true`)+ keep 规则(Hilt 生成类 / JNI native 方法签名)
- 应用图标 / 启动屏
- 签名密钥管理与 CI 集成
- 多局管理(打破 GameConfigHolder 单局限制)
- 复盘回放、连续分析曲线
- 16 KB page size 对齐(pikafish ELF 重新链接到 0x4000,见 README)
