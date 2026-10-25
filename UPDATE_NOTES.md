# CineFlow source update notes

## Included in this ZIP
- Existing project source and existing GitHub Actions workflows from the uploaded `cineflow-main.zip`.
- India-focused TMDB Discover endpoints for Indian-origin movies and series in Hindi, Bengali, Tamil, Telugu, Malayalam, Kannada, Marathi, and Punjabi.
- Empty-state messaging instead of fabricated non-Indian sample recommendations when no catalogue is configured.
- Public built-in add-on registry and first-launch installation into local add-on storage: Cinemeta, YouTube channels, WatchHub availability discovery, and OpenSubtitles v3.
- New `.github/workflows/build-debug.yml` workflow. On push to `main` or manual run, it builds `:app:assembleDebug` and uploads `CineFlow-debug-apk`.
- Existing signed-release workflow retained.

## Important limitations
- The build has not been run in this environment. Use GitHub Actions to confirm Kotlin compilation and Android packaging.
- Built-in add-ons are installed into the existing add-on repository, but the project's existing README notes that the add-on catalog/player screens are not yet wired into the main navigation.
- The TMDB feed can filter by Indian origin and original language. TMDB metadata does not reliably confirm Hindi/Bengali dubbed audio tracks.
- Public add-on URLs may change or be temporarily unavailable. Installation failures are non-fatal and existing add-on state is preserved.
- This update does not modify the remote GitHub repository; it is a local full-source ZIP assembled from the ZIP you uploaded.
