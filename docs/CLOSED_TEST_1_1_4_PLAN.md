# BabaCut 1.1.4 – Vorschlag für das Update an die Tester (nur Plan, kein Code)

> Interne Datei (Projektakte, nicht auf babacut.io veröffentlicht – siehe `docs/_config.yml`).
> Vorschlag vom 2026-11-10, Aufgabe d444acae. Sprache: Deutsch, für den Owner.
> "Erst reden, dann bauen": Es wurde **nichts am Code geändert**. Du suchst den Umfang aus, danach wird gebaut.

## Kurz gesagt

- Seit **1.1.3 (Versionscode 81, 26.10.)** haben die Tester laut Code kein Update bekommen.
- Das empfohlene Fenster "Update zwischen Testtag 7 und 10" (ca. 1.–5. November) ist **schon vorbei**. Ein Update
  jetzt nützt trotzdem: Im neuen Produktionsantrag (Entwurf: `docs/PLAY_PRODUCTION_APPLICATION.md`) kannst du dann
  ehrlich schreiben, dass während des Tests Änderungen ausgeliefert wurden. Und falls Google erneut ablehnt, läuft der
  nächste Testzeitraum schon mit der neuen Version.
- Vorschlag: ein **kleines, risikoarmes 1.1.4** – nur Dinge, die nichts an Aufnahme, Schnitt oder Export ändern.

## Was im Code gefunden wurde (Stand `main`, 2026-11-10)

**Deutsche Wörter in Fehlermeldungen, auch wenn die App auf Englisch, Farsi usw. eingestellt ist.**
Die Meldungen selbst sind übersetzt, aber das eingesetzte Wort nicht. Ein Tester mit englischer App sieht z. B.
"Error: Umrechnung" oder "Kachelvideo can't be played (…)". Stellen in `MainActivity.kt`:

| Zeile | Wort | Wann es erscheint |
|---|---|---|
| 683, 1282 | "Bild" | Bild kann nicht eingefügt werden |
| 1310 | "Umrechnung" | Umwandlung eines Kachelvideos schlägt fehl |
| 1536 | "Umwandlung" | Umwandlung eines Overlay-Videos schlägt fehl |
| 1319, 1543 | "Video" | Video kann nicht eingefügt werden |
| 2215 | "Spracherkennung" | Untertitel-Erkennung schlägt fehl |
| 3032, 3188 | "Entwurf" | Entwurf kann nicht gespeichert/geladen werden |
| 1343, 1605 | "Kachelvideo", "Overlay-Video" | Video lässt sich nicht abspielen |
| 1299, 1523, 1845 | "Video/Bild konnte nicht gelesen werden" | Datei kann nicht gelesen werden (Text landet in der Meldung) |
| `WhisperEngine.kt` 57 | "Modell konnte nicht geladen werden" | Untertitel-Modell defekt |

Alle anderen Texte der App sind in allen 7 Sprachen vollständig (215 Texte je Sprache, geprüft).
Ungenutzte deutsche Namenslisten (`CaptionStyle.TEMPLATE_NAMES`, `Mosaic.LAYOUT_NAMES`, `ColorFilters.NAMES`)
erscheinen nirgends in der App und bleiben unberührt.

## Umfang zur Auswahl

| # | Änderung | Risiko | Empfehlung |
|---|---|---|---|
| **A** | Fehlermeldungen oben in allen 7 Sprachen (ca. 10 neue Texte je Sprache, nur Meldungstexte) | sehr gering – ändert nur, *welches Wort* in einer Fehlermeldung steht | **ja** |
| **B** | Versionsnummer 1.1.4 und "Neuerungen"-Text für die Play Console in 7 Sprachen (kurz, sachlich, z. B. "Fehlermeldungen in allen Sprachen, kleine Verbesserungen") | keins (nur Text) | **ja** |
| **C** | Untertitel am Segmentende: Ein Untertitel, der kurz vor dem Ende eines Segments beginnt, kann bis zu 0,3 s in das nächste Segment hineinragen (gefunden durch die neuen automatischen Tests, Aufgabe b9d022fb) | gering bis mittel – betrifft die Untertitel-Zeiten nach Umordnen/Löschen | **nein, erst nach 1.1.4** – braucht die Tests aus dem noch offenen PR und einen eigenen Handytest |
| **D** | Absturzbericht: Feldnamen "Zeit/Gerät" auf Englisch (liest nur der Support) | keins | optional, kann mit A mit |

**Bewusst nicht in 1.1.4** (zu groß oder ungeklärt): Untertitel-Verschiebung bei sehr langen Videos (Ursache unklar),
Rechts-nach-links-Layout für Farsi/Arabisch (`supportsRtl=false`, wäre ein Umbau), Aufteilen von `MainActivity.kt`.

**Empfehlung: A + B (+ D).** Aufwand für den Entwickler: unter einer Stunde plus ein Build (~4,5 min).

## Ablauf, wenn du zustimmst

1. Entwickler setzt A, B (und ggf. D) auf einem eigenen Branch um, mit allen 7 Sprachen; Versionscode +1.
   Hinweis: Der offene PR mit den automatischen Tests (b9d022fb) setzt den Versionscode schon auf **82**.
   Wird er zuerst übernommen, bekommt 1.1.4 die **83**, sonst die 82.
2. Du übernimmst den PR, dann Tag `v1.1.4` → Store-Build (`.aab`).
3. Du lädst die `.aab` zuerst in den **internen Test** (dein Handy), prüfst mit der Liste unten, dann dasselbe
   Bundle in den **geschlossenen Test "Closed Beta"** mit dem "Neuerungen"-Text.
4. Danach den Produktionsantrag stellen; in Antwort "Änderungen" 1.1.4 nennen.

## Bitte prüfen (auf deinem Handy, nach dem Build)

- [ ] App öffnet sich, in den Einstellungen steht unten **Version 1.1.4**.
- [ ] Ein Video aufnehmen, in der Review schneiden, exportieren – alles wie vorher.
- [ ] Untertitel erkennen lassen – funktioniert wie vorher.
- [ ] Sprache in den Einstellungen auf **Englisch** stellen und ein Bild sowie ein Video als Overlay einfügen –
      funktioniert wie vorher. Falls dabei irgendeine Fehlermeldung erscheint: Sie ist **ganz auf Englisch**,
      ohne deutsches Wort (bitte Screenshot schicken). Eine Fehlermeldung absichtlich auszulösen ist nicht nötig;
      der Entwickler prüft die Texte zusätzlich im Code.
- [ ] Danach Sprache zurück auf Deutsch – alles wieder auf Deutsch.
- [ ] Entwürfe öffnen sich noch, Pro-Status ist unverändert.
- [ ] In der Play Console: Version 1.1.4 im geschlossenen Test sichtbar, "Neuerungen"-Text in allen Sprachen.

## Deine Entscheidung (bitte eine Zeile antworten)

1. **"1.1.4 klein: A + B (+ D)"** – kleines Update jetzt, dann Produktionsantrag. *(Empfehlung)*
2. **"Kein Update, Antrag mit 1.1.3"** – Antrag sofort; die Fehlermeldungen kommen später.

Ohne Antwort passiert nichts am Code.
