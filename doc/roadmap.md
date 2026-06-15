# 开发路线图

本文件跟踪 8 个里程碑(M0–M7)的进展。每个里程碑完成后,在状态列更新为 ✅。

## 当前阶段

🎯 **M4 — 游戏流程**(已完成,SetupScreen + Navigation Compose + SelfEngine 自动应招 + 认输/悔棋门控)

下一步:M5 皮卡鱼引擎集成(NDK 编译、子进程、UCI 协议)。

## 里程碑清单

| ID | 名称 | 状态 | 关键交付 | 备注 |
|---|---|---|---|---|
| M0 | 工程化基线 | ✅ 完成 | CI、文档骨架、包名重命名、Android 风配色、Hilt+KSP | feature/M0-baseline + feature/M0-hilt-retry |
| M1 | 领域模型与规则引擎 | ✅ 完成 | 棋盘、棋子、走法生成、FEN、将军/将死判定 | feature/M1,76 单测全绿,Perft d1/d2/d3 = 44/1920/79666 |
| M2 | 自研搜索引擎 | ✅ 完成 | Negamax + AB + Zobrist TT + QSearch + 4 档难度 | feature/M2,bench d3=273ms / d4=448ms |
| M3 | 棋盘 UI | ✅ 完成 | Canvas 棋盘、棋子、点击交互、简单动画、双人手动走 | feature/M3-board-ui,9 新测试,BoardGeometry round-trip 全 90 格 |
| M4 | 游戏流程 | ✅ 完成 | SetupScreen、Navigation Compose、SelfEngine 接入、认输/悔棋门控 | feature/M4-game-flow,15 新提交,SetupViewModel+AiTest 共 13 用例 |
| M5 | 皮卡鱼引擎集成 | ⏳ 待启动 | NDK 编译、子进程、UCI 协议、难度映射 | feature/M5-* |
| M5 | 皮卡鱼引擎集成 | ⏳ 待启动 | NDK 编译、子进程、UCI 协议、难度映射 | feature/M5-* |
| M6 | 高级对战功能 | ⏳ 待启动 | 提示、求和、局势分析 | feature/M6-* |
| M7 | 打磨与发布 | ⏳ 待启动 | R8、图标、签名、Release | feature/M7-* |

## 状态图例

- ⏳ 待启动
- 🔄 进行中
- ✅ 完成
- ⚠️ 阻塞
- ❌ 取消

## 详细计划

完整里程碑计划见:`C:\Users\x7145\.claude\plans\android11-ai-ai-feature-github-ci-pr-rippling-babbage.md`(会话内计划文件)。
