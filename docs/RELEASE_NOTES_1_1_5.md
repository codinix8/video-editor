# BabaCut 1.1.5 – Play Console "What's new" (draft)

> Internal project-record file, not published on babacut.io (excluded in `docs/_config.yml`).
> Draft of 2026-11-12, task 9c582082 (part B of the owner scope "A + B + D" for 1.1.5, versionCode 84).
> Status: **draft, not uploaded.** The owner pastes it into the Play Console himself.
> **FA and AR: owner proofreads** before use.

## What the text is based on (git, `v1.1.4..main`, app code only)

- A – error messages in all 7 languages (PR #15, task 6f0c1893): adding photos/videos, overlays and tiles,
  caption recognition, caption model download, drafts, export.
- D – crash report labels in all 7 languages (PR #18, task 84b19ca3/a0f44077): shown on the start sheet and in
  Settings.
- Unit tests and CI (PR #12): no user-visible change, therefore not mentioned.

No other user-visible change since `v1.1.4`, so the text claims nothing else (no "small improvements").
No superlatives (UWG). UI terms match the app's own `strings.xml` per language (e.g. "Ajustes", "Paramètres",
"Katman", "لایه", "الطبقة"). Informal address like the app (du/sen/tú/tu); FA uses the polite "شما" with a
closing thank-you, as is customary in Persian store texts.

## Character counts (Play limit: 500 per language)

| Language | Play code | Characters | Proofreading |
|---|---|---|---|
| English | en-US | 303 | – |
| German | de-DE | 381 | – |
| Turkish | tr-TR | 327 | – |
| Spanish | es-ES | 369 | – |
| French | fr-FR | 375 | – |
| Persian | fa-IR | 351 | **owner proofreads** |
| Arabic | ar | 274 | **owner proofreads** |

Counted as Unicode characters including line breaks (`wc -m`, without the tags).

## Text to paste (Play Console → release → "Release notes", all languages at once)

```
<en-US>
BabaCut 1.1.5
• Error messages now appear in the language you set in the app: when adding photos and videos, overlays, captions, the caption model download, drafts and export.
• The crash report (start screen and Settings) is now shown in your language too.
Recording, cutting and export work as before.
</en-US>
<de-DE>
BabaCut 1.1.5
• Fehlermeldungen erscheinen jetzt in der Sprache, die du in der App eingestellt hast: beim Einfügen von Fotos und Videos, bei Overlays, Untertiteln, dem Laden des Untertitel-Modells, Entwürfen und beim Export.
• Auch der Absturzbericht (Startbildschirm und Einstellungen) wird jetzt in deiner Sprache angezeigt.
Aufnahme, Schnitt und Export funktionieren wie bisher.
</de-DE>
<tr-TR>
BabaCut 1.1.5
• Hata mesajları artık uygulamada seçtiğin dilde görünüyor: fotoğraf ve video eklerken, katmanlarda, altyazılarda, altyazı modelinin indirilmesinde, taslaklarda ve dışa aktarmada.
• Çökme raporu (başlangıç ekranı ve Ayarlar) da artık senin dilinde gösteriliyor.
Kayıt, kesme ve dışa aktarma eskisi gibi çalışıyor.
</tr-TR>
<es-ES>
BabaCut 1.1.5
• Los mensajes de error aparecen ahora en el idioma que elegiste en la app: al añadir fotos y vídeos, en superposiciones, subtítulos, la descarga del modelo de subtítulos, borradores y la exportación.
• El informe de fallos (pantalla de inicio y Ajustes) también se muestra ahora en tu idioma.
La grabación, el corte y la exportación funcionan como antes.
</es-ES>
<fr-FR>
BabaCut 1.1.5
• Les messages d'erreur s'affichent désormais dans la langue choisie dans l'appli : ajout de photos et de vidéos, superpositions, sous-titres, téléchargement du modèle de sous-titres, brouillons et export.
• Le rapport de plantage (écran d'accueil et Paramètres) s'affiche aussi dans ta langue.
L'enregistrement, le montage et l'export fonctionnent comme avant.
</fr-FR>
<fa-IR>
BabaCut نسخهٔ 1.1.5
• از این پس پیام‌های خطا به زبانی نمایش داده می‌شوند که در برنامه انتخاب کرده‌اید: هنگام افزودن عکس و ویدیو، در لایه‌ها، زیرنویس‌ها، دریافت مدل زیرنویس، پیش‌نویس‌ها و خروجی گرفتن.
• گزارش خطای برنامه (صفحهٔ آغاز و تنظیمات) نیز اکنون به زبان شما نمایش داده می‌شود.
ضبط، برش و خروجی مانند گذشته کار می‌کنند. از همراهی شما سپاسگزاریم.
</fa-IR>
<ar>
BabaCut الإصدار 1.1.5
• تظهر رسائل الخطأ الآن باللغة التي اخترتها في التطبيق: عند إضافة الصور والفيديوهات، وفي الطبقات والترجمة وتنزيل نموذج الترجمة والمسودات والتصدير.
• يظهر تقرير الأعطال (شاشة البدء والإعدادات) الآن بلغتك أيضًا.
يعمل التسجيل والقص والتصدير كما في السابق.
</ar>
```

## Open for the owner (German)

- [ ] FA bitte gegenlesen (Ton, Wortwahl „لایه“ für Overlay, „خروجی گرفتن“ für Export).
- [ ] AR bitte gegenlesen.
- [ ] Prüfen, ob die Play-Console-Codes zu deinen Store-Sprachen passen (z. B. `fa-IR` oder `fa`, `ar`).
- [ ] Erst nach Tag `v1.1.5` und internem Test in den geschlossenen Test "Closed Beta" einfügen.
