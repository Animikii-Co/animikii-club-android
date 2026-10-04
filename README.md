# Animikii Club for Android

A native Android player for Animikii Club's Eclectic001 Turtle Island Ojibwe Edition.

The app gets the current track, history, and song list from the station's public AzuraCast API. It plays the public 128 kbps MP3 stream and sends song requests to the station. No account or station credentials are needed. Album art is loaded from HTTPS URLs supplied by the station.

## Listener requests

The list starts with 20 tracks and adds another 20 as you scroll. To load the next page, scroll away from the end and back to it.

## Network recovery

If playback drops, the app retries after 2 seconds, then waits longer between attempts, up to 60 seconds. It can retry immediately when the default network comes back. If the stream stays in a buffering state for 45 seconds, the app opens a new connection. Pausing playback stops automatic retries.

## Build

Requirements: JDK 17 or newer, Android SDK Platform 36, and Android Build Tools 36.0.0.

On macOS or Linux:

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
```

On Windows:

```bat
gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs lint, unit tests, and the debug build on pushes and pull requests to `main`.

## Versioning

GitHub releases follow [Semantic Versioning 2.0.0](https://semver.org/). The first public release is `v1.0.0`. Android's `versionCode` is an internal build number and increases independently so installed copies can be updated.

## License

The source code is available under the [PolyForm Noncommercial License 1.0.0](LICENSE). It allows noncommercial use, modification, and redistribution; commercial use is not allowed. This is source-available, not open source under the Open Source Definition.

The license covers the project code, not the Animikii Club name, logos, station artwork, music, or third-party software. Those may have separate rights or terms.
