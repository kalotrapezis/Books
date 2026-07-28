![Books](docs/banner.png)

# Books

An offline EPUB reader for Android phones and tablets, built on
[`foliate-js`](https://github.com/johnfactotum/foliate-js) and speaking
[Foliate](https://johnfactotum.github.io/foliate/)'s reading data, so highlights,
notes, bookmarks and reading position move between your desktop and your phone
without conversion.

This is an independent project. It is not an official Foliate application.
No DRM. No network permission.

- Branch: `android-dev`
- Version: 0.0.2
- Licence: `GPL-3.0-or-later`

## Screenshots

![Tablet](docs/screenshots/tablet-sidebar.png)

*A tablet: the library, the chapters and the notes share one sidebar beside the page.*

| Library | Reading | Book details |
|---|---|---|
| ![Library](docs/screenshots/library.png) | ![Reader](docs/screenshots/reader.png) | ![Details](docs/screenshots/details.png) |

## What works today

**Library**

- Add books through the system picker; access survives reboots.
- EPUB, PDF, CBZ comics and FB2/FBZ, plus whatever else `foliate-js` can open
  (MOBI/AZW3 is wired up but untested — no file to try it on).
- Cover thumbnails read straight from the book — the first page for a PDF, the
  first image for a comic — stored bounded and app-private.
- Independent reading position per book, restored after a force stop.
- Long press a book for its details: identifiers, SHA-256, counts, dates.

**Reading**

- Paginated and scrolled modes, both crossing chapter boundaries.
- Drag left or right to turn the page; a tap shows or hides the UI. The seek bar
  scrubs at a third of finger speed with a haptic tick per page.
- Chapters from the book's table of contents.
- Two low-glare themes, grey on white and white on grey, with the book's own
  colours kept as shades of grey.
- Text size, line spacing, margins and font family.
- Adaptive layout: single pane on a phone; on a tablet the library, the chapters
  and the notes share one sidebar beside the page, as three tabs.
- Pin the sidebar open, or unpin it so it steps aside while you read and comes
  back floating — dragged in from the left edge, or from the reader's button.

**Annotations, Foliate-compatible**

- Highlights in Foliate's palette (yellow, orange, red, magenta, aqua, lime),
  notes on the same record, and bookmarks.
- Import and export Foliate JSON with a preview before anything is written;
  unknown fields survive the round trip untouched.
- Merge on `value` and `modified`, union of bookmarks, warning on identifier
  mismatch.
- Sync against one file per book inside a Syncthing or Nextcloud folder.
- Export as JSON, HTML, Markdown or ORG, the same markup Foliate writes.
- Dictionary, Wikipedia, translation, copy, cite and share from a selection.

## Not yet

MOBI/AZW3 on a real file; search; text to speech; OPDS catalogs; automatic
backup and restore. See [Plan.md](Plan.md) for the phase-by-phase plan.

## Security

EPUBs are untrusted HTML, so the reader runs in a WebView with no file or
content access, a Content Security Policy that only allows bundled scripts, book
scripts blocked, book documents sanitized, and an origin-checked message channel
instead of a JavaScript interface. The app requests no Internet permission;
links and lookups are handed to apps that already have it.

## Building

Needs a JDK 17 and the Android SDK.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

```bash
./gradlew connectedDebugAndroidTest
```

The second command needs a device or emulator. On MIUI/HyperOS also enable
Developer options → Install via USB.

## Changelog

### 0.0.2 — 2026-07-28

More formats, a sidebar that earns its space, and two real bugs fixed.

**Formats**

- PDF, CBZ comics and FB2/FBZ open, page and remember their position. The book
  is served under its own extension so `foliate-js` picks the right loader, and
  PDF.js 5 gets the two ES features the Android WebView still lacks.
- PDF covers are rendered from the first page by the platform `PdfRenderer`;
  comics use the first image in the archive.
- Fixed-layout books (PDF, comics) keep paginated controls even in scrolled
  mode, turn by drag, and pinch to zoom.
- A file that is not a book, or a password-protected archive, gives a clean
  error instead of a crash.
- An 18 MB, 77-page PDF was walked end to end five times on a tablet: memory
  settles and stops growing.

**Tablet**

- The library, the chapters and the notes are three tabs in one sidebar, so
  those panels sit beside the page instead of covering it. A phone keeps its
  own pill.
- The sidebar pins open, or unpins to step aside while you read and return
  floating over the page — dragged in from the left edge, or from the reader's
  sidebar button.
- Colours follow libadwaita: the sidebar a step away from the page, lists in a
  rounded well, floating panels the same barely-translucent island as the
  reader chrome.
- The book title and chapter are centred with room to breathe.

**Fixes**

- Highlights no longer vanish when you leave a chapter. `foliate-js` draws an
  annotation only while its section is loaded and keeps no list of its own, so
  the reader now redraws them on every section's overlay — an imported file was
  invisible outside the open chapter before this.
- The selection panel no longer sticks. Tapping away from selected text drops
  the selection inside the WebView without firing a click or a selectionchange,
  so the app never heard it end: the panel stayed open and the reader's islands
  stayed hidden until Cancel was pressed.
- Locally built APKs are out of the repository.

### 0.0.1 — 2026-07-27

First build that is useful to read with.

- EPUB reading through a hardened WebView, with CFI positions compatible with
  Foliate and restored after process death.
- Room library with covers, per-book progress, and book details.
- Paginated and scrolled reading, tap zones, swipes, animated page turns,
  chapters, themes and typography settings.
- Bookmarks, highlights with notes, and the annotations list.
- Foliate JSON import, export and folder sync, plus HTML, Markdown and ORG
  export.
- Verified against a real Foliate export of 567 annotations, kept as a test
  fixture.

## Licence

`GPL-3.0-or-later`. See [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the bundled `foliate-js`,
`zip.js`, `fflate` and PDF.js notices.
