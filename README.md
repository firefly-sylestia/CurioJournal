# 📖 Curio Journal

> **One page a day. Keep what you noticed.**

Curio Journal is the journal from [Curio](https://github.com/firefly-sylestia/Curio) — and nothing else. No deck, no feed, no account, no network. A page for today, the days before it, the moods you wrote them in, and the ruled paper the whole thing lives on.

It installs beside Curio, with its own name and package (`com.curio.journal`), and shares nothing with it but the design language.

![Platform](https://img.shields.io/badge/Platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/github/license/firefly-sylestia/CurioJournal?label=License)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?logo=kotlin&logoColor=white)

---

## What this is

The journal is a **build of the same Kotlin**, not a rewrite. It is an edition of the Curio project the way `core` and `full` already are, so the paper, the mood palette, the editor, the collection list and the writing store are the exact files Curio ships — unchanged. The edition adds three things of its own and leaves everything else alone:

- a boot activity that starts the writing store and nothing else (`app/src/journal/java/.../JournalActivity.kt`)
- a day-focused home (`JournalHomeScreen.kt`)
- settings in three cards (`JournalSettingsScreen.kt`)

The rest of Curio — the Spin, the Cabinet, the reader, quests, the pet, the community layer — is still in this repository. It is kept as an archive, unbuilt by this edition, and frozen at the state it was in. Nothing was deleted or moved to make the journal build. See [The archive](#-the-archive).

---

## 📅 A day, not a feed

Home answers “what day is it, and what did I write on it”, not “what shall I explore”.

- **The date is the subject.** The day number is the largest thing on the page, set in the display serif, with the weekday and month beside it and a time-of-day greeting above.
- **The days are doors.** The last four weeks sit in a strip you can scroll back through, with today at its right edge and the month's own count above it; a written day carries a dot, today wears the accent, and any day opens its page — or a blank one, already dated, if there is nothing there yet.
- **Today is a sheet of paper.** A written day shows its mood, its title, the opening of the writing and how many words it holds, on ruled paper. An unwritten day is the same sheet, empty, and the page itself is the button.
- **The habit, said once.** With a word goal set, one card reads “*n* of *n* words today” beside the streak, with a bar as its only graphic. With no goal, the streak alone.
- **Before today.** The last six written days, each with its own colour as the spine, newest first, with a door to all of them.
- **A pen that stays put.** The floating pen opens today's page — or the last page you opened, if that is the door you chose.

## ✍️ The page

The editor is Curio's journal editor, untouched.

- **Six moods** — calm, happy, curious, inspired, tired, heavy — folded into one pill that names itself and expands when tapped.
- **A title, and the writing** on ruled paper, autosaving as you go and flushing when you leave.
- **Photos** from the device, carried inside the page.
- **Voice notes** recorded on the page and played back from it. The APK carries no speech models, so this edition records and keeps the sound; it does not transcribe it.
- **A page colour of its own**, if you want one, which follows the page into the collection.
- **A read view** that sets the day back in the display serif, so a page can be read without a caret in it.
- **A word count**, counted from the page itself.

## 🗂️ The collection

Curio's journal list, unchanged, with everything a growing journal needs:

- **Search** across a page's title, its opening line, its mood and its date line.
- **Filter by mood and by length** — a short page, a long one, or everything.
- **Sort by day**: tap the date to flip between newest first and oldest first.
- **Today's count** beside the goal, when a goal is set, so the goal is not a number you have to remember to check.

## ⚙️ Settings

Three cards, and every row is wired to a real preference:

| Card | Row | What it does |
|---|---|---|
| **The page** | Theme | Light, dark, or the phone's own choice — the journal is painted the instant it changes |
| | Opening the journal | The pen lands on today's page, or on the last one you opened |
| **The habit** | Daily word goal | Off, or 250–5,000 words in steps of 250 |
| | Evening reminder | A nudge at the end of a day you have not written (needs the goal set) |
| **The pages** | All pages | The collection, with its search, filters and sorting |
| | Kept on this device | How the pages, photos and voice notes are stored |

---

## 🚀 Install

Download the journal APK from the [Releases page](https://github.com/firefly-sylestia/CurioJournal/releases) and install it on any Android 8.0+ device. Android will ask you to allow “install from unknown sources” for your browser — that is normal for a sideloaded app.

- **It installs beside Curio.** A different package, a different data directory, its own launcher tile. Neither app can see or change the other's pages.
- **The release APK is debug-signed** unless the repository's keystore secrets are present. It installs fine; a later build signed with the same key updates over it, and one signed with a different key does not.
- **Permissions it uses:** the microphone, for a voice note on a page; notifications, for the evening nudge. The APK carries the app's whole manifest, so more are declared beside them — no screen in this edition asks for or uses any of those.

## 🔨 Build it

```bash
git clone https://github.com/firefly-sylestia/CurioJournal.git
cd CurioJournal
./gradlew assembleJournalRelease   # installable release APK
./gradlew assembleJournalDebug     # debug build
```

**Requirements:** Android Studio (latest stable), a JDK 17+ toolchain, and Android SDK 26+.

The other editions still build the archived discovery app exactly as before:

```bash
./gradlew assembleCoreRelease
./gradlew assembleFullRelease
```

CI builds all three editions on every push to `main` — each one is a row in the existing `Curio Android CI` build and lint matrices — and uploads the journal APK as an artifact. See [`.github/workflows/android.yml`](.github/workflows/android.yml) and [docs/JOURNAL_BUILD.md](docs/JOURNAL_BUILD.md) for the build, the four routes and the signing fallback.

## 📂 Where things live

| Path | What it is |
|---|---|
| `app/src/journal/` | This edition: the manifest, the launcher activity, the home, the settings, the launcher name |
| `app/src/main/` | The shared Kotlin the journal uses — the editor, the collection, the writing store, the paper, the theme, every string |
| `app/src/core/` | No-op twins of the Full-only pieces (`OfflineTranscriber`, the neural voices, the ISBN scanner), so no Vosk, sherpa-onnx or ML Kit binary ships in a journal APK |
| `archive/` | Everything that is not the journal, recorded and frozen — see below |

## 🔒 Privacy

Your journal is a Room database on your phone. Pages, photos and voice notes stay there: nothing is uploaded, there is no account, no analytics, no tracking and no ads. No screen in this edition makes a network request; the archived discovery screens and the updater beside them are the only parts of this repository that ever talk to a server.

## 🗄️ The archive

The discovery app, its 16,000-topic catalog, the account site, the web and Flutter ports, the design sources and the store material are all still here, at the state they were in, unbuilt by this edition. [`archive/README.md`](archive/README.md) names each surface and where it lives; nothing below it was deleted, moved or rewritten to make the journal build.

The catalog is not packaged into the journal APK either: the bundle step copies it into the discovery editions' own source sets, so a journal build has no topic data to merge — no JSON, no catalog, no import on first launch.

## 📜 License

Curio Journal is free and open source under the [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.en.html) — see [LICENSE](LICENSE) for the full text.

The AGPL is a strong copyleft licence: you may use, study, share and modify the app freely, but any modified version you run — including one offered to users over a network — has to offer those users its complete source under the same licence (AGPL §13).

Copyright (C) 2026 the Curio contributors.

## 👋 Credits

**Made by Firefly** — the journal, its paper and its design language.

Curio Journal stands on the work of the Curio project and the libraries underneath it: **Kotlin, Jetpack Compose, Material 3, Room, Coil, kotlinx-coroutines** (Apache-2.0), and the bundled typefaces under their own licences in `app/third_party/`. The writing editor, the collection and the paper are Curio's, unchanged; this edition's home and settings were written for it.

---

*One page a day. Keep what you noticed.*
