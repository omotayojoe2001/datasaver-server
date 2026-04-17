package com.datasaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DataSaverVpnService extends VpnService {

    private static final String TAG = "DataSaverVPN";
    private static final String CHANNEL_ID = "datasaver_vpn";
    private static final int NOTIF_ID = 2;

    public static volatile boolean isVpnRunning = false;

    // Real savings counters
    public static final AtomicLong blockedAdBytes = new AtomicLong(0);
    public static final AtomicLong blockedBgBytes = new AtomicLong(0);
    public static final AtomicLong blockedAdRequests = new AtomicLong(0);
    public static final AtomicLong blockedBgSyncs = new AtomicLong(0);
    public static final AtomicLong totalPacketsProcessed = new AtomicLong(0);

    // Background app DNS tracking: domain -> estimated bytes that would load
    private static final ConcurrentHashMap<String, Long> bgBlockedDomains = new ConcurrentHashMap<>();

    private ParcelFileDescriptor vpnInterface;
    private volatile boolean running = false;
    private Set<String> adDomains = new HashSet<>();
    private volatile String foregroundPackage = "";
    private PowerManager.WakeLock wakeLock;

    // System packages that should never be blocked (even in background)
    private final Set<String> systemWhitelist = new HashSet<>();

    // Known heavy background domains and their estimated data per request
    private static final String[][] BG_HEAVY_DOMAINS = {
        {"graph.facebook.com", "204800"},      // 200KB - Facebook sync
        {"api.facebook.com", "102400"},         // 100KB
        {"mqtt-mini.facebook.com", "51200"},    // 50KB - Facebook push
        {"edge-mqtt.facebook.com", "51200"},    // 50KB
        {"api.instagram.com", "204800"},        // 200KB - Instagram sync
        {"i.instagram.com", "512000"},          // 500KB - Instagram prefetch images
        {"scontent.cdninstagram.com", "1048576"}, // 1MB - Instagram content prefetch
        {"api2.musical.ly", "102400"},          // 100KB - TikTok sync
        {"log.tiktokv.com", "20480"},           // 20KB - TikTok analytics
        {"api.twitter.com", "102400"},          // 100KB
        {"mobile.twitter.com", "204800"},       // 200KB
        {"web.whatsapp.com", "51200"},          // 50KB
        {"pps.whatsapp.net", "102400"},         // 100KB - WhatsApp status prefetch
        {"static.whatsapp.net", "204800"},      // 200KB
        {"play.googleapis.com", "204800"},      // 200KB - Play Store sync
        {"android.clients.google.com", "102400"}, // 100KB
        {"update.googleapis.com", "102400"},    // 100KB - Google updates
        {"firebaseinstallations.googleapis.com", "20480"}, // 20KB
        {"app-measurement.com", "20480"},       // 20KB - Firebase analytics
        {"connectivitycheck.gstatic.com", "1024"}, // 1KB
    };

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
        if (running) return;

        loadAdBlockList();
        buildSystemWhitelist();
        restoreSavings();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        // Wake lock
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "datasaver:vpn");
            wakeLock.acquire(24 * 60 * 60 * 1000L);
        } catch (Exception e) {}

        try {
            Builder builder = new Builder();
            builder.setSession("DataSaver");
            builder.addAddress("10.0.0.2", 32);

            // Route ALL DNS through our VPN — this is how we control background apps
            builder.addDnsServer("10.0.0.1");

            // Only route DNS IPs through tunnel — regular traffic goes direct
            builder.addRoute("10.0.0.1", 32);
            builder.addRoute("1.1.1.1", 32);
            builder.addRoute("1.0.0.1", 32);
            builder.addRoute("8.8.8.8", 32);
            builder.addRoute("8.8.4.4", 32);
            // Capture common Nigerian ISP DNS servers
            builder.addRoute("154.118.0.0", 16);  // MTN DNS range
            builder.addRoute("197.210.0.0", 16);   // Glo DNS range

            builder.setMtu(1500);

            // Exclude our own app
            try { builder.addDisallowedApplication(getPackageName()); } catch (Exception e) {}

            // Split tunneling — exclude bypass apps
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            String bypassList = sp.getString("bypass_apps", "");
            if (!bypassList.isEmpty()) {
                for (String pkg : bypassList.split(",")) {
                    try { builder.addDisallowedApplication(pkg.trim()); } catch (Exception e) {}
                }
            }

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "VPN establish returned null");
                stopSelf();
                return;
            }

            running = true;
            isVpnRunning = true;

            new Thread(this::processPackets, "VPN-Packets").start();
            new Thread(this::detectForegroundApp, "VPN-FgDetect").start();

            Log.i(TAG, "VPN started — Ads: " + adDomains.size() + " domains, BG Guard: ON");

        } catch (Exception e) {
            Log.e(TAG, "VPN start failed: " + e.getMessage());
            stopVpn();
        }
    }

    /**
     * Build whitelist of system packages that should never have DNS blocked.
     * These are essential for the phone to function.
     */
    private void buildSystemWhitelist() {
        systemWhitelist.clear();
        // Always allow these system domains
        systemWhitelist.add("connectivitycheck.gstatic.com");
        systemWhitelist.add("clients3.google.com");
        systemWhitelist.add("time.android.com");
        systemWhitelist.add("dns.google");
        systemWhitelist.add("captive.apple.com");
        systemWhitelist.add("mtalk.google.com"); // Google Cloud Messaging - needed for notifications
        systemWhitelist.add("fcm.googleapis.com"); // Firebase Cloud Messaging
        systemWhitelist.add("accounts.google.com");
        systemWhitelist.add("oauth2.googleapis.com");
    }

    /**
     * Main packet processing loop.
     * Handles DNS packets: blocks ads, blocks background app DNS, forwards foreground DNS.
     */
    private void processPackets() {
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
        byte[] packetData = new byte[32767];

        DatagramSocket dnsSocket = null;
        try {
            dnsSocket = new DatagramSocket();
            protect(dnsSocket);
            dnsSocket.setSoTimeout(3000);
        } catch (Exception e) {
            Log.e(TAG, "DNS socket failed: " + e.getMessage());
        }

        while (running) {
            try {
                int length = in.read(packetData);
                if (length <= 0) {
                    Thread.sleep(10);
                    continue;
                }

                totalPacketsProcessed.incrementAndGet();

                int version = (packetData[0] >> 4) & 0xF;
                if (version != 4 || length < 20) {
                    out.write(packetData, 0, length);
                    continue;
                }

                int headerLength = (packetData[0] & 0xF) * 4;
                int protocol = packetData[9] & 0xFF;

                // Only handle UDP (DNS is UDP port 53)
                if (protocol == 17 && length > headerLength + 8) {
                    int destPort = ((packetData[headerLength + 2] & 0xFF) << 8)
                        | (packetData[headerLength + 3] & 0xFF);

                    if (destPort == 53 && dnsSocket != null) {
                        int dnsOffset = headerLength + 8;
                        int dnsLength = length - dnsOffset;
                        if (dnsLength > 12) {
                            String domain = parseDnsQuery(packetData, dnsOffset, dnsLength);

                            if (domain != null) {
                                // 1. AD BLOCKING — always block ad domains
                                if (isAdDomain(domain)) {
                                    blockedAdRequests.incrementAndGet();
                                    long estSize = getEstimatedAdSize(domain);
                                    blockedAdBytes.addAndGet(estSize);
                                    Log.d(TAG, "AD blocked: " + domain + " (~" + (estSize/1024) + "KB)");
                                    continue;
                                }

                                // 2. SYSTEM WHITELIST — always allow
                                if (systemWhitelist.contains(domain)) {
                                    forwardDns(dnsSocket, packetData, headerLength, dnsOffset, dnsLength, out);
                                    continue;
                                }

                                // 3. BACKGROUND GUARD — block DNS for background apps
                                if (shouldBlockBackground(domain)) {
                                    blockedBgSyncs.incrementAndGet();
                                    long estSize = getEstimatedBgSize(domain);
                                    blockedBgBytes.addAndGet(estSize);
                                    Log.d(TAG, "BG blocked: " + domain + " (~" + (estSize/1024) + "KB)");
                                    continue;
                                }

                                // 4. FOREGROUND — allow, forward to Cloudflare
                                forwardDns(dnsSocket, packetData, headerLength, dnsOffset, dnsLength, out);
                                continue;
                            }
                        }
                    }
                }

                // Non-DNS traffic: pass through
                out.write(packetData, 0, length);

            } catch (Exception e) {
                if (running) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                }
            }
        }

        if (dnsSocket != null) dnsSocket.close();
        try { in.close(); } catch (Exception e) {}
        try { out.close(); } catch (Exception e) {}
    }

    /**
     * Determine if a DNS request should be blocked because it's from a background app.
     * We check if the domain belongs to a known app and if that app is NOT in the foreground.
     */
    private boolean shouldBlockBackground(String domain) {
        // Check if this domain belongs to a known app
        String appPackage = domainToPackage(domain);
        if (appPackage == null) return false; // Unknown domain, allow

        // If the app owning this domain is in the foreground, allow
        if (appPackage.equals(foregroundPackage)) return false;

        // If DataSaver background blocking is disabled, allow
        SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
        if (!sp.getBoolean("bg_block_enabled", true)) return false;

        // This is a background app trying to use data — BLOCK
        return true;
    }

    /**
     * Map a domain to the app package that likely owns it.
     * This is how we know "graph.facebook.com" belongs to Facebook.
     */
    private String domainToPackage(String domain) {
        if (domain.contains("facebook.com") || domain.contains("fbcdn.net") || domain.contains("fb.com"))
            return "com.facebook.katana";
        if (domain.contains("instagram.com") || domain.contains("cdninstagram.com"))
            return "com.instagram.android";
        if (domain.contains("tiktok") || domain.contains("musical.ly") || domain.contains("tiktokv.com") || domain.contains("byteoversea.com"))
            return "com.zhiliaoapp.musically";
        if (domain.contains("twitter.com") || domain.contains("twimg.com"))
            return "com.twitter.android";
        if (domain.contains("whatsapp.com") || domain.contains("whatsapp.net"))
            return "com.whatsapp";
        if (domain.contains("snapchat.com") || domain.contains("snap.com"))
            return "com.snapchat.android";
        if (domain.contains("youtube.com") || domain.contains("googlevideo.com") || domain.contains("ytimg.com"))
            return "com.google.android.youtube";
        if (domain.contains("spotify.com") || domain.contains("scdn.co"))
            return "com.spotify.music";
        if (domain.contains("netflix.com") || domain.contains("nflxvideo.net"))
            return "com.netflix.mediaclient";
        if (domain.contains("linkedin.com"))
            return "com.linkedin.android";
        if (domain.contains("telegram.org") || domain.contains("t.me"))
            return "org.telegram.messenger";
        if (domain.contains("play.google") || domain.contains("android.clients.google"))
            return "com.android.vending";
        return null; // Unknown — don't block
    }

    /**
     * Estimate how much data a blocked ad request would have downloaded.
     */
    private long getEstimatedAdSize(String domain) {
        if (domain.contains("video") || domain.contains("vast")) return 5 * 1024 * 1024; // 5MB video ad
        if (domain.contains("doubleclick") || domain.contains("googlesyndication")) return 200 * 1024; // 200KB
        if (domain.contains("facebook") || domain.contains("instagram")) return 300 * 1024; // 300KB
        return 50 * 1024; // 50KB default
    }

    /**
     * Estimate how much data a blocked background request would have downloaded.
     */
    private long getEstimatedBgSize(String domain) {
        for (String[] entry : BG_HEAVY_DOMAINS) {
            if (domain.contains(entry[0]) || entry[0].contains(domain)) {
                return Long.parseLong(entry[1]);
            }
        }
        // Default estimates by app type
        if (domain.contains("instagram") || domain.contains("cdninstagram")) return 500 * 1024; // 500KB - image prefetch
        if (domain.contains("facebook") || domain.contains("fbcdn")) return 200 * 1024; // 200KB
        if (domain.contains("tiktok") || domain.contains("byteoversea")) return 300 * 1024; // 300KB
        if (domain.contains("youtube") || domain.contains("googlevideo")) return 1024 * 1024; // 1MB
        if (domain.contains("play.google")) return 200 * 1024; // 200KB
        return 100 * 1024; // 100KB default
    }

    /**
     * Forward a DNS query to Cloudflare 1.1.1.1 and write response back to tunnel.
     */
    private void forwardDns(DatagramSocket dnsSocket, byte[] packetData, int headerLength,
                            int dnsOffset, int dnsLength, FileOutputStream out) {
        try {
            byte[] dnsPayload = new byte[dnsLength];
            System.arraycopy(packetData, dnsOffset, dnsPayload, 0, dnsLength);

            InetAddress dnsServer = InetAddress.getByName("1.1.1.1");
            DatagramPacket dnsRequest = new DatagramPacket(dnsPayload, dnsLength, dnsServer, 53);
            dnsSocket.send(dnsRequest);

            byte[] responseBuffer = new byte[1024];
            DatagramPacket dnsResponse = new DatagramPacket(responseBuffer, responseBuffer.length);
            try {
                dnsSocket.receive(dnsResponse);
                byte[] fullResponse = buildDnsResponse(packetData, headerLength,
                    responseBuffer, dnsResponse.getLength());
                if (fullResponse != null) {
                    out.write(fullResponse);
                }
            } catch (java.net.SocketTimeoutException ste) {
                // Timeout
            }
        } catch (Exception e) {
            Log.w(TAG, "DNS forward: " + e.getMessage());
        }
    }

    private String parseDnsQuery(byte[] data, int offset, int length) {
        try {
            int pos = offset + 12;
            if (pos >= offset + length) return null;
            StringBuilder domain = new StringBuilder();
            while (pos < offset + length) {
                int labelLen = data[pos] & 0xFF;
                if (labelLen == 0) break;
                if (labelLen > 63) break;
                if (domain.length() > 0) domain.append('.');
                pos++;
                for (int i = 0; i < labelLen && pos < offset + length; i++, pos++) {
                    domain.append((char) (data[pos] & 0xFF));
                }
            }
            return domain.length() > 0 ? domain.toString().toLowerCase() : null;
        } catch (Exception e) { return null; }
    }

    private boolean isAdDomain(String domain) {
        if (adDomains.contains(domain)) return true;
        int dot = domain.indexOf('.');
        while (dot > 0 && dot < domain.length() - 1) {
            String parent = domain.substring(dot + 1);
            if (adDomains.contains(parent)) return true;
            dot = domain.indexOf('.', dot + 1);
        }
        return false;
    }

    private byte[] buildDnsResponse(byte[] originalPacket, int ipHeaderLen,
                                     byte[] dnsResponse, int dnsResponseLen) {
        try {
            int udpHeaderLen = 8;
            int totalLen = ipHeaderLen + udpHeaderLen + dnsResponseLen;
            byte[] response = new byte[totalLen];
            System.arraycopy(originalPacket, 0, response, 0, ipHeaderLen);
            System.arraycopy(originalPacket, 12, response, 16, 4);
            System.arraycopy(originalPacket, 16, response, 12, 4);
            response[2] = (byte) ((totalLen >> 8) & 0xFF);
            response[3] = (byte) (totalLen & 0xFF);
            response[8] = 64;
            response[10] = 0;
            response[11] = 0;
            response[ipHeaderLen] = originalPacket[ipHeaderLen + 2];
            response[ipHeaderLen + 1] = originalPacket[ipHeaderLen + 3];
            response[ipHeaderLen + 2] = originalPacket[ipHeaderLen];
            response[ipHeaderLen + 3] = originalPacket[ipHeaderLen + 1];
            int udpLen = udpHeaderLen + dnsResponseLen;
            response[ipHeaderLen + 4] = (byte) ((udpLen >> 8) & 0xFF);
            response[ipHeaderLen + 5] = (byte) (udpLen & 0xFF);
            response[ipHeaderLen + 6] = 0;
            response[ipHeaderLen + 7] = 0;
            System.arraycopy(dnsResponse, 0, response, ipHeaderLen + udpHeaderLen, dnsResponseLen);
            int sum = 0;
            for (int i = 0; i < ipHeaderLen; i += 2) {
                sum += ((response[i] & 0xFF) << 8) | (response[i + 1] & 0xFF);
            }
            while ((sum >> 16) > 0) sum = (sum & 0xFFFF) + (sum >> 16);
            sum = ~sum & 0xFFFF;
            response[10] = (byte) ((sum >> 8) & 0xFF);
            response[11] = (byte) (sum & 0xFF);
            return response;
        } catch (Exception e) { return null; }
    }

    private void detectForegroundApp() {
        int saveCounter = 0;
        while (running) {
            try {
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    long now = System.currentTimeMillis();
                    UsageEvents events = usm.queryEvents(now - 5000, now);
                    UsageEvents.Event event = new UsageEvents.Event();
                    String latest = "";
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event);
                        if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            latest = event.getPackageName();
                        }
                    }
                    if (!latest.isEmpty()) foregroundPackage = latest;
                }
                // Persist savings every ~30 seconds
                saveCounter++;
                if (saveCounter >= 20) {
                    persistSavings();
                    syncSavingsToServer();
                    saveCounter = 0;
                }
                Thread.sleep(1500);
            } catch (Exception e) {
                try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
            }
        }
    }

    /** Sync savings to server so user can see total savings in their account */
    private void syncSavingsToServer() {
        try {
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            String phone = sp.getString("phone", "");
            if (phone.isEmpty()) return;
            long totalSaved = blockedAdBytes.get() + blockedBgBytes.get();
            long totalBlocked = blockedAdRequests.get() + blockedBgSyncs.get();
            // Fire and forget
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL("https://datasaver-server.onrender.com/api/savings/sync");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setDoOutput(true);
                    String body = "{\"phone\":\"" + phone + "\",\"saved_bytes\":" + totalSaved
                        + ",\"blocked_requests\":" + totalBlocked
                        + ",\"ad_bytes\":" + blockedAdBytes.get()
                        + ",\"bg_bytes\":" + blockedBgBytes.get() + "}";
                    conn.getOutputStream().write(body.getBytes());
                    conn.getOutputStream().close();
                    conn.getResponseCode(); // trigger the request
                } catch (Exception e) {}
            }).start();
        } catch (Exception e) {}
    }

    private void loadAdBlockList() {
        adDomains.clear();
        try {
            InputStream is = getResources().openRawResource(
                getResources().getIdentifier("adblock", "raw", getPackageName()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty() && !line.startsWith("#")) adDomains.add(line);
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load blocklist: " + e.getMessage());
        }
    }

    private void stopVpn() {
        // Persist savings before stopping
        persistSavings();
        running = false;
        isVpnRunning = false;
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
            vpnInterface = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception e) {}
        }
        stopForeground(true);
        stopSelf();
        Log.i(TAG, "VPN stopped. Ads: " + blockedAdRequests.get() + ", BG: " + blockedBgSyncs.get()
            + ", Saved: " + ((blockedAdBytes.get() + blockedBgBytes.get()) / 1024) + " KB");
    }

    /** Save counters to SharedPreferences so they survive restarts */
    private void persistSavings() {
        try {
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            sp.edit()
                .putLong("real_ad_bytes", blockedAdBytes.get())
                .putLong("real_bg_bytes", blockedBgBytes.get())
                .putLong("real_ad_requests", blockedAdRequests.get())
                .putLong("real_bg_syncs", blockedBgSyncs.get())
                .putLong("real_total_packets", totalPacketsProcessed.get())
                .apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to persist savings: " + e.getMessage());
        }
    }

    /** Restore counters from SharedPreferences on startup */
    private void restoreSavings() {
        try {
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            blockedAdBytes.set(sp.getLong("real_ad_bytes", 0));
            blockedBgBytes.set(sp.getLong("real_bg_bytes", 0));
            blockedAdRequests.set(sp.getLong("real_ad_requests", 0));
            blockedBgSyncs.set(sp.getLong("real_bg_syncs", 0));
            totalPacketsProcessed.set(sp.getLong("real_total_packets", 0));
            Log.i(TAG, "Restored savings: ads=" + blockedAdRequests.get() + " bg=" + blockedBgSyncs.get());
        } catch (Exception e) {}
    }

    @Override
    public void onDestroy() { stopVpn(); super.onDestroy(); }

    @Override
    public void onRevoke() { stopVpn(); super.onRevoke(); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "DataSaver VPN", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("DataSaver is protecting your data");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, DataSaverVpnService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DataSaver Active")
            .setContentText("Blocking ads & background data")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPending)
            .addAction(new Notification.Action.Builder(null, "Stop", stopPending).build())
            .setOngoing(true)
            .build();
    }
}
