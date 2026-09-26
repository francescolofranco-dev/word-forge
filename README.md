# WordForge

WordForge is an Android vocabulary trainer built with Kotlin, Jetpack Compose, Room, and spaced repetition.

## AI practice sessions

AI practice is optional. A user can connect either an OpenAI Platform API key or a Google Gemini API key, choose the number and types of exercises, choose how much of the saved vocabulary to cover, and generate an interactive HTML session.

The provider returns structured exercise data. WordForge validates that data and renders it with a bundled HTML/CSS/JavaScript client; provider-authored code is never executed. The WebView has network, file, content, popup, and navigation access disabled.

Developer API access is separate from a consumer ChatGPT or Gemini subscription. Provider quotas and charges may apply. API keys are encrypted at rest with Android Keystore and excluded from app backups. Selected vocabulary is sent only after the user confirms generation; review history and statistics remain on device.

Users are responsible for meeting the selected provider's current API eligibility and usage terms, including any age, region, audience, and purpose restrictions.

This is a personal bring-your-own-key integration. Android Keystore protects a saved key at rest, but no mobile client can make a long-lived developer key immune to extraction on a compromised or instrumented device. A production service distributed to untrusted devices should prefer a backend credential broker or provider-approved short-lived authorization flow.

## Statistics

Tap the chart icon on the home screen to see items added over the last seven local calendar days, the current distribution across learning tiers, and correct/incorrect review totals. Statistics include saved words and verb conjugations; deleted items and AI practice results are excluded.

## Build and test

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```
