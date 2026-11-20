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

## Publishing a release (maintainers)

Installed apps check [`update.json`](update.json) on `main` every time they open. Releasing is one click:

1. Go to **Actions → Release → Run workflow**.
2. Enter the version (e.g. `1.2.0`, must be higher than the current one), the changelog, and tick
   **mandatory** if older versions must stop working until they update.
3. The workflow builds signed APKs, publishes the GitHub Release, and updates `update.json`.
   Every user then sees the update popup. With *mandatory*, the popup can't be dismissed.

Version codes are derived from the version: `major × 10000 + minor × 100 + patch`.

### Release signing

Updates only install if **every** release is signed with the same key. The keystore is private and
**never committed**. Locally, `keystore.properties` in the project root (git-ignored) points to it:

```properties
storeFile=../nextube-signing/nextube-release.jks
storePassword=...
keyAlias=nextube
keyPassword=...
```

The release workflow needs these repository secrets (Settings → Secrets and variables → Actions):
`NEXTUBE_KEYSTORE_BASE64` (the `.jks` file, base64-encoded), `NEXTUBE_KEYSTORE_PASSWORD`,
`NEXTUBE_KEY_ALIAS`, `NEXTUBE_KEY_PASSWORD`. Back up the keystore. If it is lost, existing users can
never receive updates again.

### Testing the update flow locally

Debug builds allow plain HTTP to `127.0.0.1`, so you can fake a release:

```bash
./gradlew assembleDebug -PversionCode=10001 -PversionName=1.0.1 -PupdateUrl=http://127.0.0.1:8765/update.json
# copy that APK and an update.json pointing at it into a folder, then:
python -m http.server 8765
adb reverse tcp:8765 tcp:8765
# build/install an older versionCode with the same -PupdateUrl and open the app
```

## License

By contributing, you agree that your contributions are licensed under the [GPL-3.0](LICENSE).
