# WordForge

WordForge is an offline-first Android vocabulary and language trainer built with Kotlin, Jetpack Compose, Room, and an adaptive spaced repetition engine.

## Features

- **Spaced Repetition System (SRS):** Schedules word reviews across 9 adaptive learning tiers (from newly added to mastered) based on active recall performance.
- **Vocabulary & Verb Conjugations:** Save custom words and complete verb conjugation tables with definitions, notes, and examples.
- **Active Recall Quizzes:** Practice with interactive flashcard-style quizzes and instant self-assessment.
- **Comprehensive Statistics:** Track items added over 7-day rolling windows, review correct/incorrect ratios, and visualize vocabulary distribution across learning tiers.
- **Expressive Craft Design Identity:** Custom editorial aesthetic with Bricolage Grotesque, Manrope, and JetBrains Mono typography, rich ink & parchment surfaces, and warm forge accents.
- **Offline & Private:** All vocabulary and statistics are stored locally on your device in a Room database. No accounts, no ads, no trackers.

## Tech Stack

- Kotlin & Jetpack Compose
- Room Database (offline-first architecture)
- WorkManager (scheduled review reminders)
- Downloadable Fonts (Google Fonts API with offline fallback)
- Gradle Play Publisher (automated Play Store deployment)

## Build and Test

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

### Play Store release

The Android application ID is `dev.lofrancolabs.wordforge`. This uses a new Google Play
listing; the older `com.wordforge` listing cannot receive builds with the new ID.
The Kotlin source namespace remains `com.wordforge` independently of the application ID.

Release builds are signed with the upload key when a root `keystore.properties` (untracked) exists:

```properties
storeFile=/absolute/path/to/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Without it, release artifacts are built unsigned. `versionCode` is the git commit count and
`versionName` is `1.4.<versionCode>`, so every commit produces a higher version automatically.

```bash
./gradlew :app:bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

Every push to `main` that changes the app runs `.github/workflows/release.yml`: unit tests, signed
bundle, upload to Google Play, and a `play/<versionCode>` tag marking what was published. Release
notes are the commit subjects since the previous `play/*` tag. Docs-only pushes are skipped.

Repository variables choose the destination (`gh variable set NAME --body VALUE`):

| Variable | Default | Values |
|---|---|---|
| `PLAY_TRACK` | `alpha` | `alpha`, `internal`, `beta`, `production` |
| `PLAY_RELEASE_STATUS` | `completed` | `completed`, `draft`, `inProgress` (staged) |
| `PLAY_USER_FRACTION` | unset | rollout share for `inProgress`, e.g. `0.2` |

Required secrets: `KEYSTORE_BASE64` (base64 upload keystore), `KEYSTORE_PASSWORD`, and
`PLAY_SERVICE_ACCOUNT_JSON` (Play Developer API service account key). Without the last one the
workflow builds the bundle but skips the upload. To upload from your machine instead, add
`playServiceAccount=/absolute/path/to/key.json` to `keystore.properties` and run
`./gradlew :app:publishReleaseBundle`.

Google Play only accepts each `versionCode` once, so a manual run (Actions → Play release) must be
on a commit that has not been published yet. The very first upload of a new Play app has to be done
by hand in Play Console; the Play Developer API cannot create apps.
