package com.capecter.atlayaswitch;

interface IUserService {
    String listUsersRaw();
    void switchUser(int userId);
    boolean switchUserAndEndSession(int targetUserId, int sourceUserId);
    boolean isPackageInstalledForUser(int userId, String pkg);
    boolean uninstallForUser(int userId, String pkg);
}
