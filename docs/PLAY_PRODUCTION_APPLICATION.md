# Produktionszugriff Google Play – Entwurf für den zweiten Antrag (BabaCut)

> Interne Datei (Projektakte, nicht auf babacut.io veröffentlicht – siehe `docs/_config.yml`).
> Entwurf vom 2026-11-10 (Aufgabe 036afa99, überarbeitet nach Review von PR #9); auf **1.1.5 (Versionscode 84)**
> aktualisiert am 2026-11-13 (Aufgabe f16621ce). Sprache: Deutsch, für den Owner.
> Status: **Entwurf. Nichts eingereicht, nichts hochgeladen, nichts gesendet.**
> **Z1 … Z7** = Werte, die du in der Play Console abliest (nur lesen, nichts ändern).
> **K1 … K7** = Aussagen, die nur du kennst. Du antwortest je Aussage mit „stimmt“ oder „streichen“.
> In den Antworttexten (Abschnitt 3) stehen Z- und K-Nummern als Platzhalter; die setze **ich** ein, nicht du.
> Grundsatz: Nichts erfinden. Eine Zahl in der Console belegt nur sich selbst, nicht, *was* Tester gemacht haben.

## Owner-Aktion (kurz)

**Wann:** erst, wenn 1.1.5 (Versionscode 84) im geschlossenen Test „Closed Beta“ veröffentlicht ist
(deine Entscheidung 2a5ce05a: erst das Update, dann der Antrag). Stand 2026-11-13: `v1.1.5` ist noch nicht
getaggt; die Schritte davor stehen in `docs/TEST_CHECKLIST_1_1_5.md`.

**Was du tust:** sieben Werte in der Play Console ablesen (Tabelle unten, nur lesen) und sieben Fragen mit
„stimmt“ oder „streichen“ beantworten. Screenshots reichen.

**Was du nicht tust:** nichts in der Console ändern, keinen Antrag absenden, nichts hochladen. Den Antrag stellst
du erst, wenn ich die Texte mit deinen Werten fertig gemacht habe.

**Einstieg:** https://play.google.com/console → App **BabaCut** (`de.codinix.babacut`) öffnen. Ein Direktlink auf
einzelne Seiten braucht die Nummer deines Entwicklerkontos, die nicht in der Akte steht; deshalb unten jeweils der
Klickpfad. Die Console zeigt die Menünamen je nach Spracheinstellung auf Deutsch oder Englisch; beide sind genannt.

### Z1–Z7: Werte ablesen

| # | Was du abliest | Klickpfad in der Play Console (DE / EN) | Was du uns zurückschickst |
|---|---|---|---|
| Z1 | Ob die Bedingung „12 Tester, 14 Tage“ als erfüllt angezeigt wird, und das Startdatum des Zeitraums | Dashboard → Kasten „Produktionszugriff beantragen“ / Dashboard → „Apply for production“ | Screenshot des Kastens, oder: „erfüllt ja/nein, seit TT.MM.“ |
| Z2 | Anzahl Tester, die sich angemeldet (opt-in) haben | Testen und veröffentlichen → Tests → Geschlossener Test → Closed Beta → Tab „Tester“ / Test and release → Testing → Closed testing → Closed Beta → „Testers“ | eine Zahl |
| Z3 | Installationen bzw. aktive Nutzer im Testzeitraum | Statistiken / Statistics (Zeitraum auf den Testzeitraum stellen) | eine Zahl (oder Screenshot) |
| Z4 | Anzahl privater Feedback-Einträge der Tester, dazu 2–3 kurze Zitate, falls vorhanden | Bewertungen und Rezensionen → Testerfeedback / Ratings and reviews → Testing feedback | Zahl + Zitate (Screenshot reicht) |
| Z5 | Abstürze und ANRs im Testzeitraum (Anzahl, betroffene Geräte) | Beobachten und verbessern → Android Vitals → Abstürze und ANRs / Monitor and improve → Android vitals → Crashes and ANRs | zwei Zahlen (oder Screenshot) |
| Z6 | Anzahl verschiedener Gerätemodelle bzw. Android-Versionen der Tester, falls angezeigt | Statistiken → nach Gerät aufschlüsseln / Statistics → by device; sonst Android Vitals | eine Zahl, oder „nicht angezeigt“ |
| Z7 | Welche Versionen im **geschlossenen** Test veröffentlicht wurden (Versionscode + Datum) und ob ein Text bei „Neuerungen“ dabei war | Testen und veröffentlichen → Tests → Geschlossener Test → Closed Beta → Tab „Releases“ / Closed testing → Closed Beta → „Releases“ (nicht „Interner Test“) | Liste „Versionscode – Datum – Neuerungen ja/nein“ |

Wird ein Wert nicht angezeigt: einfach „nicht angezeigt“ schreiben, ich passe den Text an.

### K1–K7: Fragen, die nur du beantworten kannst

Antwort je Frage: „stimmt“ oder „streichen“ (bei K3 und K5 mit Zahl).

| # | Aussage | Wofür |
|---|---|---|
| K1 | Die Tester haben vor allem Aufnahme in Segmenten, Schnitt, Untertitel und Export benutzt (weißt du aus Feedback oder von den Anbietern). | A2 |
| K2 | Rückmeldungen kamen zusätzlich per E-Mail an support@babacut.io. | A3 |
| K3 | Anzahl dieser E-Mails (Zahl oder „keine“). | A3 |
| K4 | Die Änderungen in 1.1.2 bis 1.1.5 kamen aus Rückmeldungen der Tester (stimmt) oder aus eigenen Tests während des geschlossenen Tests (streichen). | C1 |
| K5 | Aufnahme, Schnitt, Untertitel, Export und Pro-Abo wurden auf echten Geräten geprüft – Anzahl Geräte. | C2 |
| K6 | Die Console zeigt bei Store-Eintrag, App-Inhalten und Datensicherheit keine offenen Punkte. | C2 |
| K7 | Zwei Auswahlfelder: A1 „Wie einfach war es, Tester zu finden?“ (Vorschlag: „Schwierig“ bzw. mittlere Stufe) und B3 „Erwartete Installationen im ersten Jahr“ (Vorschlag: 1.000–10.000). | A1, B3 |

### Schon bekannt – wirst du nicht noch einmal gefragt

Diese Angaben stehen in der Akte (HANDOVER §5, DECISIONS, Hub-Wissen) und werden direkt verwendet:

- App und Konto: BabaCut, `de.codinix.babacut`, kostenlos, Kategorie Video Players & Editors, Kontakt
  support@babacut.io, Datenschutz https://babacut.io/privacy.
- Erster Antrag am 25.10.2026 abgelehnt („requires more testing“); neuer Antrag ab ca. 9.11.2026 möglich.
- Geschlossener Test heißt „Closed Beta“; Tester von einem Fiverr-Anbieter (~19 Adressen) und einem weiteren
  Anbieter (CSV); Einladungslink https://play.google.com/apps/testing/de.codinix.babacut.
- Erklärungen in der Console laut Akte ausgefüllt: Zielgruppe 18+, Datensicherheit „keine Daten erhoben“, keine
  Werbung, keine Finanzfunktionen, Anmeldung/Zahlungen mit Prüfer-Hinweis. (K6 fragt nur, ob die Console heute
  noch etwas Offenes anzeigt.)
- Store-Eintrag in 7 Sprachen (EN Standard + DE/TR/ES/FR/FA/AR), Abo `babacut_pro` (monatlich 2,99 €, jährlich
  19,99 € mit 7 Tagen Probezeit).
- Version für den Antrag: 1.1.5, Versionscode 84 (Hub-Wissen #124, nicht mehr zu bestätigen).
- „Neuerungen“-Text für 1.1.5 in 7 Sprachen: `docs/RELEASE_NOTES_1_1_5.md` (FA/AR liest du vor dem Hochladen
  gegen; das ist ein Schritt aus der Checkliste, nicht aus diesem Antrag).
- Zeichengrenze der Antwortfelder: ca. 300 Zeichen (HANDOVER §5); die Console zeigt den Zähler.
- Bekannte offene Stelle: Das Impressum auf babacut.io ist noch ein Platzhalter (KNOWN_ISSUES). Das betrifft
  nicht den Antrag selbst, aber die App in Produktion.

**Noch offen bleiben also genau:** Z1–Z7 und K1–K7, dazu deine Wahl, ob der Antrag auf Deutsch (Standard) oder
Englisch gestellt wird.

## 1. Ausgangslage

**Aus der Projektakte (HANDOVER §5):** erster Antrag am 25.10.2026 abgelehnt; danach erneut 14 Tage geschlossener
Test mit mindestens 12 Testern → neuer Antrag ab 9.11.2026 möglich.

**Deine Entscheidung (Hub-Entscheidung 2a5ce05a):** erst ein kleines Update mit Fehlermeldungen in allen 7 Sprachen
und „Neuerungen“-Text, danach der Antrag. Dieses Update ist **1.1.5 (Versionscode 84)**: im Code fertig
(`main`, 2026-11-12), noch nicht getaggt, noch nicht im geschlossenen Test.

**Was der Code zeigt (git, Stand 2026-11-13)** – das sind gebaute Versionen, **nicht** belegte Auslieferungen:

| Version | Versionscode | Tag / Datum | Inhalt laut Commit |
|---|---|---|---|
| 1.1.1 | 79 | `v1.1.1-pro-b`, 08.10. | BabaCut Pro, Pro-Funktionen mit Krone, 1080p-Grenze und Wasserzeichen ohne Pro |
| 1.1.2 | 80 | `v1.1.2`, 22.10. | Overlay-/Kachelvideos (HEVC/AV1, HDR, über 1080p, über 30 fps) werden beim Einfügen umgewandelt; Wiedergabefehler werden angezeigt |
| 1.1.3 | 81 | `v1.1.3`, 26.10. | Untertitel werden nach Umordnen/Löschen von Segmenten automatisch neu erkannt |
| 1.1.4 | 82 | `v1.1.4`, 10.11. | Untertitel-Halluzinationen bei Stille gefiltert, Plural im Status, Hilfe bei verweigerter Kamera-/Mikrofon-Berechtigung, Hinweis zu Live-Overlays, tatsächliche Dateigröße nach dem Export |
| – | 83 | kein Tag | nur ein Build von `main` (CI, Name noch 1.1.4), kein Store-Release |
| 1.1.5 | 84 | noch kein Tag (Stand 13.11.) | siehe nächster Abschnitt |

**Unbekannt (nur in der Console sichtbar, Z7):** welche dieser Versionen nur im **internen Test** waren und welche
tatsächlich im **geschlossenen Test „Closed Beta“** veröffentlicht wurden, und wann. Im Antrag werden nur Versionen
genannt, die laut Z7 im geschlossenen Test waren.

### Was sich mit 1.1.5 für Tester ändert

Belegt durch git `v1.1.4..main` (nur App-Code):

- **Fehlermeldungen in allen 7 Sprachen** (PR #15): beim Einfügen von Fotos und Videos, bei Overlays und Kacheln,
  bei der Untertitel-Erkennung, beim Laden des Untertitel-Modells, bei Entwürfen und beim Export. Vorher standen
  dort in jeder Sprache deutsche Wörter.
- **Absturzbericht in allen 7 Sprachen** (PR #18): Die Beschriftungen („Zeit“, „Gerät“ usw.) auf dem Startbildschirm
  und in den Einstellungen waren vorher in jeder Sprache deutsch.
- **Mikrofon-Hinweis und arabische Mehrzahl** (PR #24): Im Mikrofon-Hinweis stand auf Türkisch, Spanisch,
  Französisch und Arabisch „%%“ statt „%“; die Segmentanzahl hat auf Arabisch jetzt alle Mehrzahlformen.
- **„Neuerungen“-Text in 7 Sprachen** (PR #21, `docs/RELEASE_NOTES_1_1_5.md`): wird beim Release in der Console
  eingefügt; FA/AR liest du vorher gegen.
- Keine Änderung an Aufnahme, Schnitt oder Export. Unit-Tests und CI (PR #12, #23) ändern nichts Sichtbares.

## 2. Was wir bewusst nicht schreiben

- Keine erfundenen Zahlen oder Zitate (Google kann die Console-Daten vergleichen; falsche Angaben gefährden das Konto).
- Keine Aussagen über Nutzung, Funktionen, E-Mails oder Bereitschaft, die nicht von dir bestätigt sind (K1–K6).
- Keine Version als „ausgeliefert“ nennen, die nur gebaut oder nur im internen Test war.
- Keine Superlative („beste App“, „einzigartig“).
- Nicht behaupten, Tester hätten etwas gemeldet, wenn es aus eigenen Tests kam.

## 3. Antworttexte (Entwurf)

Die Texte sind so kurz, dass sie mit eingesetzten Werten in ca. 300 Zeichen passen sollten. Z- und K-Nummern in
spitzen Klammern ‹…› setze ich ein, sobald du geantwortet hast. Google akzeptiert Deutsch; Englisch auf Wunsch.

### Teil A – Über den geschlossenen Test

**A1. Wie einfach war es, Tester zu finden?** (Auswahlfeld, K7)
Ehrlich antworten. Vorschlag: „Schwierig“ bzw. die mittlere Stufe – wir haben Tester über Dienstleister gewonnen.

**A2. Beschreibe das Engagement der Tester während des Tests.**
> ‹Z2› Tester haben sich angemeldet, ‹Z3› haben die App installiert. Sie nutzten die App auf ‹Z6› Gerätemodellen.
> ‹K1: Getestet wurden vor allem Aufnahme in Segmenten, Schnitt, Untertitel und Export.›
> Während des Tests haben wir ‹Z7: Versionen im geschlossenen Test, z. B. 1.1.4 und 1.1.5› veröffentlicht.

Hinweis: Z2/Z3/Z6 belegen nur Anmeldungen, Installationen und Geräte, **nicht**, welche Funktionen die Tester
benutzt haben. Der K1-Satz bleibt nur, wenn du „stimmt“ sagst. Der letzte Satz entfällt, wenn Z7 keine Version
innerhalb des Testzeitraums zeigt.

**A3. Zusammenfassung des Feedbacks und wie es gesammelt wurde.**
> Feedback kam über das private Testerfeedback in Google Play (‹Z4› Einträge) und Android Vitals (‹Z5› Abstürze).
> ‹K2/K3: Zusätzlich per E-Mail an support@babacut.io (Anzahl) Nachrichten.›
> Wichtigste Punkte: ‹2–3 echte Stichworte/Zitate aus Z4›.

Wenn Z4 = 0 und keine E-Mails kamen: den Satz „Wichtigste Punkte“ streichen und ehrlich schreiben
„Die Tester gaben kaum schriftliches Feedback; wir haben Android Vitals und eigene Tests ausgewertet.“

### Teil B – Über die App

**B1. Zielgruppe.**
> Erwachsene Content-Creator, die kurze Videos für TikTok, Instagram und YouTube Shorts direkt am Handy aufnehmen.
> Die App ist in 7 Sprachen verfügbar (u. a. Deutsch, Türkisch, Farsi, Arabisch); seit 1.1.5 erscheinen auch
> Fehlermeldungen in der eingestellten Sprache. Sie richtet sich an Nutzer, die ohne Computer schneiden wollen.

**B2. Welchen Nutzen bietet die App?**
> BabaCut schneidet schon während der Aufnahme: Segmente aufnehmen, umordnen, löschen, Texte, Overlays, Filter und
> Untertitel hinzufügen. Untertitel werden auf dem Gerät erzeugt (Whisper); kein Konto, kein Upload, keine Werbung.
> Gratis bis 1080p, Pro-Abo für 4K und weitere Funktionen.

(B1/B2 beschreiben die App selbst; das ist durch Code und Store-Eintrag belegt.)

**B3. Erwartete Installationen im ersten Jahr.** (Auswahlfeld, K7)
Realistisch wählen; Vorschlag: die Stufe „1.000–10.000“. Diese Stufe steht im ersten Entwurf, ist aber nicht als
deine Entscheidung in der Akte belegt; deshalb fragt K7 noch einmal.

### Teil C – Bereitschaft für die Produktion

**C1. Welche Änderungen hast du aufgrund des Tests vorgenommen?**
Nur die Zeilen behalten, deren Version laut **Z7 im geschlossenen Test** war:
> - (1.1.2) Videos in HEVC/AV1, HDR, 60 fps oder über 1080p werden als Overlay automatisch umgewandelt;
>   Wiedergabefehler werden angezeigt.
> - (1.1.3) Untertitel werden nach Umordnen oder Löschen von Segmenten neu erkannt.
> - (1.1.4) Keine erfundenen Untertitel mehr bei Stille; Hilfe, wenn Kamera/Mikrofon verweigert wurde; tatsächliche
>   Dateigröße nach dem Export.
> - (1.1.5) Fehlermeldungen und Absturzbericht in allen 7 Sprachen; „Neuerungen“-Text in 7 Sprachen.
> ‹K4: Auslöser waren Rückmeldungen der Tester / eigene Tests während des geschlossenen Tests.›

Hinweis: Die Änderungen sind im Code belegt. Ob **Tester** oder deine eigenen Tests sie ausgelöst haben, sagt K4.
Bei eigenen Tests: „In eigenen Tests während des geschlossenen Tests fielen … auf“. Für die Zeichengrenze kürze ich
die Liste auf die 2–3 wichtigsten Punkte.

**C2. Woran hast du erkannt, dass die App bereit für die Produktion ist?**
> Der geschlossene Test lief ‹Z1: Datum bis Datum› mit ‹Z2› Testern. Android Vitals zeigt ‹Z5› Abstürze und ANRs
> in dieser Zeit. ‹K5: Aufnahme, Schnitt, Untertitel, Export und Pro-Abo wurden auf (Anzahl) echten Geräten
> geprüft.› ‹K6: Store-Eintrag, Datenschutzerklärung und Datensicherheit sind in der Console vollständig.›

Hinweis: Den K5-Satz nur stehen lassen, wenn alle genannten Funktionen geprüft wurden; sonst zähle ich die
geprüften auf. Den K6-Satz nur, wenn die Console keine offenen Punkte zeigt.

## 4. Nächste Schritte

1. 1.1.5 taggen, intern auf deinem Handy prüfen (`docs/TEST_CHECKLIST_1_1_5.md`), dann im geschlossenen Test
   „Closed Beta“ mit dem Text aus `docs/RELEASE_NOTES_1_1_5.md` veröffentlichen.
2. Danach: Z1–Z7 ablesen und K1–K7 beantworten (Abschnitt „Owner-Aktion“), Screenshots reichen.
3. Ich setze alles ein und kürze auf die Zeichengrenze; danach stellst du den Antrag selbst.
