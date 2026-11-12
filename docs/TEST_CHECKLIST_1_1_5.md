# Bitte prüfen – BabaCut 1.1.5 (interner Test auf deinem Handy)

Für den internen Test nach dem Tag `v1.1.5` (versionCode 84). Ziel: Fehlertexte, Absturzbericht und das
Speichern-Fenster erscheinen in der App-Sprache, ohne deutsche Wörter in Englisch und Persisch.
Dauer: etwa 15 Minuten. Bei jedem Punkt: Haken setzen oder kurz notieren, was du stattdessen siehst (ein Screenshot
reicht).

## Vorbereitung

- [ ] **1.1.5 installieren.** Play Store → BabaCut → Aktualisieren (interner Test).
      Erwartet: Einstellungen → ganz unten steht Version 1.1.5.
- [ ] **Kaputte Bilddatei anlegen.** Im Datei-Manager eine leere Datei `test.jpg` in „Downloads“ anlegen (oder eine
      Textdatei in `test.jpg` umbenennen).
      Erwartet: Die Datei liegt in „Downloads“.

## A. Fehlermeldung auslösen – Englisch

- [ ] **Sprache auf Englisch.** Einstellungen → „App-Sprache“ → English.
      Erwartet: Die App ist komplett auf Englisch.
- [ ] **Kaputtes Bild einfügen.** Medien-Knopf lange drücken → Datei-Browser → Downloads → `test.jpg` wählen.
      Erwartet: Meldung auf Englisch, z. B. „Error: Image could not be read“. Kein deutsches Wort („Fehler“, „Bild“).
      Falls sich `test.jpg` nicht auswählen lässt: Punkt überspringen und notieren.
- [ ] **Untertitel ohne Internet.** Flugmodus an, ein Video aufnehmen, in der Review „Captions“ starten (nur wenn das
      Sprachmodell noch nicht geladen ist).
      Erwartet: Eine englische Fehlermeldung. Kein „Spracherkennung“. (Technische Systemtexte von Android dürfen
      englisch bleiben.) Danach Flugmodus wieder aus.

## B. Fehlermeldung auslösen – Persisch (FA)

- [ ] **Sprache auf Persisch.** Einstellungen → „App-Sprache“ → فارسی.
      Erwartet: Die App ist auf Persisch und von rechts nach links.
- [ ] **Kaputtes Bild einfügen.** Wie oben: Medien-Knopf lange drücken → Downloads → `test.jpg`.
      Erwartet: Meldung auf Persisch, beginnt mit „خطا:“. Kein deutsches Wort.
- [ ] **Untertitel ohne Internet.** Wie oben, mit Flugmodus.
      Erwartet: Persische Meldung, kein „Spracherkennung“. Danach Flugmodus aus.

## C. Absturzbericht – Beschriftungen

- [ ] **Bericht öffnen (Persisch).** Einstellungen → „راهنما“ (Hilfe) → „نمایش آخرین گزارش خرابی“
      (Letzten Absturzbericht anzeigen).
      Erwartet: Entweder „گزارش خرابی وجود ندارد“ (kein Bericht vorhanden – dann ist dieser Punkt erledigt) oder ein
      Bericht mit den Beschriftungen „زمان“ (Zeit) und „دستگاه“ (Gerät) statt „Zeit“ und „Gerät“.
- [ ] **Bericht öffnen (Englisch).** Sprache auf English, gleicher Weg: Settings → Help → „Show last crash report“.
      Erwartet: „No crash report available“ oder ein Bericht mit „Time“, „Thread“, „Version“, „Device“.
      Der technische Teil darunter (Stack Trace) bleibt englisch – das ist so gewollt.
- [ ] **Alter Bericht von 1.1.4** (nur falls einer da ist): Er wird jetzt ebenfalls mit den Beschriftungen der
      App-Sprache angezeigt.

## D. Speichern-Fenster (Export)

- [ ] **Englisch.** Ein kurzes Video aufnehmen → Speichern.
      Erwartet: Fenster „Save video“ mit Auflösung und Dateigröße auf Englisch; nach dem Speichern „Video saved“ mit
      „File size: …“.
- [ ] **Persisch.** Das Gleiche auf Persisch.
      Erwartet: Fenster „ذخیرهٔ ویدیو“, danach „ویدیو ذخیره شد“. Alles von rechts nach links, Zahlen gut lesbar.
- [ ] **Video ansehen.** In der Galerie öffnen.
      Erwartet: Das Video spielt mit Ton.

## E. Prozentzeichen und arabische Mehrzahl (Aufgabe 0eecba17)

- [ ] **Mikrofon-Lautstärke.** Sprache Türkisch, Spanisch, Französisch oder Arabisch einstellen, das Fenster
      „Mikrofon-Lautstärke“ öffnen.
      Erwartet: Der Hinweis zeigt ein einzelnes Prozentzeichen („100 %“ bzw. „%100“), nie „%%“.
- [ ] **Arabisch gegenlesen.** Sprache Arabisch, 2, 3 und 11 Segmente aufnehmen.
      Erwartet: „مقطعان“, „3 مقاطع“, „11 مقطعًا“. Bitte die Wörter auf Richtigkeit prüfen.

## Zum Schluss

- [ ] Sprache wieder auf „Systemsprache“ (oder Deutsch) stellen, `test.jpg` löschen.
- [ ] Rückmeldung an uns: „1.1.5 ok“ oder die Nummer des Punkts mit Screenshot.

Wenn alles passt: 1.1.5 in den geschlossenen Test „Closed Beta“ geben, mit den Versionshinweisen aus
`docs/RELEASE_NOTES_1_1_5.md`.
