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
