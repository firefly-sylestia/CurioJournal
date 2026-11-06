# Curio Journal — the work still owed

A running list, so nothing said in passing is lost. Newest requests at the top
of each section; a line is removed only when the build carrying it is pushed.

## The page's photos (next pass — reported 2026-11-06)

The photo blocks inside the journal page misbehave when held and moved:

- **A stack cannot be separated.** Two pictures that landed together stay a
  stack: there is no way to pull one out of it.
- **A held picture cannot be flipped through.** While holding a stack, the
  member cannot switch to the picture on its left or right, and the preview
  that would show what is coming is not drawn.
- **A drag does not land where the finger is.** A moved picture should land at
  the exact place it was moved to.
- **Auto-snap needs a switch.** The automatic snapping/adjustment that pulls a
  picture into a row should be a setting the member can turn off, and with it
  off that picture stays separate and where it was dropped.

The blocks, the drag and the snap live in the shared writing page
(`features/personal/PersonalPage.kt`, `PersonalPhotoOverlay.kt`), which the
discovery editions also build — so this pass touches shared code and needs a CI
run of all three editions.

## The editor

- **Several pages in one day**, created from inside the writing page rather than
  only from Home.
- **Video attachments** — record or pick a video and keep it on the page, beside
  the pictures and voice notes.
- **The contents pill** — replace it with the calendar door (confirm which pill
  the member means: the headings list in the page's top corner).

## Settings

- The redesigned settings shipped in 1.5.19 (four groups: the page, the habit,
  the pages, about). Anything the member asks for after seeing it belongs here.

## Deferred by the member

- The topic catalog, the account site, the web/Flutter ports and the discovery
  app stay in the repository as an archive — not deleted, not built into the
  journal APK.
