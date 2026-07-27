# Test list — annotation redraw fix and Phase 3 formats

Build and install:

```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tick each one on the tablet. `✔` = verified on the Android 36 tablet emulator
already; the rest need your real device and real files.

## A. Annotations (the reported bug)

1. ✔ Highlight a word in chapter 1, page forward into chapter 3, come back:
   the highlight is still drawn. (Before the fix it disappeared. Confirmed on
   the phone.)
2. Highlight in three different chapters, close the book, reopen it, and walk
   through all three: every highlight is drawn when its chapter loads.
3. Import a Foliate JSON while the book is open at chapter 1, with annotations
   that live in later chapters: page to those chapters, highlights are there
   without reopening the book.
4. Sync against a file (Syncthing/Nextcloud folder): same as 3.
5. Delete a highlight from the Annotations list, leave the chapter and come
   back: it stays deleted.
6. Import your real 567-annotation export: the list count matches and the open
   chapter shows its highlights.
7. Tap a highlight: its note popup still opens.

## B. New formats

8. ✔ PDF opens, pages turn with the arrows and the seek bar, progress is shown.
9. ✔ PDF: close the app, reopen, the same page comes back. (Confirmed on the phone.)
10. ✔ CBZ opens, pages turn, the cover thumbnail appears in the library.
11. ✔ A large real PDF: opens, pages turn, no crash, memory settles. Tested with
    `Pliroforiki_A-Dimotikou_pdf-web_v6.0.pdf` (18 MB, 77 pages) on the tablet
    emulator: five full passes, renderer settled at ~630 MB RSS with no growth
    on the last two passes, app process ~265 MB. Title and author came from the
    PDF metadata and the cover thumbnail rendered.
12. ~~MOBI/AZW3~~ — not applicable, you have no such files. The loader is wired
    up and untested; leave it that way until a file turns up.
13. ~~FB2/FBZ~~ — same.
14. Pick something that is not a book (a .txt, a photo): the reader shows a
    clean error, no crash, and nothing broken in the library afterwards.
15. A password-protected or DRM file (`.acsm` is not a book): clean error.

## C. No regressions

16. ✔ Your real EPUB library opens as before, both panes on the tablet.
17. Paginated and scrolled mode, both across chapter boundaries.
18. Themes, font size, line height, margins, font family.
19. Bookmarks: ribbon adds, long press lists, tapping one jumps.
20. Export JSON / HTML / Markdown / ORG.
21. Force stop, reopen: every book is at its own last position.
22. Rotate the tablet and resize the window: position kept.

## D. Fixed-layout gestures (PDF and comics)

23. ✔ Drag left/right on a PDF page turns it, both directions.
24. ✔ Same on a CBZ.
25. ✔ With "Scrolled reading" ON, a PDF or comic keeps the paginated arrows and
    seek bar and still turns by drag — scrolled mode only applies to text books.
26. Tap the middle of a PDF or comic page: the UI hides and shows, as in EPUB.
    (Image-only pages report no selection object at all, which used to make the
    reader treat every tap and drag as "text is selected" and ignore it.)

## Known gaps, not bugs to report

- A fixed-layout page (PDF, comic) is not centred in the pane and its bottom is
  cut off on the tablet — cosmetic, not new.
- PDF and comics have no text to highlight, so annotations are EPUB-only.
