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
| 0 | Kamera-Vorschau, Aufnahme, Speichern in Galerie, Build-Kette | in Arbeit |
| 1 | Segment-Aufnahme (Start/Pause/Weiter), letztes Segment löschen, Export, Auflösungswahl | offen |
| 2 | Bild-Overlays aus Galerie, Video-Overlays (PiP), verschieben/skalieren | offen |
| 3 | Freistellung (Person vor Video-Hintergrund), Mosaik-Layouts | offen |
| 4 | Farbfilter, Text- und Emoji-Overlays | offen |

## Technik

Kotlin · CameraX · Media3 · OpenGL ES · ML Kit Selfie Segmentation
minSdk 26 (Android 8) · targetSdk 35
