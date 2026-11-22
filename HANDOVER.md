# BabaCut – Projekt-Handbuch (Stand 20. November 2026)

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
- AI-Nation-Agenten arbeiten auf `agent/…`-Branches mit Pull Requests, nie direkt auf `main` (siehe CLAUDE.md).
  Token/Secrets/Pages-Einstellungen ändert nur der Nutzer in der GitHub-Oberfläche.
- **Workflows** (`.github/workflows/`):
  - `build.yml`: Push auf `main` (nicht bei reinen Doku-Pushes, `paths-ignore`) → Debug-APK, Release `build-N`; bei Fehlern
    `build.log` im Branch `build-logs`. `release.yml`: Tag `v*` → signiertes AAB + APK (Upload-Schlüssel aus Secrets
    `UPLOAD_KEYSTORE_*`, Alias `babacut`), Release `release-<version>-<run>`; das AAB lädt der Nutzer in die Play Console.
  - `ci.yml` (Prüfung `build-test-lint`, seit 2026-11-10): jeder Push (außer `build-logs`) und jeder PR auf `main` →
    `assembleDebug`, `testDebugUnitTest`, `lintDebug` (Altfehler in `app/lint-baseline.xml`), alle blockierend; kein Release.
    Lokale Befehle: `CONTRIBUTING.md`. `screenshots.yml`: App-Screenshots auf dem Emulator (en/fa/ar, API 35; PR #31).
- Build dauert ~4,5 min. Gradle-Build-Cache ist aus (lieferte veraltete Zwischenstände). API: compileSdk/targetSdk 36,
  minSdk 26, AGP 8.7.3 (`android.suppressUnsupportedCompileSdk=36`), NDK 27.2.12479018, CMake 3.22.1,
  Submodule `third_party/whisper.cpp` v1.9.4, 16-KB-Page-Size-Linkerflags gesetzt, kein R8/Minify.
- Versionierung: `versionCode` **84** / `versionName` **1.1.5** auf `main` (noch nicht getaggt; im Play-Store 81). Regel: ein Code je Play-Upload.
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
- `Downscaler.kt`: Media3-Umwandlung nach H.264 1080p; `transcodeReason()` (Regel in `TranscodePolicy.kt`, mit Unit-Tests) prüft HEVC/AV1, >1080p, >31 fps, HDR –
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
- **Interner Test**: 1.1.3 (81); `v1.1.4` (82) getaggt am 10.11. **Geschlossener Test "Closed Beta"**: ~19 Tester
  (Fiverr-Anbieter + weitere CSV); Einladung https://play.google.com/apps/testing/de.codinix.babacut. Produktionsantrag am
  25.10. abgelehnt ("requires more testing"); neuer Antrag möglich seit 9.11. (≥12 Tester, 14 Tage). Entwurf für den
  zweiten Antrag: `docs/PLAY_PRODUCTION_APPLICATION.md` (Stand 13.11.: auf 1.1.5/84 umgestellt, Owner-Runbook Z1–Z7,
  K1–K8; Aufgabe f16621ce). Z1–Z7 liest der Owner erst, wenn 1.1.5 im geschlossenen Test ist; nichts eingereicht.
- Abo in der Console: `babacut_pro` aktiv, Pläne `monthly` (netto so gesetzt, dass DE 2,99 € brutto) und `annual`
  (19,99 € brutto) mit Angebot `trial7`; alter Prepaid-Plan `yearly` inaktiv. Lizenztester: das Konto des Nutzers.
- Play App Signing aktiv; Upload-Schlüssel `babacut-upload.jks` (Alias `babacut`, gültig bis 2054) liegt beim Nutzer.

## 6. Domain, Webseite, E-Mail

- Domains bei Hosteurope: **babacut.io** (Haupt), **babacut.net** (Weiterleitung auf .io, ggf. noch einzurichten).
- Webseite: GitHub Pages aus `docs/` (CNAME babacut.io): index, support, privacy (EN/DE/FA), imprint
  (**Platzhalter – Impressumsdaten fehlen noch**), 404; Sprachwahl mit Flaggen DE/EN/FA, Farsi RTL.
  Geplant (nicht gebaut): Umbau "Live statt Post-Editing", Vergleichsseite, Blog, Glossar, Presseseite, llms.txt,
  rechtssicheres Impressum (Fragen an den Nutzer: Name/Firma, Anschrift, USt-ID, MStV, Telefon). Werbeaussagen sachlich (UWG).
- Schrift: Vazirmatn selbst gehostet in `docs/assets/fonts/` (OFL), keine externen Schrift-/CSS-Links.
- E-Mail: Microsoft 365 Business Basic mit Domain babacut.io; DNS (MX, autodiscover, SPF) bei Hosteurope gesetzt;
  `support@babacut.io` als Alias am freigegebenen Postfach `supporter@`; DKIM noch offen.
- Optional später: Claude-Code-Routine (geplanter Task) für tägliche Blog-Entwürfe als PR in `claude/`-Branch.

## 7. Offene Punkte / Ideen

- Weg zur Produktion: Tag `v1.1.5` (84; Owner-Entscheidung 14.11.: der Agent setzt den Tag auf den geprüften
  main-Stand) → interner Test → geschlossener Test → Produktionsantrag. Grundlagen: [Tester-Plan](docs/CLOSED_TEST_1_1_4_PLAN.md),
  [Neuigkeiten-Text 1.1.5](docs/RELEASE_NOTES_1_1_5.md) (FA/AR liest der Owner gegen), [Testliste](docs/TEST_CHECKLIST_1_1_5.md).
- Impressumsdaten → Webseite fertigstellen; DKIM; babacut.net-Weiterleitung.
- Untertitel-Drift bei sehr langen Videos (Ton-Kontrolle-Meldung nur im Debug-Build) – nicht abschließend geklärt.
- Farsi/Arabisch-Texte vom Nutzer gegenlesen; Screenshots je Sprache auf abgeschnittene Knöpfe prüfen.
- Später: Whistle (Cactus) als schnelle Gratis-Erkennung für 7 Sprachen prüfen (kein Farsi/Türkisch/Arabisch);
  Personen-Freistellung on-device (MediaPipe) mit zeitlicher Glättung; iOS-Portierung (~60–70 % Aufwand).

## 8. Arbeitsregeln, die sich bewährt haben

- Vor größeren Umbauten Fragen stellen und Vorschlag machen ("erst reden, dann bauen").
- Jeder Build: Versionscode +1, Commit, Push, Warten (~4,5 min), Ergebnis mit Download-Link und "Bitte prüfen"-Liste
  melden. Store-Builds per Tag `vX.Y.Z` auslösen.
- Diagnosen über Screenshots des Nutzers; erst Code lesen, dann gezielt ändern. Keine Superlative; keine Geheimnisse in Repo/Doku.

## 9. AI Nation (since 2026-11-08)

Agent rules: [CLAUDE.md](CLAUDE.md). Record map, conflict-free record rules and English state summary:
[docs/AI_NATION.md](docs/AI_NATION.md). Roadmap, decisions, knowledge, known issues, done log ([docs/DONE.md](docs/DONE.md)),
owner profile ([docs/FINETUNING.md](docs/FINETUNING.md)), troubleshooting, concepts, plans and the design audit live in `docs/`.
Die Webseite bleibt reines HTML, alle `docs/*.md` sind per Glob ausgeschlossen.

**Aktueller Stand (2026-11-22, Aufgabe d04f27db):**
- `v1.1.5` (84) ist noch nicht getaggt (`git ls-remote --tags origin` am 22.11. erneut geprüft: neuester Tag `v1.1.4`); App-Änderungen bleiben bis dahin gesperrt.
- 1.1.6 ist fertig geplant ([docs/plans/](docs/plans/)): PR-1 → (PR-2) → PR-3 → PR-4 → PR-5 → PR-6 (oberer Scrim, A20); ein Versionscode je Play-Upload (85 = erster 1.1.6-Store-Build). Danach 1.1.7 = Export abbrechbar (PR-7a/7b).
- **Offene Frage:** zwei PR-6-Pläne ([Vollbild](docs/plans/2026-11-15-a20-top-scrim.md), [XML-Scrim](docs/plans/2026-11-15-a20-top-scrim-pr6.md)); der Planer muss einen wählen (docs/DECISIONS.md).
- Offen beim Owner: `MainActivity.kt`-Aufteilung ([Vorschlag](docs/MAINACTIVITY_SPLIT.md)), Impressumsdaten, `build-test-lint` als Pflichtprüfung.

**Nächste Schritte:** Tag `v1.1.5` → interner/geschlossener Test → Z1–Z7 ablesen → Produktionsantrag; PR-6-Plan wählen; danach 1.1.6 umsetzen.
