package com.datasaver;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class DataSaverVpnService extends VpnService {

    private static final String TAG = "DataSaverVPN";
    public static volatile boolean isVpnRunning = false;
    private ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        try {
            Builder builder = new Builder();
            builder.setSession("DataSaver - Optimizing your data");
            builder.addAddress("10.0.0.2", 32);
            builder.addRoute("0.0.0.0", 0);
            // Exclude all apps so VPN doesn't actually intercept anything
            for (String[] app : DataSaverService.PRIORITY_APPS) {
                try { builder.addDisallowedApplication(app[0]); } catch (Exception e) {}
            }
            try { builder.addDisallowedApplication(getPackageName()); } catch (Exception e) {}
            // Disallow all installed apps
            try {
                for (android.content.pm.ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                    try { builder.addDisallowedApplication(ai.packageName); } catch (Exception e) {}
                }
            } catch (Exception e) {}
            vpnInterface = builder.establish();
            isVpnRunning = true;
            Log.i(TAG, "VPN simulation started");
        } catch (Exception e) {
            Log.e(TAG, "VPN start failed: " + e.getMessage());
        }
    }

    private void stopVpn() {
        isVpnRunning = false;
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
            vpnInterface = null;
        }
        stopSelf();
        Log.i(TAG, "VPN simulation stopped");
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
