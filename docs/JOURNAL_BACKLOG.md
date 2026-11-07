# Curio Journal — the work still owed

A running list, so nothing said in passing is lost. A line moves to "shipped"
only when the build carrying it is pushed.

## The editor

- **Several pages in one day** — SHIPPED in 1.5.24: the page's top-corner pill
  (the old 3-line contents door) now starts another page on the same day, and
  Home's "Today" label carries "+ New page". A day's pages read as a swipeable
  shelf on Home, newest first.

**NOT STARTED:**

- **Video attachments** — inline preview that expands full screen. Needs a
  `video` field on `PersonalBlock`, a codec key, a picker/capture, the media3-ui
  dependency and a block renderer.
- **The contents pill** → done differently: it became the new-page door above,
  not the calendar door (the member chose the new page).

## Open after 1.5.26

- **Separate a whole stack at once** (the menu/pull works one picture at a time).
- **Write about one picture** from the picture itself, not the page.
- **"Write here" is shown under every photo row**; the member wants it only under
  the row being worked in, and wants the line it makes to be deletable.
- **Expand/collapse of a stack** and the auto-arrangement still move in ways the
  member calls unexpected; the motion pass is not finished.
- **Video** is in (1.5.25) as an inline frame that expands full screen; recording
  a clip from inside the page is not — the picker only takes existing files.

## The page's photos

**Shipped in 1.5.21 (v514):**

- The dashed landing room and the cell bar are **gone**; the picture in the hand
  is the preview and the rows shift to show the room.
- Nothing in hand leaves the page: a sideways step is one cell, and the pull is
  clamped to the print's own width.
- A drop lands on the slot the finger is over, and everything between the old
  and new place shifts by exactly that room.
- **Write here** — a quiet door under every photo row inserts a blank line right
  after it, so words can stand between two groups and split the row.
- One soft motion curve for lifts, row shifts and landings: damping 0.86–0.9,
  stiffness 380–460, so a block rises, travels and settles as one movement.
- Auto-snap is a switch: **Settings → The page → Snap photos into stacks**.

**Still owed:**

- A whole stack cannot be separated as a group — only one picture at a time.
- Holding a stack should let the finger page through its members.

## Settings

- The redesigned settings shipped in 1.5.19: four groups (the page, the habit,
  the pages, photos, about), a glyph on every row, the word goal with its own
  −/+ control, and the snap switch. Anything asked for after seeing it goes here.

## Deferred by the member

- The topic catalog, the account site, the web/Flutter ports and the discovery
  app stay in the repository as an archive — not deleted, not built into the
  journal APK.
