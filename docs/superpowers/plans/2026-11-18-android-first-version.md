# Android First Version Implementation Plan

> Execute in this session. Use superpowers:subagent-driven-development for scoped implementation/review work; do not ask the user to reconfirm the approved scope.

**Goal:** Deliver an installable offline Android stand up app with timer, music, and lock-screen reminders.

**Architecture:** Independent native Kotlin application under android/. A pure elapsed-time state machine drives a persisted repository and AlarmManager; a Media3 service owns background music. Native views render state without owning timer lifetime.

**Tech Stack:** Kotlin, Android SDK 36 / minSdk 26, AGP 8.13.2, Gradle 8.13, JDK 17, Media3 1.8.0, JUnit 4.

**Spec:** docs/superpowers/specs/2026-11-18-android-design.md

## Global Constraints
- Android only, no floating window, no network permission.
- Existing desktop code and the user's original working copy edits must remain intact.
- Version 1.5.0 across all published distributions; Windows x64 and both Mac architectures must build and pass checks before publication. Preserve Mac preview disclosures.
- No app signing key, password, machine path, or SDK download committed.
- No claim of real-device validation without a connected device.

## Task 1: Build environment and timer contract
Files: android/{settings.gradle.kts,build.gradle.kts,gradle.properties,gradlew,gradlew.bat,gradle/wrapper/*}, android/app/build.gradle.kts; core TimerState.kt and TimerStateTest.kt.
- [ ] Add pinned build configuration and official wrapper with distribution checksum.
- [ ] Write JUnit tests before state-machine implementation: start 30 minutes at 1000ms -> remaining at 61000ms = 1740000ms; pause then resume after 10 minutes still has 1740000ms; due is true at deadline only; resetting invalidates prior generation; minutes 0/1000 invalid; 999 valid.
- [ ] Run `./gradlew :app:testDebugUnitTest` to observe expected failure, implement state machine, run again.
- [ ] Commit only task files once verified.

## Task 2: Android timer, reminder and music experience
Files: android/app/src/main/AndroidManifest.xml; MainActivity.kt, timer/*.kt, music/*.kt, res/layout, res/drawable, res/values, res/raw.
- [ ] Implement permission-aware alarm scheduling and persistent state. Cancel pending alarms on pause/reset; stale generations cannot fire a newer timer.
- [ ] Use notification channel configured with bundled gentle chime; pause local music before notifying. Make permission denial actionable and visible.
- [ ] Implement media session service and persistent music library. System picker grants only chosen URIs; validate readable files, retain permission, release removed URI grants where possible.
- [ ] Render existing style on native scrollable phone UI; use 48dp touch controls and system window insets.
- [ ] Add Android integration tests for timer transitions, notification delivery/cancellation, persistence, and music pause on due, with meaningful assertions against app behavior.
- [ ] Run unit tests, lint, build; install on emulator and exercise starting, pause/resume/reset, navigation, background reminder and music.

## Task 3: Packaging and release
Files: .github/workflows/android.yml, .github/workflows/release.yml, scripts/build_android.ps1, docs/ANDROID.md, docs/releases/v1.5.0.md, README.md.
- [ ] Add CI Android tests, APK build and emulator smoke check; reusable workflow builds unsigned release for private local signing.
- [ ] Produce and verify signed APK and SHA256. Document installation, exact-alarm and notification permission setup, force-stop/reboot limits.
- [ ] Run all existing distribution workflows against the same source commit. Download verified artifacts, verify every checksum, stage all assets in one draft release, verify complete asset set, publish together.
- [ ] Review complete diff independently; fix actionable defects and rerun affected checks before publishing.
- [ ] Deliver APK/release link and honest verification summary, explain one useful implementation principle.
