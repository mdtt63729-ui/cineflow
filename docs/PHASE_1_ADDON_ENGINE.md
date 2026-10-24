# CineFlow — Phase 1 Addon Engine Core

## Scope

This patch is limited to the P1 foundation. It does not modify `MainActivity.kt`, existing TMDB/Trakt/Jellyfin/Emby adapters, navigation, or playback UI. It does not add torrent, subtitles, downloads, debrid, or Cloudstream support.

## Added source files

- `AddonModels.kt` — manifest, catalog, resource, result and catalog item models.
- `AddonJson.kt` / `ResourceRefSerializer.kt` — defensive parsing for the Stremio `resources` string-or-object union.
- `AddonClient.kt` — HTTPS-only addon JSON client, 2 MiB response cap, explicit timeouts, no redirects, loopback-only HTTP exception, encoded path/query construction.
- `AddonManager.kt` — install/restore/enable/disable/uninstall policy and resource/type/id-prefix matching. Empty prefix lists are unrestricted.
- `AddonStore.kt` — versioned DataStore JSON persistence; invalid saved records are ignored safely.
- `ResourceDispatcher.kt` — catalog/home rows and declared-search support, six-request concurrency cap, independent per-catalog failures.
- `AddonEngineTest.kt` — parser, URL policy, search capability and empty-prefix tests.

## Apply to repository

1. Copy `app/src/main/java/com/gtx/cineflow/addons/` and `app/src/test/java/com/gtx/cineflow/addons/` into the repository.
2. Apply `apply-p1.patch` to `app/build.gradle.kts` (adds OkHttp and DataStore Preferences dependencies).
3. Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` in GitHub Actions or a configured Android build environment.
4. Review all failures before accepting P1. Do not start P2 unless the gate passes.

## Acceptance gate status

**Not verified in this authoring environment.** The repository's GitHub integration rejected branch creation with HTTP 403, and this environment could not reach GitHub over the shell network. Consequently this deliverable is a source patch, not a commit or PR against the repository. No Gradle build or unit tests are claimed as passing.

## Security notes / known limits

- HTTP is allowed only for `localhost`, `127.0.0.1`, or `::1`; other addon base URLs must be HTTPS.
- Redirects are disabled and response bodies are bounded to 2 MiB.
- This is a client-side policy baseline, not a complete defense against every DNS-rebinding or malicious-addon scenario. Addon-provided image, stream and subtitle URLs require the same policy checks when those later phases are implemented.
- Catalog requests are metadata discovery only. A catalog entry is not evidence that a playable stream exists or that content is authorized. Future playback must only use content the user is authorized to access.
