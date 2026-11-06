# The journal build — Curio Journal

Curio Journal is the journal, its assets and its design language, built as a
**standalone app** from the same Kotlin. It is an edition of this project, the
way `core` and `full` already are: it installs side by side, has its own package
name (`com.curio.journal`), its own launcher label (*Curio Journal*) and its own
data directory.

Nothing in `app/src/main` was copied, rewritten or forked. The pages, the
writing store, the paper, the mood palette, the accent sheet, the word goal and
the collection list are the exact files `core`/`full` ship.

## Build it

```bash
./gradlew assembleJournalDebug        # installable debug APK
./gradlew assembleJournalRelease      # signed release, when KEYSTORE_* is set
```

The other editions are unaffected:

```bash
./gradlew assembleCoreRelease
./gradlew assembleFullRelease
```

> This workspace has no Android SDK, so Gradle builds do not run here (see the
> root `AGENTS.md`). CI is the compile step: `journal` is a row in the existing
> `Curio Android CI` build and lint matrices, so every push to `main` produces
> `assembleJournalRelease` (and `lintJournalRelease`) beside the core and full
> editions, and uploads the APK as an artifact.
>
> **No signing key is needed for the journal build.** The edition uses the same
> release config as the others, which signs with the repository keystore when
> its secrets are present and falls back to the debug keystore when they are
> not — so a journal release APK is produced either way, and is installable
> from a sideload in both cases.

## What is in the journal edition

| Path | What it is |
|---|---|
| `app/src/journal/AndroidManifest.xml` | One launcher activity (`.journal.JournalActivity`); the discovery app's `MainActivity` is removed from this edition's merged manifest. |
| `app/src/journal/java/.../JournalActivity.kt` | Boots only the writing store + theme, and hosts four routes. |
| `app/src/journal/java/.../JournalHomeScreen.kt` | **New home** — the day, not a feed: the date as the page's subject, the week as seven doors, today's page as a ruled sheet, the goal/streak line, and the days before it. |
| `app/src/journal/java/.../JournalSettingsScreen.kt` | **Redesigned settings** — three cards (the page, the habit, the pages), each row wired to a real preference. |
| `app/src/journal/res/values/strings.xml` | The launcher name, *Curio Journal*. |
| `app/src/core/java/...` | Included by this edition for the no-op `OfflineTranscriber`, `NeuralVoices`/`NeuralSpeaker` and `IsbnScannerScreen` twins — no Vosk, sherpa-onnx or ML Kit in a journal APK. |

## The four routes

| Route | Screen | Source |
|---|---|---|
| `journal-home` | `JournalHomeScreen` | new, this edition |
| `journals` | `JournalListScreen` | shared, unchanged |
| `journal/{entryId}` | `JournalEditorScreen` | shared, unchanged |
| `journal-settings` | `JournalSettingsScreen` | new, this edition |

## What the journal keeps

- One page per day, with an editable date, a title and the writing.
- The mood row (`Calm · Happy · Curious · Inspired · Tired · Heavy`).
- The paper: ruled / torn / torn-ruled, cream / white / kraft, per page.
- A page's own colour, picked from the app's hues or the colour wheel, worn by
  its doors (Home's chips, the collection's spine, the editor's tools).
- Word count, autosave, the read/pen switch, photos and voice notes inside the
  writing.
- The collection: search, mood and length filters, month headings, sort by day,
  delete with confirmation.
- The daily word goal and its evening nudge, and the "open on today / last page"
  door preference.
- The whole design language: Curio rose (and named themes), the paper palette,
  Fraunces for the day, the warm-cream page, light/dark/system.

Everything else — the Spin, the Cabinet, the reader, quests, the pet, the
community layer — is archived in [`../archive/README.md`](../archive/README.md)
and is not reachable from this edition.
