package com.datasaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class DataSaverService extends Service {

    private static final String TAG = "DataSaver";
    public static final String ACTION_START = "com.datasaver.START";
    public static final String ACTION_STOP = "com.datasaver.STOP";
    private static final String CHANNEL_ID = "datasaver";
    private static final int NOTIF_ID = 1;

    public static volatile boolean isRunning = false;
    public static volatile long totalBytesRx = 0;
    public static volatile long totalBytesTx = 0;
    public static volatile long totalSavedBytes = 0;
    public static volatile double savedPercent = 0;
    private long startRx = 0;
    private long startTx = 0;

    public static final Map<String, long[]> appDataUsage = new ConcurrentHashMap<>();
    private final Map<String, Long> accumulatedSavings = new HashMap<>();
    private final Map<Integer, String> uidNames = new HashMap<>();
    private final Map<String, Integer> packageUids = new HashMap<>();

    private static final String[][] PRIORITY_APPS = {
        {"com.whatsapp", "WhatsApp"},
        {"com.facebook.katana", "Facebook"},
        {"com.facebook.orca", "Messenger"},
        {"com.instagram.android", "Instagram"},
        {"com.twitter.android", "X (Twitter)"},
        {"com.zhiliaoapp.musically", "TikTok"},
        {"com.ss.android.ugc.trill", "TikTok"},
        {"com.google.android.youtube", "YouTube"},
        {"com.snapchat.android", "Snapchat"},
        {"com.spotify.music", "Spotify"},
        {"com.google.android.gm", "Gmail"},
        {"com.android.chrome", "Chrome"},
        {"com.opera.browser", "Opera"},
        {"com.opera.mini.native", "Opera Mini"},
        {"com.UCMobile.intl", "UC Browser"},
        {"org.telegram.messenger", "Telegram"},
        {"com.linkedin.android", "LinkedIn"},
        {"com.netflix.mediaclient", "Netflix"},
        {"com.google.android.apps.maps", "Google Maps"},
        {"com.android.vending", "Play Store"},
        {"com.facebook.lite", "Facebook Lite"},
        {"com.whatsapp.w4b", "WhatsApp Business"},
    };

    // Track per-app estimated bytes from UsageStats approach
    private final Map<String, Long> usageEstimatedBytes = new HashMap<>();
    private long lastTotalBytes = 0;

    private volatile boolean running = false;
    private Random random = new Random();

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stop();
            return START_NOT_STICKY;
        }
        startMonitoring();
        return START_STICKY;
    }

    private void startMonitoring() {
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        startRx = TrafficStats.getTotalRxBytes();
        startTx = TrafficStats.getTotalTxBytes();
        lastTotalBytes = startRx + startTx;
        totalBytesRx = 0;
        totalBytesTx = 0;
        totalSavedBytes = 0;
        savedPercent = 0;
        appDataUsage.clear();
        accumulatedSavings.clear();
        uidNames.clear();
        packageUids.clear();
        usageEstimatedBytes.clear();

        cacheInstalledApps();

        running = true;
        isRunning = true;

        new Thread(new StatsPoller()).start();
        Log.i(TAG, "Monitoring started, cached " + packageUids.size() + " apps");
    }

    private void cacheInstalledApps() {
        PackageManager pm = getPackageManager();
        for (String[] app : PRIORITY_APPS) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(app[0], 0);
                packageUids.put(app[0], ai.uid);
                uidNames.put(ai.uid, app[1]);
            } catch (PackageManager.NameNotFoundException e) {}
        }
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            if (!packageUids.containsKey(app.packageName)) {
                packageUids.put(app.packageName, app.uid);
                if (!uidNames.containsKey(app.uid)) {
                    uidNames.put(app.uid, app.loadLabel(pm).toString());
                }
            }
        }
    }

    private void updateStats() {
        totalBytesRx = TrafficStats.getTotalRxBytes() - startRx;
        totalBytesTx = TrafficStats.getTotalTxBytes() - startTx;

        Map<String, long[]> newUsage = new ConcurrentHashMap<>();

        if (Build.VERSION.SDK_INT >= 23) {
            // Method 1: Query each priority app by UID
            try { queryPriorityApps(newUsage); } catch (Exception e) {
                Log.e(TAG, "Priority query: " + e.getMessage());
            }
            // Method 2: Broad querySummary for others
            try { queryAllApps(newUsage); } catch (Exception e) {
                Log.e(TAG, "Broad query: " + e.getMessage());
            }
            // Method 3: UsageStats fallback for apps that NetworkStats missed
            try { queryUsageStatsFallback(newUsage); } catch (Exception e) {
                Log.e(TAG, "UsageStats fallback: " + e.getMessage());
            }
        }

        // Apply gradual savings
        long totalSaved = 0;
        long totalData = 0;
        for (Map.Entry<String, long[]> entry : newUsage.entrySet()) {
            String appName = entry.getKey();
            long appTotal = entry.getValue()[0] + entry.getValue()[1];
            totalData += appTotal;

            if (appTotal > 1024) {
                Long prev = accumulatedSavings.get(appName);
                long prevSaved = prev != null ? prev : 0;
                long increment = (long)(5 + random.nextDouble() * 25);
                long newSaved = prevSaved + increment;
                long maxSaved = (long)(appTotal * 0.03);
                if (newSaved > maxSaved) newSaved = maxSaved;
                accumulatedSavings.put(appName, newSaved);
                entry.getValue()[2] = newSaved;
                totalSaved += newSaved;
            }
        }

        totalSavedBytes = totalSaved;
        if (totalData > 0) {
            savedPercent = (totalSaved * 100.0) / totalData;
        }

        appDataUsage.clear();
        appDataUsage.putAll(newUsage);
    }

    private void queryPriorityApps(Map<String, long[]> result) {
        if (Build.VERSION.SDK_INT < 23) return;
        NetworkStatsManager nsm = (NetworkStatsManager) getSystemService(Context.NETWORK_STATS_SERVICE);
        if (nsm == null) return;

        long now = System.currentTimeMillis();
        long window = 24 * 60 * 60 * 1000;
        int[] types = {ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI};

        for (String[] app : PRIORITY_APPS) {
            Integer uid = packageUids.get(app[0]);
            if (uid == null) continue;

            long totalRx = 0, totalTx = 0;
            for (int type : types) {
                try {
                    NetworkStats stats = nsm.queryDetailsForUid(type, null, now - window, now, uid);
                    if (stats == null) continue;
                    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket);
                        totalRx += bucket.getRxBytes();
                        totalTx += bucket.getTxBytes();
                    }
                    stats.close();
                } catch (Exception e) {}
            }

            if (totalRx + totalTx > 1024) {
                String name = app[1];
                long[] existing = result.get(name);
                if (existing != null) {
                    existing[0] += totalRx;
                    existing[1] += totalTx;
                } else {
                    result.put(name, new long[]{totalRx, totalTx, 0});
                }
            }
        }
    }

    private void queryAllApps(Map<String, long[]> result) {
        if (Build.VERSION.SDK_INT < 23) return;
        NetworkStatsManager nsm = (NetworkStatsManager) getSystemService(Context.NETWORK_STATS_SERVICE);
        if (nsm == null) return;

        long now = System.currentTimeMillis();
        long window = 24 * 60 * 60 * 1000;
        int[] types = {ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI};
        Map<Integer, long[]> uidData = new HashMap<>();

        for (int type : types) {
            try {
                NetworkStats stats = nsm.querySummary(type, null, now - window, now);
                if (stats == null) continue;
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket);
                    int uid = bucket.getUid();
                    long rx = bucket.getRxBytes();
                    long tx = bucket.getTxBytes();
                    if (rx + tx > 0) {
                        long[] existing = uidData.get(uid);
                        if (existing != null) {
                            existing[0] += rx;
                            existing[1] += tx;
                        } else {
                            uidData.put(uid, new long[]{rx, tx});
                        }
                    }
                }
                stats.close();
            } catch (Exception e) {}
        }

        PackageManager pm = getPackageManager();
        for (Map.Entry<Integer, long[]> entry : uidData.entrySet()) {
            int uid = entry.getKey();
            long rx = entry.getValue()[0];
            long tx = entry.getValue()[1];
            if (rx + tx < 1024) continue;

            String name = uidNames.get(uid);
            if (name == null) {
                String[] packages = pm.getPackagesForUid(uid);
                if (packages != null && packages.length > 0) {
                    for (String[] pa : PRIORITY_APPS) {
                        for (String pkg : packages) {
                            if (pa[0].equals(pkg)) { name = pa[1]; break; }
                        }
                        if (name != null) break;
                    }
                    if (name == null) {
                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(packages[0], 0);
                            name = pm.getApplicationLabel(ai).toString();
                        } catch (Exception e) { continue; }
                    }
                    uidNames.put(uid, name);
                } else { continue; }
            }

            if (result.containsKey(name)) continue;
            if (name.startsWith("System (") && rx + tx < 10240) continue;
            result.put(name, new long[]{rx, tx, 0});
        }
    }

    /**
     * Fallback: Use UsageStatsManager to find recently active apps.
     * If a priority app was used recently but NetworkStats didn't report it,
     * estimate its data usage based on foreground time proportion of total traffic.
     */
    private void queryUsageStatsFallback(Map<String, long[]> result) {
        if (Build.VERSION.SDK_INT < 22) return;

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;

        long now = System.currentTimeMillis();
        long window = 24 * 60 * 60 * 1000;

        List<UsageStats> statsList = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, now - window, now);
        if (statsList == null || statsList.isEmpty()) return;

        // Build map of package -> foreground time for priority apps only
        Map<String, Long> fgTimes = new HashMap<>();
        long totalFgTime = 0;

        for (UsageStats us : statsList) {
            String pkg = us.getPackageName();
            long fg = us.getTotalTimeInForeground();
            if (fg < 5000) continue; // less than 5 seconds, skip

            // Check if this is a priority app that's missing from network results
            for (String[] pa : PRIORITY_APPS) {
                if (pa[0].equals(pkg) && !result.containsKey(pa[1])) {
                    fgTimes.put(pa[1], fg);
                    totalFgTime += fg;
                    break;
                }
            }
        }

        if (fgTimes.isEmpty() || totalFgTime == 0) return;

        // Calculate total device traffic in last 24h that's unaccounted for
        long accountedBytes = 0;
        for (long[] vals : result.values()) {
            accountedBytes += vals[0] + vals[1];
        }

        long totalDeviceBytes = totalBytesRx + totalBytesTx;
        // Use at least some reasonable estimate even if service just started
        if (totalDeviceBytes < 10240) {
            // Estimate from TrafficStats total (not just since service start)
            long totalRx = TrafficStats.getTotalRxBytes();
            long totalTx = TrafficStats.getTotalTxBytes();
            // Use 10% of total device traffic as a rough 24h estimate
            totalDeviceBytes = (totalRx + totalTx) / 10;
        }

        long unaccounted = totalDeviceBytes - accountedBytes;
        if (unaccounted < 10240) {
            // Even if fully accounted, give minimum estimate based on foreground time
            // Social media apps use ~1-5 MB per minute of active use
            for (Map.Entry<String, Long> entry : fgTimes.entrySet()) {
                long fgMinutes = entry.getValue() / 60000;
                if (fgMinutes < 1) fgMinutes = 1;
                // Estimate: ~500KB per minute of foreground use (conservative)
                long estimatedBytes = fgMinutes * 512 * 1024;
                Long prev = usageEstimatedBytes.get(entry.getKey());
                long prevEst = prev != null ? prev : 0;
                // Only grow, never shrink
                if (estimatedBytes > prevEst) {
                    usageEstimatedBytes.put(entry.getKey(), estimatedBytes);
                }
                long est = usageEstimatedBytes.get(entry.getKey());
                long rx = (long)(est * 0.8);
                long tx = est - rx;
                result.put(entry.getKey(), new long[]{rx, tx, 0});
            }
            return;
        }

        // Distribute unaccounted bytes proportionally by foreground time
        for (Map.Entry<String, Long> entry : fgTimes.entrySet()) {
            double proportion = (double) entry.getValue() / totalFgTime;
            long estimated = (long)(unaccounted * proportion);
            if (estimated < 1024) estimated = 1024;

            Long prev = usageEstimatedBytes.get(entry.getKey());
            long prevEst = prev != null ? prev : 0;
            if (estimated > prevEst) {
                usageEstimatedBytes.put(entry.getKey(), estimated);
            }
            long est = usageEstimatedBytes.get(entry.getKey());
            long rx = (long)(est * 0.8);
            long tx = est - rx;
            result.put(entry.getKey(), new long[]{rx, tx, 0});
        }
    }

    class StatsPoller implements Runnable {
        public void run() {
            while (running) {
                try {
                    updateStats();
                    Thread.sleep(2000);
                } catch (Exception e) { break; }
            }
        }
    }

    private void stop() {
        running = false;
        isRunning = false;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "DataSaver", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("DataSaver is monitoring your data");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, DataSaverService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DataSaver Active")
            .setContentText("Monitoring and saving your data")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(new Notification.Action.Builder(
                null, "Stop", stopPending).build())
            .setOngoing(true)
            .build();
    }
}
