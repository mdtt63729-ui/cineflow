# CineFlow — modular Android movie & TV app

Native Android app built with Kotlin, Jetpack Compose, Material 3, Retrofit/Gson, Coil, and AndroidX Media3. This update includes Phase 1 (TMDB metadata, configurable authorized catalogue, local watchlist) and Phase 2 (Jellyfin personal media server connection, library browsing, and direct server playback). It is not a claim that all 16 repositories or every optional provider have been integrated.

## Current integration status

| Integration | Status | What works / what is still needed |
|---|---|---|
| TMDB v3 metadata | Implemented in app | Trending today, popular movies, popular TV, multi-search, poster/backdrop mapping. Requires your own TMDB v3 API key. Metadata never supplies playback URLs. |
| Custom authorized catalogue API | Implemented | `home`, `search`, `detail`, and `stream` endpoints described below. Only HTTPS base URLs and HTTPS playback URLs are accepted. |
| Local watchlist | Implemented | IDs and known movie records persist in app preferences. |
| Media3 player | Implemented | Plays sample video or an authorized HTTPS URL returned by the configured catalogue API. TMDB-only entries correctly show “Metadata only”. |
| Jellyfin personal media server | Phase 2 implemented in source | HTTPS sign-in, saved session token, library views/items, artwork URLs, search across loaded library items, and direct server stream URLs. Needs live-server testing. |
| Plex / Emby | Adapter foundations | UI wiring, token handling and live playback validation remain. |
| Stremio / Nuvio / CloudStream / scraper providers | Not integrated | Requires per-addon contract and licensing review; no generic universal scraper is assumed. |
| Trakt | Public API adapter foundation | Public trending, popular, and search endpoints for movies and shows; no OAuth or personal-account sync. |
| Simkl / Kodi / IPTV / debrid | Not integrated | Independent auth/API and permission work remains. |
| Anime, manga, music, podcasts, audiobooks | Out of scope for movie MVP | Separate modules/adapters only if product scope is expanded. |

## Configure TMDB

1. Create a TMDB account and obtain a v3 API key through your account's API settings.
2. Open CineFlow → Settings → Integrations.
3. Paste the key into **TMDB API key (v3)** and save.
4. With a working internet connection, the home feed loads trending, popular movies and popular TV; Search uses TMDB multi-search.

The app uses the official API host `https://api.themoviedb.org/3/` and image host `https://image.tmdb.org/t/p/`. A key embedded in an APK is not a secret; use a backend if you need to protect a privileged credential. Do not commit personal keys to Git.

TMDB attribution/branding requirements apply. Review current terms and attribution requirements at https://developer.themoviedb.org/docs before public distribution.


## Connect a personal Jellyfin server (Phase 2)

1. Open **Settings → Integrations**.
2. Enter your own Jellyfin server HTTPS URL, username and password.
3. Tap **Connect to Jellyfin**. On success, CineFlow clears the password field and holds the returned session token in memory.
4. Tap **Save settings**. CineFlow then loads the server's library views and movie/series items on Home. Search also filters the currently loaded Jellyfin library.
5. Open an item and choose **Watch now** to use the Jellyfin server stream endpoint. Transcoding and playback depend on server configuration and media compatibility.

The password is not persisted. The session token and user ID are stored in app-private SharedPreferences in this phase; a production release should move tokens to Android Keystore-backed encrypted storage and add a disconnect/revoke flow. Use a low-privilege account and only connect to a server you own or are authorized to access. HTTPS is required by this implementation.

## Authorized catalogue backend contract

In Settings, configure your own HTTPS base URL, with a trailing slash recommended. The adapter expects:

- `GET home` → `{ "sections": [{ "title": "Trending", "items": [Movie] }] }`
- `GET search?q=...` → `{ "items": [Movie] }`
- `GET detail/{id}` → a `Movie` JSON object
- `GET stream/{id}` → `{ "url": "https://your-authorized-cdn.example/video.mp4", "title": "Example" }`

Movie fields: `id`, `title`, `overview`, `posterUrl`, `backdropUrl`, `year`, `rating`, `kind`, `streamUrl`. Keep the metadata provider and stream provider independent. The `streamUrl` field may be empty; do not fabricate quality, codecs, source availability, or video URLs.

## Build APK with GitHub Actions

1. Upload this project to a GitHub repository.
2. Enable Actions if prompted.
3. Push to `main` or manually run **Build Android APK**.
4. Download the `CineFlow-debug-apk` artifact from the workflow run.

Local build requirements: JDK 17 and Android SDK 35. The current execution environment has Java but no Gradle executable, so this source update has not been compiled in this session. GitHub Actions is the intended build validation step.

## Security and privacy

- Do not put private backend secrets or server passwords in source code.
- API errors should not log tokens or full signed playback URLs.
- Keep server connectors isolated; each service has its own authentication and response schema.
- Before reusing code from a public repository, inspect its LICENSE and dependency licenses. A public GitHub repository does not grant blanket reuse rights.
- Only configure media sources you own or are authorized to access.

## Phase 3 — playback progress and Continue watching

- Playback position and duration are sampled every five seconds and saved locally in app-private SharedPreferences.
- Leaving the player also saves the current position; reopening the same item seeks to the saved position.
- Home shows a **Continue watching** shelf ordered by the most recently updated playback progress.
- Entries are removed from progress when playback finishes or reaches the final 15 seconds.
- Progress is local to this device in Phase 3. It is not yet synchronized to Jellyfin's server-side playback sessions, Trakt, or another device. This is a foundation, not a claim of cross-device sync.

Playback resume and real-server behavior still require GitHub Actions compilation and device testing. Never use CineFlow to access media without authorization.


## Phase 4 — player controls and Jellyfin playback reporting

The full-screen Media3 player uses the standard controller, buffering indicator, and supported audio/subtitle track selection exposed by the stream. When playing a Jellyfin library item, CineFlow sends start, progress, and stopped reports to the Jellyfin Sessions API. Local Continue Watching progress remains available as a fallback. Server reporting depends on Jellyfin version/configuration and must be tested against a server you control.

**Validation note:** source and ZIP checks are not equivalent to a successful Android build or live-server test. Use the included GitHub Actions workflow to compile, then verify sign-in, track selection, playback reporting, resume, and completion on a test Jellyfin server.


## Phase 6: Emby adapter foundation

`EmbyApi.kt` adds the initial Retrofit adapter for Emby authentication, library views, item listing, and mapping server items into CineFlow's normalized media model. The adapter requires an HTTPS base URL. This phase does **not** yet connect Emby to Settings/Home/player UI, persist Emby tokens, or implement server-side playback reporting. Test against an Emby server you control and review API compatibility before production use.

## Trakt public catalogue integration (Phase 7 foundation)

OAuth has been removed. `TraktApi.kt` now contains public catalogue endpoints for trending/popular movies and shows plus movie/show search. Configure your own Trakt Client ID in the request header. This does not access a user account and cannot read or sync private watch history, personal watchlists, or scrobble to a user profile. The adapter is not yet wired into the Settings/Home/Search UI and has not been live-tested. See `docs/INTEGRATION_STATUS.md` for limitations.


## Addon engine upgrade (P1–P5 source bundle)

This upgraded project merges the existing CineFlowAndroid project and additive P1–P5 addon-engine sources. P1 provides addon persistence/client/dispatch, P2 catalog/search UI components, P3 detail/source models, P4 ranking/direct Media3 playback, and P5 loopback HTTP Range streaming primitives.

**Integration status:** these are additive components; `MainActivity.kt` navigation has not been rewritten to wire the new addon screens. P5 includes the Range server and backend interface but not a concrete native libtorrent4j adapter. Therefore this is not yet a complete magnet-to-playback torrent feature. Android compilation and unit tests have not been run in this environment. The GitHub Actions workflow is configured to run unit tests before assembling the debug APK.


## P6 — Subtitles and downloads
P6 adds SRT/WebVTT parsing, HTTPS subtitle fetching, a subtitle selection UI and chunked direct downloads. Android 10+ uses public Downloads/CineFlow; Android 7–9 uses app-specific Downloads. These are additive components, not wired into existing playback/navigation. Build and tests are not yet verified.

## Phase 7: Real-Debrid
See `docs/PHASE_7_REAL_DEBRID.md` for secure token storage, account check, and authorized direct-link handling. This is an additive integration component; existing app navigation/player wiring remains a separate integration step.

## Phase 7: Real-Debrid
See `docs/PHASE_7_REAL_DEBRID.md` for secure token storage, account check, and authorized direct-link handling. This is an additive integration component; existing app navigation/player wiring remains a separate integration step.

## Phase 8 — Cloudstream `.cs3` compatibility boundary

P8 adds a **safe, non-executing `.cs3` archive metadata inspector** and a reusable Material 3 preview panel. It reads bounded `metadata.json` / `plugin.json` metadata and rejects archives containing executable bytecode or native binaries. It does **not** run Cloudstream plugins or provide full Cloudstream runtime compatibility. The UI component is additive and is not yet wired into `MainActivity`; see `docs/PHASE_8_CLOUDSTREAM_COMPATIBILITY.md`.
