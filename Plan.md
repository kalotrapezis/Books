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
- διαφορετικά `foliate:` + MD5 των πρώτων έως 10.000.000 bytes.

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
- Ολοκληρώθηκαν μετρήσεις στα ίδια app/renderer PIDs με EPUB 3,33 MB και με
  πραγματικό EPUB 11,16 MB. Στο μεγάλο βιβλίο των 143 ενοτήτων έγιναν πέντε
  πλήρεις διαδρομές σε κατανεμημένες ενότητες: το app σταθεροποιήθηκε περίπου
  στα 313 MB RSS, ενώ ο WebView renderer κορυφώθηκε στα 195 MB στον τρίτο
  κύκλο, δεν αυξήθηκε στους επόμενους δύο και έπεσε στα 189 MB μετά από 12
  δευτερόλεπτα αδράνειας.
- Η χρήση μνήμης είναι bounded για το EPUB spike και το gate της Φάσης 0
  πέρασε. Οι δοκιμές μεγάλων PDF/CBZ και η ευρύτερη σταθεροποίηση παραμένουν
  στις αντίστοιχες μεταγενέστερες φάσεις.

### Φάση 1 — Πλήρες EPUB vertical slice

- Room library: τίτλος, συγγραφέας, URI, identifiers και ανεξάρτητη πρόοδος.
- Μικρογραφίες εξωφύλλων σε app-private αρχεία, με οριοθετημένο μέγεθος.
- Paginated και scrolled mode.
- TOC, RTL, reflowable και fixed-layout EPUB.
- Themes, fonts, line height, margins και tablet columns.
- Progress και CFI restore.
- Adaptive phone/tablet UI.

Τρέχουσα πρόοδος:

- Ο reader στέλνει ελεγμένα `BookReady`, `Relocated` και `ReaderError` events
  στη native UI και εμφανίζει τίτλο, συγγραφέα και ποσοστό ανάγνωσης.
- Τα native Previous/Next χρησιμοποιούν το υπάρχον origin-checked WebView
  channel, διατηρούν τη σειρά γρήγορων εντολών και χειρίζονται με ασφάλεια τις
  αλλαγές EPUB section.
- Η Room βιβλιοθήκη αποθηκεύει πολλά EPUB με εσωτερικό UUID, συμβατό
  `foliateKey`, SHA-256, μόνιμο SAF URI και ξεχωριστό CFI/progress ανά βιβλίο.
  Η προηγούμενη επιλογή SharedPreferences εισάγεται μία φορά χωρίς απώλεια.
- Το εξώφυλλο εξάγεται πλέον native από το EPUB zip (container.xml → OPF →
  `cover-image` ή EPUB 2 `meta name="cover"`), αποθηκεύεται ως οριοθετημένη
  μικρογραφία JPEG (μέγιστη πλευρά 512 px) σε app-private αρχείο και η Room
  κρατά μόνο το path (`coverPath`, migration 1→2). Δεν περνά τίποτα από το
  WebView ούτε αποθηκεύεται blob. Εκκρεμεί επαλήθευση σε συσκευή.
- Διορθώθηκε σοβαρό σφάλμα του reader shell: ο `WebViewClient` έκοβε τα `blob:`
  URL που δημιουργεί το ίδιο το `foliate-js` για κάθε ενότητα, οπότε το iframe
  φόρτωνε κενό έγγραφο και δεν εμφανιζόταν ποτέ κείμενο βιβλίου, παρότι CFI,
  ποσοστό και TOC δούλευαν. Πλέον επιτρέπονται μόνο `blob:`/`data:`, χωρίς άδεια
  Internet.
- Reader chrome κατά το σκίτσο: overlay top bar με back, τίτλο/συγγραφέα και
  bookmark ribbon (tap αποθηκεύει το τρέχον CFI, long press ανοίγει τη λίστα),
  tap στο κέντρο κρύβει/δείχνει το UI, Chapters view από το TOC, Annotate
  toggle που κρατά την επιλογή κειμένου κλειστή όσο διαβάζεις.
- Paginated: κάτω μπάρα με seek slider. Scrolled: τρεις τελείες αριστερά με
  bubble σελίδας και προσγείωση στο release. Η ρύθμιση flow αποθηκεύεται.
- Bookmarks: Foliate-συμβατό JSON array CFI ανά βιβλίο (migration 2→3).
- Ο reader έδειχνε λευκή σελίδα: ο `WebViewClient` έκοβε τα `blob:` URL που
  φτιάχνει το ίδιο το `foliate-js` για κάθε ενότητα. Πλέον επιτρέπονται μόνο
  `blob:`/`data:` επιπλέον του bundled origin, χωρίς άδεια Internet.
- Chrome ανάγνωσης: επιπλέοντα ημιδιαφανή islands (top bar με τίτλο, κεφάλαιο
  και ribbon για bookmarks, Chapters/Annotate, seek bar), tap οπουδήποτε
  εμφανίζει/κρύβει το UI με animation, οριζόντιο drag γυρίζει σελίδα με
  slide animation.
- Bookmarks: πολλαπλά CFI ανά βιβλίο σε Foliate-συμβατό JSON array
  (`bookmarks`, migration 2→3), λίστα με long press στο ribbon.
- Chapters view από το TOC, select mode εκτός ανάγνωσης, paginated/scrolled,
  δύο θέματα (grey on white, white on grey) και ρυθμίσεις τυπογραφίας
  (μέγεθος, ύψος γραμμής, περιθώρια, γραμματοσειρά) που διατηρούνται.
- Επιβεβαιώθηκαν σε Android 16 συσκευή η compact phone διάταξη, η side-panel
  διάταξη σε landscape/tablet πλάτος, η βιβλιοθήκη δύο πραγματικών EPUB, η
  ανεξάρτητη επαναφορά θέσης ανά βιβλίο, η αμφίδρομη πλοήγηση και η ακριβής
  επαναφορά CFI μετά από force-stop.

### Φάση 2 — Foliate-compatible reading data

- Bookmarks.
- Highlights, colors και notes.
- Annotation list και CFI navigation.
- Exact Foliate JSON import/export.
- HTML, Markdown και ORG export.
- Identifier mismatch warning.
- Golden fixtures από πραγματικό Foliate.

Τρέχουσα πρόοδος:

- Bookmarks ανά βιβλίο ως συμβατός πίνακας CFI· η κορδέλα στο top bar τα προσθέτει
  και με long press ανοίγει η λίστα τους.
- Highlights με την παλέτα του Foliate (yellow, orange, red, magenta, aqua, lime),
  σημειώσεις στην ίδια εγγραφή, διαγραφή χρώματος, και λίστα annotations με
  μετάβαση στο CFI.
- Import/export ακριβούς Foliate JSON: `metadata`, `lastLocation`,
  `progress` ως `[τρέχουσα τοποθεσία, σύνολο]`, `bookmarks`, `annotations`.
  Άγνωστα πεδία διατηρούνται αυτούσια και επιστρέφουν στο επόμενο export.
- Συγχώνευση βάσει `value` και `modified`, ένωση bookmarks, προειδοποίηση όταν
  το `identifier` δεν ταιριάζει, και preview modal πριν γραφτεί οτιδήποτε.
- Συγχρονισμός μέσα σε συγχρονισμένο φάκελο (Syncthing/Nextcloud) με δομή
  φακέλων: επιλέγεται μία φορά ο φάκελος για όλη τη βιβλιοθήκη και κάθε βιβλίο
  παίρνει δικό του υποφάκελο με το όνομά του, όπου η εφαρμογή γράφει
  `annotations.json`. Έτσι μπορεί να ρίξει κανείς αρχεία από Linux με το χέρι.
  Κατά την ανάγνωση λαμβάνεται το νεότερο `.json` του φακέλου, με οποιοδήποτε
  όνομα, ώστε ένα export που ήρθε από τον υπολογιστή να υπερισχύει. Αν το αρχείο
  είναι νεότερο από αυτό που είδε τελευταία η εφαρμογή, το άνοιγμα του βιβλίου
  εμφανίζει μόνο του το preview εισαγωγής. Μόνιμη άδεια ανάγνωσης/εγγραφής,
  χωρίς server.
- Επαληθεύτηκε με πραγματικό αρχείο εξαγωγής Foliate (567 annotations).
- HTML/Markdown/ORG export και golden fixture από πραγματικό Foliate: έτοιμα.
- Διορθώθηκε σοβαρό σφάλμα εμφάνισης: το `foliate-js` σχεδιάζει ένα annotation μόνο
  αν η ενότητά του είναι φορτωμένη εκείνη τη στιγμή και δεν κρατά δικό του
  κατάλογο. Έτσι τα highlights εξαφανίζονταν μόλις άλλαζε ενότητα και ένα import
  δεν φαινόταν πουθενά εκτός της ανοιχτής ενότητας. Πλέον το `check.js` κρατά
  `Map` με value → {annotation, index} και τα ξανασχεδιάζει στο `create-overlay`
  κάθε ενότητας, όπως κάνει και το ίδιο το Foliate.

### Φάση 3 — PDF και επιπλέον formats

- PDF.js ως ξεχωριστό experimental milestone.
- MOBI/KF8/AZW3.
- FB2/FBZ.
- CBZ.
- Μεγάλα αρχεία και bounded memory.
- Καθαρό error για password-protected files.

Τρέχουσα πρόοδος:

- Το βιβλίο σερβίρεται πλέον με τη δική του κατάληξη (`selected.<ext>`) και το
  αντίστοιχο MIME. Το `foliate-js` αναγνωρίζει zip και PDF από τα bytes, αλλά
  ξεχωρίζει CBZ, FBZ και FB2 από το όνομα, οπότε αυτό ήταν το μόνο που έλειπε
  για να δουλέψουν οι υπόλοιποι loaders του ίδιου, ελεγμένου engine.
- Ο picker δέχεται `*/*`: CBZ, FB2 και MOBI φτάνουν συνήθως χωρίς δηλωμένο MIME
  και το SAF τα γκριζάριζε. Ό,τι δεν αναλύεται δίνει καθαρό μήνυμα λάθους.
- PDF: το PDF.js 5 χρησιμοποιεί `Uint8Array.toHex/fromHex/toBase64` και
  `Map.getOrInsertComputed`, που το Android WebView δεν έχει ακόμη. Το
  `reader/polyfills.mjs` τα συμπληρώνει, και επειδή ο worker έχει δικό του
  global, το `reader/pdf-worker.mjs` είναι shim που πρώτα φορτώνει τα polyfills
  και μετά τον πραγματικό worker.
- Το fixed-layout renderer (PDF, comics) δεν έχει `render()`/`firstSection()`:
  η εκκίνηση περνά πλέον πάντα από το `view.init()` και το `render()` καλείται
  προαιρετικά.
- Εξώφυλλο για CBZ: όταν δεν υπάρχει EPUB manifest, χρησιμοποιείται η πρώτη
  εικόνα του zip κατά σειρά ονόματος.
- Τίτλος: PDF και comics σπάνια έχουν, οπότε η εφαρμογή πέφτει πίσω στο όνομα
  του αρχείου αντί για «Untitled book».
- Επαληθεύτηκαν σε emulator tablet (Android 36, 10.1"): PDF δύο σελίδων με
  σελιδοποίηση και seek bar, CBZ με εξώφυλλο και σελίδες, μικρό EPUB και
  πραγματικό EPUB 1,4 MB με 4721 σελίδες.
- Εξώφυλλο για PDF: όταν το αρχείο δεν είναι zip, η πρώτη σελίδα αποδίδεται με
  τον native `PdfRenderer` του Android και αποθηκεύεται ως η ίδια οριοθετημένη
  μικρογραφία. Δεν εμπλέκεται PDF.js ούτε το WebView. Επαληθεύτηκε σε emulator.
- Μετρήσεις μνήμης σε μεγάλο PDF (18,2 MB, 77 σελίδες, emulator tablet Android
  36): πέντε πλήρεις διαδρομές μπρος-πίσω. Ο WebView renderer σταθεροποιήθηκε
  γύρω στα 630 MB RSS (613–630 στις διαδρομές 2–5, χωρίς αύξηση στις τελευταίες)
  και το app έμεινε στα ~265 MB. Η χρήση είναι bounded· η απόλυτη τιμή είναι
  υψηλή επειδή ο emulator δεν ασκεί memory pressure στα caches του Chromium.
- FB2 και FBZ επαληθεύτηκαν σε emulator με δοκιμαστικό αρχείο: τίτλος,
  συγγραφέας, κεφάλαια και TOC σωστά, και τα δύο ως ξεχωριστά βιβλία.
- Καθαρά μηνύματα λάθους χωρίς crash και χωρίς να μπει το αρχείο στη
  βιβλιοθήκη: αρχείο που δεν είναι βιβλίο («File type not supported») και
  password-protected zip/CBZ («No supported image files in archive»).
- Εκκρεμούν: MOBI/AZW3 σε πραγματικό αρχείο· το μήνυμα για password-protected
  αρχείο είναι καθαρό αλλά γενικό, δεν λέει ότι το αρχείο είναι κλειδωμένο· και
  το fit της σελίδας fixed layout στο viewport (η σελίδα δεν κεντράρεται ούτε
  χωράει ολόκληρη σε ύψος).

### Φάση 4 — Πλήρες feature set

- Search, page list και landmarks.
- Android Text-to-Speech και media overlays.
- Copy text/citation.
- Dictionary, Wikipedia και translation actions.
- OPDS catalogs και downloads.
- Image viewer.
- Calibre embedded highlights.
- TalkBack, scalable text και accessibility tests.

Τρέχουσα πρόοδος:

- Αναζήτηση σε όλο το βιβλίο με τη μηχανή του `foliate-js`: τα αποτελέσματα
  έρχονται ανά ενότητα σε παρτίδες, με κεφάλαιο και απόσπασμα, και το άγγιγμα
  πηγαίνει στο CFI. Νέα αναζήτηση ακυρώνει την προηγούμενη· όριο 300 ευρήματα.
  Καρτέλα «Search» στο sidebar του tablet, πλήρης οθόνη στο τηλέφωνο.
- Copy/citation και dictionary/Wikipedia/translation υπάρχουν ήδη από τη Φάση 2.
- Διορθώθηκε: όταν το `foliate-js` πλοηγείται, μετακινεί και τον δρομέα
  (`setSelectionTo` του paginator), οπότε κάθε μετάβαση φαινόταν σαν επιλογή
  κειμένου και άνοιγε το πάνελ. Πλέον αναφέρεται μόνο επιλογή που ξεκίνησε από
  άγγιγμα στη σελίδα.
- Page list και landmarks: το `BookReady` φέρνει και τα δύο και εμφανίζονται στην
  ίδια οθόνη Chapters, κάτω από δικές τους επικεφαλίδες («Landmarks»,
  «Contents», «Printed pages»). Βιβλίο χωρίς αυτά δεν δείχνει επικεφαλίδες.
- Ανάγνωση φωναχτά με το Android TTS. Το `foliate-js` παράγει SSML για
  speech-dispatcher, που το Android δεν δέχεται, οπότε ο reader το ισοπεδώνει σε
  κείμενο και το δίνει μπλοκ-μπλοκ· όταν τελειώσει η φωνή ζητείται το επόμενο,
  και η ενότητα γυρίζει μόνη της. Το πρώτο `mark` κάθε μπλοκ ορίζεται από τον
  reader, γιατί το Android δεν αναφέρει marks: αυτό υπογραμμίζει και κυλά στην
  πρόταση που διαβάζεται. Η γλώσσα έρχεται από τα metadata του βιβλίου.
- Image viewer: εικόνα του βιβλίου ανοίγει σε πλήρη οθόνη μέσα στην ίδια σελίδα
  του reader (tap κλείνει, διπλό tap μεγεθύνει). Τα bytes δεν περνούν από το
  bridge ούτε φτάνουν σε native decoder.
- Accessibility: content descriptions στα σχεδιασμένα χειριστήρια (κορδέλα
  bookmark, βελάκια σελίδας, back, ανάγνωση φωναχτά).
- Εκκρεμούν: OPDS catalogs (χρειάζεται άδεια Internet — απόφαση δική σου, το
  app σήμερα διαφημίζει ότι δεν έχει καμία), Calibre embedded highlights
  (το `foliate-js` δίνει `getCalibreBookmarks()`, αλλά η μετατροπή των calibre
  CFI σε EPUB CFI δεν γράφεται στα τυφλά — χρειάζεται πραγματικό αρχείο
  calibre), TalkBack end-to-end σε συσκευή.

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
