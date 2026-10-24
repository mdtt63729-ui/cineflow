# Phase 3 — Detail Metadata and Source List

## Scope
- Fetch `meta` from addons whose manifest advertises `meta` and supports the item's type/id.
- Parse common Stremio metadata fields: id, type, name, poster, background, logo, description, release info, rating, genres, runtime, videos.
- Fetch `stream` responses only from addons whose manifest advertises `stream` and supports the type/id.
- Parse direct-link and info-hash source descriptors into typed UI models.
- Isolate errors per addon. One bad addon must not hide successful results from other addons.
- Material 3 detail/source list components; no change to existing navigation/player is made in this patch.

## Integration notes
`AddonDetailScreen` and `AddonSourceList` are additive composables. Route/navigation wiring is intentionally left to the integrating app because `MainActivity.kt` has the existing app's full screen state and should not be replaced by a patch bundle.

For each selected catalog item, use its `addonBaseUrl` as the preferred metadata provider, but allow other enabled addons that declare support as fallback. The repository tries the selected item's addon first, then remaining supported addons.

P3 source descriptors are informational. P4 will implement ranking, source validation, and playback resolution. Never infer a playable URL from a metadata record.

## Gate checklist
- [ ] P1 source files are applied.
- [ ] P2 catalog integration is applied.
- [ ] Detail endpoint succeeds for a known test item.
- [ ] Unsupported type/id produces a recoverable empty/error state.
- [ ] One failing addon does not block healthy addons.
- [ ] `:app:testDebugUnitTest` passes.
- [ ] `:app:assembleDebug` passes.
