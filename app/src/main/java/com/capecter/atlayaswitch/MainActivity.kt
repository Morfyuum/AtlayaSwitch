package com.capecter.atlayaswitch

import android.content.Intent
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

/**
 * Startet entweder per App-Icon-Tap (LAUNCHER-Intent, immer aktiv) oder per NFC-Scan
 * eines gekoppelten Rings (TECH_DISCOVERED-Intent, nur bei UID-Treffer aktiv). Beide
 * Wege führen zum selben Profilwechsel - kein sichtbares UI, beendet sich danach selbst.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuUtils.REQUEST_CODE_PERMISSION) {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                performSwitch()
            } else {
                toastAndFinish("Shizuku-Berechtigung verweigert.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("atlaya_switch", MODE_PRIVATE)

        if (intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            handleNfcLaunch(intent)
            return
        }

        startSwitchFlow()
    }

    /**
     * Nur bei erkanntem, gekoppeltem Ring geht es weiter - bei jedem anderen Tag
     * (falscher Ring, fremde Karte) schließt sich die App sofort und lautlos, ohne
     * jede Rückmeldung. Das ist Absicht: ein NFC-Trigger soll unauffällig bleiben,
     * niemand soll aus einer Fehlermeldung erfahren, dass es diesen Trigger gibt.
     */
    private fun handleNfcLaunch(intent: Intent) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        val scannedUid = tag?.id?.let { bytes -> bytes.joinToString("") { "%02X".format(it) } }
        val pairedUid = prefs.getString(SettingsActivity.KEY_PAIRED_NFC_UID, null)

        if (scannedUid == null || pairedUid == null || !scannedUid.equals(pairedUid, ignoreCase = true)) {
            finish()
            return
        }

        startSwitchFlow()
    }

    /**
     * Sperre gegen doppelte/schnelle Ausloesung (Doppel-Tap, hängengebliebener
     * Stray-Intent, o.ä.): Ohne das koennen mehrere ueberlappende Shizuku-Anfragen
     * denselben Verstopfungs-Effekt erzeugen, der frueher zu verspaeteten,
     * ueberraschenden Wechseln gefuehrt hat (siehe [[pixel-grapheneos... Verlauf]]).
     * Bewusst SharedPreferences statt eines In-Memory-Flags, damit die Sperre auch
     * ueber einen Prozess-Neustart hinweg greift, nicht nur innerhalb derselben
     * App-Instanz.
     */
    private fun tooSoonSinceLastAttempt(): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_SWITCH_ATTEMPT_MS, 0L)
        prefs.edit().putLong(KEY_LAST_SWITCH_ATTEMPT_MS, now).apply()
        return now - last < MIN_SWITCH_INTERVAL_MS
    }

    private fun startSwitchFlow() {
        if (tooSoonSinceLastAttempt()) {
            finish()
            return
        }
        if (!ShizukuUtils.isShizukuAvailable()) {
            toastAndFinish("Shizuku läuft nicht. Bitte Shizuku starten und erneut versuchen.")
            return
        }

        val targetUserId = prefs.getInt(KEY_TARGET_USER_ID, -1)
        if (targetUserId == -1) {
            // Noch kein Zielprofil festgelegt -> Einstellungen öffnen statt zu wechseln
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)

        if (ShizukuUtils.hasPermission()) {
            performSwitch()
        } else {
            ShizukuUtils.requestPermission()
        }
    }

    private fun performSwitch() {
        val targetUserId = prefs.getInt(KEY_TARGET_USER_ID, -1)
        val onError: (Exception) -> Unit = { e ->
            Toast.makeText(this, "Wechsel fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }

        if (prefs.getString(SettingsActivity.KEY_SWITCH_MODE, SettingsActivity.SWITCH_MODE_END_SESSION)
            == SettingsActivity.SWITCH_MODE_END_SESSION
        ) {
            // Erfolg des Beendens wird bewusst nicht per Toast angezeigt (auch nicht bei
            // Fehlschlag, z.B. Profil "Eigentümer" laesst sich als Systemnutzer nicht
            // stoppen) - der Trigger soll unauffaellig bleiben, siehe Klassenkommentar.
            ShizukuUtils.switchToUserAndEndSession(
                context = this,
                targetUserId = targetUserId,
                sourceUserId = ShizukuUtils.currentProfileUserId(),
                onDone = { finish() },
                onError = onError
            )
        } else {
            ShizukuUtils.switchToUser(
                context = this,
                userId = targetUserId,
                onDone = { finish() },
                onError = onError
            )
        }
    }

    private fun toastAndFinish(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    companion object {
        const val KEY_TARGET_USER_ID = "target_user_id"
        private const val KEY_LAST_SWITCH_ATTEMPT_MS = "last_switch_attempt_ms"
        private const val MIN_SWITCH_INTERVAL_MS = 3000L
    }
}
