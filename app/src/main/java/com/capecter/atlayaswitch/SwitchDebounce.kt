package com.capecter.atlayaswitch

import android.content.SharedPreferences

/**
 * Gemeinsame Entprellung fuer alle Wechsel-Ausloeser (Icon-Tap, Schnelleinstellungen-
 * Kachel): verhindert, dass zwei Trigger kurz hintereinander gleichzeitig Shizuku-
 * UserService-Binds anstossen (siehe ShizukuUtils.withUserService-Kommentar zu
 * ueberlappenden Binds durch die SELinux-bedingte Bind-Verzoegerung auf diesem Geraet).
 * Bewusst geraetweite SharedPreferences statt eines In-Memory-Flags pro Ausloeser, damit
 * die Sperre auch trigger- und prozessuebergreifend greift.
 */
object SwitchDebounce {
    private const val KEY_LAST_SWITCH_ATTEMPT_MS = "last_switch_attempt_ms"
    private const val MIN_SWITCH_INTERVAL_MS = 3000L

    fun tooSoonSinceLastAttempt(prefs: SharedPreferences): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_SWITCH_ATTEMPT_MS, 0L)
        prefs.edit().putLong(KEY_LAST_SWITCH_ATTEMPT_MS, now).apply()
        return now - last < MIN_SWITCH_INTERVAL_MS
    }
}
