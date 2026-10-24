# Phase 7 — Real-Debrid account integration

## Included
- `EncryptedTokenStore`: encrypts the user-provided API token with an AES-GCM key held by Android Keystore.
- `RealDebridClient`: account check and HTTPS direct-link unrestrict request, with timeouts, redirects disabled, safe error messages, and no token logging.
- `RealDebridSettingsSection`: embeddable Compose settings UI to save/test/remove the token.

## Security / scope
- No API token is hard-coded or sent to a third-party analytics service.
- Only HTTPS URLs without embedded credentials are accepted for direct-link unrestrict.
- This phase intentionally does not add torrent/magnet submission or scraping. Use the service only for content you have permission to access.
- The settings section is additive; existing `MainActivity` navigation is not modified. Embed `RealDebridSettingsSection(EncryptedTokenStore(context), RealDebridClient { EncryptedTokenStore(context).read() })` in the app's existing settings destination when ready. Prefer creating the store once and sharing it.
- The direct-link method returns a validated HTTPS result but is not automatically wired into the P4 source ranker/player; caller integration is still required.

## Validation status
Source and ZIP structure checked. Android Gradle compilation and live API calls have not been run in this environment. Verify against the current Real-Debrid API and your own account in GitHub Actions before release.
