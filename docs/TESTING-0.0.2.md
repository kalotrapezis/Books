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
13. ✔ FB2 and FBZ open with the right title, author, chapters and TOC. Tested on
    the emulator with a hand-made file, since there is no real one here.
14. ✔ Pick something that is not a book (a .txt): "File type not supported", no
    crash, and it does not end up in the library.
15. ✔ A password-protected archive: clean error ("No supported image files in
    archive"), no crash. The wording is generic — it does not say the file is
    locked. `.acsm` (DRM) is untested, there is no such file here.

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
    (Pages turn by drag, not by tap — a tap only toggles the UI.)
    (Image-only pages report no selection object at all, which used to make the
    reader treat every tap and drag as "text is selected" and ignore it.)

## E. The selection bug you reported

27. ✔ Select a word, then tap somewhere else: the panel closes and the bottom
    island comes back. (Before the fix the WebView ate that tap — no click and
    no selectionchange reached the reader — so the panel stayed open and the
    chrome stayed hidden until you pressed Cancel.)
28. Select a word, then press Cancel, Copy, Cite or a colour: same as before.
29. Select a word, then long press another word: the new selection is reported
    and the panel follows it.

## F. Search, and the two fixes after 0.0.2

30. Sidebar → Search, type two letters or more, Find: results list with the
    chapter and the words around the match, and every hit outlined in the page.
    Tapping one goes there.
31. A second search replaces the first; an empty box clears the outlines.
32. On a phone, Search is in the bottom pill next to Chapters and Notes.
33. Select a word and press Note: the panel stays open with the field. This was
    broken in 0.0.2 — the field took focus, the WebView lost it, and the panel
    went with it.
34. Add a file that is not a book: the error shows and it does not stay in the
    library. A book you have read keeps its row even if its file is gone.
35. Long press the ribbon: each bookmark says its chapter, its section and the
    words it sits on, instead of "Saved page 1".

## G. Phase 4

36. Chapters: a book with landmarks or printed page numbers shows them under
    their own headings, and each one opens where it says. A book with neither
    looks exactly as before.
37. Press ▶ in the top bar: the book is read aloud, the sentence being read is
    underlined and scrolls into view, and it carries on into the next chapter
    on its own. ✕ stops it.
38. A Greek book should be read with a Greek voice — it comes from the book's
    metadata, so a book that declares nothing falls back to the device voice.
    (Install the voice in Android's settings if it sounds wrong.)
39. Leave the book while it is reading: the voice stops.
40. Tap a picture in a book: it opens full screen, double tap fills the screen,
    a tap closes it.
41. Select text: the top bar stays, so read aloud and the bookmark ribbon are
    still in reach while the selection panel is open. On the emulator, select
    with the mouse too — that is a pointer, not a finger, and it used to be
    ignored, which left you with no panel at all.
42. Read aloud is in two places now: circled in the top bar, and bigger in the
    island under the page.
43. TalkBack on: the bookmark ribbon, the page arrows, the back chevron and the
    read-aloud button all announce what they do. This is the one item here I
    could not check on the emulator.

## Known gaps, not bugs to report

- A fixed-layout page (PDF, comic) is not centred in the pane and its bottom is
  cut off on the tablet — cosmetic, not new.
- PDF and comics have no text to highlight, so annotations are EPUB-only.
