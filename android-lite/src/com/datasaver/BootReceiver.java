package com.datasaver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.net.conn.CONNECTIVITY_CHANGE".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // Restart DataSaverService if it was running before
            android.content.SharedPreferences sp = context.getSharedPreferences("datasaver", Context.MODE_PRIVATE);
            if (sp.getBoolean("vpn_should_run", false)) {
                try {
                    Intent si = new Intent(context, DataSaverService.class);
                    si.setAction(DataSaverService.ACTION_START);
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(si);
                    } else {
                        context.startService(si);
                    }
                } catch (Exception e) {}
                // Note: VPN cannot auto-start after reboot without user interaction
                // DataSaverService will start and show notification prompting user to reconnect
            }
        }
    }
}
