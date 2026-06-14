# 开发路线图

本文件跟踪 8 个里程碑(M0–M7)的进展。每个里程碑完成后,在状态列更新为 ✅。

## 当前阶段

🎯 **M0 — 工程化基线**(已完成,等 PR 合并;**Hilt 推迟**到独立 PR)

## 里程碑清单

| ID | 名称 | 状态 | 关键交付 | 备注 |
|---|---|---|---|---|
| M0 | 工程化基线 | ✅ 完成 | CI、文档骨架、包名重命名、Android 风配色 | feature/M0-baseline;Hilt+KSP 因 AGP 9 兼容性暂缓,见 dev-log |
| M0+ | Hilt + KSP 接入 | ⚠️ 阻塞 | 等 Dagger 发布兼容 AGP 9 的稳定版 | 待 Hilt 团队跟进 |
| M1 | 领域模型与规则引擎 | ⏳ 待启动 | 棋盘、棋子、走法生成、FEN、将军/将死判定 | feature/M1-* |
| M2 | 自研搜索引擎 | ⏳ 待启动 | Minimax + AB + 估值 + 难度档位 | feature/M2-* |
| M3 | 棋盘 UI | ⏳ 待启动 | Canvas 棋盘、棋子、点击交互、动画 | feature/M3-* |
| M4 | 游戏流程 | ⏳ 待启动 | 主循环、设置界面、悔棋/重开/认输 | feature/M4-* |
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
