package com.datasaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private Button btnConnect;
    private TextView tvStatus, tvUsed, tvSaved, tvSavedPct, tvAppUsageEmpty;
        
        // Server data for homepage display
        private long serverTodaySaved = 0, serverWeekSaved = 0, serverMonthSaved = 0, serverAllTimeSaved = 0;
        private long serverTodayBlocked = 0, serverWeekBlocked = 0, serverMonthBlocked = 0, serverAllTimeBlocked = 0;
    private View barUsed, barSaved;
    private LinearLayout appUsageContainer;
    private TextView navHome, navAirtime, navData, navTransactions, navEarn, navProfile;
    private ScrollView tabHome, tabAirtime, tabData, tabTransactions, tabEarn, tabProfile;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;
        private boolean wasVpnRunning = false;

    // Airtime/Data tab
    private TextView toggleAirtime, toggleData;
    private LinearLayout airtimeSection, dataSection, dataPlansContainer;
    private TextView tvSelectNetwork;
    private Button btnBuy;
    private EditText etPhone, etAirtimeAmount;
    private TextView btnMTN, btnAirtel, btnGlo, btn9mobile;

    private String selectedNetwork = null;
    private boolean isDataMode = false;
    private int selectedAirtimeAmount = 0;
    private int selectedDataPlanIndex = -1;

    private static final String SERVER_URL = "https://datasaver-server.onrender.com";
    private static final int PICK_PHOTO = 1001;
    private ArrayList<JSONObject> fetchedPlans = new ArrayList<>();
    private TextView tvWalletBalance, tvSavedValue;
    private boolean showAllApps = false;
    private String usageFilter = "today";

    // Tips
    private int currentTipIndex = 0;
    private static final String[] DATA_TIPS = {
        "\ud83d\udcf1 Disable auto-play videos on Instagram, Facebook, and TikTok. Videos use 10x more data than images.",
        "\ud83d\udce5 Download music and videos on WiFi for offline use. Streaming a song uses ~5MB each time.",
        "\ud83d\uddbc Set WhatsApp to only download media on WiFi: Settings \u2192 Storage \u2192 Media auto-download.",
        "\ud83d\udd04 Turn off automatic app updates on mobile data: Play Store \u2192 Settings \u2192 WiFi only.",
        "\ud83d\udccd Disable background data for apps you don't need: Settings \u2192 Apps \u2192 Mobile data \u2192 Background OFF.",
        "\ud83c\udf10 Use lite versions of apps: Facebook Lite, Twitter Lite, Instagram Lite \u2014 they use 50-80% less data.",
        "\ud83d\udce7 Set email to fetch manually instead of push. Push email checks constantly and wastes data.",
        "\ud83d\uddfa Download Google Maps areas offline. Map loading uses a lot of data when navigating.",
        "\ud83d\udcf8 Upload photos to cloud only on WiFi. A single photo backup can use 3-5MB.",
        "\ud83d\udd14 Disable unnecessary push notifications. Each notification check uses a small amount of data.",
        "\ud83d\udcac Send photos as documents on WhatsApp \u2014 they won't be compressed and re-downloaded multiple times.",
        "\ud83d\udcca Check which apps use the most data in this app and restrict the heavy ones."
    };

    // Data price table: {maxBytes, priceNaira}
    private static final long[][] DATA_PRICES = {
        {250L * 1024 * 1024, 150},
        {2L * 1024 * 1024 * 1024, 1500},
        {3L * 1024 * 1024 * 1024, 2000},
        {4L * 1024 * 1024 * 1024, 2500},
        {8L * 1024 * 1024 * 1024, 3000},
        {10L * 1024 * 1024 * 1024, 4000},
        {18L * 1024 * 1024 * 1024, 6000},
        {25L * 1024 * 1024 * 1024, 8000},
        {35L * 1024 * 1024 * 1024, 10000},
        {60L * 1024 * 1024 * 1024, 15000},
        {100L * 1024 * 1024 * 1024, 20000},
        {160L * 1024 * 1024 * 1024, 30000},
    };

    private static double bytesToNaira(long bytes) {
        if (bytes <= 0) return 0;
        // Value: 1 MB = ₦1
        double nairaPerMB = 1.0;
        double mb = bytes / (1024.0 * 1024.0);
        return mb * nairaPerMB;
    }

    private static String formatNaira(double amount) {
        if (amount <= 0) return "\u20a60";
        if (amount >= 1000) return String.format("\u20a6%,.0f", amount);
        // Show decimals for small amounts
        return String.format("\u20a6%.2f", amount);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = findViewById(R.id.btnConnect);
        tvStatus = findViewById(R.id.tvStatus);
        tvUsed = findViewById(R.id.tvUsed);
        tvSaved = findViewById(R.id.tvSaved);
        tvSavedPct = findViewById(R.id.tvSavedPct);
        tvAppUsageEmpty = findViewById(R.id.tvAppUsageEmpty);
        barUsed = findViewById(R.id.barUsed);
        barSaved = findViewById(R.id.barSaved);
        appUsageContainer = findViewById(R.id.appUsageContainer);

        navHome = findViewById(R.id.navHome);
        navAirtime = findViewById(R.id.navAirtime);
        navData = findViewById(R.id.navData);
        navTransactions = findViewById(R.id.navTransactions);
        navEarn = findViewById(R.id.navEarn);
        navProfile = findViewById(R.id.navProfile);

        tabHome = findViewById(R.id.contentArea);
        tabAirtime = findViewById(R.id.tabAirtime);
        tabData = findViewById(R.id.tabData);
        tabTransactions = findViewById(R.id.tabTransactions);
        tabEarn = findViewById(R.id.tabEarn);
        tabProfile = findViewById(R.id.tabProfile);

        toggleAirtime = findViewById(R.id.toggleAirtime);
        toggleData = findViewById(R.id.toggleData);
        airtimeSection = findViewById(R.id.airtimeSection);
        dataSection = findViewById(R.id.dataSection);
        dataPlansContainer = findViewById(R.id.dataPlansContainer);
        tvSelectNetwork = findViewById(R.id.tvSelectNetwork);
        btnBuy = findViewById(R.id.btnBuy);
        etPhone = findViewById(R.id.etPhone);
        etAirtimeAmount = findViewById(R.id.etAirtimeAmount);
        btnMTN = findViewById(R.id.btnMTN);
        btnAirtel = findViewById(R.id.btnAirtel);
        btnGlo = findViewById(R.id.btnGlo);
        btn9mobile = findViewById(R.id.btn9mobile);

        btnConnect.setOnClickListener(v -> toggle());
        navHome.setOnClickListener(v -> switchTab(0));
        navAirtime.setOnClickListener(v -> switchTab(1));
        navData.setOnClickListener(v -> switchTab(2));
        navTransactions.setOnClickListener(v -> switchTab(3));
        navEarn.setOnClickListener(v -> switchTab(4));
        navProfile.setOnClickListener(v -> switchTab(5));

        btnMTN.setOnClickListener(v -> selectNetwork("MTN"));
        btnAirtel.setOnClickListener(v -> selectNetwork("AIRTEL"));
        btnGlo.setOnClickListener(v -> selectNetwork("GLO"));
        btn9mobile.setOnClickListener(v -> selectNetwork("9MOBILE"));
        setupPhoneNetworkAutoDetect();
        // Set network logos (scaled to fit)
        setScaledLogo(btnMTN, R.drawable.logo_mtn, "MTN");
        setScaledLogo(btnAirtel, R.drawable.logo_airtel, "Airtel");
        setScaledLogo(btnGlo, R.drawable.logo_glo, "Glo");
        setScaledLogo(btn9mobile, R.drawable.logo_9mobile, "9mobile");

        // Create notification channel for push notifications
        createNotificationChannel();

        toggleAirtime.setOnClickListener(v -> setMode(false));
        toggleData.setOnClickListener(v -> setMode(true));

        setupAirtimeButtons();
        btnBuy.setOnClickListener(v -> onBuy());
        initHistoryTab();
        initProfileTab();
        initLogin();
        tvWalletBalance = findViewById(R.id.tvWalletBalance);
        tvWalletBalance.setOnClickListener(v -> showTopUpPrompt("Add money to your wallet"));
        findViewById(R.id.btnAddFunds).setOnClickListener(v -> showTopUpPrompt("Add money to your wallet"));
        // Warm up server first, then fetch data (prevents timeout on cold start)
        warmUpServerThenLoad();
        // Start foreground service for reliable background notification polling
        NotificationPollService.start(this);
        // Ask for usage permission immediately on first launch
        if (!hasUsagePermission()) {
            new AlertDialog.Builder(this)
                .setTitle("Enable App Data Tracking")
                .setMessage("To show which apps are using your data, Acorn Datasaver needs Usage Access permission.\n\n1. Tap ALLOW below\n2. Find 'Acorn Datasaver' in the list\n3. Turn it ON\n4. Come back to the app")
                .setPositiveButton("Allow", (d, w) -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                .setNegativeButton("Skip", null)
                .setCancelable(false)
                .show();
        }
        
        // Ask for notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
        
        tvSavedValue = findViewById(R.id.tvSavedValue);

        // Load app usage in background (show loading state first)
        tvAppUsageEmpty.setVisibility(View.VISIBLE);
        tvAppUsageEmpty.setText("Loading your data usage...");
        new Thread(() -> {
            buildIconCache();
            handler.post(() -> loadAppUsageBackground());
        }).start();

        findViewById(R.id.btnViewAll).setOnClickListener(v -> {
            showAllApps = !showAllApps;
            ((TextView) v).setText(showAllApps ? "Show Less" : "View All >");
            updateAppCards();
        });

        // Protected Apps
        findViewById(R.id.btnProtectedApps).setOnClickListener(v -> showProtectedAppsPage());

        // Time filters
        TextView filterToday = findViewById(R.id.filterToday);
        TextView filterWeek = findViewById(R.id.filterWeek);
        TextView filterMonth = findViewById(R.id.filterMonth);
        filterToday.setOnClickListener(v -> applyFilter("today", filterToday, filterWeek, filterMonth));
        filterWeek.setOnClickListener(v -> applyFilter("week", filterToday, filterWeek, filterMonth));
        filterMonth.setOnClickListener(v -> applyFilter("month", filterToday, filterWeek, filterMonth));

        restoreSavedStats();
        // Load real savings from server (survives restarts)
        loadSavingsFromServer();
        // Restore VPN counters from SharedPreferences
        long savedAdReqs = prefs().getLong("real_ad_requests", 0);
        long savedBgSyncs = prefs().getLong("real_bg_syncs", 0);
        if (savedAdReqs > DataSaverVpnService.blockedAdRequests.get()) DataSaverVpnService.blockedAdRequests.set(savedAdReqs);
        if (savedBgSyncs > DataSaverVpnService.blockedBgSyncs.get()) DataSaverVpnService.blockedBgSyncs.set(savedBgSyncs);
        // Show loading state until data is ready
        tvUsed.setText("...");
        tvSaved.setText("...");
        tvSavedPct.setText("...");
        updateUI();
        // Delay summary update until background data loads
        handler.postDelayed(() -> { updateSummary(); updateAppCards(); }, 1500);
        initTips();
        initSavingsCard();
        initFingerprintLogin();
        initEarnTab();
        initNotificationBell();

        // Handle notification open intent
        if (getIntent() != null && getIntent().getBooleanExtra("open_notifications", false)) {
            handler.postDelayed(() -> showNotificationInbox(), 500);
        }

        // Handle referral link (datasaver.app/ref/CODE)
        if (getIntent() != null && getIntent().getData() != null) {
            String uri = getIntent().getData().toString();
            if (uri.contains("/ref/")) {
                String code = uri.substring(uri.lastIndexOf("/ref/") + 5);
                if (!code.isEmpty()) {
                    prefs().edit().putString("pending_referral_code", code).apply();
                    Toast.makeText(this, "Referral link detected! Sign up to earn rewards.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // ==================== NOTIFICATIONS ====================

    private TextView tvNotifBadge;

    private void initNotificationBell() {
        tvNotifBadge = findViewById(R.id.tvNotifBadge);
        findViewById(R.id.btnNotifBell).setOnClickListener(v -> showNotificationInbox());
        // Restore unread count
        DataSaverVpnService.unreadNotifCount = prefs().getInt("unread_notif_count", 0);
        updateNotifBadge();
    }

    private void updateNotifBadge() {
        int count = DataSaverVpnService.unreadNotifCount;
        if (count > 0) {
            tvNotifBadge.setVisibility(View.VISIBLE);
            tvNotifBadge.setText(count > 99 ? "99+" : String.valueOf(count));
        } else {
            tvNotifBadge.setVisibility(View.GONE);
        }
    }

    private void showNotificationInbox() {
        // Launch standalone notifications activity instead of popup
        startActivity(new android.content.Intent(this, NotificationsActivity.class));
        
        // Reset unread count
        DataSaverVpnService.unreadNotifCount = 0;
        DataSaverVpnService.hasNewNotif = false;
        prefs().edit().putInt("unread_notif_count", 0).apply();
        updateNotifBadge();
    }

    // ==================== TIPS ====================

    private void initTips() {
        TextView tvTip = findViewById(R.id.tvTip);
        TextView btnNextTip = findViewById(R.id.btnNextTip);
        if (tvTip == null || btnNextTip == null) return;
        updateTipStyle(tvTip);
        btnNextTip.setOnClickListener(v -> {
            currentTipIndex = (currentTipIndex + 1) % DATA_TIPS.length;
            updateTipStyle(tvTip);
        });
        // Auto-rotate every 8 seconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                currentTipIndex = (currentTipIndex + 1) % DATA_TIPS.length;
                updateTipStyle(tvTip);
                handler.postDelayed(this, 8000);
            }
        }, 8000);
    }

    private void updateTipStyle(TextView tvTip) {
        tvTip.setText(DATA_TIPS[currentTipIndex]);
        tvTip.setTextColor(0xFF333333);
        LinearLayout tipContainer = (LinearLayout) tvTip.getParent();
        if (tipContainer != null) {
            tipContainer.setBackground(getResources().getDrawable(R.drawable.card_bg));
        }
    }

    // ==================== SAVINGS CARD ====================

    private void initSavingsCard() {
        // Updated in updateSummary()
    }

    /** Load savings from server database - survives phone restarts */
    private void loadSavingsFromServer() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) return;
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/savings/" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(45000);
                conn.setReadTimeout(45000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                long serverBlocked = res.optLong("total_blocked", 0);
                // Use server value if higher than local (only real counts)
                if (serverBlocked > DataSaverVpnService.blockedAdRequests.get() + DataSaverVpnService.blockedBgSyncs.get()) {
                    long currentAdR = DataSaverVpnService.blockedAdRequests.get();
                    long currentBgR = DataSaverVpnService.blockedBgSyncs.get();
                    if (currentAdR + currentBgR > 0) {
                        double ratio = (double) serverBlocked / (currentAdR + currentBgR);
                        DataSaverVpnService.blockedAdRequests.set((long)(currentAdR * ratio));
                        DataSaverVpnService.blockedBgSyncs.set(serverBlocked - (long)(currentAdR * ratio));
                    } else {
                        DataSaverVpnService.blockedAdRequests.set(serverBlocked);
                    }
                    prefs().edit()
                        .putLong("real_ad_requests", DataSaverVpnService.blockedAdRequests.get())
                        .putLong("real_bg_syncs", DataSaverVpnService.blockedBgSyncs.get())
                        .apply();
                }
                // Store period breakdowns
                JSONObject today = res.optJSONObject("today");
                JSONObject week = res.optJSONObject("week");
                JSONObject month = res.optJSONObject("month");
                if (today != null) prefs().edit().putLong("savings_today", today.optLong("saved", 0)).apply();
                if (week != null) prefs().edit().putLong("savings_week", week.optLong("saved", 0)).apply();
                if (month != null) prefs().edit().putLong("savings_month", month.optLong("saved", 0)).apply();
                handler.post(() -> updateSummary());
            } catch (Exception e) {
                // Server unavailable, use local data
            }
        }).start();
    }

    // ==================== FINGERPRINT LOGIN ====================

    private void initFingerprintLogin() {
        TextView btnFingerprint = findViewById(R.id.btnFingerprint);
        if (btnFingerprint == null) return;

        if (Build.VERSION.SDK_INT >= 28) {
            boolean canBiometric = false;
            try {
                android.hardware.fingerprint.FingerprintManager fm =
                    (android.hardware.fingerprint.FingerprintManager) getSystemService(Context.FINGERPRINT_SERVICE);
                canBiometric = fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
            } catch (Exception e) {}

            if (canBiometric) {
                btnFingerprint.setVisibility(View.VISIBLE);
                btnFingerprint.setOnClickListener(v -> showBiometricPrompt());
                // Auto-prompt if user has logged in before
                if (prefs().getString("phone", "").length() > 0
                    && loginOverlay != null && loginOverlay.getVisibility() == View.VISIBLE) {
                    handler.postDelayed(() -> showBiometricPrompt(), 500);
                }
            } else {
                btnFingerprint.setVisibility(View.GONE);
            }
        } else {
            btnFingerprint.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("deprecation")
    private void showBiometricPrompt() {
        // Only allow fingerprint if we have a saved phone number
        String savedPhone = prefs().getString("phone", "");
        if (savedPhone.isEmpty()) {
            Toast.makeText(this, "Please login with your phone number first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            android.hardware.biometrics.BiometricPrompt.Builder builder =
                new android.hardware.biometrics.BiometricPrompt.Builder(this);
            builder.setTitle("DataSaver Login");
            builder.setSubtitle("Use your fingerprint to login as " + savedPhone);
            builder.setNegativeButton("Use PIN", getMainExecutor(), (dialog, which) -> {});
            android.hardware.biometrics.BiometricPrompt prompt = builder.build();
            prompt.authenticate(
                new android.os.CancellationSignal(),
                getMainExecutor(),
                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        // Load user data from server using saved phone
                        loginOverlay.setVisibility(View.GONE);
                        findViewById(R.id.bottomNav).setVisibility(View.VISIBLE);
                        fetchWalletBalance(); // This also syncs name/email from server
                        refreshProfileUI();
                        loadAppUsageBackground();
                        // Initialize and refresh referral stats
                        if (earnTasksContainer == null) initEarnTab();
                        loadReferralStats();
                        String name = prefs().getString("name", "");
                        String welcome = (name.isEmpty() || "null".equals(name)) ? "User" : name;
                        Toast.makeText(MainActivity.this, "Welcome back, " + welcome + "!", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        if (errorCode != 10 && errorCode != 13) {
                            Toast.makeText(MainActivity.this, "Auth error: " + errString, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            );
        }
    }

    // ==================== LOGIN / SIGNUP ====================

    private ScrollView loginOverlay;
    private LinearLayout loginForm, signupForm, paystackOverlay;
    private WebView paystackWebView;
    private boolean isSignupMode = false;

    private void initLogin() {
        loginOverlay = findViewById(R.id.loginOverlay);
        loginForm = findViewById(R.id.loginForm);
        signupForm = findViewById(R.id.signupForm);
        paystackOverlay = findViewById(R.id.paystackOverlay);
        paystackWebView = findViewById(R.id.paystackWebView);

        String phone = prefs().getString("phone", "");
        // Migrate existing installs: record the current logged-in phone as savings owner so a post-update re-login doesn't wipe their data
        SharedPreferences devPrefs = getSharedPreferences("datasaver_device", MODE_PRIVATE);
        if (devPrefs.getString("savings_owner", "").isEmpty() && !phone.isEmpty()) {
            devPrefs.edit().putString("savings_owner", phone).apply();
        }
        if (phone.isEmpty()) {
            loginOverlay.setVisibility(View.VISIBLE);
            // New users see signup first
            switchAuthTab(true, (TextView) findViewById(R.id.tabLogin), (TextView) findViewById(R.id.tabSignup));
        }

            loginOverlay.setVisibility(View.VISIBLE);


        TextView tabLogin = findViewById(R.id.tabLogin);
        TextView tabSignup = findViewById(R.id.tabSignup);
        tabLogin.setOnClickListener(v -> switchAuthTab(false, tabLogin, tabSignup));
        tabSignup.setOnClickListener(v -> switchAuthTab(true, tabLogin, tabSignup));
        TextView tvLoginStatus = findViewById(R.id.tvLoginStatus);

        // LOGIN button
        findViewById(R.id.btnDoLogin).setOnClickListener(v -> {
            String identity = ((EditText) findViewById(R.id.loginIdentity)).getText().toString().trim();
            String pin = ((EditText) findViewById(R.id.loginPinField)).getText().toString().trim();
            if (identity.isEmpty()) { showAuthError(tvLoginStatus, "Enter your email or phone number"); return; }
            if (pin.length() < 4) { showAuthError(tvLoginStatus, "Enter your 4-digit PIN"); return; }

            ((Button) v).setEnabled(false);
            ((Button) v).setText("Logging in...");
            tvLoginStatus.setVisibility(View.GONE);

            new Thread(() -> {
                try {
                    URL url = new URL(SERVER_URL + "/api/login");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(45000);
                    conn.setReadTimeout(45000);
                    conn.setDoOutput(true);
                    JSONObject body = new JSONObject();
                    if (identity.contains("@")) body.put("email", identity);
                    else body.put("phone", identity);
                    body.put("pin", pin);
                    body.put("device_id", getDeviceId());
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes());
                    os.close();
                    int code = conn.getResponseCode();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject res = new JSONObject(sb.toString());
                    handler.post(() -> {
                        ((Button) v).setEnabled(true);
                        ((Button) v).setText("LOGIN");
                        if (code < 400 && res.optBoolean("success")) {
                            String rName = res.isNull("name") ? "" : res.optString("name", "");
                            String rEmail = res.isNull("email") ? "" : res.optString("email", "");
                            String rPhone = res.optString("phone", "");
                            handleUserSwitch(rPhone);
                            prefs().edit()
                                .putString("user_id", res.optString("user_id"))
                                .putString("name", rName)
                                .putString("phone", rPhone)
                                .putString("email", rEmail)
                                .putString("subscription_plan", res.optString("subscription_plan", "none"))
                                .putString("referral_code", res.optString("referral_code", ""))
                                .apply();
                            loginOverlay.setVisibility(View.GONE);
                            findViewById(R.id.bottomNav).setVisibility(View.VISIBLE);
                            refreshProfileUI();
                            fetchWalletBalance();
                            preloadEarnTab();
                            loadReferralStats();
                            String welcome = (rName.isEmpty() || "null".equals(rName)) ? "User" : rName;
                            Toast.makeText(this, "Welcome back, " + welcome + "!", Toast.LENGTH_SHORT).show();
                        } else {
                            showAuthError(tvLoginStatus, res.optString("error", "Login failed"));
                        }
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        ((Button) v).setEnabled(true);
                        ((Button) v).setText("LOGIN");
                        showAuthError(tvLoginStatus, "Connection failed. Check your internet.");
                    });
                }
            }).start();
        });

        // SIGNUP button
        findViewById(R.id.btnDoSignup).setOnClickListener(v -> {
            String name = ((EditText) findViewById(R.id.signupName)).getText().toString().trim();
            String ph = ((EditText) findViewById(R.id.signupPhone)).getText().toString().trim();
            String email = ((EditText) findViewById(R.id.signupEmail)).getText().toString().trim();
            String pin = ((EditText) findViewById(R.id.signupPin)).getText().toString().trim();
            String enteredRef = ((EditText) findViewById(R.id.signupReferral)).getText().toString().trim().toUpperCase();
            if (name.isEmpty()) { showAuthError(tvLoginStatus, "Enter your name"); return; }
            if (ph.length() < 10) { showAuthError(tvLoginStatus, "Enter a valid phone number"); return; }
            if (email.isEmpty() || !email.contains("@")) { showAuthError(tvLoginStatus, "Enter a valid email address"); return; }
            if (pin.length() < 4) { showAuthError(tvLoginStatus, "Create a 4-digit PIN"); return; }

            ((Button) v).setEnabled(false);
            ((Button) v).setText("Creating account...");
            tvLoginStatus.setVisibility(View.GONE);

            new Thread(() -> {
                try {
                    URL url = new URL(SERVER_URL + "/api/register");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(45000);
                    conn.setReadTimeout(45000);
                    conn.setDoOutput(true);
                    JSONObject body = new JSONObject();
                    body.put("email", email);
                    body.put("pin", pin);
                    body.put("name", name);
                    body.put("phone", ph);
                    // Include referral code: manually entered field takes priority, then deep-link code
                    String refCode = enteredRef;
                    if (refCode.isEmpty()) refCode = getIntent() != null ? getIntent().getStringExtra("referral_code") : null;
                    if (refCode == null || refCode.isEmpty()) refCode = prefs().getString("pending_referral_code", "");
                    if (refCode != null && !refCode.isEmpty()) body.put("referral_code", refCode.trim().toUpperCase());
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes());
                    os.close();
                    int code = conn.getResponseCode();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject res = new JSONObject(sb.toString());
                    handler.post(() -> {
                        ((Button) v).setEnabled(true);
                        ((Button) v).setText("CREATE ACCOUNT");
                        if (code < 400 && res.optBoolean("success")) {
                            handleUserSwitch(ph);
                            prefs().edit()
                                .putString("user_id", res.optString("user_id"))
                                .putString("name", name)
                                .putString("phone", ph)
                                .putString("email", email)
                                .putString("password", pin)
                                .putString("referral_code", res.optString("referral_code", ""))
                                .apply();
                            // Clear pending referral
                            prefs().edit().remove("pending_referral_code").apply();
                            loginOverlay.setVisibility(View.GONE);
                            findViewById(R.id.bottomNav).setVisibility(View.VISIBLE);
                            refreshProfileUI();
                            fetchWalletBalance();
                            preloadEarnTab();
                            loadReferralStats();
                            String regMsg = res.optString("message", "Account created");
                            if (res.optBoolean("referral_applied")) {
                                Toast.makeText(this, regMsg, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
                                if (regMsg.contains("referral")) {
                                    Toast.makeText(this, regMsg, Toast.LENGTH_LONG).show();
                                }
                            }
                        } else {
                            showAuthError(tvLoginStatus, res.optString("error", "Registration failed"));
                        }
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        ((Button) v).setEnabled(true);
                        ((Button) v).setText("CREATE ACCOUNT");
                        showAuthError(tvLoginStatus, "Connection failed. Check your internet.");
                    });
                }
            }).start();
        });

        // Paystack WebView setup
        paystackWebView.getSettings().setJavaScriptEnabled(true);
        paystackWebView.getSettings().setDomStorageEnabled(true);
        paystackWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("/api/wallet/callback")) {
                    paystackOverlay.setVisibility(View.GONE);
                    fetchWalletBalance();
                    verifyPendingPayment();
                    Toast.makeText(MainActivity.this, "Payment processing...", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.btnClosePaystack).setOnClickListener(v -> {
            paystackOverlay.setVisibility(View.GONE);
            fetchWalletBalance();
            verifyPendingPayment();
        });
    }

    private void switchAuthTab(boolean signup, TextView tabLogin, TextView tabSignup) {
        isSignupMode = signup;
        if (signup) {
            tabSignup.setBackground(getResources().getDrawable(R.drawable.pill_active));
            tabSignup.setTextColor(0xFFFFFFFF);
            tabLogin.setBackground(getResources().getDrawable(R.drawable.pill_bg));
            tabLogin.setTextColor(0xFF666666);
            loginForm.setVisibility(View.GONE);
            signupForm.setVisibility(View.VISIBLE);
        } else {
            tabLogin.setBackground(getResources().getDrawable(R.drawable.pill_active));
            tabLogin.setTextColor(0xFFFFFFFF);
            tabSignup.setBackground(getResources().getDrawable(R.drawable.pill_bg));
            tabSignup.setTextColor(0xFF666666);
            loginForm.setVisibility(View.VISIBLE);
            signupForm.setVisibility(View.GONE);
        }
        findViewById(R.id.tvLoginStatus).setVisibility(View.GONE);
    }

    private void showAuthError(TextView tv, String msg) {
        tv.setText(msg);
        tv.setTextColor(0xFFC62828);
        tv.setVisibility(View.VISIBLE);
    }

    private void warmUpServerThenLoad() {
        // Ping /health first — wakes up Render server before real API calls
        // If server responds fast (already awake), load immediately
        // If slow (cold start), wait for it then load — no timeout for user
        new Thread(() -> {
            long start = System.currentTimeMillis();
            boolean awake = false;
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(SERVER_URL + "/health").openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    if (code == 200) { awake = true; break; }
                } catch (Exception e) {
                    // Server still waking up — wait and retry
                    try { Thread.sleep(6000); } catch (InterruptedException ie) { break; }
                }
            }
            // Server is awake (or we gave up after 5 tries) — now load real data
            handler.post(() -> {
                fetchWalletBalance();
                preloadEarnTab();
            });
        }).start();
    }

    private void fetchWalletBalance() {
        fetchWalletBalanceWithRetry(0);
    }

    private void fetchWalletBalanceWithRetry(int attempt) {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { tvWalletBalance.setText("--"); return; }
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/user/" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(45000);
                conn.setReadTimeout(45000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                double bal = res.optDouble("wallet_balance", 0);
                String serverName = res.isNull("name") ? "" : res.optString("name", "");
                String serverEmail = res.isNull("email") ? "" : res.optString("email", "");
                if (!serverName.isEmpty() && !"null".equals(serverName)) {
                    prefs().edit().putString("name", serverName).apply();
                }
                if (!serverEmail.isEmpty() && !"null".equals(serverEmail)) {
                    prefs().edit().putString("email", serverEmail).apply();
                }
                String serverPhoto = res.isNull("photo_base64") ? "" : res.optString("photo_base64", "");
                if (!serverPhoto.isEmpty() && !"null".equals(serverPhoto)) {
                    try {
                        byte[] photoBytes = android.util.Base64.decode(serverPhoto, android.util.Base64.NO_WRAP);
                        Bitmap photoBmp = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.length);
                        if (photoBmp != null) {
                            File pf = new File(getFilesDir(), "profile.jpg");
                            FileOutputStream pfos = new FileOutputStream(pf);
                            photoBmp.compress(Bitmap.CompressFormat.JPEG, 80, pfos);
                            pfos.close();
                            prefs().edit().putString("photo_path", pf.getAbsolutePath()).apply();
                            handler.post(() -> { setCircularPhoto(photoBmp); });
                        }
                    } catch (Exception pe) {}
                }
                handler.post(() -> {
                    tvWalletBalance.setText(String.format("\u20a6%.0f", bal));
                    refreshProfileUI();
                });
            } catch (Exception e) {
                if (attempt < 2) {
                    // Retry after 5s (server may be waking up)
                    handler.postDelayed(() -> fetchWalletBalanceWithRetry(attempt + 1), 5000);
                } else {
                    handler.post(() -> tvWalletBalance.setText("\u20a60"));
                }
            }
        }).start();
    }

    // ==================== AIRTIME/DATA TAB ====================

    private void setupAirtimeButtons() {
        int[] ids = {R.id.air100, R.id.air200, R.id.air500, R.id.air1000,
                     R.id.air2000, R.id.air3000, R.id.air5000, R.id.air10000};
        int[] amounts = {100, 200, 500, 1000, 2000, 3000, 5000, 10000};
        for (int i = 0; i < ids.length; i++) {
            final int amt = amounts[i];
            findViewById(ids[i]).setOnClickListener(v -> selectAirtimeAmount(amt));
        }
    }

    private void selectAirtimeAmount(int amount) {
        selectedAirtimeAmount = amount;
        etAirtimeAmount.setText(String.valueOf(amount));
        int[] ids = {R.id.air100, R.id.air200, R.id.air500, R.id.air1000,
                     R.id.air2000, R.id.air3000, R.id.air5000, R.id.air10000};
        int[] amounts = {100, 200, 500, 1000, 2000, 3000, 5000, 10000};
        for (int i = 0; i < ids.length; i++) {
            TextView tv = findViewById(ids[i]);
            if (amounts[i] == amount) {
                tv.setBackgroundColor(0xFFC62828);
                tv.setTextColor(0xFFFFFFFF);
            } else {
                tv.setBackgroundColor(0xFFFFFFFF);
                tv.setTextColor(0xFFC62828);
            }
        }
    }

    private void selectNetwork(String network) {
        selectedNetwork = network;
        btnMTN.setAlpha(0.3f);
        btnAirtel.setAlpha(0.3f);
        btnGlo.setAlpha(0.3f);
        btn9mobile.setAlpha(0.3f);
        // Highlight selected with full opacity and blue border
        TextView selected = null;
        if ("MTN".equals(network)) selected = btnMTN;
        else if ("AIRTEL".equals(network)) selected = btnAirtel;
        else if ("GLO".equals(network)) selected = btnGlo;
        else if ("9MOBILE".equals(network)) selected = btn9mobile;
        if (selected != null) {
            selected.setAlpha(1.0f);
            selected.setBackgroundColor(0xFFE3F2FD);
        }
        if (isDataMode) fetchAndLoadPlans();
    }

    private String getDeviceId() {
        SharedPreferences dev = getSharedPreferences("datasaver_device", MODE_PRIVATE);
        String saved = dev.getString("device_id", "");
        if (!saved.isEmpty()) return saved;
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isEmpty()) id = "unknown-" + System.currentTimeMillis();
        dev.edit().putString("device_id", id).apply();
        return id;
    }

    private void setupPhoneNetworkAutoDetect() {
        etPhone.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String network = detectNetworkFromPhone(s.toString());
                if (network != null) selectNetwork(network);
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    private String normalizeNigerianPhone(String input) {
        if (input == null) return "";
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.startsWith("234") && digits.length() >= 13) {
            digits = "0" + digits.substring(3);
        } else if (digits.length() == 10 && digits.charAt(0) != '0') {
            digits = "0" + digits;
        }
        return digits;
    }

    private String detectNetworkFromPhone(String input) {
        String phone = normalizeNigerianPhone(input);
        if (phone.length() >= 5) {
            String prefix5 = phone.substring(0, 5);
            if ("07025".equals(prefix5) || "07026".equals(prefix5)) return "MTN";
        }
        if (phone.length() < 4) return null;

        String prefix = phone.substring(0, 4);
        String[] mtn = {"0803", "0806", "0703", "0706", "0810", "0813", "0814", "0816", "0903", "0906", "0913", "0916", "0704"};
        String[] airtel = {"0802", "0808", "0708", "0701", "0812", "0901", "0902", "0904", "0907", "0912"};
        String[] glo = {"0805", "0807", "0705", "0811", "0815", "0905", "0915"};
        String[] nineMobile = {"0809", "0817", "0818", "0908", "0909"};

        for (String p : mtn) if (prefix.equals(p)) return "MTN";
        for (String p : airtel) if (prefix.equals(p)) return "AIRTEL";
        for (String p : glo) if (prefix.equals(p)) return "GLO";
        for (String p : nineMobile) if (prefix.equals(p)) return "9MOBILE";
        return null;
    }

    private void setScaledLogo(TextView btn, int drawableRes, String label) {
        try {
            Drawable d = getResources().getDrawable(drawableRes);
            int size = dp(36);
            d.setBounds(0, 0, size, size);
            btn.setCompoundDrawables(null, d, null, null);
            btn.setText(label);
            btn.setTextSize(10);
            btn.setBackgroundColor(0x00000000);
        } catch (Exception e) {
            btn.setText(label);
        }
    }

    private void setMode(boolean dataMode) {
        isDataMode = dataMode;
        selectedDataPlanIndex = -1;
        if (dataMode) {
            toggleData.setBackground(getResources().getDrawable(R.drawable.pill_active));
            toggleData.setTextColor(0xFFFFFFFF);
            toggleAirtime.setBackground(getResources().getDrawable(R.drawable.pill_bg));
            toggleAirtime.setTextColor(0xFF666666);
            airtimeSection.setVisibility(View.GONE);
            dataSection.setVisibility(View.VISIBLE);
            btnBuy.setText("BUY DATA");
            if (selectedNetwork != null) fetchAndLoadPlans();
        } else {
            toggleAirtime.setBackground(getResources().getDrawable(R.drawable.pill_active));
            toggleAirtime.setTextColor(0xFFFFFFFF);
            toggleData.setBackground(getResources().getDrawable(R.drawable.pill_bg));
            toggleData.setTextColor(0xFF666666);
            airtimeSection.setVisibility(View.VISIBLE);
            dataSection.setVisibility(View.GONE);
            btnBuy.setText("BUY AIRTIME");
        }
    }

    private void fetchAndLoadPlans() {
        fetchAndLoadPlansWithRetry(0);
    }

    private void fetchAndLoadPlansWithRetry(int attempt) {
        dataPlansContainer.removeAllViews();
        selectedDataPlanIndex = -1;
        if (selectedNetwork == null) {
            tvSelectNetwork.setVisibility(View.VISIBLE);
            tvSelectNetwork.setText("Please select a network above");
            return;
        }
        tvSelectNetwork.setVisibility(View.VISIBLE);
        tvSelectNetwork.setText(attempt > 0 ? "Connecting to server..." : "Loading plans...");

        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/plans?network=" + selectedNetwork);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(45000);
                conn.setReadTimeout(45000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray arr = new JSONArray(sb.toString());
                ArrayList<JSONObject> plans = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) plans.add(arr.getJSONObject(i));
                handler.post(() -> showDataPlans(plans));
            } catch (Exception e) {
                if (attempt < 2) {
                    handler.postDelayed(() -> fetchAndLoadPlansWithRetry(attempt + 1), 5000);
                } else {
                    handler.post(() -> {
                        tvSelectNetwork.setText("Failed to load plans. Tap to retry.");
                        tvSelectNetwork.setVisibility(View.VISIBLE);
                        tvSelectNetwork.setOnClickListener(v -> fetchAndLoadPlans());
                    });
                }
            }
        }).start();
    }

    private String selectedPlanCategory = "Daily";
    private LinearLayout dataCategoryTabs;

    private void showDataPlans(ArrayList<JSONObject> plans) {
        dataPlansContainer.removeAllViews();
        fetchedPlans = plans;
        if (plans.isEmpty()) {
            tvSelectNetwork.setText("No plans available for " + selectedNetwork);
            tvSelectNetwork.setVisibility(View.VISIBLE);
            return;
        }
        tvSelectNetwork.setVisibility(View.GONE);

        // Add category tabs
        if (dataCategoryTabs == null) {
            dataCategoryTabs = new LinearLayout(this);
            dataCategoryTabs.setOrientation(LinearLayout.HORIZONTAL);
            dataCategoryTabs.setPadding(0, 0, 0, dp(8));
        }
        dataCategoryTabs.removeAllViews();
        String[] cats = {"Daily", "Weekly", "Monthly", "Special"};
        for (String cat : cats) {
            TextView tab = new TextView(this);
            tab.setText(cat);
            tab.setTextSize(13);
            tab.setPadding(dp(14), dp(8), dp(14), dp(8));
            tab.setTypeface(null, Typeface.BOLD);
            if (cat.equals(selectedPlanCategory)) {
                tab.setBackground(getResources().getDrawable(R.drawable.pill_active));
                tab.setTextColor(0xFFFFFFFF);
            } else {
                tab.setBackground(getResources().getDrawable(R.drawable.pill_bg));
                tab.setTextColor(0xFF666666);
            }
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tp.rightMargin = dp(2);
            tab.setLayoutParams(tp);
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> {
                selectedPlanCategory = cat;
                showDataPlans(fetchedPlans);
            });
            dataCategoryTabs.addView(tab);
        }
        dataPlansContainer.addView(dataCategoryTabs);

        // Filter plans by selected category
        ArrayList<JSONObject> filtered = new ArrayList<>();
        for (JSONObject p : plans) {
            try {
                String v = p.getString("validity").toLowerCase();
                boolean match = false;
                if ("Daily".equals(selectedPlanCategory)) match = v.contains("1 day") || v.contains("2 day") || v.contains("3 day");
                else if ("Weekly".equals(selectedPlanCategory)) match = v.contains("7 day") || v.contains("7day");
                else if ("Monthly".equals(selectedPlanCategory)) match = v.contains("30") || v.contains("month");
                else match = !(v.contains("1 day") || v.contains("2 day") || v.contains("3 day") || v.contains("7 day") || v.contains("7day") || v.contains("30") || v.contains("month"));
                if (match) filtered.add(p);
            } catch (Exception e) {}
        }

        if (filtered.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No " + selectedPlanCategory.toLowerCase() + " plans available");
            empty.setTextSize(13);
            empty.setTextColor(0xFF999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            dataPlansContainer.addView(empty);
        } else {
            addPlanSection(null, filtered);
        }
    }

    private void addPlanSection(String title, ArrayList<JSONObject> plans) {
        if (title != null) {
            TextView header = new TextView(this);
            header.setText(title);
            header.setTextSize(14);
            header.setTextColor(0xFFC62828);
            header.setTypeface(null, Typeface.BOLD);
            header.setPadding(0, dp(12), 0, dp(8));
            dataPlansContainer.addView(header);
        }

        for (JSONObject plan : plans) {
            try {
                final int idx = fetchedPlans.indexOf(plan);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackground(getResources().getDrawable(R.drawable.card_bg));
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setElevation(dp(3));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.bottomMargin = dp(8);
                card.setLayoutParams(cp);

                LinearLayout left = new LinearLayout(this);
                left.setOrientation(LinearLayout.VERTICAL);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView tvSize = new TextView(this);
                tvSize.setText(plan.getString("size"));
                tvSize.setTextSize(16);
                tvSize.setTextColor(0xFFC62828);
                tvSize.setTypeface(null, Typeface.BOLD);
                left.addView(tvSize);

                TextView tvVal = new TextView(this);
                tvVal.setText(plan.getString("validity") + " \u2022 " + plan.getString("plan_type"));
                tvVal.setTextSize(11);
                tvVal.setTextColor(0xFF888888);
                left.addView(tvVal);

                card.addView(left);

                TextView tvPrice = new TextView(this);
                tvPrice.setText("\u20a6" + plan.getString("amount"));
                tvPrice.setTextSize(16);
                tvPrice.setTextColor(0xFF333333);
                tvPrice.setTypeface(null, Typeface.BOLD);
                card.addView(tvPrice);

                card.setOnClickListener(v -> selectDataPlan(idx));
                card.setTag(idx);
                dataPlansContainer.addView(card);
            } catch (Exception e) {}
        }
    }

    private void selectDataPlan(int idx) {
        selectedDataPlanIndex = idx;
        for (int i = 0; i < dataPlansContainer.getChildCount(); i++) {
            View child = dataPlansContainer.getChildAt(i);
            if (child.getTag() != null) {
                if ((int) child.getTag() == idx) {
                    child.setBackground(getResources().getDrawable(R.drawable.card_green));
                } else {
                    child.setBackground(getResources().getDrawable(R.drawable.card_bg));
                }
            }
        }
        try {
            JSONObject plan = fetchedPlans.get(idx);
            btnBuy.setText("BUY " + plan.getString("size") + " - \u20a6" + plan.getString("amount"));
        } catch (Exception e) {}
    }

    private void onBuy() {
        String phone = etPhone.getText().toString().trim();
        if (phone.length() < 10) {
            Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedNetwork == null) {
            Toast.makeText(this, "Select a network", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isDataMode && selectedDataPlanIndex < 0) {
            Toast.makeText(this, "Select a data plan", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isDataMode && etAirtimeAmount.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Select or enter an amount", Toast.LENGTH_SHORT).show();
            return;
        }

        String detected = detectNetworkFromPhone(phone);
        if (detected != null && !detected.equals(selectedNetwork)) {
            showNetworkMismatchDialog(phone, detected);
            return;
        }
        proceedWithBuy(phone);
    }

    private String networkDisplayName(String network) {
        if ("MTN".equals(network)) return "MTN";
        if ("AIRTEL".equals(network)) return "Airtel";
        if ("GLO".equals(network)) return "Glo";
        if ("9MOBILE".equals(network)) return "9mobile";
        return network;
    }

    private void proceedWithBuy(String phone) {
        btnBuy.setEnabled(false);
        btnBuy.setText("Processing...");

        if (isDataMode) {
            try {
                int dataId = fetchedPlans.get(selectedDataPlanIndex).getInt("data_id");
                callApi("/api/buy-data", phone, selectedNetwork, String.valueOf(dataId), true);
            } catch (Exception e) {
                btnBuy.setEnabled(true);
                btnBuy.setText("BUY DATA");
            }
        } else {
            String amt = etAirtimeAmount.getText().toString().trim();
            callApi("/api/buy-airtime", phone, selectedNetwork, amt, false);
        }
    }

    private void callApi(String endpoint, String phone, String network, String value, boolean isData) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("phone", phone);
                body.put("network", network);
                String uid = prefs().getString("user_id", "");
                if (!uid.isEmpty()) body.put("user_id", uid);
                if (isData) body.put("data_plan_id", Integer.parseInt(value));
                else body.put("amount", Integer.parseInt(value));

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.close();

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject res = new JSONObject(sb.toString());
                String btnText = isData ? "BUY DATA" : "BUY AIRTIME";
                handler.post(() -> {
                    btnBuy.setEnabled(true);
                    btnBuy.setText(btnText);
                    if (code < 400 && res.optBoolean("success")) {
                        showReceiptDialog(true, res.optString("message", "Success!"),
                            phone, network, isData ? "Data" : "Airtime",
                            isData ? value : ("\u20a6" + value));
                        fetchWalletBalance();
                    } else {
                        String err = res.optString("error", "Unknown error");
                        if (err.contains("Insufficient wallet")) {
                            showTopUpPrompt(err);
                        } else {
                            showReceiptDialog(false, err, phone, network, isData ? "Data" : "Airtime", "");
                        }
                    }
                });
            } catch (Exception e) {
                String btnText = isData ? "BUY DATA" : "BUY AIRTIME";
                String errMsg = e.getMessage();
                if (errMsg != null && errMsg.contains("Unable to resolve host")) errMsg = "No internet connection";
                else if (errMsg != null && errMsg.contains("timed out")) errMsg = "Server timed out. Try again.";
                else if (errMsg == null) errMsg = "Connection failed";
                final String msg = errMsg;
                handler.post(() -> {
                    btnBuy.setEnabled(true);
                    btnBuy.setText(btnText);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showReceiptDialog(boolean success, String message, String phone, String network, String type, String detail) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(8));
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView icon = new TextView(this);
        icon.setText(success ? "OK" : "X");
        icon.setTextSize(24);
        icon.setTextColor(success ? 0xFF43A047 : 0xFFC62828);
        icon.setTypeface(null, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(0, 0, 0, dp(12));
        layout.addView(icon);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(message);
        tvMsg.setTextSize(16);
        tvMsg.setTextColor(0xFF333333);
        tvMsg.setTypeface(null, Typeface.BOLD);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setPadding(0, 0, 0, dp(16));
        layout.addView(tvMsg);

        if (success) {
            String[] labels = {"Type", "Network", "Phone"};
            String[] values = {type, network, phone};
            for (int i = 0; i < labels.length; i++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(4), 0, dp(4));
                TextView l = new TextView(this);
                l.setText(labels[i]);
                l.setTextSize(13);
                l.setTextColor(0xFF888888);
                l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                TextView v = new TextView(this);
                v.setText(values[i]);
                v.setTextSize(13);
                v.setTextColor(0xFF333333);
                v.setTypeface(null, Typeface.BOLD);
                row.addView(l);
                row.addView(v);
                layout.addView(row);
            }
        }

        new AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("OK", null)
            .show();
    }

    // ==================== HISTORY TAB ====================

    private void showTopUpPrompt(String message) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(20), dp(24), dp(8));
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(message);
        tvMsg.setTextSize(15);
        tvMsg.setTextColor(0xFFC62828);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setPadding(0, 0, 0, dp(16));
        layout.addView(tvMsg);

        TextView tvHint = new TextView(this);
        tvHint.setText("Enter amount to add to your wallet");
        tvHint.setTextSize(13);
        tvHint.setTextColor(0xFF666666);
        layout.addView(tvHint);

        EditText etAmt = new EditText(this);
        etAmt.setHint("Amount (e.g. 1000)");
        etAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etAmt.setPadding(dp(12), dp(10), dp(12), dp(10));
        etAmt.setBackgroundColor(0xFFF5F5F5);
        layout.addView(etAmt);

        // Quick amount buttons
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setPadding(0, dp(10), 0, 0);
        int[] quickAmts = {500, 1000, 2000, 5000};
        for (int a : quickAmts) {
            TextView btn = new TextView(this);
            btn.setText("\u20a6" + a);
            btn.setTextSize(12);
            btn.setTextColor(0xFFC62828);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(8), dp(6), dp(8), dp(6));
            btn.setBackgroundColor(0xFFE3F2FD);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            bp.rightMargin = dp(4);
            btn.setLayoutParams(bp);
            btn.setOnClickListener(v -> etAmt.setText(String.valueOf(a)));
            quickRow.addView(btn);
        }
        layout.addView(quickRow);

        new AlertDialog.Builder(this)
            .setTitle("Add Funds")
            .setView(layout)
            .setPositiveButton("Pay with Paystack", (d, w) -> {
                String amt = etAmt.getText().toString().trim();
                if (amt.isEmpty() || Integer.parseInt(amt) < 100) {
                    Toast.makeText(this, "Minimum amount is \u20a6100", Toast.LENGTH_SHORT).show();
                    return;
                }
                initPaystackPayment(Integer.parseInt(amt));
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private String pendingPayRef = null;

    private void savePendingRef(String ref) {
        pendingPayRef = ref;
        prefs().edit().putString("pending_pay_ref", ref != null ? ref : "").apply();
    }

    private void initPaystackPayment(int amount) {
        String phone = prefs().getString("phone", "");
        final String email = safeGet("email");
        if (email.isEmpty()) { Toast.makeText(this, "Please add your email in Edit Profile first", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Connecting to Paystack...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/wallet/initialize");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(45000);
                conn.setReadTimeout(45000);
                conn.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("phone", phone);
                body.put("amount", amount);
                body.put("email", email);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.close();
                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                handler.post(() -> {
                    if (res.optBoolean("success")) {
                        savePendingRef(res.optString("reference"));
                        String payUrl = res.optString("authorization_url");
                        paystackOverlay.setVisibility(View.VISIBLE);
                        paystackWebView.loadUrl(payUrl);
                    } else {
                        Toast.makeText(this, res.optString("error", "Payment init failed"), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "Connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void verifyPendingPayment() {
        // Restore from prefs if memory was cleared
        if (pendingPayRef == null) {
            pendingPayRef = prefs().getString("pending_pay_ref", "");
            if (pendingPayRef.isEmpty()) pendingPayRef = null;
        }
        if (pendingPayRef == null) return;
        String ref = pendingPayRef;
        handler.post(() -> Toast.makeText(this, "Checking payment status...", Toast.LENGTH_SHORT).show());
        verifyWithRetry(ref, 0);
    }

    private void verifyWithRetry(String ref, int attempt) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/wallet/verify");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(45000);
                conn.setReadTimeout(45000);
                conn.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("reference", ref);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.close();
                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                handler.post(() -> {
                    if (res.optBoolean("success")) {
                        savePendingRef(null);
                        double bal = res.optDouble("balance", 0);
                        tvWalletBalance.setText(String.format("\u20a6%.0f", bal));
                        Toast.makeText(this, "Payment successful! Balance: \u20a6" + (int) bal, Toast.LENGTH_LONG).show();
                        fetchWalletBalance();
                    }
                });
            } catch (Exception e) {
                // Retry up to 3 times (handles Render cold starts)
                if (attempt < 2) {
                    handler.postDelayed(() -> verifyWithRetry(ref, attempt + 1), 5000);
                } else {
                    handler.post(() -> Toast.makeText(this, "Could not verify payment. Will retry next time.", Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private LinearLayout usageHistoryContainer, txnHistoryContainer, savingsHistoryContainer;
    private LinearLayout histUsageSection, histTxnSection, histSavingsSection;
    private TextView tvNoTxn, tvHistorySaved, tvHistorySavedPct, tvNoSavings;
    private TextView histTabUsage, histTabTxn, histTabSavings;
    private boolean showAllUsage = false;

    private void initHistoryTab() {
        usageHistoryContainer = findViewById(R.id.usageHistoryContainer);
        txnHistoryContainer = findViewById(R.id.txnHistoryContainer);
        tvNoTxn = findViewById(R.id.tvNoTxn);
        tvHistorySaved = findViewById(R.id.tvHistorySaved);
        tvHistorySavedPct = findViewById(R.id.tvHistorySavedPct);
        histTabUsage = findViewById(R.id.histTabUsage);
        histTabTxn = findViewById(R.id.histTabTxn);
        histTabSavings = findViewById(R.id.histTabSavings);
        histUsageSection = findViewById(R.id.histUsageSection);
        histTxnSection = findViewById(R.id.histTxnSection);
        histSavingsSection = findViewById(R.id.histSavingsSection);
        savingsHistoryContainer = findViewById(R.id.savingsHistoryContainer);
        tvNoSavings = findViewById(R.id.tvNoSavings);

        histTabUsage.setOnClickListener(v -> switchHistoryTab(0));
        histTabTxn.setOnClickListener(v -> switchHistoryTab(1));
        histTabSavings.setOnClickListener(v -> switchHistoryTab(2));

        findViewById(R.id.btnSeeAllUsage).setOnClickListener(v -> {
            showAllUsage = !showAllUsage;
            ((TextView) v).setText(showAllUsage ? "Show Less" : "See All >");
            refreshUsageHistory();
        });
        findViewById(R.id.btnRefreshHistory).setOnClickListener(v -> {
            refreshUsageHistory();
            fetchTransactions();
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
        });

        // Transaction date filters
        TextView txnAll = findViewById(R.id.txnFilterAll);
        TextView txnToday = findViewById(R.id.txnFilterToday);
        TextView txnWeek = findViewById(R.id.txnFilterWeek);
        TextView txnMonth = findViewById(R.id.txnFilterMonth);
        if (txnAll != null) {
            txnAll.setOnClickListener(v -> applyTxnFilter("all", txnAll, txnToday, txnWeek, txnMonth));
            txnToday.setOnClickListener(v -> applyTxnFilter("today", txnAll, txnToday, txnWeek, txnMonth));
            txnWeek.setOnClickListener(v -> applyTxnFilter("week", txnAll, txnToday, txnWeek, txnMonth));
            txnMonth.setOnClickListener(v -> applyTxnFilter("month", txnAll, txnToday, txnWeek, txnMonth));
        }
    }

    private void switchHistoryTab(int tab) {
        // Style all three tabs
        histTabUsage.setBackground(getResources().getDrawable(tab == 0 ? R.drawable.pill_active : R.drawable.pill_bg));
        histTabUsage.setTextColor(tab == 0 ? 0xFFFFFFFF : 0xFF666666);
        histTabTxn.setBackground(getResources().getDrawable(tab == 1 ? R.drawable.pill_active : R.drawable.pill_bg));
        histTabTxn.setTextColor(tab == 1 ? 0xFFFFFFFF : 0xFF666666);
        if (histTabSavings != null) {
            histTabSavings.setBackground(getResources().getDrawable(tab == 2 ? R.drawable.pill_active : R.drawable.pill_bg));
            histTabSavings.setTextColor(tab == 2 ? 0xFFFFFFFF : 0xFF666666);
        }
        // Show/hide all three sections
        histUsageSection.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        histTxnSection.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        if (histSavingsSection != null) histSavingsSection.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        // Load data for selected tab
        if (tab == 1) fetchAllTransactions();
        if (tab == 2) loadSavingsHistory();
    }

    private void clearUserScopedData() {
        // Reset device-level counters/savings so a different account on this device does not inherit the previous user's data
        prefs().edit()
            .remove("real_ad_requests").remove("real_bg_syncs").remove("real_total_dns")
            .remove("real_total_packets").remove("savings_date")
            .remove("last_notif_id").remove("unread_notif_count")
            .remove("last_daily_summary").remove("last_task_reminder").remove("last_upgrade_prompt")
            .apply();
        getSharedPreferences("datasaver_blocks", MODE_PRIVATE).edit().clear().apply();
        try {
            DataSaverVpnService.blockedAdRequests.set(0);
            DataSaverVpnService.blockedBgSyncs.set(0);
            DataSaverVpnService.totalDnsQueries.set(0);
            DataSaverVpnService.totalPacketsProcessed.set(0);
            DataSaverVpnService.perAppBlockedCount.clear();
            DataSaverVpnService.unreadNotifCount = 0;
        } catch (Exception e) {}
    }

    // Resets device-local savings ONLY when a genuinely different account signs in on this phone.
    // Uses a persistent "savings_owner" marker (in a separate prefs file that logout never clears),
    // so the primary owner logging back in keeps their own savings.
    private void handleUserSwitch(String newPhone) {
        if (newPhone == null) newPhone = "";
        SharedPreferences dev = getSharedPreferences("datasaver_device", MODE_PRIVATE);
        String owner = dev.getString("savings_owner", "");
        if (!newPhone.equals(owner)) {
            clearUserScopedData();
            dev.edit().putString("savings_owner", newPhone).apply();
        }
    }

    private void showBlockedAdsLog(LinearLayout container) {
        showBlockedAppsForDate(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date()));
    }

    private void showBlockedAppsForDate(String date) {
        android.content.SharedPreferences sp = getSharedPreferences("datasaver_blocks", MODE_PRIVATE);
        String[] apps = {"Facebook","Instagram","TikTok","Twitter/X","YouTube","Google Ads","Amazon Ads","Snapchat","LinkedIn","Ad Network","Other"};
        StringBuilder msg = new StringBuilder();
        int total = 0;
        for (String app : apps) {
            int c = sp.getInt("blocked_" + date + "_" + app, 0);
            if (c > 0) { msg.append(app).append(": ").append(c).append(" blocked\n"); total += c; }
        }
        if (total == 0) {
            new AlertDialog.Builder(this)
                .setTitle("Blocked on " + date)
                .setMessage("No blocked ads recorded for this date.\n\nMake sure DataSaver VPN was ON while browsing.")
                .setPositiveButton("OK", null).show();
            return;
        }
        msg.insert(0, "Blocked ads by app on " + date + ":\n\n");
        msg.append("\nTotal: ").append(total).append(" ads blocked");
        new AlertDialog.Builder(this)
            .setTitle("Blocked Ads - " + date)
            .setMessage(msg.toString())
            .setPositiveButton("OK", null).show();
    }

    private void loadSavingsHistory() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { tvNoSavings.setText("Login to see savings"); tvNoSavings.setVisibility(View.VISIBLE); return; }
 tvNoSavings.setText("Loading..."); tvNoSavings.setVisibility(View.VISIBLE);
 savingsHistoryContainer.removeAllViews();
 new Thread(() -> {
 try {
 URL url = new URL(SERVER_URL + "/api/savings/" + phone);
 HttpURLConnection conn = (HttpURLConnection) url.openConnection();
 conn.setConnectTimeout(45000); conn.setReadTimeout(45000);
 BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
 StringBuilder sb = new StringBuilder(); String line;
 while ((line = reader.readLine()) != null) sb.append(line);
 reader.close();
 JSONObject res = new JSONObject(sb.toString());
 long totalSaved = res.optLong("total_saved", 0);
 long totalBlocked = res.optLong("total_blocked", 0);
 double totalSavedNaira = res.optDouble("total_saved_naira", 0);
 JSONObject todayObj = res.optJSONObject("today");
 JSONObject weekObj = res.optJSONObject("week");
 JSONObject monthObj = res.optJSONObject("month");
 JSONArray history = res.optJSONArray("history");
 // Store server data for homepage display
 if (todayObj != null) { serverTodaySaved = todayObj.optLong("saved", 0); serverTodayBlocked = todayObj.optLong("blocked", 0); }
 if (weekObj != null) { serverWeekSaved = weekObj.optLong("saved", 0); serverWeekBlocked = weekObj.optLong("blocked", 0); }
 if (monthObj != null) { serverMonthSaved = monthObj.optLong("saved", 0); serverMonthBlocked = monthObj.optLong("blocked", 0); }
 serverAllTimeSaved = totalSaved;
 serverAllTimeBlocked = totalBlocked;
 handler.post(() -> {
 TextView tvToday = findViewById(R.id.tvSavingsToday);
 TextView tvWeek = findViewById(R.id.tvSavingsWeek);
 TextView tvMonth = findViewById(R.id.tvSavingsMonth);
 TextView tvAll = findViewById(R.id.tvSavingsAllTime);
 TextView tvAllB = findViewById(R.id.tvSavingsAllTimeBlocked);
                    if (tvToday != null && todayObj != null) tvToday.setText(formatBytes(todayObj.optLong("saved", 0)));
                    if (tvWeek != null && weekObj != null) tvWeek.setText(formatBytes(weekObj.optLong("saved", 0)));
                    if (tvMonth != null && monthObj != null) tvMonth.setText(formatBytes(monthObj.optLong("saved", 0)));
 if (tvAll != null) tvAll.setText(formatBytes(totalSaved) + " (₦" + String.format("%.2f", totalSavedNaira) + ")");
 if (tvAllB != null) tvAllB.setText(String.format("%,d", totalBlocked) + " requests blocked ≈ " + formatBytes(totalSaved));
 savingsHistoryContainer.removeAllViews();
 if (history != null && history.length() > 0) {
 tvNoSavings.setVisibility(View.GONE);
 for (int i = 0; i < history.length() && i < 14; i++) {
 try {
 JSONObject day = history.getJSONObject(i);
 final String date = day.optString("date", "");
                                long saved = day.optLong("saved_bytes", 0);
 long blocked = day.optLong("blocked_requests", 0);
 double savedNaira = day.optDouble("saved_naira", 0);
 if (saved == 0 && blocked == 0) continue;
 LinearLayout row = new LinearLayout(MainActivity.this);
 row.setOrientation(LinearLayout.HORIZONTAL);
 row.setBackground(getResources().getDrawable(R.drawable.card_bg));
 row.setPadding(dp(14), dp(12), dp(14), dp(12));
 row.setElevation(dp(2)); row.setGravity(Gravity.CENTER_VERTICAL);
 LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
 rlp.bottomMargin = dp(8); row.setLayoutParams(rlp);
 TextView tvDate = new TextView(MainActivity.this);
 tvDate.setText(date); tvDate.setTextSize(13); tvDate.setTextColor(0xFF333333); tvDate.setTypeface(null, Typeface.BOLD);
 tvDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
 row.addView(tvDate);
 LinearLayout rc = new LinearLayout(MainActivity.this); rc.setOrientation(LinearLayout.VERTICAL); rc.setGravity(Gravity.END);
 TextView tvS = new TextView(MainActivity.this); tvS.setText(formatBytes(saved) + " (₦" + String.format("%.2f", savedNaira) + ")"); tvS.setTextSize(14); tvS.setTextColor(0xFF43A047); tvS.setTypeface(null, Typeface.BOLD); rc.addView(tvS);
 TextView tvB = new TextView(MainActivity.this); tvB.setText(String.format("%,d", blocked) + " blocked"); tvB.setTextSize(11); tvB.setTextColor(0xFF888888); rc.addView(tvB);
 row.addView(rc); savingsHistoryContainer.addView(row);
 } catch (Exception e) {}
 }
                    } else { tvNoSavings.setText("No savings data yet"); tvNoSavings.setVisibility(View.VISIBLE); }
 });
 } catch (Exception e) {
                // Fallback to local data
                handler.post(() -> {
                    long localBlocked = DataSaverVpnService.blockedAdRequests.get() + DataSaverVpnService.blockedBgSyncs.get();
                    long localDns = DataSaverVpnService.totalDnsQueries.get();
                    TextView tvToday = findViewById(R.id.tvSavingsToday);
                    TextView tvWeek = findViewById(R.id.tvSavingsWeek);
                    TextView tvMonth = findViewById(R.id.tvSavingsMonth);
                    TextView tvAll = findViewById(R.id.tvSavingsAllTime);
                    TextView tvAllB = findViewById(R.id.tvSavingsAllTimeBlocked);
                    if (tvToday != null) tvToday.setText(localBlocked + " requests blocked");
                    if (tvWeek != null) tvWeek.setText(localBlocked + " requests blocked");
                    if (tvMonth != null) tvMonth.setText(localBlocked + " requests blocked");
                    if (tvAll != null) tvAll.setText(localBlocked + " requests blocked");
                    if (tvAllB != null) tvAllB.setText(localDns + " DNS queries");
                    if (localBlocked > 0) tvNoSavings.setVisibility(View.GONE);
                    else { tvNoSavings.setText("Turn on DataSaver to start blocking"); tvNoSavings.setVisibility(View.VISIBLE); }
                });
            }
 }).start();
 }

 private String txnFilter = "all";

    private void applyTxnFilter(String filter, TextView all, TextView today, TextView week, TextView month) {
        txnFilter = filter;
        all.setBackground(getResources().getDrawable("all".equals(filter) ? R.drawable.pill_active : R.drawable.pill_bg));
        all.setTextColor("all".equals(filter) ? 0xFFFFFFFF : 0xFF666666);
        today.setBackground(getResources().getDrawable("today".equals(filter) ? R.drawable.pill_active : R.drawable.pill_bg));
        today.setTextColor("today".equals(filter) ? 0xFFFFFFFF : 0xFF666666);
        week.setBackground(getResources().getDrawable("week".equals(filter) ? R.drawable.pill_active : R.drawable.pill_bg));
        week.setTextColor("week".equals(filter) ? 0xFFFFFFFF : 0xFF666666);
        month.setBackground(getResources().getDrawable("month".equals(filter) ? R.drawable.pill_active : R.drawable.pill_bg));
        month.setTextColor("month".equals(filter) ? 0xFFFFFFFF : 0xFF666666);
        fetchAllTransactions();
    }

    private void fetchAllTransactions() {
        txnHistoryContainer.removeAllViews();
        tvNoTxn.setText("Loading...");
        tvNoTxn.setVisibility(View.VISIBLE);
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { tvNoTxn.setText("Login to see transactions"); return; }

        // Fetch both airtime/data transactions AND wallet transactions
        new Thread(() -> {
            JSONArray combined = new JSONArray();
            try {
                // Airtime/data transactions
                URL url1 = new URL(SERVER_URL + "/api/transactions/" + phone);
                HttpURLConnection c1 = (HttpURLConnection) url1.openConnection();
                c1.setConnectTimeout(45000); c1.setReadTimeout(45000);
                BufferedReader r1 = new BufferedReader(new InputStreamReader(c1.getInputStream()));
                StringBuilder s1 = new StringBuilder(); String l;
                while ((l = r1.readLine()) != null) s1.append(l); r1.close();
                JSONArray arr1 = new JSONArray(s1.toString());
                for (int i = 0; i < arr1.length(); i++) combined.put(arr1.getJSONObject(i));
            } catch (Exception e) {}
            try {
                // Wallet transactions
                URL url2 = new URL(SERVER_URL + "/api/wallet/transactions/" + phone);
                HttpURLConnection c2 = (HttpURLConnection) url2.openConnection();
                c2.setConnectTimeout(45000); c2.setReadTimeout(45000);
                BufferedReader r2 = new BufferedReader(new InputStreamReader(c2.getInputStream()));
                StringBuilder s2 = new StringBuilder(); String l;
                while ((l = r2.readLine()) != null) s2.append(l); r2.close();
                JSONArray arr2 = new JSONArray(s2.toString());
                for (int i = 0; i < arr2.length(); i++) {
                    JSONObject wt = arr2.getJSONObject(i);
                    wt.put("_wallet", true);
                    combined.put(wt);
                }
            } catch (Exception e) {}
            handler.post(() -> showAllTransactions(combined));
        }).start();
    }

    private void showAllTransactions(JSONArray arr) {
        txnHistoryContainer.removeAllViews();
        if (arr.length() == 0) {
            String msg = "all".equals(txnFilter) ? "No transactions yet" : "No transactions for this period";
            tvNoTxn.setText(msg); tvNoTxn.setVisibility(View.VISIBLE); return;
        }
        tvNoTxn.setVisibility(View.GONE);

        // Sort by created_at descending
        ArrayList<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) try { list.add(arr.getJSONObject(i)); } catch (Exception e) {}
        Collections.sort(list, (a, b) -> b.optString("created_at", "").compareTo(a.optString("created_at", "")));
        // Apply date filter
        if (!"all".equals(txnFilter)) {
            long now = System.currentTimeMillis();
            long cutoff = now;
            if ("today".equals(txnFilter)) cutoff = now - 24L * 60 * 60 * 1000;
            else if ("week".equals(txnFilter)) cutoff = now - 7L * 24 * 60 * 60 * 1000;
            else if ("month".equals(txnFilter)) cutoff = now - 30L * 24 * 60 * 60 * 1000;
            String cutoffStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(cutoff));
            ArrayList<JSONObject> filtered = new ArrayList<>();
            for (JSONObject t : list) { if (t.optString("created_at", "").compareTo(cutoffStr) >= 0) filtered.add(t); }
            list = filtered;
        }
        if (list.isEmpty()) {
            tvNoTxn.setText("No transactions for this period");
            tvNoTxn.setVisibility(View.VISIBLE); return;
        }

        for (JSONObject txn : list) {
            boolean isWallet = txn.optBoolean("_wallet", false);
            String title, amtText, status, date;
            int amtColor;

            if (isWallet) {
                String type = txn.optString("type", "credit");
                String desc = txn.optString("description", "Wallet transaction");
                double amt = txn.optDouble("amount", 0);
                status = txn.optString("status", "success");
                title = type.equals("credit") ? "Wallet Top-up" : desc;
                amtText = (type.equals("credit") ? "+" : "-") + "\u20a6" + (int) amt;
                amtColor = type.equals("credit") ? 0xFF43A047 : 0xFFC62828;
            } else {
                String type = txn.optString("type", "");
                String network = txn.optString("network", "");
                String amt = txn.optString("amount", "0");
                String planSize = txn.optString("plan_size", "");
                status = txn.optString("status", "pending");
                title = type.equals("data") ? network + " " + planSize + " Data" : network + " \u20a6" + amt + " Airtime";
                amtText = "-\u20a6" + amt;
                amtColor = 0xFFC62828;
            }
            date = txn.optString("created_at", "");
            String dateShort = date.length() > 10 ? date.substring(0, 10) : date;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(getResources().getDrawable(R.drawable.card_bg));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setElevation(dp(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView t = new TextView(this);
            t.setText(title);
            t.setTextSize(14); t.setTextColor(0xFF333333); t.setTypeface(null, Typeface.BOLD);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView a = new TextView(this);
            a.setText(amtText); a.setTextSize(14); a.setTextColor(amtColor); a.setTypeface(null, Typeface.BOLD);
            row.addView(t); row.addView(a);
            card.addView(row);

            TextView d = new TextView(this);
            int statusColor = "success".equals(status) ? 0xFF43A047 : "failed".equals(status) ? 0xFFC62828 : 0xFFFF8F00;
            d.setText(dateShort + " \u2022 " + status.toUpperCase());
            d.setTextSize(11); d.setTextColor(statusColor); d.setPadding(0, dp(2), 0, 0);
            card.addView(d);

            txnHistoryContainer.addView(card);
        }
    }

    private void refreshUsageHistory() {
        usageHistoryContainer.removeAllViews();
        Map<String, long[]> usage = DataSaverService.appDataUsage;
        if (usage.isEmpty()) {
            addUsageCard("No data yet", "Turn on DataSaver to start monitoring", 0, 0);
            return;
        }

        ArrayList<Map.Entry<String, long[]>> sorted = new ArrayList<>(usage.entrySet());
        Collections.sort(sorted, (a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));

        int limit = showAllUsage ? sorted.size() : Math.min(5, sorted.size());
        long totalSaved = 0;
        long totalUsed = 0;
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, long[]> e = sorted.get(i);
            long total = e.getValue()[0] + e.getValue()[1];
            long saved = e.getValue()[2];
            if (total < 1024) continue;
            totalSaved += saved;
            totalUsed += total;
            addUsageCard(e.getKey(), formatBytes(total), total, saved);
        }

        long totalBlocked = DataSaverVpnService.blockedAdRequests.get() + DataSaverVpnService.blockedBgSyncs.get();
        long totalDns = DataSaverVpnService.totalDnsQueries.get();
        tvHistorySaved.setText(totalBlocked + " requests blocked");
        // Show actual data efficiency
        double savedPct = 0;
        if (totalUsed > 0 || totalSaved > 0) {
            savedPct = (totalSaved * 100.0) / (totalUsed + totalSaved);
        }
        tvHistorySavedPct.setText(String.format("%.1f%% saved", savedPct));
    }

    private void addUsageCard(String appName, String amount, long total, long saved) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(getResources().getDrawable(R.drawable.card_bg));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setElevation(dp(3));
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        // App icon
        android.widget.ImageView icon = new android.widget.ImageView(this);
        Drawable appIcon = getAppIcon(appName);
        icon.setImageDrawable(appIcon != null ? appIcon : makeLetterIcon(appName));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconLp.rightMargin = dp(10);
        icon.setLayoutParams(iconLp);
        card.addView(icon);

        // Text
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView t = new TextView(this);
        t.setText(appName);
        t.setTextSize(14);
        t.setTextColor(0xFF333333);
        t.setTypeface(null, Typeface.BOLD);
        textCol.addView(t);

        TextView sub = new TextView(this);
        sub.setText("Used " + amount);
        sub.setTextSize(12);
        sub.setTextColor(0xFF888888);
        textCol.addView(sub);

        card.addView(textCol);

        if (saved > 0) {
            // Only show real savings, not simulated
        }

        usageHistoryContainer.addView(card);
    }

    private void fetchTransactions() {
        fetchAllTransactions();
    }

    // ==================== PROFILE TAB ====================

    private TextView tvProfileName, tvProfilePhone, tvPushNotif, tvDailyAlerts;
    private TextView tvServerAddr, tvAppVersion, profilePhoto;

    private SharedPreferences prefs() { return getSharedPreferences("datasaver", MODE_PRIVATE); }

    private void initProfileTab() {
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfilePhone = findViewById(R.id.tvProfilePhone);
        tvPushNotif = findViewById(R.id.tvPushNotif);
        tvDailyAlerts = findViewById(R.id.tvDailyAlerts);
        tvServerAddr = findViewById(R.id.tvServerAddr);
        tvAppVersion = findViewById(R.id.tvAppVersion);
        profilePhoto = findViewById(R.id.profilePhoto);

        // Load saved prefs
        refreshProfileUI();
        
        // Load Activity Log
        loadActivityLog();

        profilePhoto.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK);
            pick.setType("image/*");
            startActivityForResult(pick, PICK_PHOTO);
        });
        loadProfilePhoto();
        findViewById(R.id.rowEditProfile).setOnClickListener(v -> showEditProfileDialog());
        findViewById(R.id.rowChangePassword).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.rowManageSub).setOnClickListener(v -> switchTab(2));
        findViewById(R.id.btnProfileAddFunds).setOnClickListener(v -> showTopUpPrompt("Add money to your wallet"));
        findViewById(R.id.rowPushNotif).setOnClickListener(v -> togglePref("push_notif", tvPushNotif));
        findViewById(R.id.rowDailyAlerts).setOnClickListener(v -> togglePref("daily_alerts", tvDailyAlerts));
        findViewById(R.id.rowPrivacy).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://datasaver-server.onrender.com/privacy"))));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        // VPN Bypass List (Split Tunneling)
        View rowBypass = findViewById(R.id.rowBypassApps);
        if (rowBypass != null) rowBypass.setOnClickListener(v -> showBypassAppsDialog());

        // Background Guard toggle
        View rowBgGuard = findViewById(R.id.rowBgGuard);
        if (rowBgGuard != null) rowBgGuard.setOnClickListener(v -> togglePref("bg_block_enabled", findViewById(R.id.tvBgGuard)));
    }

    private void loadActivityLog() {
        TextView tvActivityLog = findViewById(R.id.tvActivityLog);
        if (tvActivityLog == null) return;
        
        String phone = prefs().getString("phone", "");
        android.util.Log.i("DataSaver", "Loading activity log for phone: '" + phone + "'");
        if (phone.isEmpty()) {
            tvActivityLog.setText("Login to see activity");
            return;
        }
        
        tvActivityLog.setText("Loading...");
        
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(SERVER_URL + "/api/savings/activity/" + phone);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    org.json.JSONObject res = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray activity = res.optJSONArray("activity");
                    
                    StringBuilder logText = new StringBuilder();
                    if (activity != null && activity.length() > 0) {
                        for (int i = 0; i < Math.min(activity.length(), 20); i++) {
                            org.json.JSONObject log = activity.getJSONObject(i);
                            String time = log.optString("created_at", "");
                            if (time.length() > 16) time = time.substring(0, 16).replace("T", " ");
                            long saved = log.optLong("saved_bytes", 0);
                            long blocked = log.optLong("blocked_requests", 0);
                            double naira = log.optDouble("saved_naira", 0);
                            logText.append(time).append(" - ").append(formatBytes(saved)).append(" saved (").append(formatBytes(blocked * 5 * 1024)).append("), ₦").append(String.format("%.2f", naira)).append("\n");
                        }
                    } else {
                        logText.append("No activity yet. Turn on DataSaver to start logging.");
                    }
                    final String displayText = logText.toString();
                    handler.post(() -> tvActivityLog.setText(displayText));
                }
            } catch (Exception e) {
                handler.post(() -> tvActivityLog.setText("Error loading activity: " + e.getMessage()));
            }
        }).start();
    }
    
    private void refreshProfileUI() {
        SharedPreferences sp = prefs();
        String name = sp.getString("name", "");
        if (name.isEmpty() || "null".equals(name)) name = "DataSaver User";
        String phone = sp.getString("phone", "");
        tvProfileName.setText(name);
        tvProfilePhone.setText(phone.isEmpty() ? "No Plan" : phone + " - " + (sp.getString("subscription_plan", "none").substring(0, 1).toUpperCase() + sp.getString("subscription_plan", "none").substring(1)) + " Plan");
        profilePhoto.setText(name.isEmpty() ? "U" : name.substring(0, 1).toUpperCase());

        tvPushNotif.setText(sp.getBoolean("push_notif", true) ? "ON" : "OFF");
        tvPushNotif.setTextColor(sp.getBoolean("push_notif", true) ? 0xFF43A047 : 0xFFC62828);
        tvDailyAlerts.setText(sp.getBoolean("daily_alerts", true) ? "ON" : "OFF");
        tvDailyAlerts.setTextColor(sp.getBoolean("daily_alerts", true) ? 0xFF43A047 : 0xFFC62828);

        // Background Guard status
        TextView tvBgGuard = findViewById(R.id.tvBgGuard);
        if (tvBgGuard != null) {
            boolean bgOn = sp.getBoolean("bg_block_enabled", true);
            tvBgGuard.setText(bgOn ? "ON" : "OFF");
            tvBgGuard.setTextColor(bgOn ? 0xFF43A047 : 0xFFC62828);
        }

        tvServerAddr.setText("*****");
        String subPlan = sp.getString("subscription_plan", "none");
        TextView tvManageSub = findViewById(R.id.tvManageSubLabel);
        if (tvManageSub != null) tvManageSub.setText(subPlan.substring(0, 1).toUpperCase() + subPlan.substring(1) + " >");
        try {
            String vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvAppVersion.setText(vn != null ? "v" + vn : "v1.0.0");
        } catch (Exception e) { tvAppVersion.setText("v1.0.0"); }

        // Hidden dev reset: tap version 5 times to reset all savings data
        final int[] tapCount = {0};
        final long[] lastTap = {0};
        View rowVersion = tvAppVersion.getParent() instanceof View ? (View) tvAppVersion.getParent() : null;
        View tapTarget = rowVersion != null ? rowVersion : tvAppVersion;
        tapTarget.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastTap[0] > 2000) tapCount[0] = 0;
            lastTap[0] = now;
            tapCount[0]++;
            if (tapCount[0] >= 5) {
                tapCount[0] = 0;
                resetAllSavingsData();
            }
        });

        // Update profile wallet
        fetchProfileWallet();
    }

    private void resetAllSavingsData() {
        showAppDialog(
            "Reset Savings Data",
            "This will clear all savings counters, the free plan cap, and restart fresh. Use this for testing only.",
            "Reset Everything",
            () -> {
                prefs().edit()
                    .putLong("real_ad_bytes", 0)
                    .putLong("real_bg_bytes", 0)
                    .putLong("real_ad_requests", 0)
                    .putLong("real_bg_syncs", 0)
                    .putLong("real_total_packets", 0)
                    .putLong("savings_today", 0)
                    .putLong("savings_week", 0)
                    .putLong("savings_month", 0)
                    .putLong("install_time", System.currentTimeMillis())
                    .putBoolean("cap_popup_shown", false)
                    .remove("saved_appData")
                    .apply();
                DataSaverVpnService.blockedAdRequests.set(0);
                DataSaverVpnService.blockedBgSyncs.set(0);
                DataSaverVpnService.totalDnsQueries.set(0);
                DataSaverVpnService.totalPacketsProcessed.set(0);
                DataSaverVpnService.perAppBlockedCount.clear();
                capPopupShown = false;
                DataSaverService.appDataUsage.clear();
                DataSaverService.totalSavedBytes = 0;
                DataSaverService.savedPercent = 0;
                updateSummary();
                updateAppCards();
                hideUpgradeBanner();
                Toast.makeText(this, "All savings data reset. Fresh start!", Toast.LENGTH_LONG).show();
            },
            "Cancel",
            null
        );
    }

    private void togglePref(String key, TextView tv) {
        SharedPreferences sp = prefs();
        boolean current = sp.getBoolean(key, key.equals("wifi_compress") ? false : true);
        sp.edit().putBoolean(key, !current).apply();
        tv.setText(!current ? "ON" : "OFF");
        tv.setTextColor(!current ? 0xFF43A047 : 0xFFC62828);
    }

    private void fetchProfileWallet() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) return;
        TextView tvPW = findViewById(R.id.tvProfileWallet);
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/user/" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(45000);
                conn.setReadTimeout(45000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                double bal = res.optDouble("wallet_balance", 0);
                handler.post(() -> tvPW.setText(String.format("\u20a6%.0f", bal)));
            } catch (Exception e) {
                handler.post(() -> tvPW.setText("\u20a60"));
            }
        }).start();
    }

    private void showBypassAppsDialog() {
        showProtectedAppsPage();
    }

    private void showProtectedAppsPage() {
        // Get current protected apps
        String current = prefs().getString("bypass_apps", "");
        Set<String> protectedPkgs = new java.util.HashSet<>();
        if (!current.isEmpty()) {
            for (String pkg : current.split(",")) protectedPkgs.add(pkg.trim());
        }
        // Default protected apps
        String[] defaults = {"com.whatsapp", "com.whatsapp.w4b", "com.android.chrome",
            "com.gtbank.gtworldapp", "com.accessbank.accessbankapp", "com.zenithbank.eazymoney",
            "com.firstbanknigeria.firstmobile", "ng.opay", "com.palmpay.app", "com.kuda.app",
            "org.telegram.messenger", "com.Slack"};
        for (String d : defaults) protectedPkgs.add(d);

        // Build full app list in background
        Toast.makeText(this, "Loading apps...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            ArrayList<String[]> appList = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                // Skip system apps without a launcher icon
                if (ai.icon == 0) continue;
                String label = pm.getApplicationLabel(ai).toString();
                String pkg = ai.packageName;
                if (pkg.equals(getPackageName())) continue;
                appList.add(new String[]{label, pkg});
            }
            Collections.sort(appList, (a, b) -> a[0].compareToIgnoreCase(b[0]));

            handler.post(() -> {
                // Build dialog with scrollable list
                ScrollView scroll = new ScrollView(this);
                LinearLayout container = new LinearLayout(this);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(dp(16), dp(12), dp(16), dp(12));

                // Info text
                TextView info = new TextView(this);
                info.setText("Apps you protect will never be interrupted by DataSaver. Banking apps and messaging apps are protected by default.");
                info.setTextSize(13);
                info.setTextColor(0xFF666666);
                info.setPadding(0, 0, 0, dp(12));
                container.addView(info);

                // Track checkboxes
                ArrayList<android.widget.CheckBox> checkboxes = new ArrayList<>();
                ArrayList<String> packages = new ArrayList<>();

                for (String[] app : appList) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(6), 0, dp(6));

                    // App icon
                    android.widget.ImageView icon = new android.widget.ImageView(this);
                    try {
                        icon.setImageDrawable(pm.getApplicationIcon(app[1]));
                    } catch (Exception e) {
                        icon.setImageDrawable(makeLetterIcon(app[0]));
                    }
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(32), dp(32));
                    iconLp.rightMargin = dp(10);
                    icon.setLayoutParams(iconLp);
                    row.addView(icon);

                    // App name
                    TextView name = new TextView(this);
                    name.setText(app[0]);
                    name.setTextSize(14);
                    name.setTextColor(0xFF333333);
                    name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    row.addView(name);

                    // Toggle
                    android.widget.CheckBox cb = new android.widget.CheckBox(this);
                    cb.setChecked(protectedPkgs.contains(app[1]));
                    row.addView(cb);

                    checkboxes.add(cb);
                    packages.add(app[1]);
                    container.addView(row);
                }

                scroll.addView(container);

                new AlertDialog.Builder(this)
                    .setTitle("Protected Apps")
                    .setView(scroll)
                    .setPositiveButton("Save", (d, w) -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < checkboxes.size(); i++) {
                            if (checkboxes.get(i).isChecked()) {
                                if (sb.length() > 0) sb.append(",");
                                sb.append(packages.get(i));
                            }
                        }
                        prefs().edit().putString("bypass_apps", sb.toString()).apply();
                        if (DataSaverVpnService.isVpnRunning) {
                            Intent stopIntent = new Intent(this, DataSaverVpnService.class);
                            stopIntent.setAction("STOP");
                            startService(stopIntent);
                            handler.postDelayed(() -> {
                                Intent startIntent = new Intent(this, DataSaverVpnService.class);
                                startService(startIntent);
                                Toast.makeText(this, "Protected apps updated and applied.", Toast.LENGTH_SHORT).show();
                            }, 1500);
                        } else {
                            Toast.makeText(this, "Protected apps saved.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }).start();
    }

    private String safeGet(String key) {
        String v = prefs().getString(key, "");
        return (v == null || "null".equals(v)) ? "" : v;
    }

    private void showEditProfileDialog() {
        SharedPreferences sp = prefs();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(4));

        EditText etName = new EditText(this);
        etName.setHint("Name");
        etName.setText(safeGet("name"));
        layout.addView(etName);

        EditText etPh = new EditText(this);
        etPh.setHint("Phone");
        etPh.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        etPh.setText(safeGet("phone"));
        layout.addView(etPh);

        EditText etEmail = new EditText(this);
        etEmail.setHint("Email");
        etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etEmail.setText(safeGet("email"));
        layout.addView(etEmail);

        new AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                String newName = etName.getText().toString().trim();
                String newPhone = etPh.getText().toString().trim();
                String newEmail = etEmail.getText().toString().trim();
                String originalPhone = safeGet("phone");
                sp.edit()
                    .putString("name", newName)
                    .putString("phone", newPhone)
                    .putString("email", newEmail)
                    .apply();
                refreshProfileUI();
                Toast.makeText(this, "Saving to server...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        URL url = new URL(SERVER_URL + "/api/user/update");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(45000);
                        conn.setReadTimeout(45000);
                        conn.setDoOutput(true);
                        JSONObject body = new JSONObject();
                        body.put("phone", originalPhone.isEmpty() ? newPhone : originalPhone);
                        body.put("name", newName);
                        body.put("email", newEmail);
                        if (!newPhone.equals(originalPhone) && !newPhone.isEmpty()) {
                            body.put("new_phone", newPhone);
                        }
                        OutputStream os = conn.getOutputStream();
                        os.write(body.toString().getBytes());
                        os.close();
                        int code = conn.getResponseCode();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                            code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        JSONObject res = new JSONObject(sb.toString());
                        handler.post(() -> {
                            if (res.optBoolean("success")) {
                                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Save failed: " + res.optString("error", ""), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        handler.post(() -> Toast.makeText(this, "Could not save to server", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(4));

        EditText etOld = new EditText(this);
        etOld.setHint("Current password");
        etOld.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etOld);

        EditText etNew = new EditText(this);
        etNew.setHint("New password");
        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNew);

        EditText etConfirm = new EditText(this);
        etConfirm.setHint("Confirm new password");
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etConfirm);

        new AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(layout)
            .setPositiveButton("Change", (d, w) -> {
                String oldPw = etOld.getText().toString();
                String newPw = etNew.getText().toString();
                String confirm = etConfirm.getText().toString();
                String saved = prefs().getString("password", "");
                if (!saved.isEmpty() && !saved.equals(oldPw)) {
                    Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (newPw.length() < 4) {
                    Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPw.equals(confirm)) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs().edit().putString("password", newPw).apply();
                Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void logout() {
        showAppDialog(
            "Log Out",
            "Are you sure you want to log out?",
            "Log Out",
            () -> {
                long sRx = prefs().getLong("saved_totalRx", 0);
                long sTx = prefs().getLong("saved_totalTx", 0);
                long sSaved = prefs().getLong("saved_totalSaved", 0);
                float sPct = prefs().getFloat("saved_pct", 0);
                long rAd = prefs().getLong("real_ad_bytes", 0);
                long rBg = prefs().getLong("real_bg_bytes", 0);
                long rAdR = prefs().getLong("real_ad_requests", 0);
                long rBgS = prefs().getLong("real_bg_syncs", 0);
                long instTime = prefs().getLong("install_time", 0);
                String refCode = prefs().getString("referral_code", "");
                prefs().edit().clear().apply();
                prefs().edit()
                    .putLong("saved_totalRx", sRx).putLong("saved_totalTx", sTx)
                    .putLong("saved_totalSaved", sSaved).putFloat("saved_pct", sPct)
                    .putLong("real_ad_bytes", rAd).putLong("real_bg_bytes", rBg)
                    .putLong("real_ad_requests", rAdR).putLong("real_bg_syncs", rBgS)
                    .putLong("install_time", instTime)
                    .putString("referral_code", refCode)
                    .apply();
                if (DataSaverService.isRunning) {
                    Intent i = new Intent(this, DataSaverService.class);
                    i.setAction(DataSaverService.ACTION_STOP);
                    startService(i);
                    polling = false;
                }
                refreshProfileUI();
                switchTab(0);
                updateUI();
                loginOverlay.setVisibility(View.VISIBLE);
                loginOverlay.bringToFront();
                findViewById(R.id.bottomNav).setVisibility(View.GONE);
                switchAuthTab(false, (TextView) findViewById(R.id.tabLogin), (TextView) findViewById(R.id.tabSignup));
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            },
            "Cancel",
            null
        );
    }

    private void loadProfilePhoto() {
        String path = prefs().getString("photo_path", "");
        if (!path.isEmpty()) {
            try {
                Bitmap bmp = BitmapFactory.decodeFile(path);
                if (bmp != null) setCircularPhoto(bmp);
            } catch (Exception e) {}
        }
    }

    private void setCircularPhoto(Bitmap bmp) {
        // Fix rotation using EXIF data
        int size = dp(72);
        int srcW = bmp.getWidth(), srcH = bmp.getHeight();
        int cropSize = Math.min(srcW, srcH);
        int x = (srcW - cropSize) / 2, y = (srcH - cropSize) / 2;
        Bitmap cropped = Bitmap.createBitmap(bmp, x, y, cropSize, cropSize);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, size, size, true);
        Bitmap circular = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(circular);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setAntiAlias(true);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaled, 0, 0, paint);
        profilePhoto.setBackground(new BitmapDrawable(getResources(), circular));
        profilePhoto.setText("");
    }

    private Bitmap fixRotation(Uri imageUri, Bitmap bmp) {
        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            android.media.ExifInterface exif = new android.media.ExifInterface(is);
            int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
            is.close();
            int degrees = 0;
            if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            if (degrees == 0) return bmp;
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        } catch (Exception e) { return bmp; }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startServices();
            } else {
                showAppDialog(
                    "VPN Permission Needed",
                    "Acorn Datasaver needs VPN permission to block ads and save your data.\n\nTap Try Again and then tap ALLOW on the next screen.",
                    "Try Again",
                    () -> toggle(),
                    "Cancel",
                    null
                );
            }
            return;
        }
        if (requestCode == PICK_PROOF && resultCode == RESULT_OK && data != null) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                if (bmp != null && proofTaskId != null) {
                    submitTaskProof(proofTaskId, bmp);
                    proofTaskId = null;
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == PICK_PHOTO && resultCode == RESULT_OK && data != null) {
            try {
                Uri imageUri = data.getData();
                InputStream is = getContentResolver().openInputStream(imageUri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                if (bmp != null) {
                    final Bitmap photo = fixRotation(imageUri, bmp);
                    
                    File f = new File(getFilesDir(), "profile.jpg");
                    FileOutputStream fos = new FileOutputStream(f);
                    photo.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                    fos.close();
                    prefs().edit().putString("photo_path", f.getAbsolutePath()).apply();
                    setCircularPhoto(photo);
                    Toast.makeText(this, "Photo updated", Toast.LENGTH_SHORT).show();
                    // Upload to server
                    new Thread(() -> {
                        try {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            photo.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                            String base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
                            URL url = new URL(SERVER_URL + "/api/user/update");
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setConnectTimeout(45000);
                            conn.setReadTimeout(45000);
                            conn.setDoOutput(true);
                            JSONObject body = new JSONObject();
                            body.put("phone", prefs().getString("phone", ""));
                            body.put("photo_base64", base64);
                            OutputStream os = conn.getOutputStream();
                            os.write(body.toString().getBytes());
                            os.close();
                            conn.getResponseCode();
                        } catch (Exception e) {}
                    }).start();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed to load photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== TABS / HOME ====================

    private String currentPlan = "none";

    // Subscription pricing fetched from admin panel (/api/subscription-plans). Defaults used until fetched.
    private int premiumPrice = 500, premiumDuration = 7;
    private int professionalPrice = 1500, professionalDuration = 30;
    private int enterprisePrice = 5000, enterpriseDuration = 30;

    private int planPrice(String id) {
        if ("premium".equals(id)) return premiumPrice;
        if ("professional".equals(id)) return professionalPrice;
        return enterprisePrice;
    }
    private int planDuration(String id) {
        if ("premium".equals(id)) return premiumDuration;
        if ("professional".equals(id)) return professionalDuration;
        return enterpriseDuration;
    }
    private String formatNaira(int a) { return "\u20a6" + String.format("%,d", a); }
    private String periodLabel(int days) {
        if (days == 7) return "/week";
        if (days == 30) return "/month";
        if (days == 90) return "/3 months";
        if (days % 30 == 0) return "/" + (days / 30) + " months";
        if (days % 7 == 0) return "/" + (days / 7) + " weeks";
        return "/" + days + " days";
    }

    private void loadSubscriptionPlans() {
        LinearLayout container = findViewById(R.id.subsPlansContainer);
        container.removeAllViews();
        TextView header = findViewById(R.id.tvCurrentPlanHeader);

        String phone = prefs().getString("phone", "");
        new Thread(() -> {
            // 1. Fetch pricing from admin panel
            try {
                URL purl = new URL(SERVER_URL + "/api/subscription-plans");
                HttpURLConnection pconn = (HttpURLConnection) purl.openConnection();
                pconn.setConnectTimeout(45000);
                pconn.setReadTimeout(45000);
                BufferedReader preader = new BufferedReader(new InputStreamReader(pconn.getInputStream()));
                StringBuilder psb = new StringBuilder();
                String pline;
                while ((pline = preader.readLine()) != null) psb.append(pline);
                preader.close();
                JSONObject pres = new JSONObject(psb.toString());
                JSONObject prem = pres.optJSONObject("premium");
                JSONObject prof = pres.optJSONObject("professional");
                JSONObject ent = pres.optJSONObject("enterprise");
                if (prem != null) { premiumPrice = prem.optInt("price", premiumPrice); premiumDuration = prem.optInt("duration", premiumDuration); }
                if (prof != null) { professionalPrice = prof.optInt("price", professionalPrice); professionalDuration = prof.optInt("duration", professionalDuration); }
                if (ent != null) { enterprisePrice = ent.optInt("price", enterprisePrice); enterpriseDuration = ent.optInt("duration", enterpriseDuration); }
            } catch (Exception e) { /* keep defaults */ }

            // 2. Fetch current plan (if logged in)
            if (!phone.isEmpty()) {
                try {
                    URL url = new URL(SERVER_URL + "/api/subscription/" + phone);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(45000);
                    conn.setReadTimeout(45000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject res = new JSONObject(sb.toString());
                    currentPlan = res.optString("plan", "none");
                    String expires = res.optString("expires_at", "");
                    handler.post(() -> {
                        String label = currentPlan.substring(0, 1).toUpperCase() + currentPlan.substring(1);
                        if (!expires.isEmpty()) {
                            String expDate = expires.length() > 10 ? expires.substring(0, 10) : expires;
                            header.setText("Current: " + label + " (expires " + expDate + ")");
                        } else {
                            header.setText("Current Plan: " + label);
                        }
                        buildPlanCards(container);
                    });
                } catch (Exception e) {
                    handler.post(() -> buildPlanCards(container));
                }
            } else {
                handler.post(() -> buildPlanCards(container));
            }
        }).start();
    }

    private void buildPlanCards(LinearLayout container) {
        container.removeAllViews();
        // Plans: {id, name, price, period, deviceLimit, features...}
        String[][] plans = {
            {"premium", "Premium", "\u20a6500", "/week", "1",
             "Ad blocking: UNLIMITED",
             "Saves 20-30% of your data (5x more)",
             "No savings cap \u2014 save forever",
             "Blocks background data: social apps",
             "Earn tasks: up to 5 tasks/day",
             "Access to premium-only tasks",
             "Detailed app-by-app analytics",
             "Max 1 device"},
            {"professional", "Professional", "\u20a61,500", "/month", "1",
             "Ad blocking: UNLIMITED",
             "Saves 30-40% of your data",
             "No savings cap \u2014 save forever",
             "Blocks ALL background apps",
             "Earn tasks: up to 8 tasks/day",
             "Access to professional-only tasks",
             "Aggressive image compression",
             "Full savings history and charts",
             "Max 1 device"},
            {"enterprise", "Enterprise", "\u20a65,000", "/month", "4",
             "Ad blocking: UNLIMITED",
             "Saves 40-50% of your data",
             "No savings cap \u2014 save forever",
             "Blocks ALL background apps",
             "Earn tasks: UNLIMITED tasks/day",
             "Access to ALL tasks including exclusive",
             "Maximum compression on all content",
             "Priority server \u2014 fastest speeds",
             "24/7 priority support",
             "Max 4 devices"}
        };

        for (String[] p : plans) {
            String planId = p[0];
            String planName = p[1];
            String price = formatNaira(planPrice(planId));
            String period = periodLabel(planDuration(planId));
            String deviceLimit = p[4];
            boolean isCurrent = planId.equals(currentPlan);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(getResources().getDrawable(R.drawable.card_bg));
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setElevation(dp(isCurrent ? 6 : 3));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dp(12);
            card.setLayoutParams(cp);

            // Badge row
            String badge = null;
            int badgeColor = 0xFF999999;
            if ("premium".equals(planId)) { badge = "POPULAR"; badgeColor = 0xFFC62828; }
            else if ("professional".equals(planId)) { badge = "BEST VALUE"; badgeColor = 0xFF2E7D32; }
            else if ("enterprise".equals(planId))   { badge = "MAXIMUM SAVINGS"; badgeColor = 0xFF1565C0; }
            if (badge != null) {
                TextView tvBadge = new TextView(this);
                tvBadge.setText(badge);
                tvBadge.setTextSize(10);
                tvBadge.setTypeface(null, Typeface.BOLD);
                tvBadge.setTextColor(badgeColor);
                tvBadge.setPadding(0, 0, 0, dp(4));
                card.addView(tvBadge);
            }

            // Title row
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView tvName = new TextView(this);
            tvName.setText(planName);
            tvName.setTextSize(18);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(0xFF333333);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleRow.addView(tvName);

            // Price column
            LinearLayout priceCol = new LinearLayout(this);
            priceCol.setOrientation(LinearLayout.VERTICAL);
            priceCol.setGravity(Gravity.RIGHT);
            TextView tvPrice = new TextView(this);
            tvPrice.setText(price + period);
            tvPrice.setTextSize(18);
            tvPrice.setTypeface(null, Typeface.BOLD);
            tvPrice.setTextColor(0xFFC62828);
            priceCol.addView(tvPrice);
            titleRow.addView(priceCol);
            card.addView(titleRow);

            // Device limit
            TextView tvDevices = new TextView(this);
            tvDevices.setText("\uD83D\uDCF1 " + deviceLimit + " device" + (Integer.parseInt(deviceLimit) > 1 ? "s" : "") + " max");
            tvDevices.setTextSize(11);
            tvDevices.setTypeface(null, Typeface.BOLD);
            tvDevices.setTextColor(0xFF1565C0);
            tvDevices.setPadding(0, dp(4), 0, dp(4));
            card.addView(tvDevices);

            // Divider
            View div = new View(this);
            div.setBackgroundColor(0xFFEEEEEE);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            dlp.topMargin = dp(8);
            dlp.bottomMargin = dp(8);
            div.setLayoutParams(dlp);
            card.addView(div);

            // Features (skip device limit entry)
            for (int i = 5; i < p.length; i++) {
                TextView feat = new TextView(this);
                String featureText = p[i];
                boolean isLimit = featureText.startsWith("No background") || featureText.startsWith("Capped");
                feat.setText((isLimit ? "\u2717  " : "\u2713  ") + featureText);
                feat.setTextSize(13);
                feat.setTextColor(isLimit ? 0xFFE53935 : 0xFF555555);
                feat.setPadding(0, 0, 0, dp(4));
                card.addView(feat);
            }

            // Button
            Button btn = new Button(this);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
            blp.topMargin = dp(8);
            btn.setLayoutParams(blp);
            btn.setTextSize(13);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setBackground(getResources().getDrawable(R.drawable.btn_rounded));

            if (isCurrent) {
                btn.setText("CURRENT PLAN");
                btn.setTextColor(0xFFFFFFFF);
                btn.setBackground(getResources().getDrawable(R.drawable.btn_green_rounded));
                btn.setEnabled(false);
            } else {
                String btnPrice = price + period;
                btn.setText("SUBSCRIBE - " + btnPrice);
                btn.setTextColor(0xFFFFFFFF);
                btn.setBackground(getResources().getDrawable(R.drawable.btn_rounded));
                btn.setOnClickListener(v -> subscribeToPlan(planId, planName, btnPrice));
            }
            card.addView(btn);
            container.addView(card);
        }
    }

    private void subscribeToPlan(String planId, String planName, String priceLabel) {
        showAppDialog(
            "Subscribe to " + planName,
            "This will charge " + priceLabel + " from your wallet balance.\n\nContinue?",
            "Subscribe",
            () -> {
                String phone = prefs().getString("phone", "");
                if (phone.isEmpty()) { Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show(); return; }
                Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        URL url = new URL(SERVER_URL + "/api/subscribe");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(45000);
                        conn.setReadTimeout(45000);
                        conn.setDoOutput(true);
                        JSONObject body = new JSONObject();
                        body.put("phone", phone);
                        body.put("plan", planId);
                        OutputStream os = conn.getOutputStream();
                        os.write(body.toString().getBytes());
                        os.close();
                        int code = conn.getResponseCode();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                            code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        JSONObject res = new JSONObject(sb.toString());
                        handler.post(() -> {
                            if (code < 400 && res.optBoolean("success")) {
                                currentPlan = planId;
                                prefs().edit().putString("subscription_plan", planId).apply();
                                Toast.makeText(this, res.optString("message", "Subscribed!"), Toast.LENGTH_LONG).show();
                                fetchWalletBalance();
                                loadSubscriptionPlans();
                            } else {
                                String err = res.optString("error", "Subscription failed");
                                if (err.contains("Insufficient")) {
                                    showTopUpPrompt(err);
                                } else {
                                    Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    } catch (Exception e) {
                        handler.post(() -> Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            },
            "Cancel",
            null
        );
    }

    private void switchTab(int tab) {
        tabHome.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        tabAirtime.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        tabData.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        tabTransactions.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);
        tabEarn.setVisibility(tab == 4 ? View.VISIBLE : View.GONE);
        tabProfile.setVisibility(tab == 5 ? View.VISIBLE : View.GONE);

        TextView[] navItems = {navHome, navAirtime, navData, navTransactions, navEarn, navProfile};
        for (int i = 0; i < navItems.length; i++) {
            navItems[i].setTextColor(i == tab ? 0xFFC62828 : 0xFF90A4AE);
            navItems[i].setTypeface(null, i == tab ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (tab == 1) {
            String savedPhone = prefs().getString("phone", "");
            if (!savedPhone.isEmpty() && etPhone.getText().toString().trim().isEmpty()) {
                etPhone.setText(savedPhone);
            }
        }
        if (tab == 2) { loadSubscriptionPlans(); }
        if (tab == 3) {
            refreshUsageHistory();
            fetchTransactions();
            loadSavingsHistory(); // always refresh savings when tab opens
        }
        if (tab == 4) {
            if (earnTasksContainer == null) initEarnTab();
            loadReferralStats();
            loadEarnTab();
        }
        if (tab == 5) { refreshProfileUI(); loadProfilePhoto(); loadActivityLog(); }
    }

    private boolean hasUsagePermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // ==================== EARN TAB ====================

    private LinearLayout earnTasksContainer;
    private TextView tvNoTasks, tvEarnPending, tvEarnClaimable;
    private static final int PICK_PROOF = 1003;
    private String proofTaskId = null;

    private void initEarnTab() {
        earnTasksContainer = findViewById(R.id.earnTasksContainer);
        tvNoTasks = findViewById(R.id.tvNoTasks);
        tvEarnPending = findViewById(R.id.tvEarnPending);
        tvEarnClaimable = findViewById(R.id.tvEarnClaimable);
        findViewById(R.id.btnRefreshTasks).setOnClickListener(v -> loadEarnTab());
        findViewById(R.id.btnRefreshTasksBig).setOnClickListener(v -> loadEarnTab());
        findViewById(R.id.btnClaimRewards).setOnClickListener(v -> claimRewards());
        initReferralSection();
    }

    // ==================== REFERRAL SYSTEM ====================

    private TextView tvReferralLink, tvReferralCount, tvReferralEarnings, tvReferralReward;

    private void initReferralSection() {
        tvReferralLink = findViewById(R.id.tvReferralLink);
        tvReferralCount = findViewById(R.id.tvReferralCount);
        tvReferralEarnings = findViewById(R.id.tvReferralEarnings);
        tvReferralReward = findViewById(R.id.tvReferralReward);

        // Generate or load referral code — always use server as source of truth
        String phone = prefs().getString("phone", "");
        if (!phone.isEmpty()) {
            tvReferralLink.setText("Loading your code...");
            tvReferralLink.setOnClickListener(v ->
                Toast.makeText(MainActivity.this, "Loading referral code, please wait...", Toast.LENGTH_SHORT).show());
        }

        findViewById(R.id.btnShareReferral).setOnClickListener(v -> {
            if (phone == null || phone.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please login first", Toast.LENGTH_SHORT).show();
                return;
            }
            String myCode = prefs().getString("referral_code", "");
            if (myCode.isEmpty()) {
                Toast.makeText(MainActivity.this, "Loading referral code... Open Earn tab and try again.", Toast.LENGTH_SHORT).show();
                loadReferralStats();
                return;
            }
            copyToClipboard(myCode);
            Toast.makeText(MainActivity.this, "Referral code copied!", Toast.LENGTH_SHORT).show();
        });

        // Load referral stats
        loadReferralStats();
    }

    private void loadReferralStats() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) return;
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/referrals/stats?phone=" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() != 200) return;
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                int count = res.optInt("referral_count", 0);
                int earnings = res.optInt("total_earnings", 0);
                int rewardPerRef = res.optInt("reward_per_referral", 500);
                String serverCode = res.optString("referral_code", "");
                handler.post(() -> {
                    tvReferralCount.setText(String.valueOf(count));
                    tvReferralEarnings.setText("\u20a6" + earnings);
                    tvReferralReward.setText("Up to \u20a6" + rewardPerRef + "/referral");
                    // Use the authoritative code from server so it always matches
                    if (!serverCode.isEmpty()) {
                        prefs().edit().putString("referral_code", serverCode).apply();
                        tvReferralLink.setText("Your code: " + serverCode);
                        tvReferralLink.setOnClickListener(v -> {
                            copyToClipboard(serverCode);
                            Toast.makeText(MainActivity.this, "Referral code copied!", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } catch (Exception e) {
                // Silently fail — stats will show 0
            }
        }).start();
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("referral", text));
    }

    private void preloadEarnTab() {
        if (prefs().getString("phone", "").isEmpty()) return;
        if (earnTasksContainer == null) initEarnTab();
        loadEarnTab();
    }

    private void loadEarnTab() {
        loadEarnTabWithRetry(0);
    }

    private void loadEarnTabWithRetry(int attempt) {
        if (earnTasksContainer == null) initEarnTab();
        earnTasksContainer.removeAllViews();
        tvNoTasks.setText(attempt > 0 ? "Connecting..." : "Loading tasks...");
        tvNoTasks.setVisibility(View.VISIBLE);
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { tvNoTasks.setText("Login to see tasks"); return; }

        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/tasks?phone=" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(45000); conn.setReadTimeout(45000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                JSONArray tasks = res.optJSONArray("tasks");
                JSONArray lockedTasks = res.optJSONArray("locked_tasks");
                int pending = res.optInt("pending_reward", 0);
                int claimable = res.optInt("claimable_reward", 0);
                int hiddenCount = res.optInt("hidden_count", 0);
                int dailyLimit = res.optInt("daily_limit", 2);
                String serverPlan = res.optString("user_plan", "none");
                handler.post(() -> {
                    tvEarnPending.setText("\u20a6" + pending);
                    tvEarnClaimable.setText("\u20a6" + claimable);
                    showTasks(tasks, lockedTasks, hiddenCount, dailyLimit, serverPlan);
                });
            } catch (Exception e) {
                if (attempt < 2) {
                    handler.postDelayed(() -> loadEarnTabWithRetry(attempt + 1), 5000);
                } else {
                    handler.post(() -> { tvNoTasks.setText("Could not load tasks. Tap Refresh."); tvNoTasks.setVisibility(View.VISIBLE); });
                }
            }
        }).start();
    }

    private void showTasks(JSONArray tasks, JSONArray lockedTasks, int hiddenCount, int dailyLimit, String userPlan) {
        earnTasksContainer.removeAllViews();
        tvNoTasks.setVisibility(View.GONE);

        boolean hasTasks = tasks != null && tasks.length() > 0;
        boolean hasLocked = lockedTasks != null && lockedTasks.length() > 0;

        // Daily limit info bar
        String planLabel = userPlan.substring(0, 1).toUpperCase() + userPlan.substring(1);
        TextView tvLimit = new TextView(this);
        tvLimit.setTextSize(12);
        tvLimit.setTextColor(0xFF666666);
        tvLimit.setPadding(0, 0, 0, dp(8));
        if ("none".equals(userPlan)) {
            tvLimit.setText("Free plan: up to " + dailyLimit + " tasks/day. Upgrade to earn more!");
        } else {
            tvLimit.setText(planLabel + " plan: up to " + (dailyLimit == 999 ? "unlimited" : String.valueOf(dailyLimit)) + " tasks/day");
        }
        earnTasksContainer.addView(tvLimit);

        // No tasks for this user
        if (!hasTasks) {
            tvNoTasks.setVisibility(View.VISIBLE);
            if ("none".equals(userPlan)) {
                tvNoTasks.setText("No free tasks available today.");
                // Upgrade button
                Button upgradeBtn = new Button(this);
                upgradeBtn.setText("Upgrade to see more tasks & earn bigger");
                upgradeBtn.setTextColor(0xFFFFFFFF);
                upgradeBtn.setBackground(getResources().getDrawable(R.drawable.btn_rounded));
                upgradeBtn.setTextSize(13);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
                lp.topMargin = dp(8);
                upgradeBtn.setLayoutParams(lp);
                upgradeBtn.setOnClickListener(v -> switchTab(3));
                earnTasksContainer.addView(upgradeBtn);
            } else {
                tvNoTasks.setText("No tasks available right now. Check back later!");
            }
        }

        // Visible tasks
        if (hasTasks) {
            for (int i = 0; i < tasks.length(); i++) {
                try { earnTasksContainer.addView(buildTaskCard(tasks.getJSONObject(i), false)); } catch (Exception e) {}
            }
        }

        // Hidden count banner (tasks exist but daily limit hit)
        if (hiddenCount > 0) {
            TextView tvHidden = new TextView(this);
            tvHidden.setText("+ " + hiddenCount + " more tasks available — upgrade to see them all");
            tvHidden.setTextSize(12);
            tvHidden.setTextColor(0xFFC62828);
            tvHidden.setTypeface(null, android.graphics.Typeface.BOLD);
            tvHidden.setPadding(0, dp(4), 0, dp(8));
            tvHidden.setOnClickListener(v -> switchTab(3));
            earnTasksContainer.addView(tvHidden);
        }

        // Locked tasks (require higher plan)
        if (hasLocked) {
            TextView tvLockedHeader = new TextView(this);
            tvLockedHeader.setText("Locked Tasks — People are earning big!");
            tvLockedHeader.setTextSize(13);
            tvLockedHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            tvLockedHeader.setTextColor(0xFF333333);
            tvLockedHeader.setPadding(0, dp(12), 0, dp(6));
            earnTasksContainer.addView(tvLockedHeader);

            for (int i = 0; i < lockedTasks.length(); i++) {
                try { earnTasksContainer.addView(buildTaskCard(lockedTasks.getJSONObject(i), true)); } catch (Exception e) {}
            }
        }
    }

    private View buildTaskCard(JSONObject task, boolean locked) throws Exception {
        String title = task.optString("title", "Task");
        String type = task.optString("type", "general");
        int reward = task.optInt("reward", 0);
        String rewardType = task.optString("reward_type", "airtime");
        String status = task.optString("user_status", "available");
        String minPlan = task.optString("min_plan", "none");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(getResources().getDrawable(R.drawable.card_bg));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setElevation(dp(3));
        card.setGravity(Gravity.CENTER_VERTICAL);
        if (locked) card.setAlpha(0.6f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(10);
        card.setLayoutParams(cp);

        // Icon
        TextView icon = new TextView(this);
        String emoji = locked ? "\ud83d\udd12" : "\ud83d\udcf1";
        if (!locked) {
            if ("video".equals(type) || "youtube".equals(type)) emoji = "\ud83c\udfac";
            else if ("follow".equals(type)) emoji = "\ud83d\udc65";
            else if ("advert".equals(type)) emoji = "\ud83d\udcfa";
        }
        icon.setText(emoji);
        icon.setTextSize(24);
        icon.setPadding(0, 0, dp(12), 0);
        card.addView(icon);

        // Text
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView tvTitle = new TextView(this);
        tvTitle.setText(locked ? title + " (Locked)" : title);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(0xFF333333);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvTitle);
        TextView tvReward = new TextView(this);
        tvReward.setText("Earn \u20a6" + reward + " " + rewardType);
        tvReward.setTextSize(12);
        tvReward.setTextColor(locked ? 0xFF999999 : 0xFF43A047);
        textCol.addView(tvReward);
        if (locked) {
            String planLabel = minPlan.substring(0, 1).toUpperCase() + minPlan.substring(1);
            TextView tvLock = new TextView(this);
            tvLock.setText(planLabel + " plan required");
            tvLock.setTextSize(11);
            tvLock.setTextColor(0xFFC62828);
            textCol.addView(tvLock);
        }
        card.addView(textCol);

        // Action button
        TextView tvAction = new TextView(this);
        tvAction.setTextSize(11);
        tvAction.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAction.setPadding(dp(10), dp(6), dp(10), dp(6));
        if (locked) {
            tvAction.setText("UPGRADE");
            tvAction.setTextColor(0xFFFFFFFF);
            tvAction.setBackgroundColor(0xFFC62828);
            card.setOnClickListener(v -> switchTab(3));
        } else if ("pending".equals(status)) {
            tvAction.setText("PENDING"); tvAction.setTextColor(0xFFFF8F00); tvAction.setBackgroundColor(0xFFFFF3E0);
        } else if ("approved".equals(status)) {
            tvAction.setText("CLAIM"); tvAction.setTextColor(0xFFFFFFFF); tvAction.setBackgroundColor(0xFF43A047);
        } else if ("claimed".equals(status)) {
            tvAction.setText("DONE \u2713"); tvAction.setTextColor(0xFF43A047); tvAction.setBackgroundColor(0xFFE8F5E9);
        } else {
            tvAction.setText("START"); tvAction.setTextColor(0xFFFFFFFF); tvAction.setBackgroundColor(0xFFC62828);
            final JSONObject fTask = task;
            card.setOnClickListener(v -> showTaskDetail(fTask));
        }
        card.addView(tvAction);
        return card;
    }

    private void showTaskDetail(JSONObject task) {
        String id = task.optString("id");
        String title = task.optString("title", "Task");
        String description = task.optString("description", "");
        String instructions = task.optString("instructions", "");
        String link = task.optString("link", "");
        int reward = task.optInt("reward", 0);
        String rewardType = task.optString("reward_type", "airtime");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        // Description
        if (!description.isEmpty()) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText(description);
            tvDesc.setTextSize(14); tvDesc.setTextColor(0xFF555555);
            tvDesc.setPadding(0, 0, 0, dp(12));
            layout.addView(tvDesc);
        }

        // Reward
        TextView tvRew = new TextView(this);
        tvRew.setText("\ud83c\udf81 Reward: \u20a6" + reward + " " + rewardType);
        tvRew.setTextSize(15); tvRew.setTextColor(0xFF43A047); tvRew.setTypeface(null, Typeface.BOLD);
        tvRew.setPadding(0, 0, 0, dp(12));
        layout.addView(tvRew);

        // Instructions
        if (!instructions.isEmpty()) {
            TextView tvInst = new TextView(this);
            tvInst.setText("\ud83d\udcdd Instructions:\n" + instructions);
            tvInst.setTextSize(13); tvInst.setTextColor(0xFF333333);
            tvInst.setBackgroundColor(0xFFF5F5F5);
            tvInst.setPadding(dp(12), dp(10), dp(12), dp(10));
            layout.addView(tvInst);
        }

        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("START TASK", (d, w) -> {
                if (!link.isEmpty()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); } catch (Exception e) {}
                }
                proofTaskId = id;
                handler.postDelayed(() -> showProofUploadDialog(id, title, reward, rewardType), 2000);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showProofUploadDialog(String taskId, String title, int reward, String rewardType) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Take a screenshot showing you completed the task, then upload it as proof.");
        tvInfo.setTextSize(13); tvInfo.setTextColor(0xFF555555);
        tvInfo.setPadding(0, 0, 0, dp(16));
        layout.addView(tvInfo);

        TextView tvReward = new TextView(this);
        tvReward.setText("Reward: \u20a6" + reward + " " + rewardType + " (after approval)");
        tvReward.setTextSize(13); tvReward.setTextColor(0xFF43A047); tvReward.setTypeface(null, Typeface.BOLD);
        tvReward.setPadding(0, 0, 0, dp(12));
        layout.addView(tvReward);

        new AlertDialog.Builder(this)
            .setTitle("Submit Proof - " + title)
            .setView(layout)
            .setPositiveButton("UPLOAD SCREENSHOT", (d, w) -> {
                proofTaskId = taskId;
                Intent pick = new Intent(Intent.ACTION_PICK);
                pick.setType("image/*");
                startActivityForResult(pick, PICK_PROOF);
            })
            .setNeutralButton("Do it later", null)
            .show();
    }

    private void submitTaskProof(String taskId, Bitmap proofBitmap) {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Submitting proof...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                proofBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                String base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

                URL url = new URL(SERVER_URL + "/api/tasks/submit");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(45000); conn.setReadTimeout(45000);
                conn.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("phone", phone);
                body.put("task_id", taskId);
                body.put("proof_base64", base64);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.close();
                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                handler.post(() -> {
                    if (code < 400 && res.optBoolean("success")) {
                        showAlertDialog("\u2705 Proof Submitted!", "Your screenshot has been submitted. You'll receive your reward after admin reviews it.");
                        loadEarnTab();
                    } else {
                        showAlertDialog("Submission Failed", res.optString("error", "Could not submit proof. Try again."));
                    }
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void claimRewards() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { showAlertDialog("Error", "Please login first"); return; }

        showAppDialog(
            "Claim Rewards",
            "Your approved rewards will be added to your wallet balance.",
            "Claim Now",
            () -> {
                showAlertDialog("Processing", "Claiming your rewards...");
                new Thread(() -> {
                    try {
                        URL url = new URL(SERVER_URL + "/api/tasks/claim");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(45000); conn.setReadTimeout(45000);
                        conn.setDoOutput(true);
                        JSONObject body = new JSONObject();
                        body.put("phone", phone);
                        OutputStream os = conn.getOutputStream();
                        os.write(body.toString().getBytes());
                        os.close();
                        int code = conn.getResponseCode();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                            code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        JSONObject res = new JSONObject(sb.toString());
                        handler.post(() -> {
                            if (code < 400 && res.optBoolean("success")) {
                                showAlertDialog("\u2705 Rewards Claimed!", res.optString("message", "Rewards added to your wallet!"));
                                fetchWalletBalance();
                                loadEarnTab();
                            } else {
                                showAlertDialog("No Rewards", res.optString("error", "No approved rewards to claim yet. Complete tasks and wait for admin approval."));
                            }
                        });
                    } catch (Exception e) {
                        handler.post(() -> showAlertDialog("Error", "Connection failed. Please try again."));
                    }
                }).start();
            },
            "Cancel",
            null
        );
    }

    private void showAlertDialog(String title, String message) {
        showAppDialog(title, message);
    }

    private void showAppDialog(String title, String message) {
        showAppDialog(title, message, "OK", null, null, null, true);
    }

    private void showAppDialog(String title, String message, String positiveText, Runnable onPositive,
            String negativeText, Runnable onNegative) {
        showAppDialog(title, message, positiveText, onPositive, negativeText, onNegative, true);
    }

    private void showAppDialog(String title, String message, String positiveText, Runnable onPositive,
            String negativeText, Runnable onNegative, boolean cancelable) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        if (message != null && !message.isEmpty()) {
            TextView tvMsg = new TextView(this);
            tvMsg.setText(message);
            tvMsg.setTextSize(14);
            tvMsg.setTextColor(0xFF666666);
            tvMsg.setLineSpacing(dp(3), 1f);
            content.addView(tvMsg);
        }
        showAppDialogShell(title, content, positiveText, onPositive, negativeText, onNegative, cancelable);
    }

    private void showNetworkMismatchDialog(String phone, String detected) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView tvHint = new TextView(this);
        tvHint.setText("This number may belong to a different network than the one you selected.");
        tvHint.setTextSize(14);
        tvHint.setTextColor(0xFF666666);
        tvHint.setLineSpacing(dp(3), 1f);
        tvHint.setPadding(0, 0, 0, dp(14));
        content.addView(tvHint);

        content.addView(makeNetworkCompareRow("Detected", networkDisplayName(detected), 0xFF2E7D32, 0xFFE8F5E9));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        View spacer = new View(this);
        spacer.setLayoutParams(gap);
        content.addView(spacer);
        content.addView(makeNetworkCompareRow("Selected", networkDisplayName(selectedNetwork), 0xFFC62828, 0xFFFFEBEE));

        TextView tvWarn = new TextView(this);
        tvWarn.setText("Sending to the wrong network may fail or credit the wrong line.");
        tvWarn.setTextSize(12);
        tvWarn.setTextColor(0xFF888888);
        tvWarn.setPadding(0, dp(14), 0, 0);
        content.addView(tvWarn);

        showAppDialogShell(
            "Check Network",
            content,
            "Continue Anyway",
            () -> proceedWithBuy(phone),
            "Go Back",
            null,
            true
        );
    }

    private LinearLayout makeNetworkCompareRow(String label, String network, int textColor, int bgColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(bgColor);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(0xFF888888);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tvLabel);

        TextView tvNetwork = new TextView(this);
        tvNetwork.setText(network);
        tvNetwork.setTextSize(15);
        tvNetwork.setTypeface(null, Typeface.BOLD);
        tvNetwork.setTextColor(textColor);
        row.addView(tvNetwork);
        return row;
    }

    private void showAppDialogShell(String title, View contentView, String positiveText, Runnable onPositive,
            String negativeText, Runnable onNegative, boolean cancelable) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getResources().getDrawable(R.drawable.card_bg));
        card.setPadding(dp(22), dp(22), dp(22), dp(18));

        View accent = new View(this);
        accent.setBackgroundColor(0xFFC62828);
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        accentLp.bottomMargin = dp(14);
        card.addView(accent, accentLp);

        if (title != null && !title.isEmpty()) {
            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextSize(18);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(0xFF333333);
            tvTitle.setPadding(0, 0, 0, dp(10));
            card.addView(tvTitle);
        }

        if (contentView != null) {
            card.addView(contentView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setCancelable(cancelable)
            .create();

        if (positiveText != null || negativeText != null) {
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.END);
            btnRow.setPadding(0, dp(16), 0, 0);

            if (negativeText != null) {
                TextView btnNeg = makeOutlineBtn(negativeText);
                btnNeg.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onNegative != null) onNegative.run();
                });
                btnRow.addView(btnNeg);
            }
            if (positiveText != null) {
                TextView btnPos = makePrimaryBtn(positiveText);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dp(10);
                btnPos.setLayoutParams(lp);
                btnPos.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onPositive != null) onPositive.run();
                });
                btnRow.addView(btnPos);
            }
            card.addView(btnRow);
        }

        dialog.setView(card);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private TextView makePrimaryBtn(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(13);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(getResources().getDrawable(R.drawable.btn_rounded));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView makeOutlineBtn(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(0xFFC62828);
        b.setTextSize(13);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(getResources().getDrawable(R.drawable.btn_outline_rounded));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private static final int VPN_REQUEST_CODE = 1002;

    private void toggle() {
        if (DataSaverService.isRunning || DataSaverVpnService.isVpnRunning) {
            Intent i = new Intent(this, DataSaverService.class);
            i.setAction(DataSaverService.ACTION_STOP);
            startService(i);
            Intent vi = new Intent(this, DataSaverVpnService.class);
            vi.setAction("STOP");
            startService(vi);
            polling = false;
            handler.postDelayed(() -> updateUI(), 500);
        } else {
            // Check if user has a valid subscription before allowing DataSaver
            String plan = prefs().getString("subscription_plan", "").toLowerCase();
            if (plan.isEmpty() || plan.equals("none") || plan.equals("basic") || plan.equals("free")) {
                // No subscription - prompt to subscribe
                showAppDialog(
                    "Subscription Required",
                    "You need an active Premium, Professional, or Enterprise plan to use DataSaver. Would you like to subscribe now?",
                    "Subscribe",
                    () -> switchTab(2),
                    "Cancel",
                    null
                );
                return;
            }
            // Usage permission is optional — app still works without it (just no app breakdown)
            if (!hasUsagePermission()) {
                tvAppUsageEmpty.setText("Tap here to grant permission for app usage breakdown (optional)");
            }
            // Request VPN permission
            Intent vpnIntent = android.net.VpnService.prepare(this);
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
            } else {
                startServices();
            }
        }
    }

    private void startServices() {
        try {
            Intent i = new Intent(this, DataSaverService.class);
            i.setAction(DataSaverService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            android.util.Log.d("DataSaver", "DataSaverService start failed: none");
        } catch (Exception e) {
            android.util.Log.e("DataSaver", "DataSaverService start failed: " + e.getMessage());
        }
        try {
            startService(new Intent(this, DataSaverVpnService.class));
        } catch (Exception e) {
            android.util.Log.e("DataSaver", "VpnService start failed: " + e.getMessage());
        }
        polling = true;
        handler.postDelayed(() -> pollUI(), 1000);
        handler.postDelayed(() -> updateUI(), 500);
        // Request battery optimization exemption AFTER services start, non-blocking
        handler.postDelayed(() -> requestBatteryOptimizationExemption(), 3000);
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= 23) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent bIntent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    bIntent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(bIntent);
                } catch (Exception e) {}
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restore counters in case app was backgrounded
        long savedAdReqs = prefs().getLong("real_ad_requests", 0);
        long savedBgSyncs = prefs().getLong("real_bg_syncs", 0);
        if (savedAdReqs > DataSaverVpnService.blockedAdRequests.get()) DataSaverVpnService.blockedAdRequests.set(savedAdReqs);
        if (savedBgSyncs > DataSaverVpnService.blockedBgSyncs.get()) DataSaverVpnService.blockedBgSyncs.set(savedBgSyncs);
        updateUI();
        updateSummary();
        fetchWalletBalance();
        verifyPendingPayment();
        updateNotifBadge();
        // Check for new notifications when app comes to foreground
        checkNotificationsOnResume();
        // Also start periodic notification polling (works even if VPN is off, as long as app is open)
        startNotificationPolling();
        if ((DataSaverService.isRunning || DataSaverVpnService.isVpnRunning) && !polling) {
            polling = true;
            handler.postDelayed(() -> pollUI(), 1000);
        }
        
        // Diagnostic: If VPN shows running but counters are 0, show warning
        if (DataSaverVpnService.isVpnRunning) {
            long currentBlocked = DataSaverVpnService.blockedAdRequests.get() + DataSaverVpnService.blockedBgSyncs.get();
            if (currentBlocked == 0) {
                android.util.Log.w("DataSaver", "VPN running but counters are 0 - possible issue");
                // Show subtle warning after 5 seconds if still 0
                handler.postDelayed(() -> {
                    long nowBlocked = DataSaverVpnService.blockedAdRequests.get() + DataSaverVpnService.blockedBgSyncs.get();
                    if (nowBlocked == 0 && DataSaverVpnService.isVpnRunning) {
                        android.widget.Toast.makeText(this, 
                            "VPN is on but not counting. Try toggling off and on again.", 
                            android.widget.Toast.LENGTH_LONG).show();
                    }
                }, 5000);
            }
        }
    }

    // Periodic notification polling - runs every 30 seconds when app is open (even if VPN is off)
    private void startNotificationPolling() {
        handler.removeCallbacks(notificationPoller);
        handler.postDelayed(notificationPoller, 30000);
    }
    
    private Runnable notificationPoller = new Runnable() {
        public void run() {
            checkNotificationsOnResume();
            // Keep polling while app is in foreground
            if (!isFinishing()) {
                handler.postDelayed(this, 30000);
            }
        }
    };

    private void checkNotificationsOnResume() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) return;
        
        // Check for scheduled notifications (daily summary, tasks, upgrades)
        checkScheduledNotifications();
        
        // Check for admin push notifications
        new Thread(() -> {
            try {
                long lastId = prefs().getLong("last_notif_id", 0);
                URL url = new URL(SERVER_URL + "/api/notifications?phone=" + phone + "&since_id=" + lastId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() != 200) return;
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONArray arr = new JSONArray(sb.toString());
                if (arr.length() > 0) {
                    long newLastId = lastId;
                    final String[] latestTitle = {""};
                    final String[] latestBody = {""};
                    for (int i = 0; i < arr.length(); i++) {
                        long nid = arr.getJSONObject(i).optLong("id", 0);
                        if (nid > newLastId) newLastId = nid;
                        // Get the latest notification for system popup
                        if (i == arr.length() - 1) {
                            latestTitle[0] = arr.getJSONObject(i).optString("title", "DataSaver");
                            latestBody[0] = arr.getJSONObject(i).optString("body", "");
                        }
                    }
                    int newCount = arr.length();
                    DataSaverVpnService.unreadNotifCount += newCount;
                    DataSaverVpnService.hasNewNotif = true;
                    prefs().edit()
                        .putLong("last_notif_id", newLastId)
                        .putInt("unread_notif_count", DataSaverVpnService.unreadNotifCount)
                        .apply();
                    handler.post(() -> updateNotifBadge());
                    // Show system notification popup with vibration
                    if (!latestBody[0].isEmpty()) {
                        final String title = latestTitle[0];
                        final String body = latestBody[0];
                        handler.post(() -> showPushNotification(title, body));
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    // Create notification channel for system push notifications
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "datasaver_push",
                "DataSaver Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Push notifications for data saved, airtime purchased, and more");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // Show system notification
    private void showPushNotification(String title, String body) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            );

            // Use Notification.Builder for API 11+
            Notification.Builder builder = new Notification.Builder(this);
            builder.setContentTitle(title);
            builder.setContentText(body);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info);
            builder.setContentIntent(pendingIntent);
            builder.setAutoCancel(true);
            
            // Set vibration for API < 26
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setVibrate(new long[]{0, 500, 200, 500});
            }
            
            // Use priority for API < 26
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(Notification.PRIORITY_HIGH);
            }

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify((int) System.currentTimeMillis(), builder.build());
            }
        } catch (Exception e) {
            // Ignore notification errors
        }
    }

    // Check and show scheduled notifications (daily summary, tasks, upgrades)
    private void checkScheduledNotifications() {
        try {
            SharedPreferences prefs = prefs();
            String phone = prefs.getString("phone", "");
            if (phone.isEmpty()) return;
            
            // Check if push notifications are enabled
            boolean pushEnabled = prefs.getBoolean("push_notif", true);
            if (!pushEnabled) return;
            
            long now = System.currentTimeMillis();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = cal.get(java.util.Calendar.MINUTE);
            
            // Get last notification times
            long lastDailySummary = prefs.getLong("last_daily_summary", 0);
            long lastTaskReminder = prefs.getLong("last_task_reminder", 0);
            long lastUpgradePrompt = prefs.getLong("last_upgrade_prompt", 0);
            
            // 1. Daily summary at 9pm (21:00) — only if Daily Usage Alerts are enabled
            boolean dailyAlertsOn = prefs.getBoolean("daily_alerts", true);
            if (dailyAlertsOn && hour >= 21 && hour < 22) {
                if (now - lastDailySummary > 24 * 60 * 60 * 1000) {
                    // Get today's stats and show summary
                    long blocked = DataSaverVpnService.blockedAdRequests.get() + DataSaverVpnService.blockedBgSyncs.get();
                    long savedBytes = blocked * 5 * 1024;
                    String saved = formatBytes(savedBytes);
                    String title = "Daily Summary";
                    String body = "Today you saved " + saved + "! Keep DataSaver running to save more.";
                    showPushNotification(title, body);
                    prefs.edit().putLong("last_daily_summary", now).apply();
                }
            }
            
            // 2. Task reminder (check if there are new tasks - show once per day)
            if (now - lastTaskReminder > 24 * 60 * 60 * 1000) {
                final String taskPhone = phone;
                new Thread(() -> {
                    try {
                        URL url = new URL(SERVER_URL + "/api/tasks?phone=" + taskPhone);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(10000);
                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder sb = new StringBuilder(); String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            reader.close();
                            JSONObject res = new JSONObject(sb.toString());
                            JSONArray tasks = res.optJSONArray("tasks");
                            if (tasks != null && tasks.length() > 0) {
                                handler.post(() -> showPushNotification("New Tasks Available!", 
                                    "You have " + tasks.length() + " tasks waiting. Tap to earn rewards!"));
                                prefs.edit().putLong("last_task_reminder", System.currentTimeMillis()).apply();
                            }
                        }
                    } catch (Exception e) {}
                }).start();
            }
            
            // 3. Upgrade prompt for free users (once per week)
            String plan = prefs.getString("subscription_plan", "none");
            if ("none".equals(plan) && now - lastUpgradePrompt > 7 * 24 * 60 * 60 * 1000) {
                showPushNotification("Upgrade to Premium", 
                    "Get unlimited tasks, higher rewards, and no ads! Tap to upgrade.");
                prefs.edit().putLong("last_upgrade_prompt", now).apply();
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }

    private void updateUI() {
        boolean isOn = DataSaverService.isRunning || DataSaverVpnService.isVpnRunning;
        if (isOn) {
            long ads = DataSaverVpnService.blockedAdRequests.get();
            long bgSyncs = DataSaverVpnService.blockedBgSyncs.get();
            long totalBlocked = ads + bgSyncs;
            String statusMsg = DataSaverVpnService.isVpnRunning ? "VPN Connected" : "Connected";
            if (totalBlocked > 0) {
                statusMsg += " \u2014 " + totalBlocked + " requests blocked";
            } else {
                statusMsg += " \u2014 Protecting your data";
            }
            tvStatus.setText(statusMsg);
            btnConnect.setText("ON");
            btnConnect.setBackgroundResource(R.drawable.circle_on);
            btnConnect.setTextColor(0xFFFFFFFF);
        } else {
            tvStatus.setText("Tap to connect");
            btnConnect.setText("OFF");
            btnConnect.setBackgroundResource(R.drawable.circle_off);
            btnConnect.setTextColor(0xFFC62828);
        }
    }

    private void pollUI() {
        if (!polling) return;
        
        // Auto-stop polling if both services have died
        if (!DataSaverService.isRunning && !DataSaverVpnService.isVpnRunning) {
            // If VPN was running before but now stopped unexpectedly, notify user
            if (wasVpnRunning) {
                wasVpnRunning = false;
                android.widget.Toast.makeText(this, 
                    "VPN stopped unexpectedly. Tap toggle to restart.", 
                    android.widget.Toast.LENGTH_LONG).show();
            }
            polling = false;
            updateUI();
            return;
        }
        
        // Track VPN state
        if (DataSaverVpnService.isVpnRunning) {
            wasVpnRunning = true;
        }
        
        updateUI();
        updateSummary();
        updateAppCards();
        updateNotifBadge();
        // Persist unread count
        if (DataSaverVpnService.unreadNotifCount > 0) {
            prefs().edit().putInt("unread_notif_count", DataSaverVpnService.unreadNotifCount).apply();
        }
        handler.postDelayed(() -> pollUI(), 2000);
    }

    private void applyFilter(String filter, TextView today, TextView week, TextView month) {
        usageFilter = filter;
        today.setBackground(getResources().getDrawable("today".equals(filter) ? R.drawable.pill_active : R.drawable.pill_bg));
        today.setTextColor("today".equals(filter) ? 0xFFFFFFFF : 0xFF666666);
        week.setBackground(getResources().getDrawable("week".equals(filter) ? R.drawable.pill_active : R.drawable.pill_bg));
        week.setTextColor("week".equals(filter) ? 0xFFFFFFFF : 0xFF666666);
        month.setBackground(getResources().getDrawable("month".equals(filter) ? R.drawable.pill_active : R.drawable.pill_bg));
        month.setTextColor("month".equals(filter) ? 0xFFFFFFFF : 0xFF666666);
        loadAppUsageBackground();
    }

    private long getFilterWindowMs() {
        if ("week".equals(usageFilter)) return 7L * 24 * 60 * 60 * 1000;
        if ("month".equals(usageFilter)) return 30L * 24 * 60 * 60 * 1000;
        return 24L * 60 * 60 * 1000;
    }

    private void loadAppUsageBackground() {
        if (!hasUsagePermission()) return;
        new Thread(() -> {
            try {
                DataSaverService.loadStaticUsage(this, getFilterWindowMs());
                handler.post(() -> { updateSummary(); updateAppCards(); });
            } catch (Exception e) {}
        }).start();
    }

    private void updateSummary() {
        // Use local data - server sync happens separately
        updateSummaryLocal();
    }
    
    private void updateSummaryLocal() {
        // Original local calculation as fallback
        long sessionRx = DataSaverService.totalBytesRx;
        long sessionTx = DataSaverService.totalBytesTx;
        long sessionTotal = sessionRx + sessionTx;
        
        // Also try today's total as fallback
        long todayTotal = DataSaverService.getTodayTotalUsage(this);
        long totalAppData = sessionTotal > 0 ? sessionTotal : todayTotal;
        
        if (totalAppData <= 0) {
            for (Map.Entry<String, long[]> entry : DataSaverService.appDataUsage.entrySet()) {
                String n = entry.getKey();
                if (n.equals("Acorn Datasaver") || n.equals("DataSaver")) continue;
                totalAppData += entry.getValue()[0] + entry.getValue()[1];
            }
        }

        // REAL metrics from VPN (session-based)
        boolean vpnRunning = DataSaverVpnService.isVpnRunning;

        long adReqs, bgSyncs, totalDns;
        if (vpnRunning) {
            adReqs = DataSaverVpnService.blockedAdRequests.get();
            bgSyncs = DataSaverVpnService.blockedBgSyncs.get();
            totalDns = DataSaverVpnService.totalDnsQueries.get();
        } else {
            adReqs = prefs().getLong("real_ad_requests", 0);
            bgSyncs = prefs().getLong("real_bg_syncs", 0);
            totalDns = prefs().getLong("real_total_dns", 0);
        }
        long totalBlocked = adReqs + bgSyncs;

        // CONSERVATIVE byte estimate: assume each blocked request saves ~5KB on average
        long estimatedSavedBytes = totalBlocked * 5 * 1024; // 5KB per blocked request
        
        // Show SERVER data on dashboard (from Activity Log)
        // Use local session data as fallback if server data not available
        long displaySaved = serverTodaySaved > 0 ? serverTodaySaved : estimatedSavedBytes;
        long displayBlocked = serverTodayBlocked > 0 ? serverTodayBlocked : totalBlocked;
        
        tvUsed.setText(formatBytes(totalAppData)); // Keep showing local app data
        tvSaved.setText(formatBytes(displaySaved)); // Use SERVER data (Today from Activity Log)
        tvSavedPct.setText(String.format("%,d", displayBlocked) + " blocked"); // Use SERVER data
        
        // Summary label
        TextView tvSummaryLabel = findViewById(R.id.tvSummaryLabel);
        if (tvSummaryLabel != null) {
            String period = "today".equals(usageFilter) ? "Today" : "week".equals(usageFilter) ? "This Week" : "This Month";
            // Always show server data label
            tvSummaryLabel.setText("Server Data (Today)");
        }

        // Real Savings card — show user-friendly metrics (only when VPN running)
        TextView tvRealAds = findViewById(R.id.tvRealAdsBlocked);
        TextView tvRealData = findViewById(R.id.tvRealDataSaved);
        TextView tvRealMoney = findViewById(R.id.tvRealMoneySaved);
        if (tvRealAds != null) {
            // Estimate: 5KB per blocked request (conservative). totalBlocked already
            // reflects today's persisted value when the VPN is off.
            long savedBytes = totalBlocked * 5 * 1024;
            // Note: We DON'T cap saved <= used - blocking ads saves data that would have been downloaded
            double savedNaira = bytesToNaira(savedBytes);
            tvRealAds.setText(String.format("%,d", totalBlocked) + " blocked");
            tvRealData.setText(formatBytes(savedBytes) + " saved");
            tvRealMoney.setText(formatNaira(savedNaira) + " value");
        }

        // Upgrade banner for free users
        String plan = prefs().getString("subscription_plan", "none");
        if ("none".equals(plan)) {
            showUpgradeBanner(0, false);
        } else {
            hideUpgradeBanner();
        }

        // Bar chart — blocked vs total DNS queries
        long totalBar = Math.max(totalDns, 1);
        long blockedBar = Math.max(totalBlocked, 1);
        barUsed.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) totalBar));
        barSaved.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) blockedBar));
    }

    private boolean upgradeBannerShown = false;
    private boolean capPopupShown = false;

    private void showUpgradeBanner(double savedNaira, boolean capHit) {
        LinearLayout tabHome = findViewById(R.id.tabHome);
        if (tabHome == null) return;

        // Show cap popup once when limit is hit
        // No cap popup - removed













        // Always show upgrade banner for free users
        View existing = tabHome.findViewWithTag("upgrade_banner");
        if (existing != null) {
            // Update text only
            TextView tv = existing.findViewWithTag("upgrade_banner_text");
            if (tv != null) tv.setText("You saved " + formatNaira(savedNaira) + " free. Subscribe to save 5x more!");
            return;
        }

        // Build banner
        LinearLayout banner = new LinearLayout(this);
        banner.setTag("upgrade_banner");
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setBackgroundColor(0xFF722F37);
        banner.setPadding(dp(14), dp(10), dp(14), dp(10));
        banner.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(dp(16), dp(8), dp(16), 0);
        banner.setLayoutParams(bp);
        // Round corners via background
        banner.setBackground(getResources().getDrawable(R.drawable.card_red));

        TextView tv = new TextView(this);
        tv.setTag("upgrade_banner_text");
        tv.setText("You saved " + formatNaira(savedNaira) + " free. Subscribe to save 5x more!");
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        banner.addView(tv);

        TextView btn = new TextView(this);
        btn.setText("Upgrade");
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(11);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setBackground(getResources().getDrawable(R.drawable.btn_white_rounded));
        btn.setTextColor(0xFFC62828);
        btn.setPadding(dp(10), dp(6), dp(10), dp(6));
        btn.setOnClickListener(v -> switchTab(3));
        banner.addView(btn);

        // Insert after the Real Savings card (index 2 in tabHome)
        tabHome.addView(banner, Math.min(3, tabHome.getChildCount()));
    }

    private void hideUpgradeBanner() {
        LinearLayout tabHome = findViewById(R.id.tabHome);
        if (tabHome == null) return;
        View existing = tabHome.findViewWithTag("upgrade_banner");
        if (existing != null) tabHome.removeView(existing);
    }

    private void updateAppCards() {
        Map<String, long[]> usage = DataSaverService.appDataUsage;
        if (usage.isEmpty()) {
            tvAppUsageEmpty.setVisibility(View.VISIBLE);
            tvAppUsageEmpty.setText(hasUsagePermission() ? "No app data recorded yet" : "Grant usage permission to see app data");
            appUsageContainer.removeAllViews();
            return;
        }
        tvAppUsageEmpty.setVisibility(View.GONE);

        ArrayList<Map.Entry<String, long[]>> sorted = new ArrayList<>(usage.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, long[]>>() {
            public int compare(Map.Entry<String, long[]> a, Map.Entry<String, long[]> b) {
                return Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]);
            }
        });

        appUsageContainer.removeAllViews();
        int count = 0;
        int limit = showAllApps ? sorted.size() : 5;
        for (Map.Entry<String, long[]> entry : sorted) {
            long rx = entry.getValue()[0], tx = entry.getValue()[1], saved = entry.getValue()[2];
            long total = rx + tx;
            if (total < 1024) continue;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackground(getResources().getDrawable(R.drawable.card_bg));
            card.setElevation(dp(3));
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dp(10);
            card.setLayoutParams(cp);

            // App icon
            android.widget.ImageView icon = new android.widget.ImageView(this);
            Drawable appIcon = getAppIcon(entry.getKey());
            icon.setImageDrawable(appIcon != null ? appIcon : makeLetterIcon(entry.getKey()));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            iconLp.rightMargin = dp(10);
            icon.setLayoutParams(iconLp);
            card.addView(icon);

            // Text section
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvName = new TextView(this);
            tvName.setText(entry.getKey());
            tvName.setTextSize(14);
            tvName.setTextColor(0xFF333333);
            tvName.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvName);

            TextView tvUsage = new TextView(this);
            tvUsage.setText("Used " + formatBytes(total) + "  \u2022  Tap for details");
            tvUsage.setTextSize(12);
            tvUsage.setTextColor(0xFF888888);
            textCol.addView(tvUsage);

            card.addView(textCol);

            // Per-app blocked count from VPN tracking (real)
            long appBlocked = 0;
            if (DataSaverVpnService.perAppBlockedCount.containsKey(entry.getKey())) {
                appBlocked = DataSaverVpnService.perAppBlockedCount.get(entry.getKey()).get();
            }

            // Right column: usage + blocked
            LinearLayout savedCol = new LinearLayout(this);
            savedCol.setOrientation(LinearLayout.VERTICAL);
            savedCol.setGravity(Gravity.END);

            TextView tvUsedAmt = new TextView(this);
            tvUsedAmt.setText(formatBytes(total));
            tvUsedAmt.setTextSize(12);
            tvUsedAmt.setTextColor(0xFF888888);
            savedCol.addView(tvUsedAmt);

            if (appBlocked > 0) {
                TextView tvS = new TextView(this);
                tvS.setText(appBlocked + " blocked");
                tvS.setTextSize(10);
                tvS.setTextColor(0xFF43A047);
                savedCol.addView(tvS);
            }
            card.addView(savedCol);

            final String appNameFinal = entry.getKey();
            final long fRx = rx, fTx = tx, fSaved = saved;
            card.setOnClickListener(v -> showAppDetail(appNameFinal, fRx, fTx, fSaved));

            appUsageContainer.addView(card);
            if (++count >= limit) break;
        }
    }

    private long totalAppDataForBar() {
        long total = DataSaverService.totalBytesRx + DataSaverService.totalBytesTx;
        if (total <= 0) {
            for (Map.Entry<String, long[]> entry : DataSaverService.appDataUsage.entrySet()) {
                total += entry.getValue()[0] + entry.getValue()[1];
            }
        }
        return Math.max(total, 1);
    }

    private void showAppDetail(String appName, long rx, long tx, long saved) {
        String plan = prefs().getString("subscription_plan", "none");
        if ("none".equals(plan)) {
            showAppDialog(
                "Premium Feature",
                "Tap any app to see detailed usage breakdown.\n\nUpgrade to Premium to unlock full analytics: daily charts, savings per app, and more.",
                "View Plans",
                () -> switchTab(2),
                "Later",
                null
            );
            return;
        }

        long total = rx + tx;
        // Real per-app blocked count from VPN
        long appBlocked = 0;
        if (DataSaverVpnService.perAppBlockedCount.containsKey(appName)) {
            appBlocked = DataSaverVpnService.perAppBlockedCount.get(appName).get();
        }
        // Estimate saved: 5KB per blocked request
        long appSavedBytes = appBlocked * 5 * 1024;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        String[][] rows = {
            {"Downloaded", formatBytes(rx)},
            {"Uploaded", formatBytes(tx)},
            {"Total Used", formatBytes(total)},
            {"Ads Blocked", String.valueOf(appBlocked)},
            {"Data Saved", formatBytes(appSavedBytes)},
            {"Value Saved", formatNaira(bytesToNaira(appSavedBytes))},
        };
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(5), 0, dp(5));
            TextView l = new TextView(this); l.setText(r[0]); l.setTextSize(14); l.setTextColor(0xFF888888);
            l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView v = new TextView(this); v.setText(r[1]); v.setTextSize(14); v.setTextColor(0xFF333333); v.setTypeface(null, Typeface.BOLD);
            if (r[0].equals("Ads Blocked")) { v.setTextColor(0xFF43A047); v.setTextSize(16); }
            row.addView(l); row.addView(v);
            layout.addView(row);
        }

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFFEEEEEE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(8); dlp.bottomMargin = dp(8);
        div.setLayoutParams(dlp);
        layout.addView(div);

        // Bar chart title
        TextView chartTitle = new TextView(this);
        String period = "today".equals(usageFilter) ? "Today" : "week".equals(usageFilter) ? "This Week" : "This Month";
        chartTitle.setText(period + " - Data Used");
        chartTitle.setTextSize(13); chartTitle.setTextColor(0xFFC62828); chartTitle.setTypeface(null, Typeface.BOLD);
        chartTitle.setPadding(0, 0, 0, dp(8));
        layout.addView(chartTitle);

        // Bar chart container
        LinearLayout chartContainer = new LinearLayout(this);
        chartContainer.setOrientation(LinearLayout.HORIZONTAL);
        chartContainer.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams ccp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(120));
        chartContainer.setLayoutParams(ccp);
        chartContainer.setBackgroundColor(0xFFF5F5F5);
        chartContainer.setPadding(dp(4), dp(8), dp(4), dp(4));
        layout.addView(chartContainer);

        // Show today's data for this app
        long appTotal = rx + tx;
        handler.post(() -> {
            // Simple bar showing this app's usage relative to total
            LinearLayout bar = new LinearLayout(MainActivity.this);
            bar.setOrientation(LinearLayout.VERTICAL);
            bar.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            bar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            
            View barFill = new View(MainActivity.this);
            int barHeight = appTotal > 0 ? (int) (dp(100) * Math.min((double) appTotal / Math.max(totalAppDataForBar(), 1), 1.0)) : dp(4);
            barFill.setLayoutParams(new LinearLayout.LayoutParams(dp(40), Math.max(barHeight, dp(4))));
            barFill.setBackgroundColor(0xFF43A047);
            bar.addView(barFill);
            
            TextView label = new TextView(MainActivity.this);
            label.setText(formatBytes(appTotal));
            label.setTextSize(10);
            label.setTextColor(0xFF666666);
            label.setGravity(Gravity.CENTER);
            bar.addView(label);
            
            chartContainer.addView(bar);
        });

        new AlertDialog.Builder(this)
            .setTitle(appName + " - Analytics")
            .setView(layout)
            .setPositiveButton("OK", null)
            .show();
    }

    private void drawBarChart(LinearLayout container, long[] daily) {
        container.removeAllViews();
        long max = 1;
        for (long d : daily) if (d > max) max = d;

        String[] dayLabels = new String[7];
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String[] names = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 6; i >= 0; i--) {
            dayLabels[6 - i] = names[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        }
        // Reverse so index 0 = 6 days ago
        String[] reversed = new String[7];
        for (int i = 0; i < 7; i++) reversed[i] = dayLabels[6 - i];

        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

            // Bar
            View bar = new View(this);
            int barHeight = (int)(dp(80) * ((double) daily[i] / max));
            if (daily[i] > 0 && barHeight < dp(4)) barHeight = dp(4);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(20), barHeight);
            bp.bottomMargin = dp(2);
            bar.setLayoutParams(bp);
            bar.setBackgroundColor(i == 6 ? 0xFFC62828 : 0xFF90CAF9);
            col.addView(bar);

            // Amount label
            TextView amt = new TextView(this);
            amt.setText(daily[i] > 0 ? formatBytes(daily[i]) : "-");
            amt.setTextSize(8); amt.setTextColor(0xFF666666); amt.setGravity(Gravity.CENTER);
            col.addView(amt);

            // Day label
            TextView day = new TextView(this);
            day.setText(reversed[i]);
            day.setTextSize(9); day.setTextColor(0xFF999999); day.setGravity(Gravity.CENTER);
            col.addView(day);

            container.addView(col);
        }
    }

    // Icon cache: maps display name -> icon Drawable
    private final java.util.HashMap<String, Drawable> iconCache = new java.util.HashMap<>();
    // Package name cache: maps display name -> package name
    private final java.util.HashMap<String, String> nameToPackage = new java.util.HashMap<>();
    private boolean iconCacheBuilt = false;

    private void buildIconCache() {
        if (iconCacheBuilt) return;
        iconCacheBuilt = true;
        PackageManager pm = getPackageManager();
        // Step 1: Cache all priority apps by package name (most reliable)
        for (String[] app : DataSaverService.PRIORITY_APPS) {
            try {
                Drawable d = pm.getApplicationIcon(app[0]);
                iconCache.put(app[1], d);
                nameToPackage.put(app[1], app[0]);
            } catch (Exception e) {}
        }
        // Step 2: Cache ALL installed apps by their label
        try {
            for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                String label = pm.getApplicationLabel(ai).toString();
                if (!iconCache.containsKey(label)) {
                    try {
                        iconCache.put(label, pm.getApplicationIcon(ai.packageName));
                        nameToPackage.put(label, ai.packageName);
                    } catch (Exception e) {}
                }
            }
        } catch (Exception e) {}
    }

    private Drawable getAppIcon(String appName) {
        buildIconCache();
        if (iconCache.containsKey(appName)) return iconCache.get(appName);
        // Partial match fallback
        for (Map.Entry<String, Drawable> entry : iconCache.entrySet()) {
            if (entry.getKey().contains(appName) || appName.contains(entry.getKey())) {
                iconCache.put(appName, entry.getValue());
                return entry.getValue();
            }
        }
        return null;
    }

    private Drawable makeLetterIcon(String name) {
        int size = dp(36);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        android.graphics.Paint bg = new android.graphics.Paint();
        bg.setAntiAlias(true);
        // Pick color based on name hash
        int[] colors = {0xFFC62828, 0xFF43A047, 0xFFE65100, 0xFF6A1B9A, 0xFFC62828, 0xFF00838F};
        bg.setColor(colors[Math.abs(name.hashCode()) % colors.length]);
        c.drawCircle(size / 2f, size / 2f, size / 2f, bg);
        android.graphics.Paint txt = new android.graphics.Paint();
        txt.setAntiAlias(true);
        txt.setColor(0xFFFFFFFF);
        txt.setTextSize(size * 0.45f);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setTextAlign(android.graphics.Paint.Align.CENTER);
        String letter = name.length() > 0 ? name.substring(0, 1).toUpperCase() : "?";
        c.drawText(letter, size / 2f, size / 2f - (txt.ascent() + txt.descent()) / 2f, txt);
        return new BitmapDrawable(getResources(), bmp);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private void persistStats() {
        // Save totals
        long totalAppData = 0;
        for (long[] v : DataSaverService.appDataUsage.values()) totalAppData += v[0] + v[1];
        prefs().edit()
            .putLong("saved_totalRx", totalAppData > 0 ? totalAppData / 2 : DataSaverService.totalBytesRx)
            .putLong("saved_totalTx", totalAppData > 0 ? totalAppData / 2 : DataSaverService.totalBytesTx)
            .putLong("saved_totalSaved", DataSaverService.totalSavedBytes)
            .putFloat("saved_pct", (float) DataSaverService.savedPercent)
            .apply();
        // Save per-app data as JSON
        try {
            JSONObject appData = new JSONObject();
            for (Map.Entry<String, long[]> e : DataSaverService.appDataUsage.entrySet()) {
                JSONArray arr = new JSONArray();
                arr.put(e.getValue()[0]); arr.put(e.getValue()[1]); arr.put(e.getValue()[2]);
                appData.put(e.getKey(), arr);
            }
            prefs().edit().putString("saved_appData", appData.toString()).apply();
        } catch (Exception e) {}
    }

    private void restoreSavedStats() {
        SharedPreferences sp = prefs();
        long saved = sp.getLong("saved_totalSaved", 0);
        if (saved > 0 && DataSaverService.appDataUsage.isEmpty()) {
            DataSaverService.totalBytesRx = sp.getLong("saved_totalRx", 0);
            DataSaverService.totalBytesTx = sp.getLong("saved_totalTx", 0);
            DataSaverService.totalSavedBytes = saved;
            DataSaverService.savedPercent = sp.getFloat("saved_pct", 0);
            // Restore per-app data
            try {
                String json = sp.getString("saved_appData", "");
                if (!json.isEmpty()) {
                    JSONObject appData = new JSONObject(json);
                    java.util.Iterator<String> keys = appData.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        JSONArray arr = appData.getJSONArray(key);
                        DataSaverService.appDataUsage.put(key, new long[]{arr.getLong(0), arr.getLong(1), arr.getLong(2)});
                    }
                }
            } catch (Exception e) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistStats();
    }

    static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / (1024.0 * 1024.0));
        return String.format("%.2f GB", b / (1024.0 * 1024.0 * 1024.0));
    }
}