# WearPod Phone Import Design

Date: 2026-03-27

## Goal

Replace the watch-first text-entry import flow with a lighter QR-assisted import flow that still preserves WearPod as a standalone watch app.

## Product decision

- Keep `manual RSS input` as a fallback only.
- Make `phone import` the primary path on the import screen.
- Do not require a phone companion app.
- Use a lightweight web relay instead of Android `Data Layer`.

## Flow

1. On watch, user opens `Import`.
2. User taps `Phone Import`.
3. Watch requests a one-time import session from the relay.
4. Watch shows:
   - QR code
   - 6-character short code
   - waiting state
5. Phone scans the QR code and opens a simple mobile page.
6. On phone, user:
   - pastes one RSS URL, or
   - uploads one OPML file
7. Relay parses candidate feed URLs and stores them in the session.
8. Watch polls the session until it becomes `SUBMITTED`.
9. Watch shows import preview:
   - new feeds
   - duplicates
   - invalid entries
10. User confirms import on watch.
11. Watch imports the feeds using the existing repository and RSS parser.

## Architecture

### Watch app

- `WearPodScreen.PhoneImport`
- `PhoneImportUiState`
- `ImportRelayClient`
- repository preview/import helpers

The watch remains the source of truth for actual subscription import.

### Relay

A lightweight Node/Express service provides:

- `POST /api/sessions`
- `GET /api/sessions/:sessionId`
- `POST /api/sessions/:sessionId/import`
- `GET /session/:sessionId` mobile import page
- `POST /enter-code` fallback for short-code entry

The relay only stores temporary import sessions. It does not fetch podcast metadata itself.

## Why this approach

- Better watch ergonomics than on-watch typing
- No mandatory companion app
- Supports both RSS and OPML from phone
- Keeps the watch app standalone for playback and library management

## Constraints

- Relay sessions currently use in-memory storage
- Production requires deploying the relay and setting `IMPORT_RELAY_API_BASE_URL`
- Local emulator testing uses HTTP cleartext to `10.0.2.2`
