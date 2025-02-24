// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.utils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.dao.BackupDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;

public class AppDb {
    public static final String TAG = AppDb.class.getSimpleName();

    private static final Object sLock = new Object();

    private final AppDao appDao;
    private final BackupDao backupDao;

    public AppDb() {
        appDao = AppsDb.getInstance().appDao();
        backupDao = AppsDb.getInstance().backupDao();
    }

    public List<App> getAllApplications() {
        synchronized (sLock) {
            return appDao.getAll();
        }
    }

    public List<App> getAllInstalledApplications() {
        synchronized (sLock) {
            return appDao.getAllInstalled();
        }
    }

    public List<App> getAllApplications(String packageName) {
        synchronized (sLock) {
            return appDao.getAll(packageName);
        }
    }

    public List<Backup> getAllBackups() {
        synchronized (sLock) {
            return backupDao.getAll();
        }
    }

    public List<Backup> getAllBackups(String packageName) {
        synchronized (sLock) {
            return backupDao.get(packageName);
        }
    }

    /**
     * Fetch backups without a lock file. Necessary checks must be done to ensure that the backups actually exist.
     */
    public List<Backup> getAllBackupsNoLock(String packageName) {
        return backupDao.get(packageName);
    }

    public void insert(App app) {
        synchronized (sLock) {
            appDao.insert(app);
        }
    }

    public void insert(Backup backup) {
        synchronized (sLock) {
            backupDao.insert(backup);
        }
    }

    public void insertBackups(List<Backup> backups) {
        synchronized (sLock) {
            backupDao.insert(backups);
        }
    }

    public void deleteApplication(String packageName, int userId) {
        synchronized (sLock) {
            appDao.delete(packageName, userId);
        }
    }

    public void deleteAllApplications() {
        synchronized (sLock) {
            appDao.deleteAll();
        }
    }

    public void deleteAllBackups() {
        synchronized (sLock) {
            backupDao.deleteAll();
        }
    }

    public void deleteBackup(Backup backup) {
        synchronized (sLock) {
            backupDao.delete(backup);
        }
    }

    @WorkerThread
    public void loadInstalledOrBackedUpApplications(@NonNull Context context) {
        getBackups(true);
        updateApplications(context);
    }

    @WorkerThread
    public List<App> updateApplications(@NonNull Context context, @NonNull String[] packageNames) {
        synchronized (sLock) {
            List<App> appList = new ArrayList<>();
            for (String packageName : packageNames) {
                appList.addAll(updateApplicationInternal(context, packageName));
            }
            // Update usage and others
            updateVariableData(context, appList);
            appDao.insert(appList);
            return appList;
        }
    }

    @WorkerThread
    public List<App> updateApplication(@NonNull Context context, @NonNull String packageName) {
        synchronized (sLock) {
            List<App> appList = updateApplicationInternal(context, packageName);
            // Update usage and others
            updateVariableData(context, appList);
            appDao.insert(appList);
            return appList;
        }
    }

    @WorkerThread
    @NonNull
    private List<App> updateApplicationInternal(@NonNull Context context, @NonNull String packageName) {
        int[] userIds = Users.getUsersIds();
        List<App> oldApps = new ArrayList<>(appDao.getAll(packageName));
        List<App> appList = new ArrayList<>(userIds.length);
        List<Backup> backups = new ArrayList<>(backupDao.get(packageName));
        for (int userId : userIds) {
            int oldAppIndex = findIndexOfApp(oldApps, packageName, userId);
            PackageInfo packageInfo = null;
            Backup backup = null;
            ListIterator<Backup> backupListIterator = backups.listIterator();
            while (backupListIterator.hasNext()) {
                Backup b = backupListIterator.next();
                if (b.userId == userId) {
                    backup = b;
                    backupListIterator.remove();
                    break;
                }
            }
            try {
                packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                        PackageManager.GET_META_DATA | flagSigningInfo | PackageManager.GET_ACTIVITIES
                                | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                                | PackageManager.GET_SERVICES | flagDisabledComponents | flagMatchUninstalled, userId);
            } catch (RemoteException | PackageManager.NameNotFoundException | SecurityException e) {
                // Package does not exist
            }
            if (backup == null && packageInfo == null) {
                // Neither backup nor package exist
                if (oldAppIndex >= 0) {
                    // Delete existing backup
                    appDao.delete(oldApps.get(oldAppIndex));
                }
                continue;
            }
            if (oldAppIndex >= 0) {
                // There's already existing app
                App oldApp = oldApps.get(oldAppIndex);
                appDao.delete(oldApp);
                if ((packageInfo != null && isUpToDate(oldApp, packageInfo))
                        || (backup != null && isUpToDate(oldApp, backup))) {
                    // Up-to-date app
                    appList.add(oldApp);
                    oldApp.lastActionTime = System.currentTimeMillis();
                    continue;
                }
            }
            // New app
            App app = packageInfo != null ? App.fromPackageInfo(context, packageInfo) : App.fromBackup(backup);
            appList.add(app);
        }

        // Add the rest of the backups if any
        for (Backup backup : backups) {
            appList.add(App.fromBackup(backup));
        }

        // Return the list instead of triggering broadcast
        return appList;
    }

    @WorkerThread
    public void updateApplications(@NonNull Context context) {
        synchronized (sLock) {
            Map<String, Backup> backups = getBackups(false);
            List<App> oldApps = new ArrayList<>(appDao.getAll());
            List<App> modifiedApps = new ArrayList<>();
            Set<String> newApps = new HashSet<>();
            Set<String> updatedApps = new HashSet<>();

            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;

            for (int userId : Users.getUsersIds()) {
                // Interrupt thread on request
                if (Thread.currentThread().isInterrupted()) return;

                List<PackageInfo> packageInfoList;
                try {
                    packageInfoList = PackageManagerCompat.getInstalledPackages(flagSigningInfo
                            | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                            | PackageManager.GET_SERVICES | flagDisabledComponents | flagMatchUninstalled, userId);
                } catch (Exception e) {
                    Log.e(TAG, "Could not retrieve package info list for user " + userId, e);
                    continue;
                }

                for (PackageInfo packageInfo : packageInfoList) {
                    // Interrupt thread on request
                    if (Thread.currentThread().isInterrupted()) return;

                    int oldAppIndex = findIndexOfApp(oldApps, packageInfo.packageName, UserHandleHidden.getUserId(packageInfo.applicationInfo.uid));
                    if (oldAppIndex >= 0) {
                        // There's already existing app
                        App oldApp = oldApps.remove(oldAppIndex);
                        if (isUpToDate(oldApp, packageInfo)) {
                            // Up-to-date app
                            updatedApps.add(oldApp.packageName);
                            modifiedApps.add(oldApp);
                            backups.remove(packageInfo.packageName);
                            oldApp.lastActionTime = System.currentTimeMillis();
                            continue;
                        }
                    }
                    // New app
                    App app = App.fromPackageInfo(context, packageInfo);
                    backups.remove(packageInfo.packageName);
                    newApps.add(app.packageName);
                    modifiedApps.add(app);
                }
            }

            // Update usage and others
            updateVariableData(context, modifiedApps);

            // Add rest of the backup items, i.e., items that aren't installed
            for (Backup backup : backups.values()) {
                if (backup == null) continue;
                // Interrupt thread on request
                if (Thread.currentThread().isInterrupted()) return;

                int oldAppIndex = findIndexOfApp(oldApps, backup.packageName, backup.userId);
                if (oldAppIndex >= 0) {
                    // There's already existing app
                    App oldApp = oldApps.remove(oldAppIndex);
                    if (isUpToDate(oldApp, backup)) {
                        // Up-to-date app
                        updatedApps.add(oldApp.packageName);
                        modifiedApps.add(oldApp);
                        continue;
                    }
                }
                // New app
                App app = App.fromBackup(backup);
                newApps.add(app.packageName);
                modifiedApps.add(app);
            }
            // Add new data
            appDao.delete(oldApps);
            appDao.insert(modifiedApps);
            if (oldApps.size() > 0) {
                // Delete broadcast
                Intent intent = new Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(oldApps));
                context.sendBroadcast(intent);
            }
            if (newApps.size() > 0) {
                // New apps
                Intent intent = new Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, newApps.toArray(new String[0]));
                context.sendBroadcast(intent);
            }
            if (updatedApps.size() > 0) {
                // Altered apps
                Intent intent = new Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, updatedApps.toArray(new String[0]));
                context.sendBroadcast(intent);
            }
        }
    }

    @WorkerThread
    @NonNull
    public Map<String, Backup> getBackups(boolean loadBackups) {
        if (loadBackups) {
            // Very long operation
            return BackupUtils.storeAllAndGetLatestBackupMetadata();
        } else {
            return BackupUtils.getAllLatestBackupMetadataFromDb();
        }
    }

    private static void updateVariableData(@NonNull Context context, @NonNull List<App> modifiedApps) {
        UriManager uriManager = new UriManager();
        ArrayMap<Integer, SsaidSettings> userIdSsaidSettingsMap = new ArrayMap<>();
        List<PackageUsageInfo> packageUsageInfoList = new ArrayList<>();
        for (int userId : Users.getUsersIds()) {
            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;
            try {
                packageUsageInfoList.addAll(AppUsageStatsManager.getInstance(context)
                        .getUsageStats(UsageUtils.USAGE_WEEKLY, userId));
            } catch (RemoteException | SecurityException e) {
                e.printStackTrace();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    userIdSsaidSettingsMap.put(userId, new SsaidSettings(userId));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        for (App app : modifiedApps) {
            boolean isSystemApp = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!app.isInstalled && !isSystemApp) {
                continue;
            }
            int userId = app.userId;
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(app.packageName, userId, false)) {
                app.rulesCount = cb.entryCount();
            }
            PackageSizeInfo sizeInfo = PackageUtils.getPackageSizeInfo(context, app.packageName, userId, null);
            if (sizeInfo != null) {
                app.codeSize = sizeInfo.codeSize + sizeInfo.obbSize;
                app.dataSize = sizeInfo.dataSize + sizeInfo.mediaSize + sizeInfo.cacheSize;
            } else {
                app.codeSize = app.dataSize = 0;
            }
            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;
            if (!app.isInstalled) {
                continue;
            }
            app.hasKeystore = KeyStoreUtils.hasKeyStore(app.uid);
            app.usesSaf = uriManager.getGrantedUris(app.packageName) != null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SsaidSettings ssaidSettings = userIdSsaidSettingsMap.get(userId);
                if (ssaidSettings != null) {
                    String ssaid = ssaidSettings.getSsaid(app.packageName, app.uid);
                    app.ssaid = TextUtilsCompat.isEmpty(ssaid) ? null : ssaid;
                } else {
                    app.ssaid = null;
                }
            }
            PackageUsageInfo usageInfo = findUsage(packageUsageInfoList, app.packageName, userId);
            if (usageInfo != null) {
                app.mobileDataUsage = usageInfo.mobileData != null ? usageInfo.mobileData.getTotal() : 0;
                app.wifiDataUsage = usageInfo.wifiData != null ? usageInfo.wifiData.getTotal() : 0;
                app.openCount = usageInfo.timesOpened;
                app.screenTime = usageInfo.screenTime;
                app.lastUsageTime = usageInfo.lastUsageTime;
            } else {
                app.mobileDataUsage = app.wifiDataUsage = app.screenTime = app.lastUsageTime = 0;
                app.openCount = 0;
            }
        }
    }

    private static int findIndexOfApp(@NonNull List<App> appList, @NonNull String packageName, @UserIdInt int userId) {
        for (int i = 0; i < appList.size(); ++i) {
            App app = appList.get(i);
            if (app.userId == userId && app.packageName.equals(packageName)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static PackageUsageInfo findUsage(@NonNull List<PackageUsageInfo> usageInfoList, @NonNull String packageName, @UserIdInt int userId) {
        for (PackageUsageInfo usageInfo : usageInfoList) {
            if (usageInfo.userId == userId && usageInfo.packageName.equals(packageName)) {
                return usageInfo;
            }
        }
        return null;
    }

    private static boolean isUpToDate(@NonNull App currentApp, @NonNull PackageInfo installedPackageInfo) {
        if (!currentApp.isInstalled) {
            // The app was not installed earlier
            return false;
        }
        // App was installed
        return currentApp.lastUpdateTime == installedPackageInfo.lastUpdateTime
                && currentApp.flags == installedPackageInfo.applicationInfo.flags;
    }

    private static boolean isUpToDate(@NonNull App currentApp, @NonNull Backup backup) {
        if (currentApp.isInstalled) {
            // The app was installed earlier
            return false;
        }
        // App was not installed
        if (currentApp.sdk != 0) {
            // The app is a system app
            return true;
        }
        // The app is a backed up app
        return currentApp.lastUpdateTime == backup.backupTime;
    }

    @NonNull
    private static String[] getPackageNamesFromApps(@NonNull List<App> apps) {
        HashSet<String> packages = new HashSet<>(apps.size());
        for (App app : apps) {
            packages.add(app.packageName);
        }
        return packages.toArray(new String[0]);
    }
}
