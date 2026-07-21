package com.datasaver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Foreground service for background notification polling.
 * More reliable than AlarmManager on modern Android.
 */
public class NotificationPollService extends Service {
    private static final String CHANNEL_ID = "datasaver_poll_service";
    private static final int NOTIF_ID = 1001;
    private static final String SERVER_URL = "https://datasaver-server.onrender.com";
    
    private Handler handler;
    private Runnable pollRunnable;
    private boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            return START_STICKY;
        }
        
        isRunning = true;
        
        // Start foreground service with persistent notification
        startForeground(NOTIF_ID, createNotification("DataSaver", "Protecting your data in background"));
        
        // Start polling
        startPolling();
        
        return START_STICKY;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DataSaver Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps DataSaver running in background to check for notifications");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String title, String body) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(title);
        builder.setContentText(body);
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setColor(0xFF2196F3);
        builder.setContentIntent(pendingIntent);
        builder.setOngoing(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }
        
        return builder.build();
    }
    
    private void startPolling() {
        pollRunnable = new Runnable() {
            public void run() {
                checkForNotifications();
                // Poll every 15 seconds for faster admin push delivery
                handler.postDelayed(this, 15000);
            }
        };
        // Poll immediately on start
        handler.post(pollRunnable);
    }
    
    private void checkForNotifications() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    SharedPreferences prefs = getSharedPreferences("datasaver", MODE_PRIVATE);
                    String phone = prefs.getString("phone", "");
                    if (phone.isEmpty()) return;
                    
                    long lastId = prefs.getLong("last_notif_id", 0);
                    String encodedPhone = java.net.URLEncoder.encode(phone, "UTF-8");
                    
                    URL url = new URL(SERVER_URL + "/api/notifications?phone=" + encodedPhone + "&since_id=" + lastId);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    
                    if (conn.getResponseCode() != 200) return;
                    
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    
                    JSONArray arr = new JSONArray(sb.toString());
                    if (arr.length() > 0) {
                        long newLastId = lastId;
                        String latestTitle = "";
                        String latestBody = "";
                        
                        for (int i = 0; i < arr.length(); i++) {
                            long nid = arr.getJSONObject(i).optLong("id", 0);
                            if (nid > newLastId) newLastId = nid;
                            if (i == arr.length() - 1) {
                                latestTitle = arr.getJSONObject(i).optString("title", "DataSaver");
                                latestBody = arr.getJSONObject(i).optString("body", "");
                            }
                        }
                        
                        prefs.edit().putLong("last_notif_id", newLastId).apply();
                        
                        // Show system notification (respect the user's Push Notifications toggle)
                        if (!latestBody.isEmpty() && prefs.getBoolean("push_notif", true)) {
                            showPushNotification(latestTitle, latestBody);
                        }
                    }
                } catch (Exception e) {
                    // Silent fail - will retry
                }
            }
        }).start();
    }
    
    private void showPushNotification(String title, String body) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            // Create channel first for Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    "datasaver_push",
                    "DataSaver Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Important notifications");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 500, 200, 500});
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
            
            Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF2196F3)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId("datasaver_push");
            } else {
                builder.setPriority(Notification.PRIORITY_HIGH);
                builder.setVibrate(new long[]{0, 500, 200, 500});
            }
            
            if (manager != null) {
                // Use a fixed ID that's different from service notification
                manager.notify(2001, builder.build());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    @Override
    public void onDestroy() {
        isRunning = false;
        if (handler != null && pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
        }
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    // Start the service
    public static void start(Context context) {
        Intent intent = new Intent(context, NotificationPollService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    // Stop the service
    public static void stop(Context context) {
        Intent intent = new Intent(context, NotificationPollService.class);
        context.stopService(intent);
    }
}
