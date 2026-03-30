# WearPod

[![Release](https://img.shields.io/github/v/release/Chenfyuan/wearpod?display_name=tag)](https://github.com/Chenfyuan/wearpod/releases)
[![Android](https://github.com/Chenfyuan/wearpod/actions/workflows/android.yml/badge.svg)](https://github.com/Chenfyuan/wearpod/actions/workflows/android.yml)
[![License](https://img.shields.io/github/license/Chenfyuan/wearpod)](./LICENSE)

[中文文档](./README.zh-CN.md)

> A standalone, watch-first podcast player for Wear OS by SJTech.
>
> Import by QR, listen offline, and leave the phone behind.

![WearPod cover](./docs/assets/wearpod-cover.svg)

WearPod by SJTech is a standalone Wear OS podcast app prototype built for round watches first.

It does not depend on a phone companion app. The current goal is simple: import feeds through a phone-assisted QR flow or direct RSS fallback, browse episodes, play audio, download episodes offline, and keep the core listening loop usable on a small wearable screen.

## Preview

| Home | Subscriptions | Podcast detail |
| --- | --- | --- |
| ![WearPod home screen](./docs/assets/wearpod-home.png) | ![WearPod subscriptions screen](./docs/assets/wearpod-subscriptions.png) | ![WearPod podcast detail screen](./docs/assets/wearpod-detail.png) |

| Player | Queue | Downloads |
| --- | --- | --- |
| ![WearPod player screen](./docs/assets/wearpod-player.png) | ![WearPod current queue screen](./docs/assets/wearpod-queue.png) | ![WearPod downloads screen](./docs/assets/wearpod-downloads.png) |

## What WearPod does today

- Import public podcast RSS feeds directly on the watch
- Start a phone-assisted QR import session for RSS or OPML input on mobile
- Start a phone-assisted QR export session and download an OPML backup on mobile
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
  - adjust media volume from player controls and hardware volume keys
- Use a sleep timer from the player for `15`, `30`, or `60` minutes

## Product scope

WearPod is intentionally a watch-first client, not a podcast platform.

Current scope:

- standalone Wear OS app
- public RSS feeds
- lightweight on-watch library and playback
- phone-assisted import for awkward text entry
- offline listening on the watch

Out of scope for this iteration:

- phone sync
- cloud accounts
- podcast discovery backend
- private or paid feed authentication

## App flow

The current app is organized around three root watch pages:

- `Home`: continue listening, favorites, quick context
- `Subscriptions`: import and manage subscribed feeds
- `Downloads`: queue, failures, offline library, and refresh settings

Secondary screens:

- `Import`
- `Phone import`
- `Podcast detail`
- `Player`
- `Download & refresh settings`

## Tech stack

- Kotlin
- Jetpack Compose for Wear OS
- Media3
- WorkManager
- Coil 3
- Room for subscriptions, episodes, and favorites
- DataStore Preferences for playback memory, download settings, and sleep timer

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

### 4. Run the phone import relay locally

The QR-based phone import flow depends on the lightweight relay in [`relay/`](/Users/linwj44/wearpod/relay).

```bash
cd relay
npm install
npm start
```

For emulator testing, the default local setup is:

- watch API base: `http://10.0.2.2:8787`
- relay public URL: `http://localhost:8787`

For a real deployment, point both the app and the relay to a public HTTPS URL:

```bash
./gradlew assembleDebug -PwearpodImportRelayApiBaseUrl=https://your-relay.example.com
PUBLIC_BASE_URL=https://your-relay.example.com npm start
```

### 5. Re-deploy the relay on a server

The repo includes a reusable deployment script for Docker-based servers:

```bash
bash scripts/deploy-relay.sh
```

You can also pin a specific Git ref or commit:

```bash
bash scripts/deploy-relay.sh 327d431d9a1469c588dbefd8ebae058c70e9884e
```

Useful environment overrides:

```bash
PUBLIC_BASE_URL=https://wearpod.linsblog.cn \
HOST_BIND=127.0.0.1 \
PORT=8787 \
bash scripts/deploy-relay.sh main
```

### 6. Prepare release signing

The project supports local-only release signing without committing secrets to git.

1. Copy the template:

```bash
cp release-signing.properties.example release-signing.properties
```

2. Fill in your real keystore path and passwords in `release-signing.properties`.

3. Build a signed release when you are ready:

```bash
./gradlew assembleRelease
```

The real `release-signing.properties` file is ignored by git. You can also provide the same values through Gradle properties or environment variables:

- `WEARPOD_RELEASE_STORE_FILE`
- `WEARPOD_RELEASE_STORE_PASSWORD`
- `WEARPOD_RELEASE_KEY_ALIAS`
- `WEARPOD_RELEASE_KEY_PASSWORD`

## Development notes

### Debug sample feed

`DEBUG` builds auto-import a sample feed if the app has no existing subscriptions yet:

- `https://feed.xyzfm.space/xpa79uvcn9lw`

This is only meant to make emulator testing faster.

### Phone import relay

The relay keeps a short-lived import session in memory and lets the phone submit:

- one RSS URL, or
- one OPML file

The watch stays responsible for the actual feed fetch and import into local storage.

### Persistence model

The app now uses a split persistence model:

- `Room` stores subscriptions, episodes, and favorite relationships
- `DataStore Preferences` stores playback memory, download settings, and sleep timer state

On first launch after upgrading from older builds, WearPod automatically migrates the legacy `wearpod_state.json` file into the new storage model.

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
- QR import currently depends on a separately running relay service
- No cloud sync across watch / phone / web
- Storage is now split across Room and DataStore, but schema migration coverage is still minimal beyond the initial legacy JSON import
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

1. OPML export
2. Private / paid feed support
3. Search
4. Better audio output and Bluetooth guidance
5. Stronger persistence and recovery behavior
