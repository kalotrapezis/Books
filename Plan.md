# Books for Android — τεχνικό πλάνο

## 1. Στόχος

Το **Books** είναι public εφαρμογή ανάγνωσης ηλεκτρονικών βιβλίων για Android
τηλέφωνα και tablets. Στόχος είναι να μεταφερθεί σταδιακά το πλήρες σύνολο
λειτουργιών του Foliate, χωρίς να δημιουργηθεί έκδοση Linux και χωρίς να
παρουσιαστεί το Books ως επίσημη έκδοση του Foliate.

Η εφαρμογή θα είναι offline-first και θα διανέμεται με
`GPL-3.0-or-later`. Το πρώτο παραδοτέο δεν είναι «όλο το Foliate», αλλά ένα
τεχνικό vertical slice που αποδεικνύει ότι το Android WebView μπορεί να
χρησιμοποιήσει με ασφάλεια το `foliate-js` και να διατηρήσει συμβατές θέσεις
EPUB CFI.

## 2. Συμφωνημένες απαιτήσεις

- Android μόνο, για τηλέφωνα και tablets.
- Public repository: `kalotrapezis/Books`.
- Κύριο development branch: `android-dev`.
- Όνομα εφαρμογής: `Books`.
- Package/application ID: `com.kalotrapezis.books`.
- Πλήρης λειτουργικότητα Foliate ως μακροπρόθεσμος στόχος.
- Συμβατότητα με Foliate για progress, bookmarks, annotations και notes.
- Offline ανάγνωση και τοπική αποθήκευση ως βασική λειτουργία.
- Χωρίς DRM.
- GitHub source και APK αρχικά· πιθανή προετοιμασία για F-Droid αργότερα.

## 3. Επιλεγμένη τεχνική κατεύθυνση

### Android shell

- Kotlin με το built-in Kotlin support του Android Gradle Plugin.
- Jetpack Compose και Material 3 για native UI.
- Single-activity, single-module αρχιτεκτονική στη Φάση 0.
- Adaptive layouts για compact και expanded οθόνες.
- Storage Access Framework για επιλογή βιβλίων, χωρίς broad storage permission.
- Room όταν προστεθεί η βιβλιοθήκη και η μόνιμη αποθήκευση.
- DataStore όταν προστεθούν οι ρυθμίσεις.

### Reader engine

- Android WebView φιλοξενεί bundled, τοπικά reader assets.
- `foliate-js` ως reader engine, αρχικά pinned στο commit:
  `399248a67a8862ffb5e6463a33f9d52b317ca2eb`.
- Το pin αντιστοιχεί στο `foliate-js` revision του ελεγμένου Foliate GTK4 και
  δεν ακολουθεί αυτόματα το ασταθές upstream `main`.
- `zip.js`, `fflate` και PDF.js χρησιμοποιούνται μέσω του ελεγμένου
  `foliate-js` bundle.

Το GTK/GJS UI δεν μεταφέρεται. Ξαναγράφεται ως Android UI. Επαναχρησιμοποιούνται
μόνο στοιχεία των οποίων η άδεια, η λειτουργία και η ασφάλεια έχουν ελεγχθεί.

## 4. Υφιστάμενη ροή Foliate που πρέπει να διατηρηθεί

```text
Άνοιγμα αρχείου
  → foliate-js δημιουργεί book model
  → metadata / TOC / cover
  → reader WebView
  → relocate event με CFI
  → αποθήκευση progress / lastLocation / bookmarks / annotations
```

Στο Books το Compose UI θα επικοινωνεί με το reader μέσω μικρού typed JSON
protocol. Η αρχική έκδοση χρειάζεται μόνο:

- `OpenBook`
- `BookReady`
- `Relocated`
- `Next`
- `Previous`
- `GoToCfi`
- `ReaderError`

Annotation, search και appearance messages προστίθενται όταν ξεκινήσει η
αντίστοιχη φάση. Δεν δημιουργείται γενικό bridge που εκθέτει Android APIs.

## 5. Συμβατότητα δεδομένων Foliate

### Ταυτότητα βιβλίου

Το Books διατηρεί τρία αναγνωριστικά:

1. εσωτερικό UUID,
2. `foliateKey`,
3. SHA-256 ολόκληρου του αρχείου.

Το `foliateKey` υπολογίζεται όπως στο Foliate:

- χρησιμοποιείται το `metadata.identifier` όταν υπάρχει,
- διαφορετικά `foliate:` + MD5 των πρώτων ακριβώς 10.000.000 bytes.

Το MD5 εδώ είναι compatibility identifier και όχι έλεγχος ασφάλειας.

### Συμβατό JSON

```json
{
  "metadata": {
    "identifier": "..."
  },
  "progress": [123, 900],
  "lastLocation": "epubcfi(...)",
  "bookmarks": [
    "epubcfi(...)"
  ],
  "annotations": [
    {
      "value": "epubcfi(...)",
      "color": "yellow",
      "text": "...",
      "note": "...",
      "created": "ISO-8601",
      "modified": "ISO-8601"
    }
  ]
}
```

Το Books θα διατηρεί άγνωστα metadata πεδία χωρίς απώλεια. Το
`lastLocation` CFI είναι η κύρια θέση επαναφοράς· το αριθμητικό progress είναι
δευτερεύον επειδή μπορεί να αλλάξει μεταξύ εκδόσεων του engine.

Το Foliate εισάγει σήμερα μόνο annotations από το UI. Αμφίδρομο progress και
bookmark sync θα απαιτήσει αργότερα versioned file-level format ή μικρό Linux
helper. Δεν θεωρείται ασφαλές live sync ενώ το Foliate έχει ανοιχτό το ίδιο
βιβλίο.

## 6. Ασφάλεια WebView

Τα EPUB περιέχουν μη έμπιστο HTML. Το prototype αποτυγχάνει αν δεν μπορεί να
τηρήσει τα παρακάτω:

- `WebViewAssetLoader` με HTTPS-like local origin.
- Content Security Policy που επιτρέπει μόνο bundled app scripts.
- `allowFileAccess = false` και `allowContentAccess = false`, εκτός αν
  αποδειχθεί συγκεκριμένη ασφαλής ανάγκη.
- Καμία γενική χρήση `addJavascriptInterface`.
- Περιορισμένο `WebMessageListener`/message channel με origin checks.
- Μηνύματα μόνο από το top-level bundled reader, όχι από book iframes.
- Εξωτερικοί σύνδεσμοι ανοίγουν σε system browser.
- Popups, mixed content και μη αναγκαία WebView permissions απενεργοποιημένα.
- Καμία άδεια Internet στο αρχικό offline build.
- Malicious EPUB fixture πριν από release.

## 7. Άδειες

| Στοιχείο | Άδεια | Επιτρεπτή χρήση | Υποχρέωση |
|---|---|---|---|
| Foliate GTK4 | GPL-3.0-or-later | Επιτρέπεται προσαρμογή | GPL-compatible διανομή, source, copyright, σήμανση αλλαγών |
| foliate-js | MIT | Επιτρέπεται ενσωμάτωση/τροποποίηση | Διατήρηση MIT notice |
| zip.js | BSD-3-Clause | Επιτρέπεται ενσωμάτωση | Copyright, license, disclaimer, no endorsement |
| fflate | MIT | Επιτρέπεται ενσωμάτωση | Διατήρηση MIT notice |
| PDF.js | Apache-2.0 | Συμβατό με GPLv3 | License/NOTICE και σήμανση αλλαγών |
| PDF fonts/CMaps | διάφορες permissive/OFL | Μόνο μετά από inventory | Διατήρηση κάθε επιμέρους notice |
| AndroidX/Compose | Apache-2.0 | Επιτρέπεται | Διατήρηση notices της διανομής |

Δεν επιτρέπεται:

- αφαίρεση copyright/license notices,
- παρουσίαση upstream κώδικα ως αποκλειστικά δικού μας,
- διανομή του συνδυασμού ως κλειστό λογισμικό,
- διανομή minified dependencies χωρίς τα αντίστοιχα license texts,
- χρήση του ονόματος/λογότυπου Foliate σαν να είναι επίσημο Android Foliate,
- υπόθεση ότι το root `LICENSE` καλύπτει μόνο του όλες τις τρίτες βιβλιοθήκες.

Πριν από public APK απαιτούνται πλήρες `THIRD_PARTY_NOTICES.md`, license texts
για όλα τα bundled artifacts και dependency/license report.

## 8. Φάσεις

### Φάση 0 — Τεχνικό spike

- Ελάχιστο buildable Android project.
- Bundled/pinned `foliate-js`.
- Native Books shell για phone/tablet.
- Επιλογή ενός EPUB μέσω Storage Access Framework.
- Άνοιγμα EPUB στο ασφαλές WebView.
- Επιβεβαίωση ES modules και Web Components.
- Λήψη `BookReady` και `Relocated`.
- Αποθήκευση/επαναφορά ενός CFI μετά από force-stop.
- Αρχική μέτρηση μνήμης με μικρό και μεγάλο EPUB.

Gate: αν το WebView δεν υποστηρίζει αξιόπιστα το engine ή απαιτεί επικίνδυνο
file/native bridge, σταματά η κύρια υλοποίηση και αξιολογείται custom
random-access loader.

Τρέχουσα πρόοδος:

- Ολοκληρώθηκαν το buildable project, το pin του `foliate-js`, το ασφαλές
  offline origin, το CFI capability check και η επιλογή EPUB μέσω Android.
- Επιβεβαιώθηκε σε τοπικό browser harness ότι πραγματικά EPUB αναλύονται και
  φτάνουν στο ανοιγμένο `foliate-view`.
- Επιβεβαιώθηκαν σε Android 16 συσκευή η απόδοση του reader, το `Relocated`, η
  ασφαλής αποθήκευση URI/CFI και η αυτόματη επαναφορά του ίδιου CFI μετά από
  πραγματικό force-stop.
- Εκκρεμούν οι μετρήσεις μνήμης με μικρό και μεγάλο EPUB πριν κλείσει η Φάση 0.

### Φάση 1 — Πλήρες EPUB vertical slice

- Room library: τίτλος, συγγραφέας, εξώφυλλο, URI, identifiers.
- Paginated και scrolled mode.
- TOC, RTL, reflowable και fixed-layout EPUB.
- Themes, fonts, line height, margins και tablet columns.
- Progress και CFI restore.
- Adaptive phone/tablet UI.

### Φάση 2 — Foliate-compatible reading data

- Bookmarks.
- Highlights, colors και notes.
- Annotation list και CFI navigation.
- Exact Foliate JSON import/export.
- HTML, Markdown και ORG export.
- Identifier mismatch warning.
- Golden fixtures από πραγματικό Foliate.

### Φάση 3 — PDF και επιπλέον formats

- PDF.js ως ξεχωριστό experimental milestone.
- MOBI/KF8/AZW3.
- FB2/FBZ.
- CBZ.
- Μεγάλα αρχεία και bounded memory.
- Καθαρό error για password-protected files.

### Φάση 4 — Πλήρες feature set

- Search, page list και landmarks.
- Android Text-to-Speech και media overlays.
- Copy text/citation.
- Dictionary, Wikipedia και translation actions.
- OPDS catalogs και downloads.
- Image viewer.
- Calibre embedded highlights.
- TalkBack, scalable text και accessibility tests.

### Φάση 5 — Backup και Linux/Foliate interoperability

- Versioned manual backup/restore.
- Merge annotations βάσει `value` και `modified`.
- Tombstones για διαγραφές.
- Backup πριν από κάθε merge.
- Linux helper για progress/bookmarks.
- WebDAV/Nextcloud μόνο αφού αποδειχθεί το τοπικό merge.

### Φάση 6 — Σταθεροποίηση και release

- Unit, lint, UI και accessibility tests.
- Process-death, rotation και window-resize tests.
- Revoked document permission handling.
- WebView crash/recovery.
- Memory tests για μεγάλα EPUB/PDF/CBZ.
- Malicious EPUB security tests.
- Debug και signed release APK.
- GitHub Actions build, tests και license audit.

## 9. Acceptance checks

Το πρώτο πραγματικά χρήσιμο release πρέπει:

- να ανοίγει EPUB χωρίς δίκτυο,
- να συνεχίζει στο ίδιο CFI μετά από force-stop,
- να μη χάνει annotations μετά από update,
- να ανταλλάσσει Foliate annotation JSON χωρίς αλλαγή σε CFI, text ή dates,
- να κρατά θέση σε rotation και phone/tablet layout change,
- να μην επιτρέπει σε EPUB να καλέσει Android APIs ή να διαβάσει άλλα αρχεία,
- να μη δείχνει ανεξέλεγκτη αύξηση μνήμης σε μεγάλο βιβλίο.

Οι βασικές εντολές επαλήθευσης είναι:

```text
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Η δεύτερη εντολή απαιτεί emulator ή πραγματική Android συσκευή.

## 10. Ανοιχτές αποφάσεις

- Ελάχιστο Android: API 24, επειδή είναι το ελάχιστο της τρέχουσας stable
  AndroidX WebKit 1.16.0 που χρησιμοποιείται για το ασφαλές reader shell.
- Τελικό εικαστικό/εικονίδιο του Books.
- Release signing και μέθοδος διανομής APK.
- Επιλογή WebDAV/Nextcloud μόνο στη Φάση 5.

## 11. Όρια του τρέχοντος ορόσημου

Η Φάση 0 δεν περιλαμβάνει Room, sync, OPDS, PDF, TTS ή πλήρες annotation UI.
Αυτά προστίθενται μόνο μετά την επιτυχία του ασφαλούς EPUB/CFI prototype.
