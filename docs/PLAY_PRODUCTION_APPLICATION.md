# Produktionszugriff Google Play – Entwurf für den zweiten Antrag (BabaCut)

> Interne Datei (Projektakte, nicht auf babacut.io veröffentlicht – siehe `docs/_config.yml`).
> Entwurf vom 2026-11-10, Aufgabe 036afa99, überarbeitet nach Review von PR #9. Sprache: Deutsch, für den Owner.
> **[Z1] … [Z7]** = Zahl aus der Play Console ablesen und einsetzen.
> **[BESTÄTIGEN: …]** = Aussage, die nur du kennst. Stimmt sie, Klammer entfernen; stimmt sie nicht, Satz streichen.
> Grundsatz: Nichts erfinden. Eine Zahl in der Console belegt nur sich selbst, nicht, *was* Tester gemacht haben.

## Ausgangslage

**Aus der Projektakte (HANDOVER §5):**
- Erster Antrag am **25.10.2026 abgelehnt** ("requires more testing").
- Danach erneut **14 Tage geschlossener Test mit mindestens 12 Testern** → neuer Antrag ab **9.11.2026** möglich.
- Geschlossener Test "Closed Beta", Tester-Liste ~19 Adressen (Fiverr-Anbieter) + weiterer Anbieter mit CSV.

**Deine Entscheidung (Hub-Entscheidung 2a5ce05a):** *"1.1.4 klein: Fehlermeldungen in allen 7 Sprachen +
Neuerungen-Text, danach Antrag."* Der Antrag wird also erst gestellt, wenn dieses Update im geschlossenen Test ist.
Plan dazu: `docs/CLOSED_TEST_1_1_4_PLAN.md` (eigener PR).

**Was der Code zeigt (git, Stand 2026-11-10)** – das sind gebaute Versionen, **nicht** belegte Auslieferungen:

| Version | Versionscode | Tag / Datum | Inhalt laut Commit |
|---|---|---|---|
| 1.1.1 | 79 | `v1.1.1-pro-b`, 08.10. | BabaCut Pro, Pro-Funktionen mit Krone, 1080p-Grenze und Wasserzeichen ohne Pro |
| 1.1.2 | 80 | `v1.1.2`, 22.10. | Overlay-/Kachelvideos (HEVC/AV1, HDR, über 1080p, über 30 fps) werden beim Einfügen umgewandelt; Wiedergabefehler werden angezeigt |
| 1.1.3 | 81 | `v1.1.3`, 26.10. | Untertitel werden nach Umordnen/Löschen von Segmenten automatisch neu erkannt |
| 1.1.4 | 82 | `v1.1.4`, 10.11. | Untertitel-Halluzinationen bei Stille gefiltert, Plural im Status, Hilfe bei verweigerter Kamera-/Mikrofon-Berechtigung, Hinweis zu Live-Overlays, tatsächliche Dateigröße nach dem Export (neue Texte in allen 7 Sprachen) |

**Unbekannt (nur in der Console sichtbar, Z7):** Welche dieser Versionen nur im **internen Test** waren und welche
tatsächlich an den **geschlossenen Test "Closed Beta"** ausgeliefert wurden, und wann. Im Antrag nur Versionen
nennen, die laut Z7 im geschlossenen Test waren.

**Hinweis zu 1.1.4:** Die Übersetzung der Fehlermeldungen aus deiner Entscheidung ist in `v1.1.4` **noch nicht
enthalten**. Auf `main` stehen weiter deutsche Wörter in Fehlermeldungen (z. B. "Umrechnung", "Spracherkennung",
"Entwurf" in `MainActivity.kt`, "Modell konnte nicht geladen werden" in `WhisperEngine.kt`). Ob es auch einen
Neuerungen-Text gibt, sieht man nur in der Console (Z7). Nach deiner Entscheidung gehört die Übersetzung in das
Update vor dem Antrag; das ist eine eigene Entwickler-Aufgabe (nächster Versionscode 83).

## 1. Zahlen, die du in der Play Console ablesen musst

Pfad jeweils: Play Console → BabaCut. Wenn eine Zahl nicht angezeigt wird: Feld leer lassen, ich passe den Text an.

| # | Zahl | Wo in der Console |
|---|---|---|
| Z1 | Startdatum des aktuellen 14-Tage-Zeitraums und ob "14 Tage mit ≥12 Testern" als erfüllt angezeigt wird | Dashboard → "Produktionszugriff beantragen" (Checkliste) |
| Z2 | Anzahl Tester, die sich angemeldet (opt-in) haben | Test → Geschlossene Tests → Closed Beta → Tester |
| Z3 | Anzahl Installationen / aktive Nutzer im Testzeitraum | Statistik (Statistics) oder Übersicht des Tracks |
| Z4 | Anzahl private Feedback-Einträge der Tester (und 2–3 kurze Zitate, falls vorhanden) | Bewertungen und Rezensionen → Testfeedback (Testing feedback) |
| Z5 | Abstürze / ANRs im Testzeitraum (Anzahl, betroffene Geräte) | Android Vitals → Abstürze und ANRs |
| Z6 | Anzahl verschiedener Gerätemodelle / Android-Versionen der Tester (falls angezeigt) | Statistik → Geräte bzw. Android Vitals |
| Z7 | Welche Versionen im **geschlossenen** Test ausgeliefert wurden (Versionscode + Datum), mit Neuerungen-Text ja/nein | Test → Geschlossene Tests → Closed Beta → Releases (nicht "Interner Test") |

## 2. Antworttexte (Entwurf)

Die Felder haben eine Zeichengrenze (laut Akte ca. 300 Zeichen; die Console zeigt den Zähler). Die Texte sind so
kurz, dass sie mit eingesetzten Zahlen passen sollten. Google akzeptiert Deutsch; für Englisch sag Bescheid.

### Teil A – Über den geschlossenen Test

**A1. Wie einfach war es, Tester zu finden?** (Auswahlfeld)
Ehrlich antworten. Vorschlag: "Schwierig" bzw. die mittlere Stufe – wir haben Tester über Dienstleister gewonnen.

**A2. Beschreibe das Engagement der Tester während des Tests.**
> [Z2] Tester haben sich angemeldet, [Z3] haben die App installiert. Sie nutzten die App auf [Z6] Gerätemodellen.
> [BESTÄTIGEN: Getestet wurden vor allem Aufnahme in Segmenten, Schnitt, Untertitel und Export.]
> Während des Tests lieferten wir [Z7: Versionen im geschlossenen Test, z. B. "1.1.4"] aus.

Hinweis: Z2/Z3/Z6 belegen nur Anmeldungen, Installationen und Geräte, **nicht**, welche Funktionen die Tester
benutzt haben. Den bestätigten Satz nur stehen lassen, wenn du es aus Feedback oder Rückmeldungen der Anbieter weißt.
Den letzten Satz streichen, wenn Z7 keine Version innerhalb des Testzeitraums zeigt.

**A3. Zusammenfassung des Feedbacks und wie es gesammelt wurde.**
> Feedback kam über das private Testfeedback in Google Play ([Z4] Einträge) und Android Vitals ([Z5] Abstürze).
> [BESTÄTIGEN: Zusätzlich per E-Mail an support@babacut.io ([Anzahl] Nachrichten).]
> Wichtigste Punkte: [2–3 echte Stichworte/Zitate aus Z4].

Wenn Z4 = 0 und keine E-Mails kamen: den Satz "Wichtigste Punkte" streichen und ehrlich schreiben
"Die Tester gaben kaum schriftliches Feedback; wir haben Android Vitals und eigene Tests ausgewertet."

### Teil B – Über die App

**B1. Zielgruppe.**
> Erwachsene Content-Creator, die kurze Videos für TikTok, Instagram und YouTube Shorts direkt am Handy aufnehmen.
> Die App ist in 7 Sprachen verfügbar (u. a. Deutsch, Türkisch, Farsi, Arabisch) und richtet sich an Nutzer, die
> ohne Computer schneiden wollen.

**B2. Welchen Nutzen bietet die App?**
> BabaCut schneidet schon während der Aufnahme: Segmente aufnehmen, umordnen, löschen, Texte, Overlays, Filter und
> Untertitel hinzufügen. Untertitel werden auf dem Gerät erzeugt (Whisper); kein Konto, kein Upload, keine Werbung.
> Gratis bis 1080p, Pro-Abo für 4K und weitere Funktionen.

(B1/B2 beschreiben die App selbst; das ist durch Code und Store-Eintrag belegt.)

**B3. Erwartete Installationen im ersten Jahr.** (Auswahlfeld)
Realistisch wählen; Vorschlag: die Stufe "1.000–10.000" (deine Entscheidung).

### Teil C – Bereitschaft für die Produktion

**C1. Welche Änderungen hast du aufgrund des Tests vorgenommen?**
Nur die Zeilen behalten, deren Version laut **Z7 im geschlossenen Test** war:
> - (1.1.2) Videos in HEVC/AV1, HDR, 60 fps oder über 1080p werden als Overlay automatisch umgewandelt;
>   Wiedergabefehler werden angezeigt.
> - (1.1.3) Untertitel werden nach Umordnen oder Löschen von Segmenten neu erkannt.
> - (1.1.4) Keine erfundenen Untertitel mehr bei Stille; Hilfe, wenn Kamera/Mikrofon verweigert wurde; tatsächliche
>   Dateigröße nach dem Export.
> - (Update aus deiner Entscheidung) Fehlermeldungen in allen 7 Sprachen.
> [BESTÄTIGEN: Auslöser waren Rückmeldungen der Tester / eigene Tests während des geschlossenen Tests.]

Hinweis: Die Änderungen sind im Code belegt. Ob **Tester** oder deine eigenen Tests sie ausgelöst haben, weißt nur
du. Bei eigenen Tests: "In eigenen Tests während des geschlossenen Tests fielen … auf". Für die Zeichengrenze die
Liste auf die 2–3 wichtigsten Punkte kürzen.

**C2. Woran hast du erkannt, dass die App bereit für die Produktion ist?**
> Der geschlossene Test lief [Z1: Datum bis Datum] mit [Z2] Testern. Android Vitals zeigt [Z5] Abstürze und ANRs
> in dieser Zeit. [BESTÄTIGEN: Aufnahme, Schnitt, Untertitel, Export und Pro-Abo wurden auf [Anzahl] echten Geräten
> geprüft.] [BESTÄTIGEN: Store-Eintrag, Datenschutzerklärung und Datensicherheit sind in der Console vollständig.]

Hinweis: Den ersten BESTÄTIGEN-Satz nur stehen lassen, wenn du (oder die Tester nachweislich) alle genannten
Funktionen geprüft haben; sonst die geprüften Funktionen aufzählen. Den zweiten nur, wenn die Console keine offenen
Punkte bei Store-Eintrag/Datensicherheit zeigt. Achtung: Das Impressum auf babacut.io ist laut Akte noch ein
Platzhalter (KNOWN_ISSUES).

## 3. Was wir bewusst nicht schreiben

- Keine erfundenen Zahlen oder Zitate (Google kann die Console-Daten vergleichen; falsche Angaben gefährden das Konto).
- Keine Aussagen über Nutzung, Funktionen, E-Mails oder Bereitschaft, die nicht von dir bestätigt sind.
- Keine Version als "ausgeliefert" nennen, die nur gebaut oder nur im internen Test war.
- Keine Superlative ("beste App", "einzigartig").
- Nicht behaupten, Tester hätten etwas gemeldet, wenn es aus eigenen Tests kam.

## 4. Nächster Schritt

1. Update mit übersetzten Fehlermeldungen + Neuerungen-Text in den geschlossenen Test (deine Entscheidung; eigene
   Aufgabe).
2. Du schickst mir Z1–Z7 (Screenshots reichen) und sagst bei jedem **[BESTÄTIGEN]** "stimmt" oder "streichen".
3. Ich setze alles ein und kürze auf die Zeichengrenze; danach stellst du den Antrag.
