# Phase 8 — Cloudstream `.cs3` compatibility (safe metadata inspection)

## Included
- Bounded `.cs3` ZIP inspection (50 MB archive, 500 entries, 256 KB metadata).
- Path traversal and malformed archive rejection.
- Reads `metadata.json` / `plugin.json` for name, version, description, authors, language and repository metadata.
- Reusable Material 3 panel to preview the inspection result.
- Unit tests for metadata-only archives, executable archive rejection and extension validation.

## Deliberate compatibility boundary
A Cloudstream `.cs3` is commonly a packaged executable extension. This phase **does not execute or load third-party bytecode**, emulate the Cloudstream runtime, or promise that a Cloudstream plugin's scraping/provider code will work in CineFlow. Archives containing `.dex`, `.class`, `.jar`, `.apk`, native libraries, or executables are rejected for import/compatibility. The implementation is a safe archive metadata inspector only, not full runtime support.

The UI component is additive and is not wired into `MainActivity` navigation. The host app must provide a document picker, copy the selected file to a bounded temporary file, call `Cs3ArchiveInspector.inspect(file)` off the main thread, and display the result. Do not install an inspected archive or treat its metadata as trusted instructions.

## Validation
Tests are included, but Android Gradle build and tests have not been run in this environment. Review GitHub Actions logs before treating this phase as build-verified.
