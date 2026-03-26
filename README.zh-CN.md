# WearPod

[English README](./README.md)

> 一个由 SJTech 打造的独立 Wear OS 手表播客播放器。
>
> 直接导入 RSS、离线收听、尽量不依赖手机。

![WearPod 封面图](./docs/assets/wearpod-cover.svg)

WearPod 是 SJTech 打造的、面向圆形手表优先设计的独立 Wear OS 播客应用原型。

它不依赖手机 companion app。当前版本的目标很明确：用户可以直接在手表上输入 RSS 地址导入播客、浏览节目列表、播放音频、下载离线节目，并在小屏设备上完成完整的播客消费闭环。

## 预览

![WearPod 首页预览](./docs/assets/wearpod-home.png)

| 订阅 | 导入订阅 | 播客详情 |
| --- | --- | --- |
| ![WearPod 订阅页预览](./docs/assets/wearpod-subscriptions.png) | ![WearPod 导入页预览](./docs/assets/wearpod-import.png) | ![WearPod 播客详情页预览](./docs/assets/wearpod-detail.png) |

| 播放器 | 离线 | 下载与刷新设置 |
| --- | --- | --- |
| ![WearPod 播放器预览](./docs/assets/wearpod-player.png) | ![WearPod 离线页预览](./docs/assets/wearpod-downloads.png) | ![WearPod 下载与刷新设置预览](./docs/assets/wearpod-settings.png) |

## 当前已支持的能力

- 直接在手表上导入公开播客 RSS
- 不依赖手机管理订阅
- 收藏播客，并在首页展示收藏内容
- 浏览节目列表，并支持 `全部`、`未播`、`已下载` 三种筛选
- 基于 `Media3` 进行在线播放
- 基于 `WorkManager` 下载离线节目
- 查看下载队列、下载失败项和已下载列表
- 配置下载行为：
  - 仅 Wi-Fi 下载
  - 自动下载最新 `0-3` 期
  - 后台自动下载
- 按 `6`、`12`、`24` 小时执行后台定时刷新
- 控制播放队列：
  - 上一集 / 下一集
  - 切换当前队列中的节目
  - 快退 / 快进
  - 切换播放倍速
- 在播放器中使用睡眠定时：
  - `15 / 30 / 60` 分钟

## 产品范围

WearPod 的定位是 `watch-first` 播客客户端，而不是播客平台。

当前范围：

- 独立运行的 Wear OS 应用
- 基于公开 RSS 的播客订阅
- 手表端轻量播客库与播放体验
- 手表端离线收听

当前版本明确不做：

- 手机同步
- 云端账号系统
- 播客发现与推荐后台
- 私有或付费订阅鉴权
- OPML 导入导出

## 页面结构

当前应用由 3 个顶层页面组成：

- `首页`：继续播放、收藏内容、快捷上下文
- `订阅`：导入和管理播客订阅
- `离线`：下载队列、失败列表、离线库、下载与刷新设置

二级页面包括：

- `导入订阅`
- `播客详情`
- `播放器`
- `下载与刷新设置`

## 技术栈

- Kotlin
- Jetpack Compose for Wear OS
- Media3
- WorkManager
- Coil 3
- 基于应用私有目录的 JSON 文件持久化

## 环境要求

- 安装了 Wear OS 支持的 Android Studio
- JDK 17
- 可用的 Android SDK / build tools，满足：
  - `compileSdk = 36`
  - `minSdk = 30`
  - `targetSdk = 36`
- Wear OS 模拟器或真机

## 快速开始

### 1. 构建应用

如果当前网络环境下 Gradle wrapper 可以正常下载依赖：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

如果 wrapper 无法下载分发包，可以直接使用系统安装的 Gradle：

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

### 2. 安装到 Wear OS 模拟器或设备

```bash
adb devices
adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. 启动应用

```bash
adb -s <device-id> shell am start -n com.sjtech.wearpod/.MainActivity
```

## 开发说明

### Debug 示例订阅源

在 `DEBUG` 构建中，如果应用当前还没有任何订阅，会自动导入一个示例 RSS：

- `https://feed.xyzfm.space/xpa79uvcn9lw`

它只用于加快模拟器体验，不是正式产品逻辑的一部分。

### 持久化模型

当前版本会将状态写入应用私有目录下的单个 JSON 文件，其中包括：

- subscriptions
- episodes
- favorites
- playback memory
- download settings
- sleep timer state

这种方式适合原型阶段，但不是长期的最终存储方案。

### 后台任务

当前后台任务包括：

- `EpisodeDownloadWorker`：单集下载
- `SubscriptionRefreshWorker`：周期性订阅刷新

后台刷新只会在以下条件满足时运行：

- 设置中已开启后台定时刷新
- 当前至少存在一个订阅
- 设备有可用网络
- 当前不是低电量状态

## 项目结构

- `app/src/main/java/com/sjtech/wearpod/ui`
  - Compose 页面、交互逻辑、手表端 UI 组织
- `app/src/main/java/com/sjtech/wearpod/ui/components`
  - 可复用的手表 UI 组件
- `app/src/main/java/com/sjtech/wearpod/data`
  - 数据模型、仓储、存储、RSS 解析
- `app/src/main/java/com/sjtech/wearpod/playback`
  - `MediaSessionService` 与播放器控制层
- `app/src/main/java/com/sjtech/wearpod/download`
  - 离线下载调度与 Worker
- `app/src/main/java/com/sjtech/wearpod/sync`
  - 周期性订阅刷新调度与 Worker
- `docs/plans`
  - 设计说明和实现计划文档

## 当前限制

- 仅支持公开 RSS
- 暂不支持私有或付费订阅鉴权
- 还没有 OPML 导入导出
- 不支持手表 / 手机 / Web 云端同步
- 当前仍是文件持久化，而不是 Room / DataStore 组合
- 蓝牙与音频输出状态的提示体验仍然可以继续完善

## 本地验证

本地已使用以下命令验证：

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

## 版本说明

- [v0.1.0](./docs/releases/v0.1.0.zh-CN.md)
- [GitHub 仓库文案](./docs/github-repo-copy.md)

## 开源协议

本项目使用 [MIT License](./LICENSE)。

## 后续路线

接下来最值得继续推进的方向：

1. OPML 导入导出
2. 私有 / 付费订阅支持
3. 搜索
4. 更明确的蓝牙与音频输出引导
5. 更稳健的数据持久化与恢复能力
