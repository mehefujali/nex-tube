# Contributing to NexTube

Thanks for helping make NexTube better! This guide gets you from clone to pull request.

## Ground rules

- Be kind. Everyone here follows the [Code of Conduct](CODE_OF_CONDUCT.md).
- Search [existing issues](https://github.com/mehefujali/nex-tube/issues) before opening a new one.
- For big changes (new screens, new dependencies, architecture changes), open an issue first so we can agree on the approach.
- Features must respect the app's privacy promise: **no analytics, no ads, no servers, no accounts.**
- Don't add features whose main purpose is evading platform protections, mass-downloading other people's
  channels for re-upload, or stripping creators' credit.

## Development setup

1. Install [Android Studio](https://developer.android.com/studio) (it bundles JDK 17+) and Android SDK 36.
2. Clone and open the project:
   ```bash
   git clone https://github.com/mehefujali/nex-tube.git
   ```
3. Run the `app` configuration on a phone or emulator (Android 8.0+), or from the command line:
   ```bash
   ./gradlew installDebug
   ```

The first launch takes a few seconds while yt-dlp, Python and ffmpeg unpack ("Preparing engine").

### Useful commands

| Command | What it does |
|---|---|
| `./gradlew assembleDebug` | Build debug APKs |
| `./gradlew assembleRelease` | Build minified release APKs |
| `./gradlew lint` | Android lint |
| `adb logcat -s NexTube` | App logs (engine errors, timings) |

## Project layout

| Path | Responsibility |
|---|---|
| `core/YtDlp.kt` | Everything that talks to yt-dlp: search, info, quality options, download, update |
| `core/Watermarker.kt` | Optional logo overlay using Media3 Transformer |
| `service/DownloadService.kt` | Foreground queue, progress, notifications |
| `data/` | Models, persisted download list, settings, saving to `Download/NexTube` |
| `ui/` | Jetpack Compose screens and the `MainViewModel` |

## Code style

- Kotlin official style (`kotlin.code.style=official`), 4-space indent, see `.editorconfig`.
- Keep functions small and comment the *why*, not the *what*.
- UI text is in English. If you add strings, put them in `strings.xml` so they can be translated.

## Pull requests

1. Fork the repo and create a branch: `git checkout -b fix/short-description`.
2. Make your change and test it on a real device if you can (downloads, progress, cancel).
3. Make sure `./gradlew assembleDebug lint` passes; CI runs the same checks.
4. Open a PR and fill in the template. Screenshots or a short recording help a lot for UI changes.

Commit messages: short imperative summary (`Fix progress for audio downloads`), with details in the body if needed.

## When downloads break

YouTube changes often. Before reporting a download bug:

1. Open **Settings → Download engine → Update** and try again.
2. If it still fails, open an issue with the video link, the selected quality, and `adb logcat -s NexTube` output.

## Release signing

Maintainers sign releases with a private keystore that is **never committed**. Locally, create
`keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=nextube-release.jks
storePassword=...
keyAlias=nextube
keyPassword=...
```

In CI, the release workflow reads the same values from repository secrets:
`NEXTUBE_KEYSTORE_BASE64`, `NEXTUBE_KEYSTORE_PASSWORD`, `NEXTUBE_KEY_ALIAS`, `NEXTUBE_KEY_PASSWORD`.
Without them, release APKs are signed with the debug key.

## License

By contributing, you agree that your contributions are licensed under the [GPL-3.0](LICENSE).
