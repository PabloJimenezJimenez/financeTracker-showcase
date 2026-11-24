# Finance Tracker

Personal finance tracker for Android, written in Kotlin with Jetpack Compose.

## Stack
- Kotlin 2.4, Android Gradle Plugin 9 (built-in Kotlin), Gradle 9 wrapper, JDK 21
- Jetpack Compose with Material 3 (versions via the Compose BOM)
- Single module `:app`; all versions live in `gradle/libs.versions.toml`
- minSdk 26, targetSdk 37, compileSdk 37.2

## Commands
- `./gradlew test` – all JVM unit tests (plain JUnit + Robolectric Compose UI tests)
- `./gradlew :app:testDebugUnitTest --tests '*HomeScreenTest'` – one test class
- `./gradlew lintDebug` – Android lint (report: `app/build/reports/lint-results-debug.txt`)
- `./gradlew assembleDebug` – build the debug APK

There is no emulator in cloud sessions: UI behaviour is tested with Compose UI tests
under Robolectric in `app/src/test`, never in `androidTest`.

## Layout
```
app/src/main/java/com/pablojimenez/financetracker/
  domain/      pure Kotlin models and logic, no Android imports
  data/        Room adapter of domain's FinanceRepository (entities, DAO, FinanceDatabase)
  ui/<screen>/ one package per screen: <Name>Screen.kt (+ <Name>ViewModel.kt when stateful)
  ui/theme/    FinanceTrackerTheme
app/src/test/  mirrors main; domain tests are plain JUnit, UI tests use createComposeRule
```

## Conventions
- Money is `Long` cents, never `Double`. Format only at the UI edge with `formatCents`.
- Composables are stateless: take state + lambdas, hoist state to a ViewModel.
  Every screen has a `@Preview` wrapped in `FinanceTrackerTheme`.
- User-visible text goes in `res/values/strings.xml`; no hard-coded strings in composables.
- The look and motion follow `docs/design.md` (editorial, bento tiles, greys first, Material You
  colour only on the hero tile and the primary action). Its MUST rules are checked in review.
- Use `MaterialTheme` colors and typography, never hard-coded colors or text sizes.
- Add a `Modifier.testTag` to anything a test needs to find that has no stable text.
- Test names use backticks and describe behaviour: `` `shows empty state when there are no transactions` ``.
- New dependencies go through the version catalog. Keep `./gradlew test lintDebug` green.

## Privacy and security
- No third-party library that sends data off the phone: no analytics, crash reporting or ads.
  Network traffic is HTTPS only and goes only to services a map ticket approved (banks, market
  prices). A new external service needs its own decision.
- Data lives in Room, unencrypted beyond Android's own storage encryption, with Android backup
  turned off (`docs/adr/0001-no-database-encryption-no-backup.md`).
- Secrets (bank keys, tokens) go in Android Keystore, never in the database or an export.
- The app locks behind the phone's biometrics or PIN like a banking app: on every cold start and
  every time it returns from the background, except from a system screen the app itself opened
  (file picker, Settings, the biometric prompt); it cannot be turned off. Recents thumbnails are
  hidden; screenshots are allowed.

## Commits
- The author of every commit is the repo owner: `PabloJimenezJimenez <116873476+PabloJimenezJimenez@users.noreply.github.com>`.
- Never add Claude as author or co-author: no `Co-Authored-By` lines, no `Claude-Session` lines
  or other AI attribution in commit messages.

## Cloud sessions
`.claude/hooks/session-start.sh` installs the Android SDK and routes Maven Central
through Google's mirror (Maven Central rate-limits cloud containers with HTTP 429).

## Agent skills

### Issue tracker

Issues live in GitHub Issues for PabloJimenezJimenez/financeTracker, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root, created when first needed. See `docs/agents/domain.md`.
