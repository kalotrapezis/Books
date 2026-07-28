# books-sync

Keeps Foliate on a Linux desktop and Books on Android holding the same
annotations, through a folder you already sync.

There is no server and no account. Syncthing (or Nextcloud, or anything that
moves files) carries the bytes; this only decides what each file should say.

## What it does

Books writes one folder per book inside the folder you point it at:

```text
<the folder you sync>/
  The Brothers Karamazov/
    annotations.json
  ΚΑΙΝΗ ΔΙΑΘΗΚΗ (κείμενο - μετάφραση)/
    annotations.json
```

Foliate keeps one file per book in its own data directory, named after the
book's identifier, in the same format. `books-sync` matches them by title,
merges the two, and writes the result back to whichever side is behind:

- annotations are matched on `value`, and the newer `modified` wins;
- bookmarks are a union;
- a deletion is remembered, so removing a highlight on one side is not undone
  by the other still having it;
- anything else in either file — reading position, Foliate's own fields — is
  passed through untouched;
- the file is copied into `.books-backups` before it is overwritten, five deep.

## Install

```bash
./install.sh ~/Sync/Books
```

That puts `books-sync` in `~/.local/bin`, writes a `systemd --user` service
pointed at that folder, and starts it. No root, nothing outside your home.

```bash
systemctl --user status books-sync      # is it running
journalctl --user -u books-sync -f      # what it has done
systemctl --user disable --now books-sync
```

## By hand

```bash
books-sync ~/Sync/Books --dry-run   # say what would change, write nothing
books-sync ~/Sync/Books             # once
books-sync ~/Sync/Books --watch     # keep going
```

It finds Foliate's data directory itself, Flatpak or not:

- `~/.var/app/com.github.johnfactotum.Foliate/data/com.github.johnfactotum.Foliate`
- `~/.local/share/com.github.johnfactotum.Foliate`

## Worth knowing

- **Foliate does not reload a book it already has open.** Close the book, or
  Foliate itself, before expecting a change from the phone to appear. The same
  is true the other way: Foliate writes when it closes a book.
- **A book only Android knows about is left alone**, and named in the output.
  Foliate names its files after the book's identifier, which is inside the
  book, so there is nothing to write until Foliate has opened it once.
- **Two books with the same title share a folder.** Both apps warn when the
  identifier in a file does not match the book it is being merged into.

## Checks

```bash
python3 test_books_sync.py
```

Covers the merge rule in both directions, deletions, the union of bookmarks,
fields it does not understand, the folder names (the same ones the app makes),
and a round trip over real files on disk.
