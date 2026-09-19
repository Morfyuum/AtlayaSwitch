package com.capecter.atlayaswitch

/**
 * Rückgabecodes von IUserService.disableWirelessDebugging().
 *
 * Hintergrund: Drahtloses Debugging (adb_wifi_enabled) wird nur gebraucht, um Shizuku
 * einmalig nach jedem Geräte-Neustart zu starten - danach ist es eine reine Netzwerk-
 * Angriffsfläche. Ausschalten darf man es aber nur, solange USB-Debugging (adb_enabled)
 * an bleibt: Android hält adbd am Laufen, solange EINES von beiden an ist, und beendet
 * Shizuku, sobald adbd komplett stoppt (am Gerät gemessen: shizuku_server war nach
 * "USB- UND Drahtloses Debugging aus" weg, mit "nur Drahtlos aus" lief er weiter).
 */
object AdbHardening {
    /** Drahtloses Debugging war schon aus - nichts zu tun. */
    const val WIFI_ALREADY_OFF = 0

    /** Drahtloses Debugging war an und wurde ausgeschaltet; Shizuku läuft weiter. */
    const val WIFI_TURNED_OFF = 1

    /** Drahtloses Debugging ist an, wurde aber NICHT ausgeschaltet, weil USB-Debugging aus
     * ist - sonst würde adbd komplett stoppen und Shizuku mit beenden. */
    const val KEPT_USB_DEBUGGING_OFF = 2

    /** Der Schalter ließ sich nicht umlegen. */
    const val FAILED = 3
}
