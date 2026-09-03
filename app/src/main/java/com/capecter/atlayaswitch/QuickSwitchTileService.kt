package com.capecter.atlayaswitch

import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Schnelleinstellungen-Kachel als Ersatz fuer den mit v1.8.2 entfernten NFC-Ring-Trigger
 * (siehe interne Projekt-Doku): ein Tipp auf die Kachel im heruntergezogenen Panel loest denselben
 * Wechsel aus wie der App-Icon-Tap - auch vom Sperrbildschirm aus erreichbar, ohne die
 * App zu oeffnen.
 *
 * Anders als der reine App-Icon-Tap ist die Kachel im Panel sichtbar (Symbol + Label) -
 * das steht in Spannung zum "unauffaellig"-Grundsatz des Duress-Konzepts. Deshalb bewusst:
 * - standardmaessig AUS (Settings-Schalter KEY_QUICK_TILE_ENABLED, Default false) - die
 *   Kachel muss aktiv freigeschaltet werden, bevor ein Tipp etwas ausloest, selbst wenn
 *   sie schon im Panel liegt.
 * - Label/Icon bewusst neutral gehalten (kein Hinweis auf Wechsel-/Duress-Funktion),
 *   siehe strings.xml (qs_tile_label) und ic_qs_tile.xml.
 * - Kein Toast/keine sichtbare Rueckmeldung beim Ausloesen, analog zu MainActivity.
 */
class QuickSwitchTileService : TileService() {

    private val prefs: SharedPreferences by lazy { getSharedPreferences("atlaya_switch", MODE_PRIVATE) }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!prefs.getBoolean(SettingsActivity.KEY_QUICK_TILE_ENABLED, false)) return
        if (SwitchDebounce.tooSoonSinceLastAttempt(prefs)) return

        val targetUserId = prefs.getInt(MainActivity.KEY_TARGET_USER_ID, -1)
        if (targetUserId == -1 || !ShizukuUtils.isShizukuAvailable() || !ShizukuUtils.hasPermission()) {
            // Kachel-Tap darf nicht selbst um Berechtigungen betteln oder UI oeffnen -
            // unauffaellig bleiben. Ersteinrichtung passiert regulaer per App-Icon-Tap
            // bzw. in den Einstellungen.
            return
        }

        ShizukuUtils.performConfiguredSwitch(
            context = applicationContext,
            prefs = prefs,
            targetUserId = targetUserId,
            onDone = {},
            onError = {}
        )
    }

    private fun updateTileState() {
        val enabled = prefs.getBoolean(SettingsActivity.KEY_QUICK_TILE_ENABLED, false)
        qsTile?.apply {
            state = if (enabled) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }
}
