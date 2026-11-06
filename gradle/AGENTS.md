# Gradle Build Configuration — AGENTS.md

## DOX Framework

This file is a child of the DOX hierarchy defined in `master.md`. It follows the root `AGENTS.md` as its parent DOX rail.

**DOX chain:** `master.md` ← `AGENTS.md` (root) ← `gradle/AGENTS.md` (this file)

Read `master.md` and root `AGENTS.md` first, then this file for Gradle-specific contracts.

## Purpose

Centralized Gradle build system configuration for the Curio Android project. Manages dependency versions, plugin versions, and the Gradle wrapper.

## Ownership

- `gradle/libs.versions.toml` — **Version catalog**: single source of truth for all dependency and plugin versions
- `gradle/wrapper/gradle-wrapper.properties` — Gradle wrapper distribution configuration

## Local Contracts

### Version Catalog (`libs.versions.toml`)
- **All** dependency and plugin versions MUST be declared here, not hardcoded in `build.gradle.kts`
- Only versions that need explicit pinning belong here; Compose BOM-managed versions should not be duplicated
- Sections: `[versions]`, `[libraries]`, `[plugins]`

### Key Version Ground Rules
- AGP, Kotlin, KSP, Compose BOM, and Material3 versions are pinned in `libs.versions.toml`.
- Target/Compile SDK: `37`
- Min SDK: `26`

### Naming Convention
Library keys follow the pattern: `{group}-{artifact}` with dots replaced by dashes.
Examples:
- `androidx-core-ktx` → `androidx.core:core-ktx`
- `com-squareup-retrofit2-retrofit` → `com.squareup.retrofit2:retrofit`
- `io-coil-kt-coil-compose` → `io.coil-kt:coil-compose`

### Plugin Keys
- `android-application` → `com.android.application`
- `kotlin-compose` → `org.jetbrains.kotlin.plugin.compose`
- `ksp` → `com.google.devtools.ksp`

## Work Guidance

- **Always** add new dependencies to `libs.versions.toml` first, then reference via `libs.` in build files
- When updating Kotlin, ensure KSP and Compose Compiler plugin versions match
- Do not duplicate version declarations — `libs.versions.toml` is the authority
- The Gradle wrapper (`gradlew` / `gradlew.bat`) is checked in; update with `gradle wrapper --gradle-version {version}`

## Verification

- CI runs the authoritative Curio lint and build checks.
- Do not run Gradle compile, build, lint, or test commands in this workspace; the root DOX contract reserves those for CI.

## Child DOX Index

No child AGENTS.md files defined yet.
