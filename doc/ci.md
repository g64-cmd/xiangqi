# CI (持续集成)

> 本文件描述 GitHub Actions CI 配置、缓存策略、artifact 下载路径、PR 流程。

## Workflow 清单

`.github/workflows/` 下两个 workflow:

| 文件 | 触发 | 用途 |
|---|---|---|
| `ci.yml` | `pull_request` 到 main;`push` 到 main | PR 主检查:unit-test + lint + build |
| `release.yml` | `push: tags: ['v*']` 或 `workflow_dispatch` | 构建签名 Release 包 |

## ci.yml 详解

### 触发

```yaml
on:
  pull_request:
    branches: [ main ]
  push:
    branches: [ main ]
```

### 三个并行 Job

| Job | Runner | 主要步骤 | 失败 artifact |
|---|---|---|---|
| `unit-test` | ubuntu-latest, JDK 21 temurin | `./gradlew :app:testDebugUnitTest` | `app/build/reports/tests/testDebugUnitTest/` |
| `lint` | ubuntu-latest, JDK 21 temurin | `./gradlew :app:lintDebug` | `app/build/reports/lint-results-debug.*` |
| `build` | ubuntu-latest, JDK 21 temurin | `./gradlew :app:assembleDebug` | 无,成功时上传 `app-debug.apk` |

### 缓存策略

- `actions/setup-java@v4` 内置 `cache: gradle`,自动缓存 `~/.gradle/caches` 与 `.gradle`。
- 额外 `actions/cache@v4`,key:`gradle-${{ hashFiles('**/*.gradle.kts', '**/libs.versions.toml') }}`。

### 不在 CI 中编译 Pikafish

Pikafish 二进制由开发者本地用 NDK 编译后放入 `app/src/main/assets/pikafish/`,被 `.gitignore` 忽略。CI 跑 `assembleDebug` 时如果 assets 中没有二进制,`PikafishInstaller` 运行时会报错——但 CI 跑的单元测试不依赖此,只有真机/模拟器测试需要。

## release.yml 详解

M7 启动时补充。基本结构:

- 触发:`push: tags: ['v*']` 或 `workflow_dispatch`
- 配置 secrets:`SIGNING_KEY_ID`、`SIGNING_KEY`(base64)、`SIGNING_PASSWORD`
- 运行:`./gradlew :app:assembleRelease :app:bundleRelease`
- 上传:`app-release.apk` 与 `app-release.aab`
- 可选:`softprops/action-gh-release` 创建 GitHub Release

## Artifact 下载

GitHub 仓库 → Actions → 选择某次运行 → 滚到底部 Artifacts 区域下载。

artifact 保留时长:
- 测试/lint 报告:14 天
- APK:14 天(便于测试人员下载试装)

## PR 流程

1. 从 `main` 切出 feature 分支(如 `feature/M1-fen-parser`)。
2. 实现功能 + 配套单元测试 + 更新 `doc/dev-log.md`。
3. 细颗粒切多个 commit(单 commit 净变更 ≤ ~250 行)。
4. 推送到远端,创建 PR 到 main。
5. 等 CI 跑完(三个 job 全绿)。
6. 人工 review 后 squash merge 或 rebase merge。
7. 删除 feature 分支。

## 失败排查

- **unit-test 红**:下载 `unit-test-results` artifact,打开 `index.html` 看具体失败用例。
- **lint 红**:下载 `lint-report` artifact,打开 `lint-results-debug.html`。
- **build 红**:通常依赖前两个 job 已失败;先修前两个。
- **缓存失效**:加 `--no-configuration-cache` 跑一次。
