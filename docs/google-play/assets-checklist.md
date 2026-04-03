# Play Store Assets Checklist

## Ready now
- Feature graphic (en-US): `/Users/linwj44/wearpod/docs/google-play/assets/wearpod-feature-graphic.en-US.png` (`1024x500`)
- Feature graphic (zh-CN): `/Users/linwj44/wearpod/docs/google-play/assets/wearpod-feature-graphic.zh-CN.png` (`1024x500`)
- High-res icon draft: `/Users/linwj44/wearpod/docs/google-play/assets/wearpod-play-icon-512.png` (`512x512`)
- Wear OS screenshots (zh-CN):
  - `/Users/linwj44/wearpod/docs/assets/wearpod-home.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/wearpod-player.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/wearpod-queue.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/wearpod-subscriptions.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/wearpod-detail.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/wearpod-downloads.png` (`530x530`)
- Wear OS screenshots (en-US):
  - `/Users/linwj44/wearpod/docs/assets/en/wearpod-home.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/en/wearpod-player.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/en/wearpod-queue.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/en/wearpod-subscriptions.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/en/wearpod-detail.png` (`530x530`)
  - `/Users/linwj44/wearpod/docs/assets/en/wearpod-downloads.png` (`530x530`)

## Review before upload
- Confirm the 512x512 icon matches the in-app brand direction you want to keep long term
- Confirm both screenshot sets reflect the latest release build
- Keep screenshot language aligned with the localized listing you upload
- Use the matching localized feature graphic for each language listing
- Make sure no screenshot shows debug-only behavior or placeholder data

## Regeneration
- Generate Play graphics with:

```bash
cd /Users/linwj44/wearpod
python3 scripts/generate-play-assets.py
```
