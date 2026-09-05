# Ressourcen-Review: Strings & Drawables – AtlayaSwitch

Ursprünglich rein lesende Analyse, Stand 2026-09-05. Für die Analyse selbst
wurden ausschließlich Lesewerkzeuge (Dateien lesen, Textsuche) verwendet, keine
Build-Tools oder sonstigen Befehle; dabei wurde außer dieser Datei keine andere
Datei erstellt, geändert, umbenannt, verschoben oder gelöscht. Der in Abschnitt 3
dokumentierte Locale-Gap (`settings_help_copyright_prefix`) wurde danach auf
ausdrücklichen Wunsch nachträglich geschlossen – siehe Abschnitt 6 für den
aktuellen Änderungsstand dieser vier Ressourcendateien.

## 1. Vollständige Ressourcen-Inventur

### 1.1 String-Keys je Sprachdatei

| Datei | Anzahl `name="..."`-Einträge (inkl. 2 String-Arrays nur in default) |
|---|---|
| `values/strings.xml` (Default/Deutsch) | 47 (45 `<string>` + `settings_language_names` + `settings_language_tags`) |
| `values-en/strings.xml` | 44 |
| `values-es/strings.xml` | 44 |
| `values-fr/strings.xml` | 44 |
| `values-it/strings.xml` | 44 |

Vollständige Liste der 45 `<string>`-Keys (identisch benannt in allen Sprachen,
bis auf die in Abschnitt 3 gelistete Ausnahme) + 2 `<string-array>`-Keys (nur
Default):

```
app_name, settings_title, settings_language_button_cd, settings_help_button_cd,
settings_help_title, settings_help_copyright_prefix*, settings_help_license_link,
settings_help_body, settings_section_profile, settings_target_profile_warning,
settings_target_profile_warning_button, settings_target_profile_removed_toast,
settings_target_profile_remove_failed, settings_section_switch_mode,
settings_switch_mode_switch_only, settings_switch_mode_switch_only_hint,
settings_switch_mode_end_session, settings_switch_mode_end_session_hint,
settings_shizuku_not_installed_owner, settings_shizuku_not_installed_other,
settings_shizuku_install_button, settings_shizuku_switch_profile_button,
settings_shizuku_not_running, settings_shizuku_open_button,
settings_shizuku_wrong_profile, settings_back_button, settings_section_quick_tile,
settings_quick_tile_hint, settings_quick_tile_toggle_label, qs_tile_label,
settings_section_updates, settings_installed_version,
settings_update_status_checking, settings_update_status_current,
settings_update_status_available, settings_update_status_error,
settings_update_check_button, settings_update_download_button,
settings_update_auto_label, settings_update_background_label,
update_notification_channel_name, update_notification_title,
update_notification_text, settings_section_support, settings_support_button

String-Arrays (nur values/strings.xml): settings_language_names, settings_language_tags
```
`*` = nur in Default vorhanden, siehe Abschnitt 3.

### 1.2 Drawable-/Mipmap-Ressourcen

| Ressource | Typ | Varianten |
|---|---|---|
| `ic_qs_tile` | Vector-Drawable (XML) | 1 Datei: `drawable/ic_qs_tile.xml` |
| `ic_launcher` | Mipmap (Raster + Adaptive-Icon-XML) | PNG in mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi + `mipmap-anydpi-v26/ic_launcher.xml` |
| `ic_launcher_round` | Mipmap (Raster + Adaptive-Icon-XML) | PNG in mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi + `mipmap-anydpi-v26/ic_launcher_round.xml` |
| `ic_launcher_foreground` | Mipmap (Raster) | PNG in mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi |
| `ic_launcher_background` | Mipmap (Raster) | PNG in mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi |

Keine weiteren `drawable/`- oder `mipmap/`-Ordner oder -Dateien im Projekt
vorhanden (per Glob über `app/src/main/res/drawable*/**` und
`app/src/main/res/mipmap*/**` vollständig erfasst). Kein `fastlane/`- oder
sonstiger Store-Metadaten-Ordner im Repo gefunden (Glob-Suche ergebnislos) –
`tools/AtlayaSwitch.png` (README-Vorschaubild) liegt außerhalb von `res/` und
ist keine Android-Ressource im Sinne dieser Prüfung.

## 2. Referenzsuche je Eintrag

### 2.1 Strings – jeder Key hat mindestens einen Treffer

Suchmuster: `R\.string\.\w+` / `R\.array\.\w+` in `app/src/main/java/**/*.kt`,
`@string/\w+` / `@array/\w+` in `app/src/main/res/**/*.xml`,
`android:label=` in `AndroidManifest.xml`. Zusätzlich nach
`resources.getIdentifier(...)` (bzw. vergleichbaren string-basierten Lookups)
gesucht – **kein einziger Treffer** im gesamten `app/src/main`-Baum. Es gibt
also keinen Mechanismus im Code, der Ressourcennamen dynamisch zusammensetzt;
jede Referenz ist ein statischer `R.x.y`- bzw. `@x/y`-Verweis. Dadurch ist die
Referenzsuche für diesen Ressourcentyp abschließend, ohne Restunsicherheit.

| Key | Fundstelle(n) |
|---|---|
| `app_name` | `AndroidManifest.xml:13` (`android:label`) |
| `settings_title` | `AndroidManifest.xml:38` (Activity-Label) + `activity_settings.xml:24` |
| `settings_language_button_cd` | `activity_settings.xml:37` |
| `settings_help_button_cd` | `activity_settings.xml:50` |
| `settings_help_title` | `SettingsActivity.kt:328` |
| `settings_help_copyright_prefix` | `SettingsActivity.kt:304` |
| `settings_help_license_link` | `SettingsActivity.kt:305` |
| `settings_help_body` | `SettingsActivity.kt:315` |
| `settings_language_names` (Array) | `SettingsActivity.kt:286` |
| `settings_language_tags` (Array) | `SettingsActivity.kt:287` |
| `settings_section_profile` | `activity_settings.xml:86` |
| `settings_target_profile_warning` | `SettingsActivity.kt:399` |
| `settings_target_profile_warning_button` | `activity_settings.xml:118` |
| `settings_target_profile_removed_toast` | `SettingsActivity.kt:406` |
| `settings_target_profile_remove_failed` | `SettingsActivity.kt:409,413` |
| `settings_section_switch_mode` | `activity_settings.xml:130` + `SettingsActivity.kt:317` (Hilfetext) |
| `settings_switch_mode_switch_only` | `activity_settings.xml:146` + `SettingsActivity.kt:319` |
| `settings_switch_mode_switch_only_hint` | `SettingsActivity.kt:321` |
| `settings_switch_mode_end_session` | `activity_settings.xml:152` + `SettingsActivity.kt:323` |
| `settings_switch_mode_end_session_hint` | `SettingsActivity.kt:325` |
| `settings_shizuku_not_installed_owner` | `SettingsActivity.kt:231` |
| `settings_shizuku_not_installed_other` | `SettingsActivity.kt:238` |
| `settings_shizuku_install_button` | `SettingsActivity.kt:233` |
| `settings_shizuku_switch_profile_button` | `activity_settings.xml:80` |
| `settings_shizuku_not_running` | `SettingsActivity.kt:243` |
| `settings_shizuku_open_button` | `activity_settings.xml:74` + `SettingsActivity.kt:245` |
| `settings_shizuku_wrong_profile` | `SettingsActivity.kt:253` |
| `settings_back_button` | `activity_settings.xml:314` |
| `settings_section_quick_tile` | `activity_settings.xml:165` |
| `settings_quick_tile_hint` | `activity_settings.xml:173` |
| `settings_quick_tile_toggle_label` | `activity_settings.xml:188` |
| `qs_tile_label` | `AndroidManifest.xml:55` (Service-Label der QS-Kachel) |
| `settings_section_updates` | `activity_settings.xml:207` |
| `settings_installed_version` | `SettingsActivity.kt:145` |
| `settings_update_status_checking` | `SettingsActivity.kt:338` |
| `settings_update_status_current` | `SettingsActivity.kt:350` |
| `settings_update_status_available` | `SettingsActivity.kt:346` |
| `settings_update_status_error` | `SettingsActivity.kt:354` |
| `settings_update_check_button` | `activity_settings.xml:231` |
| `settings_update_download_button` | `activity_settings.xml:238` |
| `settings_update_auto_label` | `activity_settings.xml:252` |
| `settings_update_background_label` | `activity_settings.xml:272` |
| `update_notification_channel_name` | `UpdateCheckWorker.kt:56` |
| `update_notification_title` | `UpdateCheckWorker.kt:80` |
| `update_notification_text` | `UpdateCheckWorker.kt:81` |
| `settings_section_support` | `activity_settings.xml:291` |
| `settings_support_button` | `activity_settings.xml:300` |

**Ergebnis: keine toten String-Keys.** Jeder der 45 Strings + 2 Arrays hat
mindestens eine belegte, statische Verwendung.

### 2.2 Drawables/Mipmaps – jede Ressource hat mindestens einen Treffer

Suchmuster: `R\.(drawable|mipmap)\.\w+` in Kotlin, `@(drawable|mipmap)/\w+` in
allen XML-Dateien unter `app/src/main/res`, `android:icon`/`android:roundIcon`
in `AndroidManifest.xml`. Kein `getIdentifier(...)`-Treffer (siehe 2.1).

| Ressource | Fundstelle(n) |
|---|---|
| `ic_qs_tile` | `AndroidManifest.xml:54` (`android:icon` der `QuickSwitchTileService`) |
| `ic_launcher` | `AndroidManifest.xml:11` (`android:icon`) + `UpdateCheckWorker.kt:79` (`R.mipmap.ic_launcher`, Notification-Icon) |
| `ic_launcher_round` | `AndroidManifest.xml:12` (`android:roundIcon`) |
| `ic_launcher_foreground` | referenziert in `mipmap-anydpi-v26/ic_launcher.xml:4` und `ic_launcher_round.xml:4` (`<foreground android:drawable="@mipmap/ic_launcher_foreground"/>`) |
| `ic_launcher_background` | referenziert in `mipmap-anydpi-v26/ic_launcher.xml:3` und `ic_launcher_round.xml:3` (`<background android:drawable="@mipmap/ic_launcher_background"/>`) |

Die Raster-PNGs je Dichte (`mdpi`/`hdpi`/`xhdpi`/`xxhdpi`/`xxxhdpi`) sind
qualifizierte Varianten derselben Ressourcen-IDs (`ic_launcher`,
`ic_launcher_round`, `ic_launcher_foreground`, `ic_launcher_background`) – ihre
Verwendung ist mit der jeweiligen ID-Referenz oben mit abgedeckt, da Android
zur Laufzeit automatisch die passende Dichte-Variante wählt. Die
`mipmap-anydpi-v26/*.xml`-Dateien selbst sind die API-26+-Variante von
`@mipmap/ic_launcher` bzw. `@mipmap/ic_launcher_round` und damit über dieselbe
Manifest-Referenz abgedeckt.

**Ergebnis: keine toten Drawables/Mipmaps.**

## 3. Locale-Konsistenz (informativ, kein Dead-Code-Kandidat)

| Key | Vorhanden in | Fehlt in | Auswirkung |
|---|---|---|---|
| `settings_help_copyright_prefix` | `values` (Deutsch) — **inzwischen nachgetragen in `values-en/es/fr/it`** | — (behoben) | War zum Zeitpunkt dieser Analyse nur im Default vorhanden; Android fiel bei nicht-deutscher App-Sprache auf den deutschen String zurück. Nachträglich in allen 4 übrigen Sprachdateien identisch ergänzt (reine Copyright-Zeile mit Eigennamen/Domain, kein übersetzbarer Text). |
| `settings_language_names` (Array) | nur `values` | `values-en/es/fr/it` (erwartungsgemäß) | **Kein Lücke, sondern Absicht:** Das Sprachauswahlmenü (`SettingsActivity.showLanguageMenu()`) soll die Sprachnamen immer in ihrer jeweils eigenen Sprache zeigen ("Deutsch, English, Français…"), unabhängig von der aktuell gewählten App-Sprache – dafür muss der Array nur einmal (im Default) existieren. |
| `settings_language_tags` (Array) | nur `values` | `values-en/es/fr/it` (erwartungsgemäß) | Gleiche Begründung wie oben. |

Alle übrigen 44 Strings sind in allen 5 Sprachdateien identisch benannt
vorhanden – keine weiteren Lücken.

## 4. Offene Fragen

**Keine.** Die Referenzsuche war für alle Einträge abschließend: Es gibt im
gesamten Code keinen dynamischen bzw. string-basierten Ressourcen-Lookup
(`resources.getIdentifier` o. ä. – kein einziger Treffer, siehe 2.1), keine
externe Config-/Feed-Antwort, die Ressourcennamen referenziert (der einzige
externe Feed, `UpdateChecker.FEED_URL`, liefert reine Versions-/URL-Daten,
keine Ressourcennamen – siehe `ARCHITEKTUR_REVIEW.md`, Abschnitt 2.4), und kein
Store-Metadaten-Ordner im Repo. Jede Ressource ließ sich daher mit einem
statischen Suchmuster eindeutig einem Verwendungsort zuordnen.

## 5. Vorschlag (nicht umgesetzt)

**Nichts zu entfernen.** Alle 47 String-Einträge (inkl. 2 Arrays) und alle 5
Drawable-/Mipmap-Ressourcen-IDs haben eine belegte Verwendung – es gibt in
diesem Bereich keinen Kandidaten für toten Code.

Der einzige Verbesserungsvorschlag dieser Analyse (Übersetzungs-Vollständigkeit,
kein Entfernungsvorschlag) – `settings_help_copyright_prefix` in `values-en`,
`values-es`, `values-fr` und `values-it` ergänzen – wurde auf Wunsch
nachträglich umgesetzt (siehe Abschnitt 3).

## 6. Abschlussbestätigung

Für den ursprünglichen, rein lesenden Analyse-Auftrag bestätigt: In diesem
Durchlauf wurde ausschließlich diese Datei (`RESSOURCEN_REVIEW.md`) neu
angelegt, ausschließlich mit lesenden Werkzeugen (Datei lesen,
Verzeichnis-/Textsuche) – keine Build-Tools oder sonstigen Befehle, keine
andere Datei geändert, erstellt, umbenannt, verschoben oder gelöscht.

**Nachtrag (separater, ausdrücklich beauftragter Folgeschritt, kein Teil der
Lese-Analyse selbst):** Auf Wunsch wurde der in Abschnitt 3 dokumentierte
Locale-Gap geschlossen. Dabei wurden genau vier Dateien geändert –
`app/src/main/res/values-en/strings.xml`, `values-es/strings.xml`,
`values-fr/strings.xml`, `values-it/strings.xml` – in jeder davon exakt eine
Zeile ergänzt (`settings_help_copyright_prefix`), keine weitere Änderung. Per
`git diff HEAD~1 HEAD` auf diese vier Dateien verifiziert: jede zeigt genau
eine Einfügung (`+`) und keine Entfernung (`-`), kein sonstiger Treffer.
Zusätzlich wurde diese Review-Datei selbst redaktionell aktualisiert, um den
geschlossenen Gap zu vermerken.
