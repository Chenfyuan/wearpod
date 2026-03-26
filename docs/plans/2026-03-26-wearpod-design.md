# WearPod Design Notes

## Goal

Build a standalone Wear OS podcast app that does not depend on a phone companion app. The first version should let a user import a podcast by RSS URL, browse subscriptions and episodes, play audio, and keep a small offline library on the watch.

## Product interpretation from Figma

The Figma file describes a watch-first podcast flow, not a reduced phone companion:

- `首页`: continue listening + recent subscriptions + shortcut actions
- `订阅`: library entry point for subscribed feeds
- `导入订阅`: manual RSS import
- `节目列表`: per-podcast episode browser
- `播放控制`: simplified centered player
- `离线下载`: storage and download management

## Architecture

### App shell

- Single Android app module
- Compose UI with a custom circular visual shell
- Lightweight in-app navigation managed by a single `WearPodViewModel`

### Data layer

- `PodcastFeedParser`: parses RSS 2.0 / iTunes-style feeds
- `FeedNetworkClient`: direct HTTP fetches from the watch
- `WearPodStore`: JSON file persistence inside app storage
- `WearPodRepository`: source of truth for subscriptions, episodes, playback memory, and offline metadata

### Playback

- `PlaybackService`: `MediaSessionService` hosting an `ExoPlayer`
- `PlayerGateway`: app-side controller for queue setup, transport controls, and periodic playback persistence

### Offline downloads

- `EpisodeDownloadScheduler`: queues per-episode downloads through WorkManager
- `EpisodeDownloadWorker`: downloads audio files into app-private storage

## Deliberate scope cuts

- No account system
- No sync with phones or cloud backends
- No discovery backend
- No authenticated/private feed support
- No OPML import in this iteration

## Validation

Verified locally with:

- `gradle testDebugUnitTest assembleDebug --no-daemon`

## Known follow-ups

- Move persistence from JSON file storage to Room if the data model grows
- Add OPML import/export
- Add proper unsuitable-audio-output messaging and Bluetooth routing UX
- Add richer playback queue recovery after process death
