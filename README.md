# Animikii Club for Android

A native Android player for Animikii Club's Eclectic001 Turtle Island Ojibwe Edition.

The app reads the current track, history, and song list from the station's public AzuraCast API. It streams the public 128 kbps MP3 feed, sends song requests, and loads album art from HTTPS URLs the station provides.

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

The debug APK is for local testing and is written to `app/build/outputs/apk/debug/app-debug.apk`. Set the `ANIMIKII_RELEASE_*` environment variables before running `assembleRelease` to build a signed release APK; keep the keystore and passwords out of Git. GitHub Actions runs lint, unit tests, and the debug build on pushes and pull requests to `main`.

## License

The source code is available under the [PolyForm Noncommercial License 1.0.0](LICENSE). It allows noncommercial use, modification, and redistribution; commercial use is not allowed. This is source-available, not open source under the Open Source Definition.

The license covers the project code, not the Animikii Club name, logos, station artwork, music, or third-party software. Those may have separate rights or terms.

## Privacy

The app's [privacy policy](PRIVACY.md) is available here and from the in-app About menu.
