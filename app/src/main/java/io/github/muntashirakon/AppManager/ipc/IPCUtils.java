package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.os.RemoteException;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;

public final class IPCUtils {
    private static final String TAG = "IPCUtils";

    private static final ComponentName COMPONENT_NAME = new ComponentName(AppManager.getContext(), AMService.class);
    private static final AMServiceConnectionWrapper connectionWrapper = new AMServiceConnectionWrapper();
    private static IAMService amService;

    @WorkerThread
    @NonNull
    public static IAMService getAmService() throws RemoteException {
        synchronized (connectionWrapper) {
            return amService = connectionWrapper.getAmService();
        }
    }

    @NonNull
    public static AMServiceConnectionWrapper getNewConnection() {
        return new AMServiceConnectionWrapper();
    }

    @AnyThread
    @Nullable
    public static IAMService getService() {
        return amService;
    }

    @AnyThread
    @NonNull
    public static IAMService getServiceSafe() throws RemoteException {
        if (amService == null || !amService.asBinder().pingBinder()) {
            throw new RemoteException("AMService not running.");
        }
        return amService;
    }

    @WorkerThread
    public static void stopDaemon(@NonNull Context context) {
        Intent intent = new Intent(context, AMService.class);
        // Use stop here instead of unbind because AIDLService is running as a daemon.
        // To verify whether the daemon actually works, kill the app and try testing the
        // daemon again. You should get the same PID as last time (as it was re-using the
        // previous daemon process), and in AIDLService, onRebind should be called.
        // Note: re-running the app in Android Studio is not the same as kill + relaunch.
        // The root service will kill itself when it detects the client APK has updated.
        RootService.stop(intent);
        amService = null;
    }

    public static class AMServiceConnectionWrapper {
        private final AMServiceConnection conn = new AMServiceConnection();
        private IAMService amService;
        private CountDownLatch amServiceBoundWatcher;

        private class AMServiceConnection implements ServiceConnection {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.e(TAG, "service onServiceConnected");
                amService = IAMService.Stub.asInterface(service);
                if (amServiceBoundWatcher != null) {
                    // Should never be null
                    amServiceBoundWatcher.countDown();
                } else throw new RuntimeException("AMService watcher should never be null!");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.e(TAG, "service onServiceDisconnected");
                amService = null;
            }

            @Override
            public void onBindingDied(ComponentName name) {
                amService = null;
            }

            @Override
            public void onNullBinding(ComponentName name) {
                amService = null;
            }
        }

        private AMServiceConnectionWrapper() {
        }

        @WorkerThread
        private void startDaemon() {
            if (amService == null) {
                if (amServiceBoundWatcher == null || amServiceBoundWatcher.getCount() == 0) {
                    amServiceBoundWatcher = new CountDownLatch(1);
                    Log.e(TAG, "Launching service...");
                    Intent intent = new Intent();
                    intent.setComponent(COMPONENT_NAME);
                    synchronized (conn) {
                        RootService.bind(intent, conn);
                    }
                }
                // Wait for service to be bound
                try {
                    amServiceBoundWatcher.await(45, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "AMService watcher interrupted.");
                }
            }
        }

        @WorkerThread
        @NonNull
        public IAMService getAmService() throws RemoteException {
            synchronized (conn) {
                if (amService == null && AppPref.isRootOrAdbEnabled()) {
                    startDaemon();
                }
            }
            return getServiceSafe();
        }

        @AnyThread
        @Nullable
        public IAMService getService() {
            return amService;
        }

        @AnyThread
        @NonNull
        public IAMService getServiceSafe() throws RemoteException {
            if (amService == null || !amService.asBinder().pingBinder()) {
                throw new RemoteException("AMService not running.");
            }
            return amService;
        }

        public void unbindService() {
            synchronized (conn) {
                RootService.unbind(conn);
            }
        }
    }
}
