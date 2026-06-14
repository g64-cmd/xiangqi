# 发布

> 本文件描述发布流程、License 合规、签名密钥管理、致谢。

## License 合规

本项目采用 **GPL-3.0**(与根目录 `LICENSE` 一致)。

### GPL-3.0 要求

- 分发二进制时必须提供源码或源码获取指针。
- 修改 Pikafish 源码后,修改部分也必须以 GPL-3.0 发布。
- App 中需明示使用 Pikafish + 仓库链接 + GPL 声明。

### 本项目的合规策略

- **App 整体 GPL-3.0**。
- **不修改 Pikafish 源码**,只编译官方 release tag(避免衍生作品问题)。
- **About 页** 显示:
  - Pikafish 名称、版本号
  - Pikafish 仓库链接(https://github.com/official-pikafish/Pikafish)
  - Pikafish 协议(GPL-3.0)
  - NNUE 网络来源(ODbL)
- **README** 提供"如何获取源码"说明。

### 隐私声明

- 本 App 本地运行,**不联网**。
- 不收集任何用户数据。
- 不请求任何敏感权限。

## 签名密钥

### 开发期

- Debug 签名由 AGP 自动管理(`~/.android/debug.keystore`)。
- 不需要手动配置。

### 发布期(M7 准备时补充)

- 生成发布用 keystore:
  ```bash
  keytool -genkey -v -keystore xiangqi-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias xiangqi
  ```
- keystore 文件本地保存,**不要提交**(`.gitignore` 已忽略 `*.jks`)。
- 在 GitHub Actions secrets 中配置(用 base64 编码):
  - `SIGNING_KEY_ID`
  - `SIGNING_KEY`(base64 编码的 .jks 内容)
  - `SIGNING_PASSWORD`
- `app/build.gradle.kts` 中 `signingConfigs` 从环境变量读取,缺失时跳过签名(便于本地构建)。

## 版本号

- `versionCode` —— 整数,每次发布递增(1, 2, 3, ...)。
- `versionName` —— 字符串,语义化版本(`1.0.0`, `1.1.0`, `1.1.1`)。

M0–M6 开发期保持 `versionCode=1, versionName="0.1.0-dev"`。
正式发布(M7)时改为 `versionCode=1, versionName="1.0.0"`。

## 致谢

> M7 完成 About 页时填充。

### 第三方组件

| 组件 | 版本 | 协议 | 用途 |
|---|---|---|---|
| Pikafish | TBD | GPL-3.0 | 中国象棋 UCI 引擎 |
| Pikafish NNUE | TBD | ODbL | 神经网络权重 |
| Jetpack Compose | 2026.02.01 BOM | Apache-2.0 | UI 框架 |
| Hilt | TBD | Apache-2.0 | 依赖注入 |
| ... | ... | ... | ... |

## 发布前检查清单

- [ ] 所有 M0–M7 测试通过
- [ ] CI 红/绿状态全绿
- [ ] `assembleRelease` 成功
- [ ] APK 体积审计(<150MB)
- [ ] 在 Android 11 / 12 / 13 / 14 / 15 真机各跑一局
- [ ] About 页 License 与致谢完整
- [ ] 签名密钥配置正确
- [ ] README 更新版本号与下载链接

## 后续待补充

- [ ] 应用商店上架流程(如国内应用宝/华为应用市场)
- [ ] Play Console 上架流程(若面向海外)
- [ ] 灰度发布策略
