# TempusOS

Android launcher-style home experience with a clock, app search, quick notes, and a shortcuts panel. Built with **Jetpack Compose** and **Material 3**.

## Features

- **Home** — Large clock and date, dark minimal layout. Search installed launcher apps; up to four matches appear with icons and labels; tap to open.
- **Notes** — Swipe from the **left edge** to open the note list, or use the menu icon. Create notes with the **+** button; title and body save automatically after a short debounce. Notes are stored on device as JSON.
- **Panel** — Third page opens **system Settings** from a centered control (intended as a lightweight side panel).

Navigation uses a three-page **horizontal pager**: Notes (left) → Home (center, default) → Panel (right). From Notes, swipe **inward from the right edge** to return to Home. The system back gesture/button from the Notes page also returns to Home.

## Requirements

- **Android Studio** with a recent AGP (see `gradle/libs.versions.toml`; the project uses AGP 9.x).
- **JDK 11** (matches `compileOptions` in `app/build.gradle.kts`).
- **Device or emulator** running **API 26+** (minSdk 26); targets **API 36**.

## Build and run

Open the project in Android Studio and run the `app` configuration, or from the project root (on Windows use `gradlew.bat` instead of `./gradlew` if you use Command Prompt or PowerShell without a Unix-style shell):

```bash
./gradlew :app:assembleDebug
```

Install the debug APK on a connected device or emulator:

```bash
./gradlew :app:installDebug
```

Release builds:

```bash
./gradlew :app:assembleRelease
```

## Data

- **Notes** — Persisted to `notes.json` under the app’s internal files directory (`filesDir`), as a JSON array of note objects (`id`, `title`, `body`, `updatedAt`).

## Project layout

| Path | Role |
|------|------|
| `app/src/main/java/com/axion/tempus/MainActivity.kt` | Entry point, loads installed apps, hosts theme and pager |
| `app/.../ui/pager/LauncherPager.kt` | Three-page pager and page wiring |
| `app/.../ui/notes/` | Notes UI and `NotesViewModel` |
| `app/.../data/` | `Note` model and `NotesRepository` |

**Application ID / namespace:** `com.axion.tempus`

## License

No license file is included in this repository; add one if you distribute the project.
