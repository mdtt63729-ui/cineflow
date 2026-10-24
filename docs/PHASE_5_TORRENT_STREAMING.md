# Phase 5 — Torrent streaming primitives

## Delivered in this bundle
- `TorrentContentSource`: bounded byte-range bridge contract for a verified native torrent backend.
- `PieceScheduler`: backend-to-reader synchronization with explicit timeout/failure errors.
- `LocalRangeStreamServer`: loopback-only HTTP server with GET/HEAD, `Accept-Ranges`, single byte-range and suffix-range support, bounded headers, limited client pool, and chunked reads from the backend.
- `TorrentStreamRegistry`: registers local stream URLs and closes source/session resources on unregister.
- `TorrentBackend`: interface for the native libtorrent4j implementation.
- Unit tests for scheduler wait/timeout behavior.

## Important implementation boundary
This phase does **not** include a concrete native libtorrent4j adapter or an embedded torrent engine binary. The environment has no dependency/network access to verify a compatible Android artifact/API, and guessing native coordinates or JNI methods would make the full project unreliable. Therefore P5 provides the working localhost HTTP Range layer and a typed backend interface, not a complete magnet-to-stream torrent implementation. A compatible backend must implement `TorrentBackend` and `TorrentContentSource`; its piece callbacks must feed `PieceScheduler`.

## Security and content
The server binds only to `127.0.0.1` and is intended for Media3 loopback playback. Use torrent sources only when you are authorized to access and redistribute the content. This is not a catalog of streams and does not add any source provider.

## Integrating with Media3
1. Open a selected torrent source with your `TorrentBackend`.
2. Register the returned `TorrentContentSource` in `TorrentStreamRegistry`.
3. Convert the registered local stream into a direct descriptor before resolving: `source.copy(type = "direct", url = registered.url, infoHash = null)`, then pass that descriptor to `AddonPlaybackResolver`.
4. Close the `RegisteredTorrentStream` when playback ends or the source changes.
5. Keep P4's URL policy allowing loopback HTTP only.

## Acceptance gate
- [ ] Add a concrete Android-ABI-compatible libtorrent backend.
- [ ] Verify metadata/file-index mapping and hash checking.
- [ ] Verify reads wait for requested pieces and fail visibly on timeout.
- [ ] Test HTTP 200, 206, 416, HEAD, suffix ranges, disconnects, and large files.
- [ ] Run `:app:testDebugUnitTest` and `:app:assembleDebug` in GitHub Actions.
- [ ] Wire source selection to the torrent backend and player lifecycle.
