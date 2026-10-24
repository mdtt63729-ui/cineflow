# Phase 6 — Subtitles and direct downloads

## Included
- Defensive SRT/WebVTT parser with input/cue limits.
- HTTPS/loopback-only subtitle fetcher with size caps and redirects disabled.
- Subtitle selection UI and reusable progress UI.
- Chunked direct-download repository with progress/cancellation callbacks.
- Android 10+ public `Downloads/CineFlow` via MediaStore; Android 7–9 app-specific external Downloads.
- Subtitle parser unit tests.

## Integration boundary
These are additive components; they are not wired into `MainActivity.kt`, P3 source selection or P4 player navigation. For playback, map a selected subtitle to Media3 `MediaItem.SubtitleConfiguration` before preparing the player, or use Media3 track-selection APIs.

## Limits
Direct URL downloads only. Torrent download queue, resume/range retry, and persistent queue are not included. Redirects are disabled. Use only content you are authorized to download.

## Acceptance
- [ ] SRT/WebVTT timestamps parse correctly.
- [ ] Oversized subtitle bodies are rejected.
- [ ] Downloads stream to storage in chunks and report progress.
- [ ] Cancellation removes partial output.
- [ ] GitHub Actions tests and debug build pass.
