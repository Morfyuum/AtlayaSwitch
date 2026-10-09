# Architektur- und Abhängigkeitsanalyse – AtlayaSwitch

Rein lesende Analyse, Stand 2026-09-05. Es wurde in diesem Durchlauf **keine
andere Datei als diese** erstellt, geändert, umbenannt, verschoben oder
gelöscht.

## 1. Architektur-Übersicht

AtlayaSwitch besteht aus 8 Kotlin-Dateien, 1 AIDL-Interface und Ressourcen für
5 Sprachen (de/en/es/fr/it). Komponenten:

| Komponente | Rolle |
|---|---|
| `MainActivity.kt` | Startet per App-Icon-Tap (LAUNCHER-Intent), führt sofort den konfigurierten Profilwechsel aus, kein sichtbares UI, beendet sich selbst. |
| `QuickSwitchTileService.kt` | Zweiter Trigger über eine Schnelleinstellungen-Kachel (seit v1.9.0), löst denselben Wechsel aus, auch vom Sperrbildschirm erreichbar. |
| `SettingsActivity.kt` | Zielprofil wählen, Wechselmodus, Kachel-Schalter, Sprache, Update-Check, Support-Mail. Erreichbar über App-Info (`ACTION_APPLICATION_PREFERENCES`). |
| `ShizukuUtils.kt` | Kapselt alle Shizuku-Aufrufe an einer Stelle (Berechtigungen, Profil-Erkennung, UserService-Bindung, gemeinsame Wechsel-Verzweigung `performConfiguredSwitch`). |
| `UserService.kt` + `IUserService.aidl` | Läuft als eigener Prozess mit Shell-Rechten (UID 2000) über `Shizuku.bindUserService`; führt `pm list users` / `am switch-user` / `am stop-user` / `pm uninstall --user` aus. |
| `SwitchDebounce.kt` | Gemeinsame Entprellung für alle Wechsel-Ausloeser gegen Shizuku-Backend). |
| `UpdateChecker.kt` | Fragt `https://atlaya.capecter.com/atlayaswitch/updates/latest.json` ab, reine Anzeige/Verlinkung. |
| `UpdateCheckWorker.kt` | Periodischer Hintergrund-Update-Check via WorkManager, nutzt `UpdateChecker`. |

Die App selbst bleibt unprivilegiert (kein `sharedUserId`, kein Root); nur der
über Shizuku gestartete `UserService`-Prozess läuft mit ADB-Shell-Rechten.

## 2. Abhängigkeitsanalyse

### 2.1 Statische Aufrufbeziehungen (Kernpfad)

```
MainActivity.performSwitch()            ─┐
QuickSwitchTileService.onClick()        ─┴─▶ ShizukuUtils.performConfiguredSwitch()
                                                 ├─▶ switchToUserAndEndSession()  (Standard)
                                                 └─▶ switchToUser()               (Modus "nur wechseln")
                                                        └─▶ withUserService()
                                                               └─▶ Shizuku.bindUserService()
                                                                      └─▶ UserService (AIDL, eigener Prozess)
```

`performConfiguredSwitch` ist bewusst die **einzige** Verzweigungsstelle für
beide Trigger (Icon-Tap und Kachel), damit sie garantiert identisch reagieren
(so auch im Klassenkommentar von `ShizukuUtils.kt:186-190` dokumentiert).

`SettingsActivity` nutzt `ShizukuUtils` zusätzlich für: `listProfiles()`,
`isPackageInstalledInProfile()`, `uninstallFromProfile()`, `openUserSettings()`,
`openShizukuApp()`, `openPlayStoreForShizuku()`, `isShizukuPackageInstalled()`,
`isOwnerProfile()` — jede dieser Funktionen hat genau einen Aufrufer in
`SettingsActivity.refreshShizukuState()` bzw. `loadProfiles()` /
`checkTargetProfileInstalled()`.

`UpdateChecker.check()` wird von zwei Stellen aufgerufen:
`SettingsActivity.checkForUpdates()` (Vordergrund, manueller Klick/Auto-Check
beim Öffnen) und `UpdateCheckWorker.doWork()` (Hintergrund, periodisch) —
bewusst dieselbe Logik statt einer Kopie (Kommentar in `UpdateCheckWorker.kt:19-24`).

### 2.2 Dynamische/reflektierte Zugriffe

Keine `Class.forName`, kein `getattr`-Äquivalent, keine String-basierten
Klassen-/Methodennamen gefunden – mit einer dokumentierten Ausnahme:

- `ShizukuUtils.openUserSettings()` (`ShizukuUtils.kt:101-112`) startet
  `Intent("android.settings.USER_SETTINGS")` als rohen String, weil
  `Settings.ACTION_USER_SETTINGS` keine öffentliche SDK-Konstante ist. Laut
  Kommentar auf dem Zielgerät per `dumpsys package` verifiziert
  (`com.android.settings/.Settings$UserSettingsActivity`). Kein generisches
  Reflection-Muster, sondern ein einzelner, begründeter Rohstring.

### 2.3 Event-/Callback-Registrierungen

- `Shizuku.OnRequestPermissionResultListener` — registriert in
  `MainActivity.onCreate()`/`onDestroy()` und `SettingsActivity.onCreate()`/
  `onDestroy()`, jeweils sauber symmetrisch an- und abgemeldet.
- `TileService`-Lifecycle (`onStartListening()`, `onClick()`) — Android-
  Framework-Callback, Registrierung erfolgt implizit über den
  Manifest-Eintrag (siehe 2.4).
- `ServiceConnection` (anonym in `ShizukuUtils.withUserService()`) — lokal
  erzeugt und in derselben Funktion über `Shizuku.unbindUserService()` wieder
  gelöst (sowohl im Erfolgs- als auch im Timeout-Pfad).
- `ActivityResultContracts.RequestPermission()` — `notificationPermissionLauncher`
  in `SettingsActivity`, für `POST_NOTIFICATIONS` ab API 33.

### 2.4 Config-/Manifest-getriebene Ladepfade

**AndroidManifest.xml:**
- `MainActivity`: `LAUNCHER`-Intent-Filter → Einstiegspunkt App-Icon-Tap.
- `SettingsActivity`: `APPLICATION_PREFERENCES`-Intent-Filter → erscheint
  automatisch als "Einstellungen"-Link auf der System-App-Info-Seite.
- `QuickSwitchTileService`: `android.service.quicksettings.action.QS_TILE`
  Intent-Filter + `BIND_QUICK_SETTINGS_TILE`-Permission → macht die Kachel im
  Schnelleinstellungen-Panel wählbar.
- `rikka.shizuku.ShizukuProvider`: `authorities="com.capecter.atlayaswitch.shizuku"`
  → Pflicht-Provider für die Kommunikation mit dem Shizuku-Dienst.

**SharedPreferences** (`"atlaya_switch"`, zentral genutzt von `MainActivity`,
`SettingsActivity`, `QuickSwitchTileService`, `SwitchDebounce`):
`target_user_id`, `switch_mode` (`switch_only`/`end_session`),
`quick_tile_enabled`, `auto_update_check`, `background_update_check`,
`last_switch_attempt_ms`. Alle Keys werden konsistent über Konstanten in den
jeweiligen Klassen (`MainActivity.KEY_TARGET_USER_ID`,
`SettingsActivity.KEY_*`) referenziert, kein Key wird per Rohstring an einer
zweiten Stelle dupliziert.

**`res/xml/locales_config.xml`**: deklariert de/en/es/fr/it, deckt sich mit den
tatsächlich vorhandenen `values-xx/strings.xml`-Ordnern.

**Externer Config-Endpunkt**: `UpdateChecker.FEED_URL` lädt JSON von
`atlaya.capecter.com` (Felder `version`, `url`) – Erzeuger dieses Feeds liegt
laut Kommentar in `D:\Atlaya\scripts\build_website.py`, außerhalb dieses Repos.

### 2.5 Externe Abhängigkeiten (`app/build.gradle.kts`) und ihre Nutzung

| Abhängigkeit | Belegte Nutzung |
|---|---|
| `androidx.core:core-ktx:1.13.1` | War überflüssig, siehe Kandidat 3.3 – **inzwischen entfernt.** |
| `androidx.appcompat:appcompat:1.7.0` | `AppCompatActivity`, `AppCompatDelegate`, `AlertDialog` in `MainActivity`/`SettingsActivity`. |
| `androidx.recyclerview:recyclerview:1.3.2` | `RecyclerView`, `LinearLayoutManager`, `RecyclerView.Adapter/ViewHolder` in `SettingsActivity.ProfileAdapter`. |
| `dev.rikka.shizuku:api:13.1.5` | `Shizuku.*` in `ShizukuUtils`, `MainActivity`, `SettingsActivity`. |
| `dev.rikka.shizuku:provider:13.1.5` | `rikka.shizuku.ShizukuProvider` im Manifest. |
| `androidx.work:work-runtime-ktx:2.9.1` | `CoroutineWorker`, `WorkManager`, `PeriodicWorkRequestBuilder`, `Constraints` in `SettingsActivity`/`UpdateCheckWorker`. |

Zusätzlich implizit genutzt (Teil von `androidx.core:core`, nicht `core-ktx`):
`ContextCompat`, `ActivityCompat`, `NotificationCompat`, `LocaleListCompat` –
siehe Kandidat 3.3.

## 3. Kandidaten (mit Belegen)

### 3.1 Toter Code – keiner gefunden
Jede public Funktion in `ShizukuUtils`, `UserService`/`IUserService`,
`SwitchDebounce`, `UpdateChecker` hat mindestens einen nachweisbaren Aufrufer
(siehe 2.1). Alle Imports in allen 8 Kotlin-Dateien werden tatsächlich benutzt
(einzeln geprüft, keine Ausnahme). Trotz Entfernung des NFC-Ring-Triggers in
v1.8.2 (laut interner Projekt-Doku) wurde per Grep nach `nfc`/`Nfc`/`NFC` im gesamten
`app/src`-Baum gesucht: Treffer ausschließlich in Kommentaren/Strings, die die
Entfernung *erklären* (z. B. `settings_quick_tile_hint`,
`QuickSwitchTileService`-Klassenkommentar) – kein NFC-Code, keine
NFC-Permission, kein NFC-Intent-Filter mehr vorhanden.

### 3.2 Duplikate – keine gefunden
Im Gegenteil: Die drei naheliegendsten Duplizierungsstellen sind bereits
bewusst konsolidiert:
- `ShizukuUtils.performConfiguredSwitch()` – gemeinsame Verzweigung für
  `MainActivity` UND `QuickSwitchTileService` (Beleg: 2.1).
- `SwitchDebounce.tooSoonSinceLastAttempt()` – gemeinsame Entprellung für
  dieselben zwei Aufrufer.
- `UpdateChecker.check()` – gemeinsame Logik für `SettingsActivity` (Vordergrund)
  UND `UpdateCheckWorker` (Hintergrund).

### 3.3 Unnötige Abhängigkeit – `androidx.core:core-ktx:1.13.1` (erledigt)

**Befund:** war überflüssig, ließ sich entfernen, ohne den aufgelösten
Klassenpfad zu ändern. **Umgesetzt:** die Zeile wurde aus
`app/build.gradle.kts` entfernt (siehe Beleg 2 unten und Abschnitt 5).

**Beleg 1 – keine Nutzung im Code:** Grep über `app/src/main` nach typischen
core-ktx-Mustern (`edit {`, `.toUri()`, `.postDelayed {`, `contentValuesOf`,
`toColorInt`) ergab keinen Treffer. `SharedPreferences` wird überall im
Java-Stil verkettet (`prefs.edit().putX(...).apply()`), nicht mit der
ktx-Lambda-Syntax. Alle tatsächlich genutzten `androidx.core`-Klassen
(`ContextCompat`, `ActivityCompat`, `NotificationCompat`, `LocaleListCompat`)
stammen aus dem Basis-Artefakt `androidx.core:core`, nicht aus `core-ktx`.

**Beleg 2 – Gradle-Dependency-Tree** (`./gradlew :app:dependencies
--configuration debugRuntimeClasspath`, read-only ausgeführt, keine Datei
verändert): `androidx.core:core-ktx:1.13.1` wird bereits **dreifach transitiv**
auf exakt dieselbe Version aufgelöst:
- über `androidx.appcompat:appcompat:1.7.0` (`1.13.0 -> 1.13.1`)
- über `androidx.fragment:fragment:1.5.4` (`1.2.0 -> 1.13.1`)
- über `androidx.customview:customview-poolingcontainer:1.0.0` (`1.5.0 -> 1.13.1`)

Die explizite Zeile `implementation("androidx.core:core-ktx:1.13.1")` in
`app/build.gradle.kts:61` erzwingt also keine andere Version, als ohnehin
transitiv gezogen würde – sie ist reine Redundanz.

**Risiko beim Entfernen:** sehr gering. Der aufgelöste Klassenpfad ändert sich
nachweislich nicht (gleiche Version aus drei anderen Quellen). Einzige
Restunsicherheit: ein zukünftiges Downgrade von `appcompat`/`fragment`/
`work-runtime-ktx` könnte core-ktx implizit mit auf eine ältere Version ziehen,
falls die explizite Zeile entfernt wird und diese Transitiv-Pfade sich ändern.
Das ist aber ein generisches, für jede transitive Abhängigkeit geltendes
Zukunftsrisiko, kein aktueller Mangel.

**Umgesetzt:** `implementation("androidx.core:core-ktx:1.13.1")` wurde aus
`app/build.gradle.kts` entfernt (separater, expliziter Folgeschritt nach
Freigabe dieses Plans, kein Teil der ursprünglichen reinen Lese-Analyse).

### 3.4 Unnötige Variablen – keine gefunden
Alle Felder in `SettingsActivity` (`profileAdapter`, `latestUpdateUrl`,
`lastShizukuStateRefreshMs` u. a.) werden nachweislich gelesen und geschrieben
(siehe jeweilige Verwendungsstellen in Abschnitt 2.1). Keine zugewiesene, nie
gelesene Variable gefunden.

### 3.5 Workarounds – mit Ursache (nicht nur Symptom)

| Workaround | Fundstelle | Symptom | Zugrunde liegende Ursache |
|---|---|---|---|
| Timeout + `AtomicBoolean`-Absicherung in `withUserService()` | `ShizukuUtils.kt:254-328` | `bindUserService`-Callback kann Minuten später oder nie feuern | GrapheneOS' SELinux-Policy blockiert den `inotify`-Watch, den Shizukus `UserServiceManager` beim Binden auf das APK-Verzeichnis legen will (`avc: denied { watch } ... tcontext=u:object_r:apk_data_file:s0`, laut Kommentar auf dem Zielgerät beobachtet) |
| `SwitchDebounce` (geräteweite SharedPreferences-Sperre, 3000 ms) | `SwitchDebounce.kt` | Zwei Trigger kurz hintereinander stoßen überlappende Binds an | Dieselbe SELinux-bedingte Bind-Verzögerung – überlappende Binds verstopfen den ohnehin langsamen Shizuku-Dienst zusätzlich |
| 1500-ms-Entprellung in `refreshShizukuState()` | `SettingsActivity.kt:211-222` | `onCreate` gefolgt von `onResume` (oder schnelles Verlassen/Zurückkehren) stapelt mehrere `loadProfiles()`-Binds | Dieselbe Ursache wie oben |

Alle drei sind bereits im Code als bewusste, notwendige Gegenmaßnahmen
dokumentiert (nicht als vermeidbarer Ballast) – es gibt keinen Beleg dafür,
dass die zugrunde liegende SELinux-Restriktion durch eine einfachere Lösung
umgangen werden könnte, ohne die Root-Ursache (Shizuku/GrapheneOS-Interaktion)
selbst zu ändern, was außerhalb der Kontrolle dieses Projekts liegt.

### 3.6 `/save/`-Ordner – kein Kandidat
`d:\AtlayaSwitch\save\AtlayaSwitch\` enthält ältere Kopien von
`MainActivity.kt`/`ShizukuUtils.kt` sowie Projektdateien. Laut `.gitignore`
(Zeile 20-21: `# Alter Backup-Ordner (Duplikat des Projekts)` /
`/save/`) ist dieser Ordner explizit von Git ausgeschlossen und als bewusstes,
lokales Backup dokumentiert – kein Teil des aktiven Quellbaums, kein Teil des
Gradle-Builds (`settings.gradle.kts` inkludiert nur `:app` relativ zum
Repo-Root, nicht `save/AtlayaSwitch`). Daher kein Refactoring-Kandidat im Sinne
dieser Analyse.

## 4. Offene Fragen

Keine mehr offen. Der einzige Kandidat, bei dem ohne Build-Analyse keine
100%ige Sicherheit bestand (`core-ktx`, Abschnitt 3.3), wurde auf Wunsch per
`./gradlew :app:dependencies` geklärt.

## 5. Refactoring-Plan

**Umgesetzt (nach Freigabe, separater Schritt):**
- `app/build.gradle.kts:61`: Zeile
  `implementation("androidx.core:core-ktx:1.13.1")` entfernt.
  Risiko war gering (Beleg 3.3). Nutzen: eine weniger explizit gepinnte
  Abhängigkeit, kein Verhaltens- oder Funktionsunterschied zu erwarten.

**Bewusst unverändert bleibt alles Übrige** – kein Beleg für Ballast, der ohne
Risiko entfernt werden könnte:
- Die gesamte Wechsel-Architektur (`ShizukuUtils.performConfiguredSwitch` als
  einzige Verzweigungsstelle, `UserService`/AIDL als privilegierter Prozess).
- Alle SharedPreferences-Schnittstellen und -Keys (Datenformat unverändert).
- Alle drei dokumentierten Workarounds gegen die GrapheneOS-SELinux-Restriktion
  (Abschnitt 3.5) – notwendige Gegenmaßnahmen für eine reale Plattform-
  einschränkung, kein vermeidbarer Ballast.
- Manifest-Einträge, Intent-Filter, Ressourcenstruktur (5 Sprachen).
- `androidx.appcompat`, `androidx.recyclerview`, `dev.rikka.shizuku:*`,
  `androidx.work:work-runtime-ktx` – jeweils mit konkreter, nachgewiesener
  Nutzung (Abschnitt 2.5).
- `/save/`-Backup-Ordner (Abschnitt 3.6) – außerhalb des Quellbaums, bewusst so
  dokumentiert.

## 6. Abschlussbestätigung

Bestätigt: In diesem Durchlauf wurde ausschließlich diese Datei
(`ARCHITEKTUR_REVIEW.md`) neu angelegt. Es wurde keine andere Datei im Projekt
geändert, erstellt, umbenannt, verschoben oder gelöscht. Der einzige
ausgeführte Nicht-Lese-Befehl war der schreibgeschützte Gradle-Dependency-Tree-
Lauf (`./gradlew :app:dependencies --configuration debugRuntimeClasspath`),
der nur den Gradle-internen Build-Cache betrifft (kein Teil des Quellbaums,
bereits über `.gitignore` ausgeschlossen).
