package com.capecter.atlayaswitch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

/**
 * Kapselt alle Shizuku-Aufrufe an einer Stelle.
 * Shizuku selbst läuft mit den Rechten der ADB-Shell (UID 2000). Da neuere
 * Shizuku-Versionen (ab api 13) Shizuku.newProcess() nicht mehr öffentlich
 * zugänglich machen, wird ein privilegierter UserService (siehe UserService.kt)
 * über Shizuku.bindUserService gestartet, der "pm list users" bzw.
 * "am switch-user <id>" mit Shell-Rechten ausführt - ohne Root und ohne
 * Passwortabfrage im Zielprofil.
 */
object ShizukuUtils {

    const val REQUEST_CODE_PERMISSION = 1001

    private val mainHandler = Handler(Looper.getMainLooper())

    data class GraphenProfile(val userId: Int, val label: String)

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isShizukuAvailable(): Boolean = Shizuku.pingBinder()

    /** Unterscheidet "gar nicht installiert" von "installiert, läuft aber nicht" -
     * beide Fälle brauchen eine andere Anleitung (installieren vs. nur starten). */
    fun isShizukuPackageInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Entwickleroptionen/Drahtloses Debugging sind auf GrapheneOS (wie auf Android
     * allgemein) NUR im Profil "Eigentümer" (User 0) sichtbar - in sekundären Profilen
     * ("Privat", "Unterwegs") hat Shizuku dort schlicht keine Startmoeglichkeit. UID
     * durch 100000 ist die oeffentlich dokumentierte Formel fuer die Android-User-ID
     * (UID = userId * 100000 + appId), keine versteckte API.
     */
    fun isOwnerProfile(): Boolean = android.os.Process.myUid() / 100000 == 0

    /**
     * Springt direkt in die Shizuku-App (dort startet der Nutzer den Dienst selbst -
     * ein Drittanbieter kann Shizukus privilegierten Dienst ohne Root/ADB-Erstkopplung
     * nicht selbst starten, das ist bei Shizuku bewusst so abgesichert). Ist Shizuku
     * nicht installiert, wird stattdessen die Projektseite im Browser geöffnet.
     * Rückgabe: true, wenn Shizuku direkt geöffnet werden konnte.
     */
    fun openShizukuApp(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        return if (launchIntent != null) {
            context.startActivity(launchIntent)
            true
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
            false
        }
    }

    /**
     * Öffnet den Play-Store-Eintrag von Shizuku (App-Details, kein Download außerhalb
     * des Play Store nötig). Fällt auf die Webseite im Browser zurück, wenn auf diesem
     * Gerät kein Play Store installiert ist (z.B. reines GrapheneOS ohne Sandboxed
     * Google Play).
     */
    fun openPlayStoreForShizuku(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            )
        } catch (e: Exception) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")
                )
            )
        }
    }

    /**
     * Öffnet Androids eigene Mehrbenutzer-Übersicht (öffentliches, unprivilegiertes
     * Intent seit API 26) - damit ist der Wechsel ins Profil "Eigentümer" ein Tap
     * statt "Power-Button lang drücken"-Insiderwissen. Der Wechsel selbst passiert
     * erst durch Antippen des Zielprofils dort, nicht automatisch durch diesen Aufruf.
     */
    fun openUserSettings(context: Context) {
        try {
            // "android.settings.USER_SETTINGS" ist keine öffentliche SDK-Konstante
            // (Settings.ACTION_USER_SETTINGS existiert nicht im android.jar), der
            // Action-String selbst ist aber auf dem Geraet real registriert
            // (com.android.settings/.Settings$UserSettingsActivity, per dumpsys
            // package geprüft) - deshalb roh statt über eine Konstante.
            context.startActivity(Intent("android.settings.USER_SETTINGS"))
        } catch (e: Exception) {
            // Bildschirm auf diesem Geraet nicht vorhanden - nichts weiter zu tun.
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
    }

    /**
     * Parst die Ausgabe von "pm list users", z.B.:
     * UserInfo{0:Eigentümer:c13} running
     * UserInfo{10:Privat:1010} running
     * UserInfo{11:Unterwegs:1010} running
     */
    fun listProfiles(
        context: Context,
        onResult: (List<GraphenProfile>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        withUserService(context, onError) { service ->
            val raw = service.listUsersRaw()
            val regex = Regex("""UserInfo\{(\d+):([^:]*):""")
            val profiles = regex.findAll(raw).map {
                GraphenProfile(it.groupValues[1].toInt(), it.groupValues[2])
            }.toList()
            postMain { onResult(profiles) }
        }
    }

    /**
     * Wechselt direkt zum angegebenen Profil - das ist der Ein-Klick-Schritt.
     */
    fun switchToUser(
        context: Context,
        userId: Int,
        onDone: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        withUserService(context, onError) { service ->
            service.switchUser(userId)
            postMain { onDone() }
        }
    }

    /** UID durch 100000 = das aktuelle GrapheneOS-Profil dieses App-Prozesses (siehe isOwnerProfile()). */
    fun currentProfileUserId(): Int = android.os.Process.myUid() / 100000

    /**
     * Wie switchToUser(), beendet danach zusätzlich das Quellprofil ("End session") -
     * siehe UserService.switchUserAndEndSession() für die Details/Grenzen (v.a. Profil
     * "Eigentümer" laesst sich als Android-Systemnutzer nicht stoppen). onDone liefert
     * mit, ob das Beenden geklappt hat; der Aufrufer entscheidet, ob das dem Nutzer
     * angezeigt wird - im stillen NFC-/App-Icon-Trigger (MainActivity) bewusst nicht,
     * um unauffällig zu bleiben.
     */
    fun switchToUserAndEndSession(
        context: Context,
        targetUserId: Int,
        sourceUserId: Int,
        onDone: (endSessionSucceeded: Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        withUserService(context, onError) { service ->
            val ok = service.switchUserAndEndSession(targetUserId, sourceUserId)
            postMain { onDone(ok) }
        }
    }

    /**
     * Liest, ob AtlayaSwitch im aktuellen Profil per NFC-Scan gestartet werden darf
     * (Android-14-Systemeinstellung "TagAppPreference", separat pro Profil).
     */
    fun getNfcTagAppPreference(
        context: Context,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentProfileUserId()
        withUserService(context, onError) { service ->
            val allowed = service.getNfcTagAppPreference(userId, context.packageName)
            postMain { onResult(allowed) }
        }
    }

    /**
     * Schaltet die Freigabe für das aktuelle Profil um. onResult(false) bedeutet:
     * der Versuch ist fehlgeschlagen (z.B. weil das Geraet die API nicht hat) -
     * der Aufrufer sollte dann openNfcTagAppPreferenceSettings() als manuellen
     * Rückfallweg anbieten.
     */
    fun setNfcTagAppPreference(
        context: Context,
        allow: Boolean,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = currentProfileUserId()
        withUserService(context, onError) { service ->
            val ok = service.setNfcTagAppPreference(userId, context.packageName, allow)
            postMain { onResult(ok) }
        }
    }

    /**
     * Prüft, ob AtlayaSwitch selbst im angegebenen Profil installiert ist. Wichtig für
     * das Zielprofil: das ist das bewusst unverfängliche Tarnprofil, das auch Dritte
     * (z.B. bei einer Kontrolle) zu sehen bekommen dürfen sollen - AtlayaSwitch dort
     * installiert zu haben würde selbst verraten, dass es einen versteckten Wechsel-
     * mechanismus gibt, egal ob die App dort sichtbar im Menü auftaucht oder nicht.
     */
    fun isPackageInstalledInProfile(
        context: Context,
        userId: Int,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        withUserService(context, onError) { service ->
            val installed = service.isPackageInstalledForUser(userId, context.packageName)
            postMain { onResult(installed) }
        }
    }

    /** Entfernt AtlayaSwitch aus einem fremden Profil (z.B. dem Zielprofil), ohne dass
     * dafür erst dorthin gewechselt werden muss. */
    fun uninstallFromProfile(
        context: Context,
        userId: Int,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        withUserService(context, onError) { service ->
            val ok = service.uninstallForUser(userId, context.packageName)
            postMain { onResult(ok) }
        }
    }

    /**
     * Öffnet Androids eigenen Einstellungsbildschirm ("Über NFC starten"), auf dem
     * sich die TagAppPreference auch von Hand umschalten lässt - Rückfallweg, falls
     * die privilegierte Direktschaltung über Shizuku scheitert.
     */
    fun openNfcTagAppPreferenceSettings(context: Context) {
        try {
            context.startActivity(Intent("android.nfc.action.CHANGE_TAG_INTENT_PREFERENCE"))
        } catch (e: Exception) {
            // Bildschirm auf diesem Geraet nicht vorhanden - nichts weiter zu tun.
        }
    }

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(ComponentName(context.packageName, UserService::class.java.name))
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(false)
            .version(1)
    }

    /**
     * Timeout fuer einen einzelnen bindUserService-Versuch. Auf diesem Geraet blockiert
     * GrapheneOS' SELinux-Policy den inotify-Watch, den Shizukus UserServiceManager beim
     * Binden auf das APK-Verzeichnis legen will ("avc: denied { watch } ...
     * tcontext=u:object_r:apk_data_file:s0") - jeder Bind-Versuch haengt dadurch oft
     * >1s und manche verbinden nie. Ohne Timeout bleibt der onServiceConnected-Callback
     * unbegrenzt lange "scharf" und kann Minuten spaeter feuern, voellig losgeloest vom
     * Activity-Lebenszyklus, der ihn ausgeloest hat - das fuehrte dazu, dass ein alter
     * switchToUser()-Aufruf (z.B. vom App-Icon) mitten in einer ganz anderen Aktion
     * (Oeffnen von SettingsActivity) ueberraschend ausgefuehrt wurde.
     */
    private const val USER_SERVICE_TIMEOUT_MS = 6000L

    private fun withUserService(
        context: Context,
        onError: (Exception) -> Unit,
        action: (IUserService) -> Unit
    ) {
        val appContext = context.applicationContext
        val args = userServiceArgs(appContext)
        // Stellt sicher, dass entweder der Timeout oder eine tatsaechliche Verbindung
        // gewinnt - nie beide: eine verspaetet eintreffende Verbindung nach Timeout darf
        // "action" nicht mehr ausloesen.
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        lateinit var connection: ServiceConnection
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!settled.compareAndSet(false, true)) {
                    Shizuku.unbindUserService(args, connection, true)
                    return
                }
                Thread {
                    try {
                        val service = IUserService.Stub.asInterface(binder)
                        action(service)
                    } catch (e: Exception) {
                        postMain { onError(e) }
                    } finally {
                        Shizuku.unbindUserService(args, connection, true)
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }

        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            if (settled.compareAndSet(false, true)) {
                onError(e)
            }
            return
        }

        mainHandler.postDelayed({
            if (settled.compareAndSet(false, true)) {
                try {
                    Shizuku.unbindUserService(args, connection, true)
                } catch (e: Exception) {
                    // Bind kam nie zustande - nichts zum Loesen da.
                }
                onError(java.util.concurrent.TimeoutException(
                    "Shizuku-Dienst antwortete nicht innerhalb von ${USER_SERVICE_TIMEOUT_MS}ms"
                ))
            }
        }, USER_SERVICE_TIMEOUT_MS)
    }

    private fun postMain(block: () -> Unit) {
        mainHandler.post(block)
    }
}
