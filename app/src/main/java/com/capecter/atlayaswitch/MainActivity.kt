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

    private fun startSwitchFlow() {
        if (SwitchDebounce.tooSoonSinceLastAttempt(prefs)) {
            finish()
            return
        }

        val targetUserId = prefs.getInt(KEY_TARGET_USER_ID, -1)
        if (targetUserId == -1) {
            // Noch kein Zielprofil festgelegt -> Einstellungen öffnen statt zu wechseln
            // (dort steht bei Bedarf auch die geführte Shizuku-Einrichtung)
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        if (!ShizukuUtils.isShizukuAvailable()) {
            // Shizuku läuft nicht - typischerweise nach einem Geräte-Neustart. Kein Toast mit
            // Klartext (der Trigger soll unauffällig bleiben, ein "Shizuku läuft nicht" wäre
            // vor Zuschauern verräterisch), sondern der normale Android-Nutzerwechsel als
            // stiller Rückfall: zwei Taps mehr, aber man kommt trotzdem ins Zielprofil.
            fallBackToSystemUserSwitcher()
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

        // Erfolg des Beendens (End-Session-Modus) wird bewusst nicht per Toast angezeigt
        // (auch nicht bei Fehlschlag, z.B. Profil "Eigentümer" laesst sich als
        // Systemnutzer nicht stoppen) - der Trigger soll unauffaellig bleiben, siehe
        // Klassenkommentar. Gleiches gilt fuer einen fehlgeschlagenen Wechsel: kein
        // Fehler-Toast, sondern derselbe stille Rueckfall wie bei nicht laufendem Shizuku.
        ShizukuUtils.performConfiguredSwitch(
            context = this,
            prefs = prefs,
            targetUserId = targetUserId,
            onDone = { finish() },
            onError = { fallBackToSystemUserSwitcher() }
        )
    }

    private fun fallBackToSystemUserSwitcher() {
        ShizukuUtils.openUserSettings(this)
        finish()
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
    }
}
