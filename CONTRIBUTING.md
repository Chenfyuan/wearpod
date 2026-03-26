# Contributing to WearPod

[中文说明](#中文说明)

Thanks for contributing to WearPod.

This repository is still a product-focused prototype, so the goal of every contribution should be to keep the app useful on a round Wear OS watch first. Prefer small, reviewable changes over broad refactors.

## Before you start

- Read [README.md](./README.md) first for product scope and current limitations.
- Check existing issues before starting new work.
- If your change is user-visible, keep the product scope aligned with a standalone, watch-first podcast client.

## Development setup

Requirements:

- JDK 17
- Android Studio with Wear OS support
- Android SDK matching the project config
- A Wear OS emulator or physical watch

Common commands:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

If Gradle wrapper downloads are blocked in your environment:

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

Useful extra validation when release-related files change:

```bash
gradle assembleRelease --no-daemon
```

## What good contributions look like

- Keep the UI glanceable on a round display.
- Prefer simple, tap-friendly interactions.
- Avoid phone-dependent flows.
- Preserve the current product direction:
  - direct RSS import
  - watch-first playback
  - offline listening
- Avoid unrelated cleanup in the same PR.

## UI and interaction expectations

When changing watch UI:

- Use scrollable layouts instead of stacking too much fixed chrome.
- Keep controls readable inside the safe area of a round watch.
- Avoid heavy bottom navigation patterns.
- Include screenshots when behavior or visuals change.

## Pull requests

Please keep PRs focused and include:

- what changed
- why it changed
- how you validated it
- screenshots or recordings for UI changes
- any known risks or follow-up work

Before opening a PR, run:

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

If the PR changes playback, downloads, background refresh, or UI behavior, also verify it on a Wear OS emulator or device.

The repository already includes:

- issue templates under [`.github/ISSUE_TEMPLATE`](./.github/ISSUE_TEMPLATE)
- a PR template at [`.github/pull_request_template.md`](./.github/pull_request_template.md)

## Documentation

Update docs when behavior changes, especially if you touch:

- onboarding or import flows
- playback controls
- downloads or background refresh
- release or build instructions

Relevant docs:

- [README.md](./README.md)
- [README.zh-CN.md](./README.zh-CN.md)
- [docs/releases](./docs/releases)

## Release notes

For user-visible changes that should appear in the next release, update the release notes drafts under [docs/releases](./docs/releases).

## 中文说明

感谢你为 WearPod 做贡献。

当前仓库仍然是一个偏产品原型阶段的项目，所以每次修改都应尽量围绕一个目标：让它在圆形 Wear OS 手表上真正可用。请优先提交小而清晰、便于评审的改动。

### 开始之前

- 先阅读 [README.md](./README.md) 或 [README.zh-CN.md](./README.zh-CN.md)
- 开工前先看现有 issue，避免重复工作
- 如果你的改动会影响用户体验，请确保它仍然符合当前产品范围：
  - 独立运行
  - 基于 RSS
  - 手表优先
  - 离线收听

### 开发环境

需要：

- JDK 17
- 带 Wear OS 支持的 Android Studio
- 对应版本的 Android SDK
- Wear OS 模拟器或真机

常用命令：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

如果你的网络环境无法正常使用 wrapper：

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

如果你改了发布相关配置，建议额外执行：

```bash
gradle assembleRelease --no-daemon
```

### 我们希望看到的改动

- UI 在圆形表盘上依然清晰、可扫读
- 操作尽量简单，适合手表点击
- 不引入依赖手机才能完成的流程
- 不偏离当前产品方向
- 不把无关重构混在同一个 PR 里

### UI 与交互约束

如果你改了手表端界面：

- 优先使用整页滚动布局，避免堆太多固定头部
- 注意圆形屏幕的安全区域
- 避免厚重的底部导航
- UI 有变化时，请附上截图或录屏

### Pull Request 要求

请尽量在 PR 中说明：

- 改了什么
- 为什么改
- 怎么验证
- 如果是 UI 改动，附上截图或录屏
- 可能的风险和后续工作

提交 PR 前，至少运行：

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

如果改动涉及播放、下载、后台刷新或手表端 UI，请额外在 Wear OS 模拟器或真机上验证。

仓库已经提供：

- issue 模板：[`.github/ISSUE_TEMPLATE`](./.github/ISSUE_TEMPLATE)
- PR 模板：[`.github/pull_request_template.md`](./.github/pull_request_template.md)

### 文档更新

如果行为变了，请同步更新文档，尤其是这些内容：

- 导入和 onboarding
- 播放控制
- 下载与后台刷新
- 构建和发布说明

相关文档：

- [README.md](./README.md)
- [README.zh-CN.md](./README.zh-CN.md)
- [docs/releases](./docs/releases)

### 版本说明

如果你的改动是用户可感知的，并且应该进入下一版 release，请同步更新 [docs/releases](./docs/releases) 里的版本说明草稿。
