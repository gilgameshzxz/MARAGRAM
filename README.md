# MARAGRAM

MARAGRAM explores a spatial interface for Telegram conversations on Android. Instead of navigating only a chat list, the interface lets conversations occupy positions in a workspace that can be panned and zoomed. The aim is to make returning to a conversation feel like returning to a place.

This is a development project built with Kotlin, Jetpack Compose, and TDLib. The source includes movable chat nodes, resizable chat windows, spatial message views, and local workspace persistence. These describe the implementation, not a claim of release stability or validated usability.

## Project structure

| Path | Purpose |
| --- | --- |
| `app/` | Compose interface, authentication routing, and workspace state. |
| `easytdlib/` | Telegram integration, generated TDLib Java bindings, and native libraries. |
| `docs/architecture.md` | Module boundaries, request flow, configuration, and storage. |
| `ROADMAP.md` | Proposed milestones and release checks. |

The root Gradle build includes `:app` and `:easytdlib`. Other local experiments and nested projects are not automatically part of that build.

## Build locally

Prerequisites:

- JDK 21 and Android Studio compatible with the project's Android Gradle Plugin.
- Android SDK platform 36. The project uses the included Gradle 8.13 wrapper.
- An Android device or emulator with API level 24 or later and support for `arm64-v8a`; the checked-in native libraries and ABI filter target that architecture.
- Your own Telegram application API ID and hash for authentication testing.

Clone the repository, then open its root in Android Studio:

```powershell
git clone https://github.com/gilgameshzxz/MARAGRAM.git
cd MARAGRAM
```

Create an untracked `local.properties` using [local.properties.example](local.properties.example) as a guide. Set the SDK location and your own Telegram API credentials; obtain them from [Telegram's application settings](https://my.telegram.org). Keep `JAVA_HOME` pointed at JDK 21.

```powershell
.\gradlew.bat :app:assembleDebug
```

The expected APK output is `app/build/outputs/apk/debug/app-debug.apk`. On macOS or Linux use `./gradlew` in place of `.\gradlew.bat`. Release signing is separate and is unnecessary for a debug build.

Build and test tasks still need verification on a fresh checkout. This documentation update could not run Gradle because execution was denied by the editing environment.

## Configuration and data

`TELEGRAM_API_ID` and `TELEGRAM_API_HASH` configure the library. The build also accepts `TURSO_URL` and `TURSO_TOKEN` for the existing analytics implementation. Empty defaults are present in the build configuration; behavior without an analytics backend needs device verification.

The current source initializes analytics during app startup, records an installation identifier and usage counters, and includes direct Turso HTTP requests. Review that behavior before testing with personal data. Values generated into `BuildConfig` are packaged with the client: an ignored properties file does not make an embedded backend token secret.

## Contributing and licensing

See [CONTRIBUTING.md](CONTRIBUTING.md) for reporting bugs and preparing changes. Reproducing setup, testing session recovery, improving accessibility, and documenting real device behavior are useful starting points.

Root project licensing is not yet established by a root `LICENSE` file. The local `easytdlib` materials include an upstream MIT notice and third-party notices; preserve those and verify their scope and provenance before release. Public source availability alone does not establish a license for the entire project.
