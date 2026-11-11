# BabaCut – Projekt-Handbuch (Stand 3. November 2026)

Dieses Dokument ist die vollständige Einarbeitung für einen neuen Chat oder eine neue Person.
Es ersetzt den Gesprächsverlauf. **Geheimnisse (GitHub-Token, Keystore-Passwort) stehen bewusst nicht hier** –
der Repository-Inhaber (codinix8) gibt sie im neuen Chat weiter.

## 1. Was BabaCut ist

Android-Videoeditor für Kurzvideos (9:16), Kernidee **"bearbeiten während der Aufnahme, nicht danach"**:
Segment-Aufnahme, Overlays (Bild/Text/Video), Kachel-Modus (eigene Kamera als Kachel vor Hintergrundvideo,
mit "Baba Regenbogen"-Rahmen), Mosaik (2–4 Kacheln, je Kamera/Bild/Video, live verschieb-/drehbar),
Untertitel per Whisper auf dem Gerät, Schnitt in der Review, Export bis 4K. Kein Konto, kein Upload, keine Werbung.
Monetarisierung: **BabaCut Pro** (Abo, Google Play Billing).

Inhaber/Marke: codinix8 (Firma "Codinix", Play-Console-Konto "Dev Team Z", privates Konto).
Zielgruppe: Creator; wichtig ist die persische Community (Farsi-Oberfläche, Namen Top/Ali/Baba, "Baba Regenbogen").

## 2. Repository und Build-Kette

- Repo (öffentlich): https://github.com/codinix8/video-editor · Branch `main`
- Package/applicationId: `de.codinix.babacut` (Kotlin-Namespace bleibt `de.codinix.videoeditor`)
- Arbeitsweise: Claude ändert Code, pusht nach `main`; der Nutzer hat **keine Programmiererfahrung** und testet auf
  dem Handy (Xiaomi 25010PN30G, Android 16). Kommunikation auf Deutsch; Nutzer diktiert oft.
- Git-Identität im Container setzen: `git config user.email codinix8@users.noreply.github.com; git config user.name codinix8`.
  Push über `https://x-access-token:<TOKEN>@github.com/codinix8/video-editor.git` (Token vom Nutzer erfragen; Token darf
  Secrets/Pages nicht ändern → solche Dinge macht der Nutzer in der GitHub-Oberfläche).
- **Workflows** (`.github/workflows/`):
  - `build.yml`: jeder Push auf `main` (außer reine Doku-Pushes: `paths-ignore` für `docs/**` und `**/*.md`,
    seit 2026-11-09) → Debug-APK (Signatur `keystore/videoeditor.jks`), Release `build-N` mit
    `VideoEditor-buildN.apk`. Bei Fehlern wird `build.log` in Branch `build-logs` gepusht
    (lesbar unter https://raw.githubusercontent.com/codinix8/video-editor/build-logs/build.log).
  - `release.yml`: Tag `v*` oder manuell → `bundleRelease` + `assembleRelease`, signiert mit Upload-Schlüssel aus
    GitHub-Secrets `UPLOAD_KEYSTORE_B64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS` (=`babacut`); Release
    `release-<version>-<run>` mit `BabaCut-<version>.aab` (Play Console) und `BabaCut-<version>-release.apk`.
  - `pr.yml` (seit 2026-11-09): jeder Pull Request auf `main` → `assembleDebug` + `lintDebug` (Lint vorerst nicht
    blockierend, Bericht als Artifact `lint-report`). Erstellt **kein** Release, keinen Tag, keinen `build-logs`-Push.
- Build dauert ~4,5 min. Gradle-Build-Cache ist aus (lieferte veraltete Zwischenstände). API: compileSdk/targetSdk 36,
  minSdk 26, AGP 8.7.3 (`android.suppressUnsupportedCompileSdk=36`), NDK 27.2.12479018, CMake 3.22.1,
  Submodule `third_party/whisper.cpp` v1.9.4, 16-KB-Page-Size-Linkerflags gesetzt, kein R8/Minify.
- Prüfen eines Builds per GitHub-API: `.../actions/runs?per_page=1` bzw. `.../actions/workflows/release.yml/runs`.
- Versionierung: `versionCode` fortlaufend (aktuell **81**), `versionName` aktuell **1.1.3**.
- **Wichtig für Tests auf dem Nutzer-Handy**: Die App ist dort über den **Play Store (interner Test)** installiert.
  GitHub-APKs haben eine andere Signatur und lassen sich nicht darüber installieren → neue Builds immer als
  `.aab` in den internen Test laden.

## 3. Architektur (app/src/main/java/de/codinix/videoeditor/)

- `MainActivity.kt` (sehr groß; Aufnahme, Review, Dialoge, Pro-Schranken, Persistenz).
- `gl/CompositorProcessor.kt`: eigener OpenGL-ES2-SurfaceProcessor als CameraX-CameraEffect; Vorschau = Aufnahme;
  Kamera, Overlays, Kachel-Modus, Mosaik, Farbfilter (`gl/ColorFilters.kt`, 9 Looks) werden live gerendert/eingebrannt.
- `overlay/`: OverlayModel (ImageOverlay, TextOverlay, VideoOverlay mit Ereignisprotokoll `events` [atMs, gain,
  playing, seekMs], CameraOverlay), OverlayGestureView (Hit-Test, Drag/Pinch/Rotate, Einrasten 90° + Bildmitte mit
  roher Position, Mosaik-Gesten, Tipp = Play/Pause mit einblendendem Symbol), Mosaic.kt (5 Raster), TextRenderer.
- `whisper/`: WhisperEngine + JNI (app/src/main/cpp), ModelManager (Modelle von huggingface ggerganov/whisper.cpp:
  Tiny="Top (schnell)", Base="Ali (ausgewogen)", Small="Baba (am genauesten)"; Vorabladen), AudioPrep (16 kHz mono,
  `lastDiagnostics`), Captions (Chunking an Satz- **und Segmentgrenzen**), CaptionStyle (11 Vorlagen),
  CaptionView (Review-Anzeige, Gesten, Review-Texte, Wasserzeichen), CaptionOverlay/PostOverlay (Media3 BitmapOverlay
  fürs Einbrennen; ReviewText), EmojiDict.
- `Exporter.kt`: schneller MediaMuxer-Concat (`VideoConcat.kt`) oder Media3 Transformer mit festen Bitraten
  (4K 45 / 2K 24 / 1080p 14 / 720p 7 / 480p 3 Mbit/s), Audio-Mix (`OverlayAudioRenderer.kt` → WAV), Mikrofon-Gain
  je Segment, Untertitel/Review-Texte/Wasserzeichen als Overlays. `Loudness.kt` (Regler-% = empfundene Lautstärke).
- `Downscaler.kt`: Media3-Umwandlung nach H.264 1080p; `transcodeReason()` prüft HEVC/AV1, >1080p, >31 fps, HDR –
  gilt für Bild-im-Bild, Hintergrund und Kachelvideos beim Einfügen.
- `TimelineRemap.kt`: Umordnen/Löschen von Segmenten remappt Untertitel und Ton-Protokolle (mit Quell-Sprüngen);
  **seit 1.1.3 werden Untertitel nach einem Schnitt zusätzlich automatisch neu erkannt**.
- `Drafts.kt`: Entwürfe (Thumbnail, Overlays, Ton-Historie, Untertitel, Mosaik, Review-Texte, micGains, `auto`-Flag
  für Export-Sicherungen; `pruneAuto`, `keep`), Session-Wiederherstellung nach Absturz (`CrashLog.kt`).
- `Pro.kt`: Google Play Billing 8.0.0; Abo `babacut_pro`, Pläne `monthly` (2,99 €) und `annual` (19,99 €, Angebot
  `trial7` = 7 Tage gratis); Status lokal mit 3 Tagen Offline-Kulanz; `Pro.isActive(ctx)`; Debug-Override im Debug-Build.
- UI: `Sheet.kt` + `SheetRows.kt` (Bottom-Sheets im TikTok-Stil: Abschnitte außerhalb der Karten, Zeilen, Auswahl,
  Schalter, gesperrte Zeilen mit Krone), `ScrubBarView` (Spulleiste, Long-Press-Umordnen, Papierkorb oben, Rückgängig
  15 s), `SegmentProgressView`, `SafeZoneView` (Plattform-Hilfslinien), `GestureHintView` (Tipp-Animation).
- Ressourcen: 7 Sprachen (values = EN Standard, values-de/tr/es/fr/fa/ar), `xml/locales_config.xml`,
  `supportsRtl=false` (Layout bleibt fest). Übersetzungstabellen in /home/claude/i18n existieren nur im alten Container –
  neue Strings direkt in allen 7 `strings.xml` ergänzen.

## 4. Funktionsumfang (alles vom Nutzer bestätigt)

Segment-Aufnahme bis 5 min (grün/orange/rot-Balken), Speicherplatz-Warnung, Segmentteilung bei 3,5 GB, Bildschirm an;
Kamera-Wechsel; Auflösungswahl; Review mit Schleife, Spulleiste (Feinspulen), Segmente umordnen/löschen + Rückgängig;
Export mit Größenschätzung + Teilen; Entwürfe mit Vorschaubild und **Verlauf** (Export-Sicherungen, 7 Tage, Anzahl
einstellbar); Bild-/Text-Overlays (Stil wird gemerkt), Review-Texte über das ganze Video; Bild-im-Bild-Video und
Kachel-Modus (Frontkamera automatisch, Form/Rahmen immer sichtbar); Mosaik mit Bildern/Videos (Symbol-Panel wie TikTok);
Play/Pause per Tipp mit Protokoll; Lautstärke pro Video und **Ton in der Review je Segment oder gesamt** (Mikrofon je
Segment); Farbfilter; Untertitel (Whisper, Sprache wählbar, Automatik, 11 Vorlagen, Akzentfarbe, Emojis, Editor mit
Anfang/Ende, Segmentnummer); Plattform-Hilfslinien; Einrasten; Datei-Browser per langem Drücken (für Downloads);
Einstellungen (Sprache, Untertitel, Auflösung, Vorschau-Ton, Hilfslinien, Mosaik, Sicherungen, Gesten, Lizenzen,
Datenschutz, Absturzbericht, Tipps); Tipp-Karten beim ersten Öffnen; Kopfhörer-Hinweis mit Punkt.

**Pro-Schranken (ohne Abo, sichtbar mit Krone, Tipp öffnet Pro-Seite):** Aufnahme/Export nur bis 1080p
(4K/2K Pro), Gratis-Export immer neu kodiert mit Wasserzeichen "Made with BabaCut" unten rechts; Kachel-Modus,
Mosaik-Videos, Erkennung Ali/Baba, Vorlagen ab der 4., Emojis, Filter ab dem 4., Hilfslinien, >1 Export-Sicherung.

## 5. Google Play (Stand)

- App angelegt: **BabaCut**, `de.codinix.babacut`, kostenlos, Kategorie Video Players & Editors, Kontakt
  support@babacut.io, Website https://babacut.io, Datenschutz https://babacut.io/privacy.
- Alle Erklärungen ausgefüllt (Sign-in: Yes/Payments mit Prüfer-Hinweis, Altersfreigabe mit digitalen Käufen,
  Zielgruppe 18+, Datensicherheit "keine Daten erhoben", keine Werbung, keine Finanzfunktionen).
- Store-Eintrag komplett: EN Standard + DE/TR/ES/FR/FA/AR importiert; Icon 512, Feature-Grafik 1024×500, 6
  Screenshots (Phone + 7"/10"-Tablet). Texte: `BabaCut_Store_Paket.md`, `store_description_en.txt`,
  `BabaCut_store_translations.txt` (im alten Chat ausgegeben; Inhalte entsprechen den Store-Feldern).
- **Interner Test**: aktuelles Bundle 1.1.3 (81). **Geschlossener Test "Closed Beta"**: Tester-Liste mit ~19 Adressen
  eines Fiverr-Anbieters + weiterer Anbieter mit eigener CSV (bereits importiert); Einladungslink
  https://play.google.com/apps/testing/de.codinix.babacut. Zum 25.10. wurde der Produktionsantrag abgelehnt
  ("requires more testing"); seitdem laufen **14 weitere Tage** mit ≥12 Testern → erneut beantragen ab ca.
  **9. November 2026**. Es wurde empfohlen, 1.1.3 als Update in den geschlossenen Test zu laden und zwischen Tag 7
  und 10 ein weiteres Update zu liefern. Antworten für den Antrag (300-Zeichen-Felder) sind im alten Chat formuliert;
  beim zweiten Antrag konkreter mit Zahlen/Zitaten.
- Abo in der Console: `babacut_pro` aktiv, Pläne `monthly` (netto so gesetzt, dass DE 2,99 € brutto) und `annual`
  (19,99 € brutto) mit Angebot `trial7`; alter Prepaid-Plan `yearly` inaktiv. Lizenztester: das Konto des Nutzers.
- Play App Signing aktiv; Upload-Schlüssel `babacut-upload.jks` (Alias `babacut`, gültig bis 2054) liegt beim Nutzer.

## 6. Domain, Webseite, E-Mail

- Domains bei Hosteurope: **babacut.io** (Haupt), **babacut.net** (Weiterleitung auf .io, ggf. noch einzurichten).
- Webseite: GitHub Pages aus `docs/` (CNAME babacut.io): index, support, privacy (EN/DE/FA), imprint
  (**Platzhalter – Impressumsdaten fehlen noch**), 404; Sprachwahl mit Flaggen DE/EN/FA, Farsi RTL.
  Geplant (nicht gebaut): Umbau auf "Live statt Post-Editing" mit Screenshots, Vergleichsseite BabaCut vs. CapCut,
  Blog (Jekyll), Glossar, Presseseite, strukturierte Daten/llms.txt, rechtssichere Impressum/Datenschutz-Fassung (Fragen an den Nutzer: Name/Firma, Anschrift,
  USt-ID, MStV-Verantwortlicher, Telefon; Analytics/Newsletter geplant?). Werbeaussagen sachlich halten (UWG).
- Schrift: Farsi-Schrift Vazirmatn (v33.003, Regular/Bold, SIL OFL 1.1) liegt selbst gehostet in
  `docs/assets/fonts/` (Lizenz `OFL.txt` daneben), eingebunden per `@font-face` in `docs/style.css`; keine Anfrage an
  Google Fonts mehr (erledigt 2026-11-10). Keine externen Schrift-/CSS-Links in neue Seiten einbauen.
- E-Mail: Microsoft 365 Business Basic mit Domain babacut.io; DNS (MX, autodiscover, SPF) bei Hosteurope gesetzt;
  `support@babacut.io` als Alias am freigegebenen Postfach `supporter@`; DKIM noch offen.
- Optional später: Claude-Code-Routine (geplanter Task) für tägliche Blog-Entwürfe als PR in `claude/`-Branch.

## 7. Offene Punkte / Ideen

- Produktionsantrag erneut ab 9.11.; Update für Tester zwischen Tag 7–10 (z. B. 1.1.4 mit Kleinigkeiten).
- Impressumsdaten → Webseite fertigstellen; DKIM; babacut.net-Weiterleitung.
- Untertitel-Drift bei sehr langen Videos (Ton-Kontrolle-Meldung nur im Debug-Build) – nicht abschließend geklärt.
- Farsi/Arabisch-Texte vom Nutzer gegenlesen; Screenshots je Sprache auf abgeschnittene Knöpfe prüfen.
- Später: Whistle (Cactus) als schnelle Gratis-Erkennung für 7 Sprachen prüfen (kein Farsi/Türkisch/Arabisch);
  Personen-Freistellung on-device (MediaPipe) mit zeitlicher Glättung; iOS-Portierung (~60–70 % Aufwand).

## 8. Arbeitsregeln, die sich bewährt haben

- Vor größeren Umbauten Fragen stellen und Vorschlag machen ("erst reden, dann bauen").
- Jeder Build: Versionscode +1, Commit, Push, Warten (~4,5 min), Ergebnis mit Download-Link und "Bitte prüfen"-Liste
  melden. Store-Builds per Tag `vX.Y.Z` auslösen.
- Diagnosen über Screenshots des Nutzers; bei Fehlern erst Code lesen, dann gezielt ändern.
- Keine Superlative in Marketingtexten; keine Geheimnisse in Repo oder Doku.

## 9. AI Nation (since 2026-11-08)

The project is managed by AI Nation. Agent rules: [CLAUDE.md](CLAUDE.md). Project record map and English state
summary incl. "State and backlog" (2026-11-09): [docs/AI_NATION.md](docs/AI_NATION.md) (roadmap, decisions, knowledge and known issues live in `docs/`).
Owner profile (how he works and decides): [docs/FINETUNING.md](docs/FINETUNING.md).
Proposal for splitting `MainActivity.kt` (2026-11-10, waiting for the owner): [docs/MAINACTIVITY_SPLIT.md](docs/MAINACTIVITY_SPLIT.md).
Team decision `project.team` requested on 2026-11-09 (see [docs/DECISIONS.md](docs/DECISIONS.md)).
