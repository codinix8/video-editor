# Produktionszugriff Google Play – Entwurf für den zweiten Antrag (BabaCut)

> Interne Datei (Projektakte, nicht auf babacut.io veröffentlicht – siehe `docs/_config.yml`).
> Entwurf vom 2026-11-10, Aufgabe 036afa99. Sprache: Deutsch, für den Owner.
> Alles in **[ECKIGEN KLAMMERN]** bitte aus der Play Console ablesen und einsetzen.
> Nichts erfinden: Wo keine echten Zahlen oder Tester-Rückmeldungen vorliegen, lieber den Satz kürzen.

## Ausgangslage (aus der Projektakte)

- Erster Antrag am **25.10.2026 abgelehnt** ("requires more testing").
- Danach erneut **14 Tage geschlossener Test mit mindestens 12 Testern** → neuer Antrag ab **9.11.2026** möglich.
- Geschlossener Test "Closed Beta", Tester-Liste ~19 Adressen (Fiverr-Anbieter) + weiterer Anbieter mit CSV.
- Versionen im Test (laut Code-Historie):
  - **1.1.1** (Versionscode 79, 08.10.): BabaCut Pro, Pro-Funktionen mit Krone sichtbar, 1080p-Grenze und
    Wasserzeichen in der Gratis-Version.
  - **1.1.2** (80, 22.10.): Videos in HEVC/AV1, HDR, über 1080p oder über 30 fps werden beim Einfügen als
    Overlay/Kachel automatisch umgewandelt; Wiedergabefehler werden jetzt angezeigt statt still zu scheitern.
  - **1.1.3** (81, 26.10.): Untertitel werden nach dem Umordnen oder Löschen von Segmenten automatisch neu erkannt.
- **Achtung:** Seit 1.1.3 (26.10.) gab es laut Code kein weiteres Update. Empfohlen war ein Update zwischen Testtag 7
  und 10. Falls du in der Console doch ein neueres Bundle hochgeladen hast, bitte unten eintragen. Sonst: Antrag
  trotzdem stellen ist möglich, aber ein kleines Update 1.1.4 vor dem Antrag würde die Antwort "Änderungen" stärken
  (Entscheidung bei dir, siehe Ende).

## 1. Zahlen, die du in der Play Console ablesen musst

Pfad jeweils: Play Console → BabaCut.

| # | Zahl | Wo in der Console |
|---|---|---|
| Z1 | Startdatum des aktuellen 14-Tage-Zeitraums und ob "14 Tage mit ≥12 Testern" als erfüllt angezeigt wird | Dashboard → "Produktionszugriff beantragen" (Checkliste) |
| Z2 | Anzahl Tester, die sich angemeldet (opt-in) haben | Test → Geschlossene Tests → Closed Beta → Tester |
| Z3 | Anzahl Installationen / aktive Nutzer im Testzeitraum | Statistik (Statistics) oder Übersicht des Tracks |
| Z4 | Anzahl private Feedback-Einträge der Tester (und 2–3 kurze Zitate, falls vorhanden) | Bewertungen und Rezensionen → Testfeedback (Testing feedback) |
| Z5 | Abstürze / ANRs im Testzeitraum (Anzahl, betroffene Geräte) | Android Vitals → Abstürze und ANRs |
| Z6 | Anzahl verschiedener Gerätemodelle / Android-Versionen der Tester (falls angezeigt) | Statistik → Geräte bzw. Android Vitals |
| Z7 | Welche Versionen im geschlossenen Test ausgeliefert wurden (Versionscodes + Datum) | Test → Geschlossene Tests → Closed Beta → Releases |

Wenn eine Zahl nicht angezeigt wird: Feld leer lassen, ich passe den Text an.

## 2. Antworttexte (Entwurf)

Die Felder im Antrag haben eine Zeichengrenze (laut Akte ca. 300 Zeichen; die Console zeigt den Zähler an).
Die Texte unten sind so kurz gehalten, dass sie mit eingesetzten Zahlen passen sollten. Google akzeptiert Deutsch;
wenn du lieber Englisch einreichst, sag Bescheid.

### Teil A – Über den geschlossenen Test

**A1. Wie einfach war es, Tester zu finden?** (Auswahlfeld)
Ehrlich antworten. Vorschlag: "Schwierig" bzw. die mittlere Stufe – wir haben Tester über Dienstleister gewonnen.

**A2. Beschreibe das Engagement der Tester während des Tests.**
> [Z2] Tester haben sich angemeldet, [Z3] haben die App installiert und über [Anzahl Tage] Tage genutzt. Getestet
> wurden Aufnahme in Segmenten, Untertitel auf dem Gerät, Overlays, Mosaik und Export auf [Z6] Gerätemodellen.
> Während des Tests lieferten wir die Updates [Z7, z. B. 1.1.2 und 1.1.3] aus.

**A3. Zusammenfassung des Feedbacks und wie es gesammelt wurde.**
> Feedback kam über das private Testfeedback in Google Play ([Z4] Einträge), über support@babacut.io und Android
> Vitals ([Z5] Abstürze). Wichtigste Punkte: [2–3 echte Stichworte/Zitate aus Z4, sonst diesen Satz streichen].
> Eigene Tests auf Android 16 kamen hinzu.

### Teil B – Über die App

**B1. Zielgruppe.**
> Erwachsene Content-Creator, die kurze Videos für TikTok, Instagram und YouTube Shorts direkt am Handy aufnehmen.
> Die App ist in 7 Sprachen verfügbar (u. a. Deutsch, Türkisch, Farsi, Arabisch) und richtet sich an Nutzer, die
> ohne Computer schneiden wollen.

**B2. Welchen Nutzen bietet die App?**
> BabaCut schneidet schon während der Aufnahme: Segmente aufnehmen, umordnen, löschen, Texte, Overlays, Filter und
> Untertitel hinzufügen. Untertitel werden auf dem Gerät erzeugt (Whisper); kein Konto, kein Upload, keine Werbung.
> Gratis bis 1080p, Pro-Abo für 4K und weitere Funktionen.

**B3. Erwartete Installationen im ersten Jahr.** (Auswahlfeld)
Realistisch wählen; Vorschlag: die Stufe "1.000–10.000" (deine Entscheidung).

### Teil C – Bereitschaft für die Produktion

**C1. Welche Änderungen hast du aufgrund des Tests vorgenommen?**
> Im Test fielen Videos auf, die als Overlay nicht abspielten (HEVC/AV1, HDR, 60 fps, über 1080p). Version 1.1.2
> wandelt sie beim Einfügen automatisch um und zeigt Wiedergabefehler an. 1.1.3 erkennt Untertitel nach dem Umordnen
> oder Löschen von Segmenten automatisch neu. [ggf. 1.1.4: …]

Hinweis: Diese beiden Änderungen sind belegt (Code-Historie). Ob sie durch **Tester** oder durch deine eigenen Tests
ausgelöst wurden, weiß nur du. Wenn sie aus deinen eigenen Tests kamen, den ersten Satz ändern in
"In eigenen Tests während des geschlossenen Tests fielen … auf".

**C2. Woran hast du erkannt, dass die App bereit für die Produktion ist?**
> Der geschlossene Test lief [Z1: Datum bis Datum] mit [Z2] Testern. Android Vitals zeigt [Z5] Abstürze in dieser
> Zeit. Alle Hauptfunktionen (Aufnahme, Schnitt, Untertitel, Export, Pro-Abo) wurden auf echten Geräten geprüft;
> Store-Eintrag, Datenschutzerklärung und Datensicherheit sind vollständig.

## 3. Was wir bewusst nicht schreiben

- Keine erfundenen Zahlen oder Zitate (Google kann die Console-Daten vergleichen; falsche Angaben gefährden das Konto).
- Keine Superlative ("beste App", "einzigartig").
- Nicht behaupten, Tester hätten etwas gemeldet, wenn es aus eigenen Tests kam.

## 4. Offene Entscheidung für dich

- **Jetzt beantragen** mit 1.1.3 – möglich, wenn die Console den 14-Tage-Zeitraum als erfüllt anzeigt (Z1).
- **Oder erst ein kleines Update 1.1.4** in den geschlossenen Test laden und danach beantragen. Das liefert eine
  sichtbare "Änderung während des Tests" für C1. Plan für 1.1.4 ist eine eigene Aufgabe (ROADMAP "Now").

Schick mir einfach die Zahlen Z1–Z7 (Screenshot reicht), dann setze ich sie ein und kürze auf die Zeichengrenze.
