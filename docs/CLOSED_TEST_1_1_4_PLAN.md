# BabaCut – Plan für das Tester-Update „Fehlermeldungen in 7 Sprachen“ (nur Plan, kein Code)

> Interne Datei (Projektakte, nicht auf babacut.io veröffentlicht – siehe `docs/_config.yml`).
> Vorschlag vom 2026-11-10, Aufgabe d444acae, überarbeitet nach dem Review von PR #10. Sprache: Deutsch, für den Owner.
> "Erst reden, dann bauen": In diesem PR wird **nichts am Code geändert**.
> Dateiname bleibt `CLOSED_TEST_1_1_4_PLAN.md` (Verweise in der Projektakte); inhaltlich geht es jetzt um die
> **nächste** Version nach `v1.1.4`, siehe unten.
> **[BESTÄTIGEN: …]** = Aussage, die nur du kennst. Stimmt sie, Klammer entfernen; sonst Satz streichen.

## Stand 2026-11-11: A ist umgesetzt (Aufgabe 6f0c1893)

- Alle Texte aus der Tabelle unten (plus "Keine Segmente" im Export) kommen jetzt aus den 7 Sprachdateien.
  Versionscode 84, Versionsname 1.1.5. Kein Tag gesetzt – das machst du nach dem Merge (`v1.1.5`).
- "Neuerungen"-Text für die Play Console (B): **ersetzt durch `docs/RELEASE_NOTES_1_1_5.md`** (Aufgabe 9c582082, 2026-11-12). Alter Kurzentwurf:
  - de-DE: Fehlermeldungen jetzt in allen Sprachen. Kleine Verbesserungen.
  - en-US: Error messages are now shown in all languages. Small improvements.
  - tr-TR: Hata mesajları artık tüm dillerde. Küçük iyileştirmeler.
  - es-ES: Los mensajes de error ahora aparecen en todos los idiomas. Pequeñas mejoras.
  - fr-FR: Les messages d’erreur sont désormais dans toutes les langues. Petites améliorations.
  - fa-IR: پیام‌های خطا اکنون به همهٔ زبان‌ها نمایش داده می‌شوند. بهبودهای کوچک.
  - ar: رسائل الخطأ متاحة الآن بجميع اللغات. تحسينات صغيرة.

## Deine Entscheidung steht schon fest

Hub-Entscheidung 2a5ce05a, deine Antwort: *"1.1.4 klein: Fehlermeldungen in allen 7 Sprachen + Neuerungen-Text,
danach Antrag"*. Das heißt: **A + B umsetzen, danach den Produktionsantrag stellen.** D (Absturzbericht) bleibt
optional und ist **nicht** freigegeben. Diese Frage wird nicht noch einmal gestellt.

## Was das Repository sicher zeigt (Stand `main` = `40521ea`, 2026-11-10)

- Gebaute/getaggte Versionen: 1.1.3 (Versionscode 81, Tag `v1.1.3`) und **1.1.4 (82, Tag `v1.1.4`, 10.11.)**.
  Dein `v1.1.4` (Commit `f7999a1`) enthält andere Verbesserungen (Untertitel-Halluzinationen bei Stille gefiltert,
  Plural im Status, Berechtigungs-Hilfe, Overlay-Hinweis, tatsächliche Exportgröße), **aber nicht** die Übersetzung
  der Fehlermeldungen. Die deutschen Wörter unten sind auf `main` noch vorhanden.
- Darum kommt A + B in die **nächste** Version: Versionscode **83**, Versionsname Vorschlag **1.1.5**.
- Was das Repository **nicht** zeigt: welche Version wann im geschlossenen Test „Closed Beta“ angekommen ist. Ein
  Versionscode im Code beweist keine Auslieferung an Tester.
  [BESTÄTIGEN: Version(en), die tatsächlich im geschlossenen Test ausgeliefert wurden, und Datum – Play Console →
  Test → Geschlossener Test → Closed Beta → Releases.]
- Satz für den Produktionsantrag „Während des Tests wurden Änderungen ausgeliefert“ nur verwenden, **wenn** die
  Console zeigt, dass 1.1.4 und/oder 1.1.5 im geschlossenen Test (nicht nur im internen Test) veröffentlicht wurde.

## Umfang A – deutsche Texte in Fehlermeldungen (alle 7 Sprachen)

Gesucht wurde nach App-eigenen Texten, die in einer Meldung, einem Toast oder dem Export-Fehlerfenster landen können.
Zeilen Stand `main` `40521ea`:

| Datei:Zeile | Deutscher Text | Wann er erscheint |
|---|---|---|
| `MainActivity.kt` 707, 1307 | "Bild" | Bild kann nicht als Overlay/Kachel eingefügt werden |
| `MainActivity.kt` 1335 | "Umrechnung" | Umwandlung eines Kachelvideos schlägt fehl |
| `MainActivity.kt` 1561 | "Umwandlung" | Umwandlung eines Overlay-Videos schlägt fehl |
| `MainActivity.kt` 1344, 1568 | "Video" | Video kann nicht eingefügt werden |
| `MainActivity.kt` 1324, 1548 | "Video konnte nicht gelesen werden" | Datei nicht lesbar (Text landet im Toast) |
| `MainActivity.kt` 1870 | "Bild konnte nicht gelesen werden" | Bilddatei nicht lesbar |
| `MainActivity.kt` 1368, 1630 | "Kachelvideo", "Overlay-Video" | Abspielfehler-Toast (`player_error`) |
| `MainActivity.kt` 2240 | "Spracherkennung" | Untertitel-Erkennung schlägt fehl |
| `MainActivity.kt` 3065, 3221 | "Entwurf" | Entwurf kann nicht gespeichert/geladen werden |
| `WhisperEngine.kt` 92 | "Modell konnte nicht geladen werden" | Untertitel-Modell defekt (Toast über Zeile 2240) |
| `ModelManager.kt` 33 | "Download fehlgeschlagen: HTTP …" | Modell-Download scheitert (Toast über Zeile 2240) |
| `Exporter.kt` 220 | "Speichern fehlgeschlagen" | Export: Speichern in die Galerie scheitert (Fehlerfenster) |
| `Exporter.kt` 301 | "MediaStore-Eintrag fehlgeschlagen" | Export: Galerie-Eintrag scheitert (über Zeile 220) |
| `Exporter.kt` 228 | "Neukodierung fehlgeschlagen (Code …)" | Export: Neukodierung scheitert (Fehlerfenster) |
| `Exporter.kt` 136 | "Overlay-Ton: …" | Export: Overlay-Ton kann nicht gemischt werden |
| `OverlayAudioRenderer.kt` 239 | "Kein Ton im Overlay" | über Zeile 136 im Export-Fehlerfenster |

Etwa 15 neue Texte je Sprache, nur Meldungstexte; Aufnahme, Schnitt und Export bleiben unverändert.

**Bewusst nicht in A (zurückgestellt):**
- Technische Grafik-Fehler (`GlUtil.kt`, `EglCore.kt`: "Shader-Kompilierung fehlgeschlagen" usw.). Sie sind für
  Entwickler gedacht; ob sie je bis zu einer sichtbaren Meldung durchgereicht werden, ist nicht geprüft.
- Meldungen von Android/Media3 selbst (`e.message` von Systemfehlern): kommen vom System, nicht aus der App.
- Reine Log-Texte (`Log.e/Log.w`): sieht kein Nutzer.
- Absturzbericht "Zeit/Gerät" (`CrashLog.kt` 22, 25) = D, optional, nicht freigegeben.

**Was geprüft ist und was nicht:** Alle 7 `strings.xml` haben auf `main` dieselben **223 Schlüssel** (Zählung,
keine Lücke). Das beweist **nicht**, dass zur Laufzeit jeder sichtbare Text übersetzt ist – Texte, die direkt im
Kotlin-Code stehen, zählt diese Prüfung nicht. Die Tabelle oben ist eine Suche nach deutschen Fehlertexten im Code,
keine Garantie, dass es keine weiteren gibt.

## Umfang B – Version und "Neuerungen"-Text

- Versionscode 84 (83 ist seit PR #12 auf `main`), Versionsname 1.1.5 (Vorschlag) in `app/build.gradle.kts`.
- "Neuerungen"-Text für die Play Console in 7 Sprachen, kurz und sachlich, z. B. DE: "Fehlermeldungen jetzt in
  allen Sprachen. Kleine Verbesserungen." (keine Superlative).

## Nicht in diesem Update

- C: Untertitel am Segmentende (bis 0,3 s Überhang, gefunden durch die Unit-Tests; diese sind seit 2026-11-11 auf
  `main`) – braucht noch einen eigenen Handytest.
- Untertitel-Verschiebung bei sehr langen Videos (Ursache unklar), Rechts-nach-links-Layout, Aufteilen von
  `MainActivity.kt`.

## Ablauf

1. Entwickler setzt A + B auf einem eigenen Branch um (alle 7 Sprachen, Versionscode 84). Stand 2026-11-11: 83 ist
   bereits auf `main` vergeben (Unit-Tests, PR #12).
2. Du übernimmst den PR, dann Tag `v1.1.5` → Store-Build (`.aab`).
3. `.aab` zuerst in den **internen Test** (dein Handy), Liste unten prüfen, dann dasselbe Bundle in den
   **geschlossenen Test "Closed Beta"** mit dem "Neuerungen"-Text.
4. Danach Produktionsantrag (Entwurf in PR #9, `docs/PLAY_PRODUCTION_APPLICATION.md`); Versionen nur nennen, wenn
   die Console sie im geschlossenen Test zeigt.

## Bitte prüfen (auf deinem Handy, nach dem Build)

- [ ] App öffnet sich, in den Einstellungen steht unten **Version 1.1.5**.
- [ ] Ein Video aufnehmen, in der Review schneiden, exportieren – alles wie vorher, Dateigröße wird angezeigt.
- [ ] Untertitel erkennen lassen – funktioniert wie vorher.
- [ ] Sprache auf **Englisch** stellen, ein Bild und ein Video als Overlay einfügen – funktioniert wie vorher. Falls
      eine Fehlermeldung erscheint: Sie ist **ganz auf Englisch**, ohne deutsches Wort (bitte Screenshot schicken).
      Fehler absichtlich auszulösen ist nicht nötig; der Entwickler prüft die Texte zusätzlich im Code.
- [ ] Sprache zurück auf Deutsch – alles wieder auf Deutsch.
- [ ] Entwürfe öffnen sich noch, Pro-Status ist unverändert.
- [ ] Play Console: 1.1.5 im geschlossenen Test sichtbar, "Neuerungen"-Text in allen Sprachen.

## Offene Punkte nur für dich

- [BESTÄTIGEN: Versionsname 1.1.5 ist recht (oder Wunschname nennen).]
- [BESTÄTIGEN: Ausgelieferte Versionen im geschlossenen Test laut Console – siehe oben.]
