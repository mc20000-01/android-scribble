# Android Scribble

Android Scribble is an Android accessibility-service prototype that adds an always-available handwriting overlay. Tap the floating pen, write on the screen, and the service recognizes ink, handles quick editing gestures, and injects text into the currently focused field.

## What is included

- A foreground accessibility service for handwriting capture and text injection.
- A draggable floating pen button that toggles writing mode.
- Jetpack Compose onboarding/settings screens for permissions and learning controls.
- ML Kit Digital Ink recognition integration.
- Scratch-delete, circle-select, and vertical-slash gesture classification.
- Optional custom dictionary, correction, and symbol-to-emoji training helpers.

## Android compatibility

The app is configured with `minSdk = 26` and `targetSdk = 36`, so the generated APK is intended to install on Android 8.0+ and is suitable for Android 13, Android 14, and Android 15 devices.

Generated build artifacts use the `android13-15` label because the same universal APK covers Android versions 13 through 15. Add separate version-specific APKs only if future code introduces SDK-specific flavors or packaging differences.

## Repository layout

- `app/src/main/java/com/example/androidscribble/accessibility/` - accessibility service, boot receiver, and text injection code.
- `app/src/main/java/com/example/androidscribble/ink/` - ink models, smoothing, gesture classification, and contrast helpers.
- `app/src/main/java/com/example/androidscribble/ml/` - ML Kit recognition, custom dictionary, and correction storage.
- `app/src/main/java/com/example/androidscribble/onboarding/` - first-run permission onboarding.
- `app/src/main/java/com/example/androidscribble/settings/` - settings and learning screens.
- `app/src/main/java/com/example/androidscribble/symbols/` - symbol-to-emoji template training.
- `builds/` - generated local APK artifacts and checksums; APKs are ignored by Git.
- `scripts/build_android_artifacts.sh` - full APK build helper that archives old builds and writes the latest outputs to `builds/`.

## Build requirements

- JDK 17 or newer. This repository was built successfully with JDK 21.
- Android SDK with platform 36 and Android build tools installed.
- Gradle available on `PATH`, or an executable `./gradlew` if a wrapper is added later.

If your Android SDK is not auto-detected, create a local `local.properties` file that points to your SDK path:

```properties
sdk.dir=/path/to/android-sdk
```

`local.properties` is machine-specific and should not be committed.

## Full build script

Use the checked-in script to make debug and unsigned release APKs, archive any previous outputs, label them for Android 13-15, and generate SHA-256 checksums:

```bash
scripts/build_android_artifacts.sh
```

Useful options:

```bash
scripts/build_android_artifacts.sh --debug-only
scripts/build_android_artifacts.sh --release-only
scripts/build_android_artifacts.sh --no-clean
scripts/build_android_artifacts.sh --attempts 20 --label 2026-05-12
```

The script retries failed builds up to 20 times by default and prints Gradle/report diagnostics before retries. The debug APK is signed with the standard debug key and is the easiest artifact to install for testing. The release APK produced by the default project configuration is unsigned and must be signed before distribution.

## Manual build commands

```bash
gradle assembleDebug
gradle assembleRelease
```

## Installing a local debug build

After running the build script:

```bash
adb install -r builds/android-scribble-android13-15-debug.apk
```

After installation, open Android Scribble and follow the onboarding cards to enable Accessibility, overlay, notification, and battery optimization permissions as needed.

## Build artifact policy

Do not commit APK binaries. Keep the latest locally generated build artifacts directly in `builds/`. The build script moves older APKs and checksums into `builds/old/` and labels them clearly, for example:

```text
builds/old/android-scribble-android13-15-debug-2026-05-12.apk
builds/old/android-scribble-android13-15-release-unsigned-2026-05-12.apk
```

Do not leave stale APKs mixed with the latest files in `builds/`.
