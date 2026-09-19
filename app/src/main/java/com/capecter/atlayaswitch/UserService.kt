package com.capecter.atlayaswitch

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Läuft als eigener Prozess mit den Rechten der ADB-Shell (UID 2000), gestartet
 * über Shizuku.bindUserService. Nur innerhalb dieses Prozesses ist Runtime.exec()
 * mit Shell-Rechten erlaubt - der App-eigene Prozess selbst bleibt unprivilegiert.
 *
 * Dieser Prozess ist die Vertrauensgrenze der App: alles, was hier aufrufbar ist, läuft mit
 * Shell-Rechten. Deshalb nimmt er bewusst keine frei wählbaren Ziele entgegen - Paketaktionen
 * gehen nur auf das eigene Paket, Nutzer-IDs müssen gültig (nicht negativ) sein.
 */
class UserService : IUserService.Stub() {

    override fun listUsersRaw(): String = runShellCommand("pm", "list", "users")

    override fun switchUser(userId: Int, hardenAdb: Boolean) {
        if (!isValidUserId(userId)) return
        runShellCommand("am", "switch-user", userId.toString())
        if (hardenAdb) disableWirelessDebugging()
    }

    /**
     * Wie switchUser(), beendet danach zusätzlich das bisherige Profil per
     * "am stop-user -f" ("End session" im GrapheneOS-Nutzerwechsel-Dialog):
     * Apps werden beendet, GrapheneOS entfernt die Verschlüsselungsschlüssel
     * dieses Profils aus RAM/Keyring, es geht wieder "at rest". Muss auf den
     * abgeschlossenen Wechsel warten - stop-user auf den noch aktiven
     * Vordergrundnutzer schlägt fehl bzw. würde die laufende Sitzung selbst
     * beenden. "am get-current-user" liefert dafür die aktuell aktive
     * Profil-ID als Poll-Signal. Liefert false, wenn der Wechsel innerhalb
     * des Timeouts nicht bestätigt werden konnte (dann wird NICHT gestoppt)
     * oder wenn stop-user selbst fehlschlägt - z.B. bei Profil "Eigentümer"
     * (User 0): Android verweigert das Stoppen des Systemnutzers grundsätzlich.
     *
     * Das optionale Ausschalten von Drahtlosem Debugging läuft bewusst NACH dem bestätigten
     * Wechsel, aber VOR stop-user: stop-user beendet auch den App-Prozess des Quellprofils
     * (und damit den Aufrufer dieses Dienstes) - alles danach würde womöglich nicht mehr
     * ausgeführt. Der Wechsel selbst wird dadurch nicht verzögert.
     */
    override fun switchUserAndEndSession(targetUserId: Int, sourceUserId: Int, hardenAdb: Boolean): Boolean {
        if (!isValidUserId(targetUserId) || !isValidUserId(sourceUserId)) return false
        runShellCommand("am", "switch-user", targetUserId.toString())
        if (!waitUntilCurrentUser(targetUserId)) {
            return false
        }
        if (hardenAdb) disableWirelessDebugging()
        val result = runShellCommand("am", "stop-user", "-f", sourceUserId.toString())
        return !result.contains("Error", ignoreCase = true)
    }

    private fun waitUntilCurrentUser(userId: Int): Boolean {
        val deadline = System.currentTimeMillis() + SWITCH_CONFIRM_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (runShellCommand("am", "get-current-user").trim() == userId.toString()) return true
            Thread.sleep(SWITCH_POLL_INTERVAL_MS)
        }
        return false
    }

    /**
     * Prüft, ob AtlayaSwitch selbst in einem bestimmten Profil installiert ist - genutzt, um
     * AtlayaSwitch im gewählten Zielprofil zu erkennen. Das Zielprofil ist das
     * bewusst unverfängliche Tarnprofil, das auch Dritte (Kontrolle, Behörden) zu
     * Gesicht bekommen dürfen sollen; AtlayaSwitch dort installiert zu haben würde
     * genau das verraten, was das Profil verbergen soll (dass es einen versteckten
     * Wechselmechanismus/weitere Profile gibt) - unabhängig davon, ob es dort sichtbar
     * im Menü auftaucht oder nicht.
     *
     * Trotz des "pkg"-Parameters (Teil der AIDL-Schnittstelle) wird nur das eigene Paket
     * akzeptiert: dieser Prozess hat Shell-Rechte, und ein frei wählbares Paket wäre eine
     * Deinstallations-/Ausspäh-Schnittstelle für beliebige Apps.
     */
    override fun isPackageInstalledForUser(userId: Int, pkg: String): Boolean {
        if (!isValidUserId(userId) || pkg != OWN_PACKAGE) return false
        val raw = runShellCommand("pm", "list", "packages", "--user", userId.toString(), pkg)
        return raw.lines().any { it.trim() == "package:$pkg" }
    }

    override fun uninstallForUser(userId: Int, pkg: String): Boolean {
        if (!isValidUserId(userId) || pkg != OWN_PACKAGE) return false
        val raw = runShellCommand("pm", "uninstall", "--user", userId.toString(), pkg)
        return raw.trim().equals("Success", ignoreCase = true)
    }

    /**
     * Schaltet Drahtloses Debugging aus, wenn es an ist - aber nur, solange USB-Debugging an
     * bleibt (Begründung und Rückgabecodes: siehe AdbHardening). Drahtloses Debugging wird nur
     * zum Starten von Shizuku gebraucht; ist Shizuku einmal gestartet, ist der offene
     * adbd-Port im WLAN nur noch Angriffsfläche. Als Shell-Prozess darf dieser Dienst die
     * globale Einstellung selbst setzen, die App braucht dafür kein WRITE_SECURE_SETTINGS.
     */
    override fun disableWirelessDebugging(): Int {
        if (readGlobalSetting(SETTING_ADB_WIFI) != "1") return AdbHardening.WIFI_ALREADY_OFF
        if (readGlobalSetting(SETTING_ADB_USB) != "1") return AdbHardening.KEPT_USB_DEBUGGING_OFF
        runShellCommand("settings", "put", "global", SETTING_ADB_WIFI, "0")
        return if (readGlobalSetting(SETTING_ADB_WIFI) != "1") AdbHardening.WIFI_TURNED_OFF else AdbHardening.FAILED
    }

    private fun readGlobalSetting(key: String): String =
        runShellCommand("settings", "get", "global", key).trim()

    /** Negative IDs sind keine Profile - und würden als Argument von "am"/"pm" als Option gelesen. */
    private fun isValidUserId(userId: Int): Boolean = userId >= 0

    /**
     * Nimmt die Argumente als Array statt als zusammengesetzten String entgegen und
     * ruft Runtime.exec() OHNE "sh -c" auf - dadurch landet jedes Argument als eigenes
     * argv-Element beim Zielprogramm, eine Shell interpretiert nie etwas davon. Das
     * schließt Shell-Injection über Sonderzeichen in userId/pkg strukturell aus, statt
     * sich auf manuelles Escaping zu verlassen (userId/pkg sind hier zwar aktuell immer
     * vertrauenswürdig - eigener Regex-Parse bzw. der eigene Packagename -, aber dieser
     * Prozess läuft mit Shell-Rechten, also lieber grundsätzlich sicher als situativ).
     */
    private fun runShellCommand(vararg cmd: String): String {
        val process = Runtime.getRuntime().exec(cmd)
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        return output
    }

    private companion object {
        const val SWITCH_CONFIRM_TIMEOUT_MS = 8000L
        const val SWITCH_POLL_INTERVAL_MS = 150L

        /** Muss mit applicationId in app/build.gradle.kts übereinstimmen - dieser Prozess hat
         * keinen Context, aus dem er den eigenen Paketnamen lesen könnte. */
        const val OWN_PACKAGE = "com.capecter.atlayaswitch"

        // Globale Einstellungen: adb_enabled = USB-Debugging, adb_wifi_enabled = Drahtloses Debugging.
        const val SETTING_ADB_USB = "adb_enabled"
        const val SETTING_ADB_WIFI = "adb_wifi_enabled"
    }
}
