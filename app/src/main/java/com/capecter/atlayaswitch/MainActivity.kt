package com.capecter.atlayaswitch

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

/**
 * Startet per App-Icon-Tap (LAUNCHER-Intent) und führt sofort den Profilwechsel aus -
 * kein sichtbares UI, beendet sich danach selbst.
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
        android.util.Log.i("AtlayaSwitchClient", "performSwitch: gestartet")
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
