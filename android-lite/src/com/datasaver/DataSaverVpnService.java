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
import java.util.ArrayDeque;
import java.util.Deque;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class DataSaverVpnService extends VpnService {

    private static final String TAG = "DataSaverVPN";
    private static final String CHANNEL_ID = "datasaver_vpn";
    private static final String NOTIF_CHANNEL_ID = "datasaver_push_v2";
    private static final int NOTIF_ID = 2;
    private static final int LOG_PORT = 8080;
    private static final int MAX_LOG_ENTRIES = 500;
    private static final String SERVER_URL = "https://datasaver-server.onrender.com";
    private static final long NOTIF_POLL_INTERVAL = 30000; // 30 seconds for faster notification delivery

    // Push notification state
    public static volatile int unreadNotifCount = 0;
    public static volatile String latestNotifTitle = "";
    public static volatile String latestNotifBody = "";
    public static volatile long lastNotifId = 0;
    public static volatile boolean hasNewNotif = false;

    public static final Deque<String> liveLog = new ArrayDeque<>();
    public static final Object logLock = new Object();

    public static void addLog(String action, String domain, String detail) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String entry = time + "|" + action + "|" + domain + "|" + detail;
        synchronized (logLock) {
            ((ArrayDeque<String>)liveLog).addLast(entry);
            if (liveLog.size() > MAX_LOG_ENTRIES) ((ArrayDeque<String>)liveLog).removeFirst();
        }
    }

    public static volatile boolean isVpnRunning = false;

    // Bulletproof: auto-restart if killed by OS
    private static volatile long lastRestartAttempt = 0;
    public static void restartIfNeeded(android.content.Context ctx) {
        if (isVpnRunning) return;
        long now = System.currentTimeMillis();
        if (now - lastRestartAttempt < 10000) return; // debounce 10s
        lastRestartAttempt = now;
        android.content.SharedPreferences sp = ctx.getSharedPreferences("datasaver", android.content.Context.MODE_PRIVATE);
        if (sp.getBoolean("vpn_should_run", false)) {
            ctx.startService(new android.content.Intent(ctx, DataSaverVpnService.class));
        }
    }

    // REAL counters — only things we can actually measure
    public static final AtomicLong blockedAdRequests = new AtomicLong(0);  // ads blocked (DNS queries blocked)
    public static final AtomicLong blockedBgSyncs = new AtomicLong(0);     // background syncs blocked
    public static final AtomicLong totalDnsQueries = new AtomicLong(0);    // total DNS queries seen
    public static final AtomicLong totalPacketsProcessed = new AtomicLong(0);

    // Per-app blocked request counts: appName -> count of blocked DNS queries
    public static final ConcurrentHashMap<String, AtomicLong> perAppBlockedCount = new ConcurrentHashMap<>();

    // Daily reset tracking
    private static volatile String currentSavingsDate = "";

    private ParcelFileDescriptor vpnInterface;
    private volatile boolean running = false;
    private Set<String> adDomains = new HashSet<>();
    private volatile String foregroundPackage = "";
    private String userPlan = "none";
    private PowerManager.WakeLock wakeLock;
    private static DataSaverVpnService serviceRef;

    // System packages that should never be blocked (even in background)
    private final Set<String> systemWhitelist = new HashSet<>();

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
        serviceRef = this;

        userPlan = getSharedPreferences("datasaver", MODE_PRIVATE).getString("subscription_plan", "none");
        // Enforce image quality by plan
        enforceImageQualityByPlan();
        buildSystemWhitelist();
        
        // Restore persisted counters from previous session
        restoreSavings();
        
        // Check if we need to reset for a new day
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (!today.equals(currentSavingsDate) && !currentSavingsDate.isEmpty()) {
            // New day - reset counters but keep the persisted data structure
            blockedAdRequests.set(0);
            blockedBgSyncs.set(0);
            totalDnsQueries.set(0);
            totalPacketsProcessed.set(0);
            perAppBlockedCount.clear();
        }
        currentSavingsDate = today;
        checkDailyReset();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        
        // Save counters periodically - start a background thread
        new Thread(() -> {
            while (running) {
                try { Thread.sleep(10000); } catch (Exception e) {}
                if (running) saveCounters();
            }
        }, "CounterSaver").start();
        
        // Load ad block list in background — don't block VPN startup on slow phones
        new Thread(this::loadAdBlockList, "AdBlockLoader").start();

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

            builder.addDnsServer("10.0.0.1");
            builder.addRoute("10.0.0.1", 32); // Only DNS virtual IP - internet works normally



            builder.setMtu(1500);

            // Exclude our own app
            try { builder.addDisallowedApplication(getPackageName()); } catch (Exception e) {}
            // CRITICAL: Exclude WhatsApp entirely — VPN breaks WhatsApp calls (STUN/TURN/SRTP)
            String[] alwaysBypass = {
                "com.whatsapp",
                "com.whatsapp.w4b",          // WhatsApp Business
                "org.telegram.messenger",     // Telegram calls
                "org.telegram.messenger.web",
                "com.viber.voip",             // Viber calls
                "com.skype.raider",           // Skype
                "com.microsoft.teams",        // Teams
                "us.zoom.videomeetings",      // Zoom
                "com.gtbank.gtworldapp",      // Banking
                "com.accessbank.accessbankapp",
                "com.zenithbank.eazymoney",
                "com.firstbanknigeria.firstmobile",
                "ng.opay",
                "com.palmpay.app",
                "com.kuda.app"
            };
            for (String pkg : alwaysBypass) { try { builder.addDisallowedApplication(pkg); } catch (Exception e) {} }

            // Split tunneling — exclude user-saved protected apps from tunnel entirely
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            String bypassList = sp.getString("bypass_apps", "");
            if (!bypassList.isEmpty()) {
                for (String pkg : bypassList.split(",")) {
                    String p = pkg.trim();
                    if (!p.isEmpty()) {
                        try { builder.addDisallowedApplication(p); } catch (Exception e) {}
                    }
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

            // Warmup delay — don't count blocks for first 3 seconds to prevent the "jump"
            final long warmupEnd = System.currentTimeMillis() + 3000;

            new Thread(() -> processPackets(warmupEnd), "VPN-Packets").start();
            new Thread(this::detectForegroundApp, "VPN-FgDetect").start();
            new Thread(this::watchdog, "VPN-Watchdog").start();
            new Thread(this::pollNotifications, "VPN-NotifPoll").start();
            new Thread(this::reminderNotification, "VPN-Reminder").start();
            Log.i(TAG, "VPN started - Ads: " + adDomains.size() + " domains, BG Guard: ON");
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
    private void enforceImageQualityByPlan() {
        SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
        String quality;
        switch (userPlan) {
            case "professional":
            case "enterprise":  quality = "Low";    break; // most aggressive compression
            case "premium":     quality = "Medium"; break;
            default:            quality = "Low";    break; // free: forced low (least data)
        }
        sp.edit().putString("image_quality", quality).apply();
    }

    // Free plan daily ad block counter
    private int dailyAdBlockCount = 0;
    private long adBlockResetDay = 0;
    private static final int FREE_AD_BLOCK_DAILY_LIMIT = 50;

    private boolean canBlockAd() {
        return true; // No limit - block all ads for all users
    }

    // Cache DNS server address — avoid repeated DNS lookups inside packet loop
    private InetAddress cachedDnsServer = null;
    // Cache bypass apps list — avoid SharedPreferences disk read on every packet
    private volatile String cachedBypassApps = "";
    private long bypassCacheTime = 0;

    private String getBypassApps() {
        long now = System.currentTimeMillis();
        if (now - bypassCacheTime > 5000) {
            cachedBypassApps = getSharedPreferences("datasaver", MODE_PRIVATE).getString("bypass_apps", "");
            bypassCacheTime = now;
        }
        return cachedBypassApps;
    }

    private DatagramSocket createDnsSocket() {
        try {
            DatagramSocket s = new DatagramSocket();
            protect(s);
            s.setSoTimeout(2000);
            return s;
        } catch (Exception e) {
            Log.e(TAG, "DNS socket failed: " + e.getMessage());
            return null;
        }
    }

    private void processPackets(long warmupEnd) {
        // Pre-resolve DNS server address once — never block inside loop
        // Use hardcoded byte address - never call getByName() which uses DNS
        try {
            cachedDnsServer = InetAddress.getByAddress(new byte[]{1,1,1,1});
        } catch (Exception e) {
            try { cachedDnsServer = InetAddress.getByAddress(new byte[]{8,8,8,8}); } catch (Exception e2) {}
        }

        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
        byte[] packetData = new byte[32767];

        DatagramSocket dnsSocket = createDnsSocket();
        int dnsSocketErrors = 0;

        while (running) {
            try {
                int length = in.read(packetData, 0, packetData.length);
                if (length <= 0) { try { Thread.sleep(1); } catch (InterruptedException ie) { break; } continue; }

                totalPacketsProcessed.incrementAndGet();

                int version = (packetData[0] >> 4) & 0xF;
                if (version != 4 || length < 20) { try { out.write(packetData, 0, length); } catch (Exception ignored) {} continue; }

                int headerLength = (packetData[0] & 0xF) * 4;
                int protocol = packetData[9] & 0xFF;

                if (protocol == 17 && length > headerLength + 8) {
                    int destPort = ((packetData[headerLength + 2] & 0xFF) << 8)
                        | (packetData[headerLength + 3] & 0xFF);

                    if (destPort == 53 && dnsSocket != null && cachedDnsServer != null) {
                        int dnsOffset = headerLength + 8;
                        int dnsLength = length - dnsOffset;
                        if (dnsLength > 12) {
                            // Count every DNS query (real metric)
                            totalDnsQueries.incrementAndGet();
                            String domain = parseDnsQuery(packetData, dnsOffset, dnsLength);
                            if (domain != null) {
                                if (isAdDomain(domain)) {
                                    if (canBlockAd()) {
                                        if (System.currentTimeMillis() > warmupEnd) {
                                            blockedAdRequests.incrementAndGet();
                                            // Track per-app blocked count (real)
                                            String app = domainToPackage(domain);
                                            if (app != null) {
                                                perAppBlockedCount.computeIfAbsent(app, k -> new AtomicLong(0)).incrementAndGet();
                                            }
                                            saveBlockedApp(serviceRef, domain);
                                        }
                                        continue; // block it
                                    }
                                    // Free limit hit — let ad through
                                    forwardDns(dnsSocket, packetData, headerLength, dnsOffset, dnsLength, out);
                                    continue;
                                }
                                if (systemWhitelist.contains(domain)) {
                                    forwardDns(dnsSocket, packetData, headerLength, dnsOffset, dnsLength, out);
                                    continue;
                                }
                                if (shouldBlockBackground(domain)) {
                                    if (System.currentTimeMillis() > warmupEnd) {
                                        blockedBgSyncs.incrementAndGet();
                                        // Track per-app blocked count (real)
                                        String app = domainToPackage(domain);
                                        if (app != null) {
                                            perAppBlockedCount.computeIfAbsent(app, k -> new AtomicLong(0)).incrementAndGet();
                                        }
                                    }
                                    continue;
                                }
                                forwardDns(dnsSocket, packetData, headerLength, dnsOffset, dnsLength, out);
                                continue;
                            }
                            // domain parse failed - forward anyway so apps dont break
                            forwardDns(dnsSocket, packetData, headerLength, dnsOffset, dnsLength, out);
                            continue;
                        }
                    }
                }
                // All non-DNS traffic: write back to tunnel so it passes through normally
                out.write(packetData, 0, length);

            } catch (Exception e) {
                if (!running) break;
                // Recreate DNS socket if it goes bad — fixes stuck VPN on some phones
                dnsSocketErrors++;
                if (dnsSocketErrors > 10) {
                    try { if (dnsSocket != null) dnsSocket.close(); } catch (Exception ignored) {}
                    dnsSocket = createDnsSocket();
                    dnsSocketErrors = 0;
                    Log.w(TAG, "DNS socket recreated");
                }
                try { Thread.sleep(50); } catch (InterruptedException ie) { break; }
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
        if (domain.contains("whatsapp") || domain.contains("signal") ||
            domain.contains("telegram") || domain.contains("chrome") ||
            domain.contains("google.com") || domain.contains("googleapis.com") ||
            domain.contains("gstatic.com") || domain.contains("dropbox") ||
            domain.contains("cloudflare") || domain.contains("akamai") ||
            domain.contains("amazonaws") || domain.contains("microsoft") ||
            domain.contains("apple.com") || domain.contains("icloud") ||
            domain.contains("outlook") || domain.contains("live.com") ||
            domain.contains("paystack") || domain.contains("flutterwave")) return false;
        if ("none".equals(userPlan)) return false;
        String appPackage = domainToPackage(domain);
        if (appPackage == null) return false;
        // Use cached bypass list — not a disk read every packet
        if (getBypassApps().contains(appPackage)) return false;
        if ("premium".equals(userPlan)) {
            boolean isSocial = appPackage.contains("facebook") || appPackage.contains("instagram")
                || appPackage.contains("tiktok") || appPackage.contains("musically")
                || appPackage.contains("twitter") || appPackage.contains("snapchat");
            if (!isSocial) return false;
        }
        if (appPackage.equals(foregroundPackage)) return false;
        SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
        if (!sp.getBoolean("bg_block_enabled", true)) return false;
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
     * Forward a DNS query to Cloudflare 1.1.1.1 and write response back to tunnel.
     */
    private void forwardDns(DatagramSocket dnsSocket, byte[] packetData, int headerLength,
                            int dnsOffset, int dnsLength, FileOutputStream out) {
        try {
            byte[] dnsPayload = new byte[dnsLength];
            System.arraycopy(packetData, dnsOffset, dnsPayload, 0, dnsLength);
            // Use pre-resolved address — never call getByName() inside packet loop
            DatagramPacket dnsRequest = new DatagramPacket(dnsPayload, dnsLength, cachedDnsServer, 53);
            dnsSocket.send(dnsRequest);
            byte[] responseBuffer = new byte[4096]; // DNS responses can be up to 4096 bytes
            DatagramPacket dnsResponse = new DatagramPacket(responseBuffer, responseBuffer.length);
            try {
                dnsSocket.receive(dnsResponse);
                byte[] fullResponse = buildDnsResponse(packetData, headerLength,
                    responseBuffer, dnsResponse.getLength());
                if (fullResponse != null) { try { out.write(fullResponse); } catch (Exception ignored) {} }
            } catch (java.net.SocketTimeoutException ste) { /* timeout is fine */ }
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
            long totalBlocked = blockedAdRequests.get() + blockedBgSyncs.get();
            long totalDns = totalDnsQueries.get();
            // Calculate saved bytes: 5KB per blocked request (conservative estimate)
            long savedBytes = totalBlocked * 5 * 1024;
            // Fire and forget — send all data
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL("https://datasaver-server.onrender.com/api/savings/sync");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setDoOutput(true);
                    String body = "{\"phone\":\"" + phone + "\""
                        + ",\"blocked_requests\":" + totalBlocked
                        + ",\"saved_bytes\":" + savedBytes
                        + ",\"total_dns\":" + totalDns + "}";
                    conn.getOutputStream().write(body.getBytes());
                    conn.getOutputStream().close();
                    conn.getResponseCode();
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

    // Watchdog: if packet thread dies unexpectedly, restart it
    public static void saveBlockedApp(android.content.Context ctx, String domain) {
        if (ctx == null) return;
        try {
            String app = "Other";
            if (domain.contains("facebook") || domain.contains("fbcdn") || domain.contains("fb.com")) app = "Facebook";
            else if (domain.contains("instagram") || domain.contains("cdninstagram")) app = "Instagram";
            else if (domain.contains("tiktok") || domain.contains("musical") || domain.contains("byteoversea")) app = "TikTok";
            else if (domain.contains("twitter") || domain.contains("twimg")) app = "Twitter/X";
            else if (domain.contains("youtube") || domain.contains("googlevideo") || domain.contains("ytimg")) app = "YouTube";
            else if (domain.contains("doubleclick") || domain.contains("googlesyndication") || domain.contains("googleads")) app = "Google Ads";
            else if (domain.contains("amazon-adsystem")) app = "Amazon Ads";
            else if (domain.contains("snapchat") || domain.contains("snap.com")) app = "Snapchat";
            else if (domain.contains("linkedin")) app = "LinkedIn";
            else if (domain.contains("applovin") || domain.contains("admob") || domain.contains("adnxs")) app = "Ad Network";
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
            android.content.SharedPreferences sp = ctx.getSharedPreferences("datasaver_blocks", android.content.Context.MODE_PRIVATE);
            String key = "blocked_" + today + "_" + app;
            sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply();
            String dates = sp.getString("block_dates", "");
            if (!dates.contains(today)) sp.edit().putString("block_dates", dates.isEmpty() ? today : dates + "," + today).apply();
        } catch (Exception e) {}
    }

    private void watchdog() {
        while (running) {
            try {
                Thread.sleep(15000); // check every 15s
                if (!running) break;
                // If VPN interface is still valid but packets stopped, log it
                if (vpnInterface != null && totalPacketsProcessed.get() == 0) {
                    Log.w(TAG, "Watchdog: no packets processed yet - VPN may be idle");
                }
            } catch (InterruptedException e) { break; }
            catch (Exception e) { Log.w(TAG, "Watchdog error: " + e.getMessage()); }
        }
    }











    private ServerSocket logServerSocket = null;

    private void runLogServer() {
        try {
            logServerSocket = new ServerSocket(LOG_PORT);
            // ServerSocket binds locally - no protect needed
            addLog("INFO", "LogServer", "Live log server started on port " + LOG_PORT);
            while (running) {
                try {
                    Socket client = logServerSocket.accept();
                    new Thread(() -> handleLogRequest(client)).start();
                } catch (Exception e) {
                    if (!running) break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Log server error: " + e.getMessage());
        }
    }

    private void handleLogRequest(Socket client) {
        try {
            // Read HTTP request (ignore it)
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream()));
            String requestLine = br.readLine();
            while (br.ready()) br.readLine(); // drain headers

            PrintWriter pw = new PrintWriter(client.getOutputStream(), true);

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{\"packets\":").append(totalPacketsProcessed.get());
            json.append(",\"blocked\":").append(blockedAdRequests.get() + blockedBgSyncs.get());
            json.append(",\"total_dns\":").append(totalDnsQueries.get());
            json.append(",\"running\":").append(running);
            json.append(",\"logs\":[");
            synchronized (logLock) {
                boolean first = true;
                for (String entry : liveLog) {
                    if (!first) json.append(",");
                    // Escape for JSON
                    String escaped = entry.replace("\\", "\\\\").replace("\"", "\\\"");
                    json.append("\"").append(escaped).append("\"");
                    first = false;
                }
            }
            json.append("]}");

            String body = json.toString();
            pw.println("HTTP/1.1 200 OK");
            pw.println("Content-Type: application/json");
            pw.println("Access-Control-Allow-Origin: *");
            pw.println("Content-Length: " + body.getBytes().length);
            pw.println("Connection: close");
            pw.println();
            pw.print(body);
            pw.flush();
            client.close();
        } catch (Exception e) {
            try { client.close(); } catch (Exception ignored) {}
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
            + ", DNS: " + totalDnsQueries.get());
    }

    /** Save counters to SharedPreferences so they survive restarts */
    private void persistSavings() {
        try {
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            sp.edit()
                .putLong("real_ad_requests", blockedAdRequests.get())
                .putLong("real_bg_syncs", blockedBgSyncs.get())
                .putLong("real_total_dns", totalDnsQueries.get())
                .putLong("real_total_packets", totalPacketsProcessed.get())
                .putString("savings_date", currentSavingsDate)
                .apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to persist savings: " + e.getMessage());
        }
    }

    /** Restore counters from SharedPreferences on startup */
    private void restoreSavings() {
        try {
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            blockedAdRequests.set(sp.getLong("real_ad_requests", 0));
            blockedBgSyncs.set(sp.getLong("real_bg_syncs", 0));
            totalDnsQueries.set(sp.getLong("real_total_dns", 0));
            totalPacketsProcessed.set(sp.getLong("real_total_packets", 0));
            currentSavingsDate = sp.getString("savings_date", "");
            Log.i(TAG, "Restored: ads=" + blockedAdRequests.get() + " bg=" + blockedBgSyncs.get() + " dns=" + totalDnsQueries.get());
        } catch (Exception e) {}
    }

    /** Reset daily counters if it's a new day */
    private void checkDailyReset() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(new java.util.Date());
        if (!today.equals(currentSavingsDate) && !currentSavingsDate.isEmpty()) {
            Log.i(TAG, "Daily reset: " + currentSavingsDate + " -> " + today);
            blockedAdRequests.set(0);
            blockedBgSyncs.set(0);
            totalDnsQueries.set(0);
            perAppBlockedCount.clear();
        }
        currentSavingsDate = today;
    }
    
    // Save counters to SharedPreferences for persistence
    private void saveCounters() {
        try {
            SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            sp.edit()
                .putLong("real_ad_requests", blockedAdRequests.get())
                .putLong("real_bg_syncs", blockedBgSyncs.get())
                .putLong("real_total_dns", totalDnsQueries.get())
                .putLong("real_total_packets", totalPacketsProcessed.get())
                .putString("savings_date", today)
                .apply();
        } catch (Exception e) {}
    }

    @Override
    public void onDestroy() { 
        saveCounters(); // Save before destroying
        stopVpn(); 
        super.onDestroy(); 
    }

    @Override
    public void onRevoke() { stopVpn(); super.onRevoke(); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "DataSaver VPN", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("DataSaver is protecting your data");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);

            // Push notification channel — high importance so notifications appear as popups
            NotificationChannel pushCh = new NotificationChannel(
                NOTIF_CHANNEL_ID, "DataSaver Alerts", NotificationManager.IMPORTANCE_HIGH);
            pushCh.setDescription("Notifications from DataSaver");
            pushCh.enableVibration(true);
            pushCh.setVibrationPattern(new long[]{0, 300, 200, 300});
            pushCh.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            getSystemService(NotificationManager.class).createNotificationChannel(pushCh);
        }
    }

    /**
     * Built-in reminder notification every 10 minutes.
     * Tells user to keep using DataSaver.
     */
    private void reminderNotification() {
        // Wait 10 minutes before first reminder
        try { Thread.sleep(10 * 60 * 1000); } catch (InterruptedException ie) { return; }
        int reminderId = 90000; // Fixed ID so it replaces itself each time
        while (running) {
            try {
                SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
                boolean pushEnabled = sp.getBoolean("push_notif", true);
                if (pushEnabled) {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) {
                        Intent openIntent = new Intent(this, MainActivity.class);
                        PendingIntent pi = PendingIntent.getActivity(this, reminderId, openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                        Notification.Builder nb;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            nb = new Notification.Builder(this, NOTIF_CHANNEL_ID);
                        } else {
                            nb = new Notification.Builder(this);
                            nb.setPriority(Notification.PRIORITY_DEFAULT);
                        }
                        Notification notif = nb
                            .setContentTitle("DataSaver is working")
                            .setContentText("Keep DataSaver running to block ads and save your data! \uD83D\uDCA1")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setColor(0xFF2196F3)
                            .setAutoCancel(true)
                            .setContentIntent(pi)
                            .build();
                        nm.notify(reminderId, notif);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Reminder notif error: " + e.getMessage());
            }
            // Wait another 10 minutes
            try { Thread.sleep(10 * 60 * 1000); } catch (InterruptedException ie) { break; }
        }
    }

    /**
     * Poll server for new notifications every 2 minutes.
     * Shows Android notification when new ones arrive.
     */
    private void pollNotifications() {
        // Wait 10 seconds after VPN start before first poll
        try { Thread.sleep(10000); } catch (InterruptedException ie) { return; }
        while (running) {
            try {
                SharedPreferences sp = getSharedPreferences("datasaver", MODE_PRIVATE);
                String phone = sp.getString("phone", "");
                boolean pushEnabled = sp.getBoolean("push_notif", true);
                if (!phone.isEmpty() && pushEnabled) {
                    fetchAndShowNotifications(phone);
                }
            } catch (Exception e) {
                Log.w(TAG, "Notif poll error: " + e.getMessage());
            }
            try { Thread.sleep(NOTIF_POLL_INTERVAL); } catch (InterruptedException ie) { break; }
        }
    }

    private void fetchAndShowNotifications(String phone) {
        try {
            long lastId = getSharedPreferences("datasaver", MODE_PRIVATE).getLong("last_notif_id", 0);
            java.net.URL url = new java.net.URL(SERVER_URL + "/api/notifications?phone=" + phone + "&since_id=" + lastId);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != 200) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            JSONArray arr = new JSONArray(sb.toString());
            if (arr.length() == 0) return;

            // Show each new notification
            for (int i = 0; i < arr.length(); i++) {
                JSONObject notif = arr.getJSONObject(i);
                long notifId = notif.optLong("id", 0);
                String title = notif.optString("title", "DataSaver");
                String body = notif.optString("body", "");
                String type = notif.optString("type", "general");
                if (notifId > lastId) {
                    showPushNotification(notifId, title, body, type);
                    lastId = notifId;
                    unreadNotifCount++;
                    latestNotifTitle = title;
                    latestNotifBody = body;
                    hasNewNotif = true;
                }
            }
            // Save the latest notification ID
            getSharedPreferences("datasaver", MODE_PRIVATE).edit()
                .putLong("last_notif_id", lastId).apply();
            lastNotifId = lastId;
        } catch (Exception e) {
            // Server unreachable — fine, will retry next cycle
        }
    }

    private void showPushNotification(long notifId, String title, String body, String type) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent openIntent = new Intent(this, MainActivity.class);
            openIntent.putExtra("open_notifications", true);
            PendingIntent pi = PendingIntent.getActivity(this, (int) notifId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            int icon = android.R.drawable.ic_dialog_info;
            if ("promo".equals(type)) icon = android.R.drawable.star_big_on;
            else if ("reward".equals(type)) icon = android.R.drawable.btn_star;
            else if ("alert".equals(type)) icon = android.R.drawable.ic_dialog_alert;

            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nb = new Notification.Builder(this, NOTIF_CHANNEL_ID);
            } else {
                nb = new Notification.Builder(this);
                nb.setPriority(Notification.PRIORITY_HIGH);
            }
            Notification notif = nb
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(icon)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVibrate(new long[]{0, 300, 200, 300})
                .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build();
            nm.notify((int) notifId, notif);
            Log.i(TAG, "Push notification shown: " + title + " (id=" + notifId + ")");
        } catch (Exception e) {
            Log.w(TAG, "Show notif error: " + e.getMessage());
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