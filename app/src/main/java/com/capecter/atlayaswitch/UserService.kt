package com.capecter.atlayaswitch

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Läuft als eigener Prozess mit den Rechten der ADB-Shell (UID 2000), gestartet
 * über Shizuku.bindUserService. Nur innerhalb dieses Prozesses ist Runtime.exec()
 * mit Shell-Rechten erlaubt - der App-eigene Prozess selbst bleibt unprivilegiert.
 */
class UserService : IUserService.Stub() {

    override fun listUsersRaw(): String = runShellCommand("pm", "list", "users")

    override fun switchUser(userId: Int) {
        runShellCommand("am", "switch-user", userId.toString())
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
     */
    override fun switchUserAndEndSession(targetUserId: Int, sourceUserId: Int): Boolean {
        runShellCommand("am", "switch-user", targetUserId.toString())
        if (!waitUntilCurrentUser(targetUserId)) {
            return false
        }
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
     * Android verlangt seit API 34 pro App und pro Profil eine explizite Freigabe
     * ("TagAppPreference"), damit sie durch bloßes Vorhalten eines NFC-Tags gestartet
     * werden darf - sonst wird der Tag-Intent lautlos verworfen, selbst bei korrekt
     * registriertem Manifest-Filter. Es gibt dafür keinen "cmd nfc"-Shell-Befehl, der
     * Wert steht nur im "dumpsys nfc"-Textdump (Abschnitt "TagAppPreference:", darin
     * "userId=<id>" gefolgt von "pkg: <package> : true/false"-Zeilen je Profil).
     */
    override fun getNfcTagAppPreference(userId: Int, pkg: String): Boolean {
        val raw = runShellCommand("dumpsys", "nfc")
        val userBlock = Regex("""userId=$userId\b([\s\S]*?)(?=userId=\d+|\z)""").find(raw)
            ?.groupValues?.get(1) ?: return false
        val match = Regex("""pkg:\s*${Regex.escape(pkg)}\s*:\s*(true|false)""").find(userBlock)
        return match?.groupValues?.get(1) == "true"
    }

    /**
     * setTagIntentAppPreferenceForUser() ist eine @SystemApi, die im öffentlichen
     * android.jar fehlt (nicht kompilierbar) - deshalb per Reflection auf die echte
     * Framework-Methode zur Laufzeit. Der Umweg über einen per ActivityThread.systemMain()
     * konstruierten "System-Context" scheiterte (UnsupportedOperationException bei
     * getSystemService("nfc") - der Hilfs-Context ist dafuer nicht vollstaendig genug
     * initialisiert). Stattdessen wird der rohe NFC-Systemdienst direkt über
     * ServiceManager.getService("nfc") + INfcAdapter.Stub.asInterface geholt - der
     * Weg, den NfcAdapter intern selbst nimmt, nur ohne den Context dazwischen. Läuft
     * nur, weil dieser Prozess mit Shell-Rechten (UID 2000) via Shizuku gestartet wurde.
     * Zur Sicherheit wird der tatsächliche Erfolg danach über getNfcTagAppPreference
     * gegengeprüft, statt blind dem Rückgabewert zu vertrauen.
     */
    override fun setNfcTagAppPreference(userId: Int, pkg: String, allow: Boolean): Boolean {
        try {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "nfc") as android.os.IBinder
            val nfcService = Class.forName("android.nfc.INfcAdapter\$Stub")
                .getMethod("asInterface", android.os.IBinder::class.java)
                .invoke(null, binder)
            val method = nfcService.javaClass.getMethod(
                "setTagIntentAppPreferenceForUser",
                Int::class.javaPrimitiveType,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(nfcService, userId, pkg, allow)
        } catch (e: Exception) {
            android.util.Log.e("AtlayaSwitchUserService", "setNfcTagAppPreference fehlgeschlagen", e)
            return false
        }
        return getNfcTagAppPreference(userId, pkg) == allow
    }

    /**
     * Prüft, ob ein Paket in einem bestimmten Profil installiert ist - genutzt, um
     * AtlayaSwitch selbst im gewählten Zielprofil zu erkennen. Das Zielprofil ist das
     * bewusst unverfängliche Tarnprofil, das auch Dritte (Kontrolle, Behörden) zu
     * Gesicht bekommen dürfen sollen; AtlayaSwitch dort installiert zu haben würde
     * genau das verraten, was das Profil verbergen soll (dass es einen versteckten
     * Wechselmechanismus/weitere Profile gibt) - unabhängig davon, ob es dort sichtbar
     * im Menü auftaucht oder nicht.
     */
    override fun isPackageInstalledForUser(userId: Int, pkg: String): Boolean {
        val raw = runShellCommand("pm", "list", "packages", "--user", userId.toString(), pkg)
        return raw.lines().any { it.trim() == "package:$pkg" }
    }

    override fun uninstallForUser(userId: Int, pkg: String): Boolean {
        val raw = runShellCommand("pm", "uninstall", "--user", userId.toString(), pkg)
        return raw.trim().equals("Success", ignoreCase = true)
    }

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
    }
}
