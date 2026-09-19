package com.capecter.atlayaswitch;

interface IUserService {
    String listUsersRaw();
    void switchUser(int userId, boolean hardenAdb);
    boolean switchUserAndEndSession(int targetUserId, int sourceUserId, boolean hardenAdb);
    boolean isPackageInstalledForUser(int userId, String pkg);
    boolean uninstallForUser(int userId, String pkg);
    // Rückgabe: Konstante aus AdbHardening (WIFI_ALREADY_OFF, WIFI_TURNED_OFF, ...)
    int disableWirelessDebugging();
}
