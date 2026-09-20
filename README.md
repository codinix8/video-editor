# VideoEditor

Persönlicher Video-Editor für Android, gebaut in Zusammenarbeit mit Claude.
TikTok-artige Segment-Aufnahme, Overlays, Mosaik-Layouts, Freistellung, Farbfilter.

## APK herunterladen

Jeder Push auf `main` baut automatisch eine APK.
Neueste Version: **Releases** (rechte Seitenleiste) → oberster Eintrag → `.apk`-Datei.

Auf dem Handy: Datei öffnen → „Aus dieser Quelle installieren" erlauben → installieren.

## Stand

| Schritt | Inhalt | Status |
|---|---|---|
| 0 | Kamera-Vorschau, Aufnahme, Speichern in Galerie, Build-Kette | ✅ Build 1 |
| 1 | Segment-Aufnahme (Start/Pause/Weiter), letztes Segment löschen, Export, Auflösungswahl, Kamera-Wechsel | ✅ Build 2 |
| 1b | Review-Ansicht: Vorschau in Schleife, zurück zur Aufnahme, löschen, speichern | ✅ Build 4 |
| 2a | OpenGL-Render-Pipeline (Vorschau = Aufnahme), Bild-Overlays: einfügen, verschieben, skalieren, drehen, entfernen | ✅ Build 6 |
| 2a+ | Entwürfe speichern/öffnen, Spiegelungs-Kompensation Frontkamera | ✅ Build 8 |
| 2b | Video-Overlay (PiP): läuft nur während der Aufnahme, Ton beim Export mischbar, Teilen-Knopf | ✅ Build 11 |
| 2c | Lautstärkeregler pro Overlay (0–200 %), Mikrofon-Regler im Export, Overlay-Ton in Vorschau bei Kopfhörern | ✅ Build 15 |
| 2d | Review spielt Overlay-Ton synchron mit (Referenz vor dem Export) | ✅ Build 17 |
| 2e | Eigener Overlay-Ton-Renderer (WAV), Absturzbericht, Wiederherstellung nach Absturz als Entwurf | ✅ Build 22 |
| 2f | Ton-Historie: entfernte Video-Overlays behalten ihren Ton (Einfügen bis Entfernen), Mehrspur-Mix | ✅ Build 23 |
| 2g | Protokoll: Play/Pause fürs Overlay-Video während der Aufnahme, Lautstärke gilt ab Änderung | ✅ Build 24 |
| 4a | Text- und Emoji-Overlays: Farbe, Hintergrund, Deckkraft, bearbeiten | ✅ Build 26 |
| 3a | Kachel-Modus: Kamerabild als Kachel (Quadrat/Rechteck 3:4/Kreis) mit Signatur-Rahmen vor Hintergrundvideo (KI-Freistellung verworfen) | ✅ Build 29 |
| 3 | Freistellung (Person vor Video-Hintergrund), Mosaik-Layouts | offen |
| 4 | Farbfilter | offen |
| 5a | Untertitel: Whisper on-device, Sprache wählbar, automatisch in Review optional, Einbrennen beim Export | ✅ Build 33 |
| 5b | Base als Standard (Tiny/Small wählbar), Modell-Vorabladen beim Start, 5 Vorlagen, verschieb-/skalierbar | ✅ Build 34 |
| 5c | 11 Vorlagen mit Vorschaukarten, Akzentfarbe, Untertitel frei verschieb- und drehbar | ✅ Build 35 |
| 5d | Emoji-Wörterbuch (DE/EN, ~300 Stämme, Flaggen), Schalter im CC-Dialog | ✅ Build 38 |
| 1c | Entwürfe mit Vorschaubild | ✅ Build 39 |
| 5e | Untertitel-Editor: Text ändern, löschen, Anfang/Ende verschieben, Sprung zum Block; CC-Knopf rechts | ✅ Build 42 |
| 1d | Spulleiste in der Review, 5-Minuten-Limit, Speicherplatz-Warnung, Segmentteilung bei 3,5 GB, feste Export-Bitraten mit Größenschätzung, Cache-Grenzen | ✅ Build 44 |
| 3b | Mosaik: 5 Raster, Kamera/Bilder in Kacheln, Zoom/Verschieben, Füllfarbe, Trennlinien (Regenbogen optional) | in Test |

## Technik

Kotlin · CameraX · Media3 · OpenGL ES · ML Kit Selfie Segmentation
minSdk 26 (Android 8) · targetSdk 35
