# Integration status — CineFlow Phase 6

| Integration | Status | Notes |
|---|---|---|
| TMDB v3 metadata | Implemented | Trending, popular movie/TV and multi-search. Metadata only; no playback URLs. |
| Custom authorized catalogue | Implemented | HTTPS `home`, `search`, `detail/{id}`, `stream/{id}` contract. |
| Local watchlist | Implemented | IDs and known records persist in app preferences. |
| Jellyfin personal media server | Phase 2–4 implemented in source; runtime validation pending | HTTPS server URL, username/password sign-in, library views/items, artwork, authorized direct stream URL, and session playback reporting through `Sessions/Playing`, `Sessions/Playing/Progress`, and `Sessions/Playing/Stopped`. Must be tested against a real Jellyfin server and its API/version/network configuration. |
| Plex | Phase 5 adapter foundation | API adapter foundation only; account sign-in/token acquisition, UI wiring, transcoding negotiation, and live playback verification remain. |
| Emby personal media server | Phase 6 adapter foundation | Added Emby auth/library API models and Retrofit adapter plus metadata/artwork/stream URL mapping. Not yet wired into Settings/Home/player; live auth, endpoint compatibility, and playback remain unverified. |
| Stremio / Nuvio / CloudStream / scraper providers | Not implemented | Each addon/provider requires contract and licensing review. No universal scraper assumed. |
| Trakt / Simkl / Scrob / Kodi / IPTV / debrid | Not implemented | Independent auth/API and permission work remains. |
| Anime, manga, music, podcasts, audiobooks | Not implemented | Separate modules if scope expands. |

## Jellyfin setup

1. In CineFlow, open Settings → Integrations.
2. Enter the HTTPS base URL for your own Jellyfin server (for example `https://media.example.com`), username, and password.
3. Tap **Connect to Jellyfin**. A successful sign-in clears the password field and displays a connected status.
4. Tap **Save settings**. Home reloads and attempts to fetch your Jellyfin library views and their Movie/Series items.
5. Open an item and use Watch now. Playback uses the Jellyfin server's `/Videos/{id}/stream` endpoint. Direct stream support depends on server policy, media format, transcoding configuration, and network access.

The app does not save the password. The session token and user ID are stored in app-private SharedPreferences. For a production release, replace token storage with Android Keystore-backed encrypted storage, consider using a dedicated low-privilege Jellyfin account, and add an explicit disconnect/revoke flow. HTTPS is required by this app's server URL validation; do not expose Jellyfin directly to the public internet without securing the server.

## Build validation

Source ZIP integrity can be checked with `unzip -t`. This environment does not have a Gradle executable or wrapper JAR, so Kotlin compilation, unit tests, and live Jellyfin sign-in/playback have not been run here. Use the repository's GitHub Actions workflow with JDK 17 / Android SDK 35 to compile the APK, then test against a server you control.

## Phase 3 status — local playback progress

Implemented in source: local progress persistence, periodic position saves, resume seeking, completion cleanup, and a Continue watching Home shelf. Not implemented: Jellyfin server-side session reporting/sync, Trakt scrobbling, multi-device progress sync, subtitle/audio track preference persistence. Build/runtime verification remains pending because Gradle/Android SDK build tools were unavailable in the authoring environment.


## Phase 4 — player controls and server reporting

- PlayerView uses Media3's standard controller and buffering indicator. Audio/subtitle selection is available when Media3 exposes selectable tracks for the chosen stream; track availability depends on server stream format and transcoding configuration.
- For Jellyfin items, CineFlow reports start, periodic position (5-second local tick), and stop events to the Jellyfin Sessions API using the user's session token. Progress values are converted from milliseconds to Jellyfin ticks (10,000 ticks per millisecond).
- Reporting failures are isolated so a network/server reporting error does not crash playback. Server-side reporting behavior and session matching need live-server validation; some servers may require additional session identifiers or specific request fields.
- No Plex/Emby/Trakt sync is claimed as implemented.


## Phase 6 — Emby adapter foundation

- Added `EmbyApi.kt` with Emby authentication request/response models, library views, item listing, metadata mapping, artwork URL mapping, and a server-local stream URL template.
- HTTPS base URLs are required by the adapter factory. Use only a server you own or are authorized to access.
- This is an adapter foundation, not a complete user-facing Emby integration: Settings UI, token persistence, Home library loading, player wiring, session progress reporting, and live-server tests are not yet implemented.
- Emby API behavior can vary by server version/configuration. Verify against official Emby API documentation and a server you control before relying on endpoint behavior.

## Phase 7 — Trakt public API adapter foundation

- Removed OAuth token exchange, authorization URL, PKCE helpers, bearer-token helper, and all account-authenticated history/watchlist/scrobble endpoints.
- `TraktApi.kt` now contains public catalogue endpoints for trending and popular movies/shows and search for movies/shows. Requests require the developer's Trakt Client ID in `trakt-api-key` and API version header.
- No private user data, account watchlist, personal history, or user-profile scrobbling is available without an authorized account flow.
- The adapter is not wired into app UI and has not been live-tested. Verify response shapes, API policy, errors and rate limits against current Trakt documentation before release.
- Trakt metadata is metadata only; it must never be treated as an authorized video stream source.
