# Archive — everything that is not the journal

The project is now **Curio Journal**: the journal, its assets and its design
language, built as a standalone app. Everything else that exists in this
repository is kept here, untouched and unbuilt, as the archive it came from.

Nothing below was deleted, moved, or modified to make the journal build. They
are frozen at the state they were in, so the journal can borrow from them and so
they can be picked up again without a recovery step.

| Surface | Path | What it is |
|---|---|---|
| Discovery app | `app/src/main/java/com/curio/app/` | The rest of the Android app: Spin, Cabinet, reader, quests, the pet, the community layer. **Its journal files are NOT archived** — `features/personal/JournalListScreen.kt`, `JournalEditorScreen.kt`, `JournalAccent.kt`, `PersonalPage.kt`, `PersonalTheme.kt`, `PersonalHome.kt` and the `data/` writing store are the journal build's own code and are used as-is. |
| Topic catalog | `data/topics/`, `scripts/` | The 16k curated topics and the scripts that build them. Not read by the journal build. |
| Account site | `auth-web/` | Supabase email links and the account desk. Live infrastructure for the discovery app's accounts; the journal needs no account. |
| Web port | `web/` | The React port of the discovery app (on hold). |
| Windows / iOS client | `curio_flutter/` | The Flutter client for the discovery app. |
| Design sources | `design/` | Launcher icons, drawer banner, music-service marks, the constellation map. The journal build uses the launcher icon; the rest is archive. |
| Store material | `fastlane/`, `fdroid/` | Release notes and store listing drafts for the discovery app. |

## Where the journal lives

The journal app is a **build flavor of this project**, not a copy of it:

- `app/src/journal/` — its manifest, its launcher activity, its new home screen
  and its redesigned settings.
- `app/src/main/` — the exact, shared Kotlin the journal uses: the writing
  store, the editor, the collection list, the paper and the theme.

Build it with `./gradlew assembleJournalRelease` (see
[`docs/JOURNAL_BUILD.md`](../docs/JOURNAL_BUILD.md)). `assembleCoreRelease` and
`assembleFullRelease` still build the archived discovery app exactly as before.
