# WearPod

[![Release](https://img.shields.io/github/v/release/Chenfyuan/wearpod?display_name=tag)](https://github.com/Chenfyuan/wearpod/releases)
[![Android](https://github.com/Chenfyuan/wearpod/actions/workflows/android.yml/badge.svg)](https://github.com/Chenfyuan/wearpod/actions/workflows/android.yml)
[![License](https://img.shields.io/github/license/Chenfyuan/wearpod)](./LICENSE)

[中文文档](./README.zh-CN.md)

> A standalone, watch-first podcast player for Wear OS by SJTech.
>
> Import RSS, listen offline, and leave the phone behind.

![WearPod cover](./docs/assets/wearpod-cover.svg)

WearPod by SJTech is a standalone Wear OS podcast app prototype built for round watches first.

It does not depend on a phone companion app. The current goal is simple: import a podcast feed by RSS URL directly on the watch, browse episodes, play audio, download episodes offline, and keep the core listening loop usable on a small wearable screen.

## Preview

![WearPod home screen](./docs/assets/wearpod-home.png)

| Subscriptions | Import | Podcast detail |
| --- | --- | --- |
| ![WearPod subscriptions screen](./docs/assets/wearpod-subscriptions.png) | ![WearPod import screen](./docs/assets/wearpod-import.png) | ![WearPod podcast detail screen](./docs/assets/wearpod-detail.png) |

| Player | Downloads | Download & refresh settings |
| --- | --- | --- |
| ![WearPod player screen](./docs/assets/wearpod-player.png) | ![WearPod downloads screen](./docs/assets/wearpod-downloads.png) | ![WearPod download settings screen](./docs/assets/wearpod-settings.png) |

## What WearPod does today

- Import public podcast RSS feeds directly on the watch
- Manage subscriptions without a phone companion app
- Favorite subscriptions and surface them on the home screen
- Browse episode lists with filters for `All`, `Unplayed`, and `Downloaded`
- Stream audio with `Media3`
- Download episodes for offline playback with `WorkManager`
- View download queue, failures, and completed offline episodes
- Configure download behavior:
  - Wi-Fi only downloads
  - auto-download latest `0-3` episodes
  - background auto-download
- Run periodic background feed refresh every `6`, `12`, or `24` hours
- Control playback queue:
  - previous / next episode
  - switch within the current queue
  - seek backward / forward
  - cycle playback speed
- Use a sleep timer from the player for `15`, `30`, or `60` minutes

## Product scope

WearPod is intentionally a watch-first client, not a podcast platform.

Current scope:

- standalone Wear OS app
- public RSS feeds
- lightweight on-watch library and playback
- offline listening on the watch

Out of scope for this iteration:

- phone sync
- cloud accounts
- podcast discovery backend
- private or paid feed authentication
- OPML import/export

## App flow

The current app is organized around three root watch pages:

- `Home`: continue listening, favorites, quick context
- `Subscriptions`: import and manage subscribed feeds
- `Downloads`: queue, failures, offline library, and refresh settings

Secondary screens:

- `Import`
- `Podcast detail`
- `Player`
- `Download & refresh settings`

## Tech stack

- Kotlin
- Jetpack Compose for Wear OS
- Media3
- WorkManager
- Coil 3
- JSON file persistence in app-private storage

## Requirements

- Android Studio with Wear OS support
- JDK 17
- Android SDK / build tools compatible with:
  - `compileSdk = 36`
  - `minSdk = 30`
  - `targetSdk = 36`
- A Wear OS emulator or physical watch

## Getting started

### 1. Build the app

If the Gradle wrapper can download normally in your network environment:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

If wrapper downloads are blocked, use the system Gradle binary instead:

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

### GitHub Actions release build

The repository includes an Android workflow that builds an unsigned release APK:

- runs on `push`, `pull_request`, `workflow_dispatch`, and GitHub `release`
- uploads `app-release-unsigned.apk` as a workflow artifact
- attaches the same unsigned APK to the GitHub Release page when a release is published

### 2. Install on a Wear OS emulator or device

```bash
adb devices
adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Launch the app

```bash
adb -s <device-id> shell am start -n com.sjtech.wearpod/.MainActivity
```

## Development notes

### Debug sample feed

`DEBUG` builds auto-import a sample feed if the app has no existing subscriptions yet:

- `https://feed.xyzfm.space/xpa79uvcn9lw`

This is only meant to make emulator testing faster.

### Persistence model

The app currently stores state in a single JSON file under app-private storage. That includes:

- subscriptions
- episodes
- favorites
- playback memory
- download settings
- sleep timer state

This keeps the prototype simple, but it is not the long-term persistence strategy.

### Background work

WearPod currently uses:

- `EpisodeDownloadWorker` for per-episode downloads
- `SubscriptionRefreshWorker` for periodic subscription refresh

Background refresh only runs when:

- it is enabled in settings
- there is at least one subscription
- the device has connectivity
- the battery is not low

## Project structure

- `app/src/main/java/com/sjtech/wearpod/ui`
  - Compose screens, interaction logic, and watch-specific presentation
- `app/src/main/java/com/sjtech/wearpod/ui/components`
  - reusable watch UI primitives
- `app/src/main/java/com/sjtech/wearpod/data`
  - models, repository, store, and RSS parsing
- `app/src/main/java/com/sjtech/wearpod/playback`
  - `MediaSessionService` and player gateway
- `app/src/main/java/com/sjtech/wearpod/download`
  - offline download scheduling and workers
- `app/src/main/java/com/sjtech/wearpod/sync`
  - periodic subscription refresh scheduling and worker
- `docs/plans`
  - design notes and implementation planning artifacts

## Current limitations

- Public RSS feeds only
- No authentication for private or paid feeds
- No OPML import/export
- No cloud sync across watch / phone / web
- State persistence is file-based rather than Room/DataStore-backed
- Audio output guidance for Bluetooth / unsuitable output still needs a more explicit UX

## Validation

Verified locally with:

```bash
gradle assembleDebug --no-daemon
gradle testDebugUnitTest --no-daemon
```

## Release notes

- [v0.1.0](./docs/releases/v0.1.0.md)
- [GitHub repo copy](./docs/github-repo-copy.md)

## License

This project is released under the [MIT License](./LICENSE).

## Roadmap

The next meaningful steps are:

1. OPML import/export
2. Private / paid feed support
3. Search
4. Better audio output and Bluetooth guidance
5. Stronger persistence and recovery behavior
