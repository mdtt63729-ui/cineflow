# Phase 2 — Home Catalog Shelves + Federated Search

## Implemented in this bundle
- Application-facing repository over P1 `AddonStore`, `AddonManager`, `AddonClient`, and `ResourceDispatcher`.
- Persistent installed-addon restoration before every request path.
- Progressive home row updates as catalog requests complete.
- Independent addon error handling and enabled/disabled filtering.
- Federated search only for catalogs advertising `search`.
- Deduplication keeps distinct providers distinct, using `type + id + addonBaseUrl`.
- Material 3 Compose shelf and search result components in CineFlow's current palette.
- No changes to current navigation, player, detail screen, or API adapters.

## Explicit non-goals for P2
- No playable URL is inferred from catalog metadata.
- No stream/torrent/debrid logic.
- No detail endpoint requests; P3 handles metadata details.
- No Cloudstream `.cs3` support.

## Known integration requirement
The current repository's `CineFlowApp` Home and Search composables need to call the repository and render the new composables. Because this bundle does not overwrite `MainActivity.kt`, follow the exact wiring steps in the root README. This avoids replacing existing app UI/navigation code wholesale.

## Validation
ZIP integrity is checked at packaging time. Build and tests have not been run here. The P2 gate is not considered passed until the owner runs `:app:testDebugUnitTest` and `:app:assembleDebug` after applying both P1 and P2.
