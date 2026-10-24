# Phase 4 — Source ranking and direct playback

## Scope
1. Rank addon stream descriptors predictably.
2. Validate direct URLs before passing them to Media3.
3. Play validated direct links with Media3.
4. Isolate unplayable sources and surface a recoverable error.
5. Keep torrent descriptors out of direct playback until P5.

## Ranking
Scores explicit quality labels (4K/UHD, 2160p, 1080p, 720p, 480p), seed count when present, and known direct transport. Unknown values are neutral. Original order breaks ties. This is a heuristic, not a guarantee of quality, safety, or legality.

## URL policy
Accept HTTPS, or HTTP only for localhost/127.0.0.1/::1. Reject credentials, fragments, unsupported schemes, missing hosts, malformed URLs, and torrent descriptors. No arbitrary addon-supplied headers are forwarded. Media3 can follow redirects at the transport layer; high-assurance deployments should revalidate redirects with a custom DataSource before remote playback.

## Integration
- P1 must be present for addon models.
- P3 must be present for `AddonSource`.
- Add `AddonPlaybackScreen` to existing navigation from the source-selection screen. This bundle deliberately does not rewrite `MainActivity.kt`.
- On source tap, call `AddonPlaybackResolver.resolve(source)`. For `Playable`, pass its item to `AddonPlaybackScreen`. For `Rejected`, show its reason and allow another source.
- Mark torrent descriptors “P5 required”; do not pass them to Media3.

## Acceptance checklist
- [ ] HTTPS direct stream opens.
- [ ] Loopback HTTP stream opens for local development.
- [ ] `file:`, `content:`, `javascript:`, credentialed URLs, malformed URLs, and non-loopback HTTP are rejected.
- [ ] Torrent descriptors are rejected with a clear message.
- [ ] Buffering, player errors, and back navigation are handled.
- [ ] Player releases when the screen is disposed.
- [ ] Existing navigation/theme/API files remain intact.
- [ ] GitHub Actions build and tests pass.
