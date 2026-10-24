# CineFlow integration status — upgraded P1–P5 source bundle

- Original `MainActivity.kt`, TMDB, Trakt, Jellyfin, and Emby files are preserved.
- P1 addon engine sources and required OkHttp/DataStore dependencies are included.
- P2 catalog/search Compose components are included but not wired into `MainActivity.kt`.
- P3 metadata/source components are included.
- P4 source ranking and direct Media3 playback screen are included but not wired into navigation.
- P5 localhost Range server, stream registry, piece scheduler, backend interface, and scheduler tests are included.
- A concrete libtorrent4j adapter is not included; do not describe this bundle as complete torrent downloading/playback until one is implemented and verified.
- No build or tests are claimed as passing. Run GitHub Actions and fix compile errors before release.

- GitHub Actions workflow now runs `:app:testDebugUnitTest` before `:app:assembleDebug`.

- P6 subtitle parser/repository/UI and chunked direct-download repository are included.
- P6 components are not wired into existing player/source selection/navigation.
- Torrent downloads, resume, and persistent download queue are not implemented.
