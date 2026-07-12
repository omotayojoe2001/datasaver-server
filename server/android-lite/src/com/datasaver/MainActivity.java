package com.datasaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
    private View barUsed, barSaved;
    private LinearLayout appUsageContainer;
    private TextView navHome, navAirtime, navData, navTransactions, navEarn, navProfile;
    private ScrollView tabHome, tabAirtime, tabData, tabTransactions, tabEarn, tabProfile;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

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
        // Interpolate: find where bytes falls in the price table
        for (int i = 0; i < DATA_PRICES.length; i++) {
            if (bytes <= DATA_PRICES[i][0]) {
                double pricePerByte = (double) DATA_PRICES[i][1] / DATA_PRICES[i][0];
                return bytes * pricePerByte;
            }
        }
        // Beyond 160GB, use last tier rate
        double rate = (double) DATA_PRICES[DATA_PRICES.length - 1][1] / DATA_PRICES[DATA_PRICES.length - 1][0];
        return bytes * rate;
    }

    private static String formatNaira(double amount) {
        if (amount < 1) return "\u20a60";
        if (amount >= 1000) return String.format("\u20a6%,.0f", amount);
        return String.format("\u20a6%.0f", amount);
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
        // Set network logos (scaled to fit)
        setScaledLogo(btnMTN, R.drawable.logo_mtn, "MTN");
        setScaledLogo(btnAirtel, R.drawable.logo_airtel, "Airtel");
        setScaledLogo(btnGlo, R.drawable.logo_glo, "Glo");
        setScaledLogo(btn9mobile, R.drawable.logo_9mobile, "9mobile");

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
        fetchWalletBalance();
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
        // Restore VPN savings from SharedPreferences as fallback
        long savedAdBytes = prefs().getLong("real_ad_bytes", 0);
        long savedBgBytes = prefs().getLong("real_bg_bytes", 0);
        long savedAdReqs = prefs().getLong("real_ad_requests", 0);
        long savedBgSyncs = prefs().getLong("real_bg_syncs", 0);
        if (savedAdBytes > DataSaverVpnService.blockedAdBytes.get()) DataSaverVpnService.blockedAdBytes.set(savedAdBytes);
        if (savedBgBytes > DataSaverVpnService.blockedBgBytes.get()) DataSaverVpnService.blockedBgBytes.set(savedBgBytes);
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
        // Alternate: even = white bg/dark text, odd = dark bg/white text
        LinearLayout tipContainer = (LinearLayout) tvTip.getParent();
        if (tipContainer != null) {
            if (currentTipIndex % 2 == 0) {
                tipContainer.setBackground(getResources().getDrawable(R.drawable.card_bg));
                tvTip.setTextColor(0xFF444444);
            } else {
                tipContainer.setBackgroundColor(0xFF1A237E);
                tvTip.setTextColor(0xFFE8EAF6);
            }
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
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                long serverTotal = res.optLong("total_saved", 0);
                long serverBlocked = res.optLong("total_blocked", 0);
                // Use server values if they're higher than local
                if (serverTotal > DataSaverVpnService.blockedAdBytes.get() + DataSaverVpnService.blockedBgBytes.get()) {
                    // Server has higher total — restore it. Keep existing ratio or default to ad bytes
                    long currentAd = DataSaverVpnService.blockedAdBytes.get();
                    long currentBg = DataSaverVpnService.blockedBgBytes.get();
                    if (currentAd + currentBg > 0) {
                        // Scale up proportionally
                        double ratio = (double) serverTotal / (currentAd + currentBg);
                        DataSaverVpnService.blockedAdBytes.set((long)(currentAd * ratio));
                        DataSaverVpnService.blockedBgBytes.set(serverTotal - (long)(currentAd * ratio));
                    } else {
                        DataSaverVpnService.blockedAdBytes.set(serverTotal);
                    }
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
                    }
                    // Also save to SharedPreferences
                    prefs().edit()
                        .putLong("real_ad_bytes", DataSaverVpnService.blockedAdBytes.get())
                        .putLong("real_bg_bytes", DataSaverVpnService.blockedBgBytes.get())
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
                        fetchWalletBalance(); // This also syncs name/email from server
                        refreshProfileUI();
                        loadAppUsageBackground();
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
        if (phone.isEmpty()) {
            loginOverlay.setVisibility(View.VISIBLE);
            // New users see signup first
            switchAuthTab(true, (TextView) findViewById(R.id.tabLogin), (TextView) findViewById(R.id.tabSignup));
        }

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
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setDoOutput(true);
                    JSONObject body = new JSONObject();
                    if (identity.contains("@")) body.put("email", identity);
                    else body.put("phone", identity);
                    body.put("pin", pin);
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
                            prefs().edit()
                                .putString("user_id", res.optString("user_id"))
                                .putString("name", rName)
                                .putString("phone", rPhone)
                                .putString("email", rEmail)
                                .putString("subscription_plan", res.optString("subscription_plan", "basic"))
                                .apply();
                            loginOverlay.setVisibility(View.GONE);
                            refreshProfileUI();
                            fetchWalletBalance();
                            // If name/email missing from server, push from signup data
                            if ((rName.isEmpty() || "null".equals(rName)) && !identity.contains("@")) {
                                // Logged in with phone, name might be missing
                            }
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
            if (name.isEmpty()) { showAuthError(tvLoginStatus, "Enter your name"); return; }
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
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setDoOutput(true);
                    JSONObject body = new JSONObject();
                    body.put("email", email);
                    body.put("pin", pin);
                    body.put("name", name);
                    if (!ph.isEmpty()) body.put("phone", ph);
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
                            prefs().edit()
                                .putString("user_id", res.optString("user_id"))
                                .putString("name", name)
                                .putString("phone", ph)
                                .putString("email", email)
                                .putString("password", pin)
                                .apply();
                            loginOverlay.setVisibility(View.GONE);
                            refreshProfileUI();
                            fetchWalletBalance();
                            Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
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
        tv.setTextColor(0xFFD32F2F);
        tv.setVisibility(View.VISIBLE);
    }

    private void fetchWalletBalance() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { tvWalletBalance.setText("--"); return; }
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/user/" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                double bal = res.optDouble("wallet_balance", 0);
                // Re-sync name and email from server
                String serverName = res.isNull("name") ? "" : res.optString("name", "");
                String serverEmail = res.isNull("email") ? "" : res.optString("email", "");
                if (!serverName.isEmpty() && !"null".equals(serverName)) {
                    prefs().edit().putString("name", serverName).apply();
                }
                if (!serverEmail.isEmpty() && !"null".equals(serverEmail)) {
                    prefs().edit().putString("email", serverEmail).apply();
                }
                // Restore profile photo from server
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
                handler.post(() -> tvWalletBalance.setText("\u20a60"));
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
                tv.setBackgroundColor(0xFF1565C0);
                tv.setTextColor(0xFFFFFFFF);
            } else {
                tv.setBackgroundColor(0xFFFFFFFF);
                tv.setTextColor(0xFF1565C0);
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
        dataPlansContainer.removeAllViews();
        selectedDataPlanIndex = -1;
        if (selectedNetwork == null) {
            tvSelectNetwork.setVisibility(View.VISIBLE);
            tvSelectNetwork.setText("Please select a network above");
            return;
        }
        tvSelectNetwork.setVisibility(View.VISIBLE);
        tvSelectNetwork.setText("Loading plans...");

        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/plans?network=" + selectedNetwork);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
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
                handler.post(() -> {
                    tvSelectNetwork.setText("Failed to load plans. Tap to retry.");
                    tvSelectNetwork.setVisibility(View.VISIBLE);
                    tvSelectNetwork.setOnClickListener(v -> fetchAndLoadPlans());
                });
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
            header.setTextColor(0xFF1565C0);
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
                tvSize.setTextColor(0xFF1565C0);
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
        btnBuy.setEnabled(false);
        btnBuy.setText("Processing...");

        if (isDataMode) {
            if (selectedDataPlanIndex < 0) {
                Toast.makeText(this, "Select a data plan", Toast.LENGTH_SHORT).show();
                btnBuy.setEnabled(true);
                btnBuy.setText("BUY DATA");
                return;
            }
            try {
                int dataId = fetchedPlans.get(selectedDataPlanIndex).getInt("data_id");
                callApi("/api/buy-data", phone, selectedNetwork, String.valueOf(dataId), true);
            } catch (Exception e) {
                btnBuy.setEnabled(true);
                btnBuy.setText("BUY DATA");
            }
        } else {
            String amt = etAirtimeAmount.getText().toString().trim();
            if (amt.isEmpty()) {
                Toast.makeText(this, "Select or enter an amount", Toast.LENGTH_SHORT).show();
                btnBuy.setEnabled(true);
                btnBuy.setText("BUY AIRTIME");
                return;
            }
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
        icon.setTextColor(success ? 0xFF43A047 : 0xFFD32F2F);
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
        tvMsg.setTextColor(0xFFD32F2F);
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
            btn.setTextColor(0xFF1565C0);
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
                if (amt.isEmpty() || Integer.parseInt(amt) < 100) { Toast.makeText(this, "Minimum amount is \u20a6100", Toast.LENGTH_SHORT).show(); return; }
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
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
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
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
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

    private void loadSavingsHistory() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { tvNoSavings.setText("Login to see savings"); tvNoSavings.setVisibility(View.VISIBLE); return; }
 tvNoSavings.setText("Loading..."); tvNoSavings.setVisibility(View.VISIBLE);
 savingsHistoryContainer.removeAllViews();
 new Thread(() -> {
 try {
 URL url = new URL(SERVER_URL + "/api/savings/" + phone);
 HttpURLConnection conn = (HttpURLConnection) url.openConnection();
 conn.setConnectTimeout(15000); conn.setReadTimeout(15000);
 BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
 StringBuilder sb = new StringBuilder(); String line;
 while ((line = reader.readLine()) != null) sb.append(line);
 reader.close();
 JSONObject res = new JSONObject(sb.toString());
 long totalSaved = res.optLong("total_saved", 0);
 long totalBlocked = res.optLong("total_blocked", 0);
 JSONObject todayObj = res.optJSONObject("today");
 JSONObject weekObj = res.optJSONObject("week");
 JSONObject monthObj = res.optJSONObject("month");
 JSONArray history = res.optJSONArray("history");
 handler.post(() -> {
 TextView tvToday = findViewById(R.id.tvSavingsToday);
 TextView tvWeek = findViewById(R.id.tvSavingsWeek);
 TextView tvMonth = findViewById(R.id.tvSavingsMonth);
 TextView tvAll = findViewById(R.id.tvSavingsAllTime);
 TextView tvAllB = findViewById(R.id.tvSavingsAllTimeBlocked);
 if (tvToday != null && todayObj != null) tvToday.setText(formatBytes(todayObj.optLong("saved", 0)));
 if (tvWeek != null && weekObj != null) tvWeek.setText(formatBytes(weekObj.optLong("saved", 0)));
 if (tvMonth != null && monthObj != null) tvMonth.setText(formatBytes(monthObj.optLong("saved", 0)));
 if (tvAll != null) tvAll.setText(formatBytes(totalSaved));
 if (tvAllB != null) tvAllB.setText(totalBlocked + " requests blocked");
 savingsHistoryContainer.removeAllViews();
 if (history != null && history.length() > 0) {
 tvNoSavings.setVisibility(View.GONE);
 for (int i = 0; i < history.length() && i < 14; i++) {
 try {
 JSONObject day = history.getJSONObject(i);
 String date = day.optString("date", "");
                                long saved = day.optLong("saved_bytes", 0);
 long blocked = day.optLong("blocked_requests", 0);
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
 TextView tvS = new TextView(MainActivity.this); tvS.setText(formatBytes(saved)); tvS.setTextSize(14); tvS.setTextColor(0xFF43A047); tvS.setTypeface(null, Typeface.BOLD); rc.addView(tvS);
 TextView tvB = new TextView(MainActivity.this); tvB.setText(blocked + " blocked"); tvB.setTextSize(11); tvB.setTextColor(0xFF888888); rc.addView(tvB);
 row.addView(rc); savingsHistoryContainer.addView(row);
 } catch (Exception e) {}
 }
 } else { tvNoSavings.setText("No savings data yet"); tvNoSavings.setVisibility(View.VISIBLE); }
 });
 } catch (Exception e) {
                // Fallback to local data
                handler.post(() -> {
                    long localSaved = DataSaverVpnService.blockedAdBytes.get() + DataSaverVpnService.blockedBgBytes.get();
                    long localBlocked = DataSaverVpnService.blockedAdRequests.get() + DataSaverVpnService.blockedBgSyncs.get();
                    TextView tvToday = findViewById(R.id.tvSavingsToday);
                    TextView tvWeek = findViewById(R.id.tvSavingsWeek);
                    TextView tvMonth = findViewById(R.id.tvSavingsMonth);
                    TextView tvAll = findViewById(R.id.tvSavingsAllTime);
                    TextView tvAllB = findViewById(R.id.tvSavingsAllTimeBlocked);
                    if (tvToday != null) tvToday.setText(formatBytes(localSaved));
                    if (tvWeek != null) tvWeek.setText(formatBytes(localSaved));
                    if (tvMonth != null) tvMonth.setText(formatBytes(localSaved));
                    if (tvAll != null) tvAll.setText(formatBytes(localSaved));
                    if (tvAllB != null) tvAllB.setText(localBlocked + " requests blocked");
                    if (localSaved > 0) tvNoSavings.setVisibility(View.GONE);
                    else { tvNoSavings.setText("Turn on DataSaver to start saving"); tvNoSavings.setVisibility(View.VISIBLE); }
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
                c1.setConnectTimeout(10000); c1.setReadTimeout(10000);
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
                c2.setConnectTimeout(10000); c2.setReadTimeout(10000);
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
                amtColor = type.equals("credit") ? 0xFF43A047 : 0xFFD32F2F;
            } else {
                String type = txn.optString("type", "");
                String network = txn.optString("network", "");
                String amt = txn.optString("amount", "0");
                String planSize = txn.optString("plan_size", "");
                status = txn.optString("status", "pending");
                title = type.equals("data") ? network + " " + planSize + " Data" : network + " \u20a6" + amt + " Airtime";
                amtText = "-\u20a6" + amt;
                amtColor = 0xFFD32F2F;
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
            int statusColor = "success".equals(status) ? 0xFF43A047 : "failed".equals(status) ? 0xFFD32F2F : 0xFFFF8F00;
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

        tvHistorySaved.setText(formatBytes(DataSaverVpnService.blockedAdBytes.get() + DataSaverVpnService.blockedBgBytes.get()));
        long realSaved = DataSaverVpnService.blockedAdBytes.get() + DataSaverVpnService.blockedBgBytes.get();
        double pct = totalUsed > 0 ? Math.min((realSaved * 100.0 / totalUsed), 99.9) : 0;
        tvHistorySavedPct.setText(String.format("You saved %.1f%% of your data", pct));
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
    private TextView tvImageQuality, tvWifiCompress, tvServerAddr, tvAppVersion, profilePhoto;

    private SharedPreferences prefs() { return getSharedPreferences("datasaver", MODE_PRIVATE); }

    private void initProfileTab() {
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfilePhone = findViewById(R.id.tvProfilePhone);
        tvPushNotif = findViewById(R.id.tvPushNotif);
        tvDailyAlerts = findViewById(R.id.tvDailyAlerts);
        tvImageQuality = findViewById(R.id.tvImageQuality);
        tvWifiCompress = findViewById(R.id.tvWifiCompress);
        tvServerAddr = findViewById(R.id.tvServerAddr);
        tvAppVersion = findViewById(R.id.tvAppVersion);
        profilePhoto = findViewById(R.id.profilePhoto);

        // Load saved prefs
        refreshProfileUI();

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
        findViewById(R.id.rowImageQuality).setOnClickListener(v -> cycleImageQuality());
        findViewById(R.id.rowWifiCompress).setOnClickListener(v -> togglePref("wifi_compress", tvWifiCompress));
        findViewById(R.id.rowPrivacy).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://datasaver-server.onrender.com/privacy"))));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        // VPN Bypass List (Split Tunneling)
        View rowBypass = findViewById(R.id.rowBypassApps);
        if (rowBypass != null) rowBypass.setOnClickListener(v -> showBypassAppsDialog());

        // Background Guard toggle
        View rowBgGuard = findViewById(R.id.rowBgGuard);
        if (rowBgGuard != null) rowBgGuard.setOnClickListener(v -> togglePref("bg_block_enabled", findViewById(R.id.tvBgGuard)));
    }

    private void refreshProfileUI() {
        SharedPreferences sp = prefs();
        String name = sp.getString("name", "");
        if (name.isEmpty() || "null".equals(name)) name = "DataSaver User";
        String phone = sp.getString("phone", "");
        tvProfileName.setText(name);
        tvProfilePhone.setText(phone.isEmpty() ? "Basic Plan" : phone + " - " + (sp.getString("subscription_plan", "basic").substring(0, 1).toUpperCase() + sp.getString("subscription_plan", "basic").substring(1)) + " Plan");
        profilePhoto.setText(name.isEmpty() ? "U" : name.substring(0, 1).toUpperCase());

        tvPushNotif.setText(sp.getBoolean("push_notif", true) ? "ON" : "OFF");
        tvPushNotif.setTextColor(sp.getBoolean("push_notif", true) ? 0xFF43A047 : 0xFFD32F2F);
        tvDailyAlerts.setText(sp.getBoolean("daily_alerts", true) ? "ON" : "OFF");
        tvDailyAlerts.setTextColor(sp.getBoolean("daily_alerts", true) ? 0xFF43A047 : 0xFFD32F2F);

        String quality = sp.getString("image_quality", "Medium");
        tvImageQuality.setText(quality + " >");

        tvWifiCompress.setText(sp.getBoolean("wifi_compress", false) ? "ON" : "OFF");
        tvWifiCompress.setTextColor(sp.getBoolean("wifi_compress", false) ? 0xFF43A047 : 0xFFD32F2F);

        // Background Guard status
        TextView tvBgGuard = findViewById(R.id.tvBgGuard);
        if (tvBgGuard != null) {
            boolean bgOn = sp.getBoolean("bg_block_enabled", true);
            tvBgGuard.setText(bgOn ? "ON" : "OFF");
            tvBgGuard.setTextColor(bgOn ? 0xFF43A047 : 0xFFD32F2F);
        }

        tvServerAddr.setText("*****");
        String subPlan = sp.getString("subscription_plan", "basic");
        TextView tvManageSub = findViewById(R.id.tvManageSubLabel);
        if (tvManageSub != null) tvManageSub.setText(subPlan.substring(0, 1).toUpperCase() + subPlan.substring(1) + " >");
        try {
            String vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvAppVersion.setText(vn != null ? "v" + vn : "v1.0.0");
        } catch (Exception e) { tvAppVersion.setText("v1.0.0"); }

        // Update profile wallet
        fetchProfileWallet();
    }

    private void togglePref(String key, TextView tv) {
        SharedPreferences sp = prefs();
        boolean current = sp.getBoolean(key, key.equals("wifi_compress") ? false : true);
        sp.edit().putBoolean(key, !current).apply();
        tv.setText(!current ? "ON" : "OFF");
        tv.setTextColor(!current ? 0xFF43A047 : 0xFFD32F2F);
    }

    private void fetchProfileWallet() {
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) return;
        TextView tvPW = findViewById(R.id.tvProfileWallet);
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/user/" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
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

    private void cycleImageQuality() {
        String[] opts = {"Low", "Medium", "High"};
        String current = prefs().getString("image_quality", "Medium");
        int idx = 0;
        for (int i = 0; i < opts.length; i++) if (opts[i].equals(current)) idx = i;
        String next = opts[(idx + 1) % opts.length];
        prefs().edit().putString("image_quality", next).apply();
        tvImageQuality.setText(next + " >");
        Toast.makeText(this, "Image quality: " + next, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Saved! Restart DataSaver to apply changes.", Toast.LENGTH_LONG).show();
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
                // Use the ORIGINAL phone to find the record on server
                String originalPhone = safeGet("phone");
                sp.edit()
                    .putString("name", newName)
                    .putString("phone", newPhone)
                    .putString("email", newEmail)
                    .apply();
                refreshProfileUI();
                Toast.makeText(this, "Saving to server...", Toast.LENGTH_SHORT).show();
                // Push to server using ORIGINAL phone as lookup key
                new Thread(() -> {
                    try {
                        URL url = new URL(SERVER_URL + "/api/user/update");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);
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
        new AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out", (d, w) -> {
                // Keep ALL saved stats including real VPN savings
                long sRx = prefs().getLong("saved_totalRx", 0);
                long sTx = prefs().getLong("saved_totalTx", 0);
                long sSaved = prefs().getLong("saved_totalSaved", 0);
                float sPct = prefs().getFloat("saved_pct", 0);
                long rAd = prefs().getLong("real_ad_bytes", 0);
                long rBg = prefs().getLong("real_bg_bytes", 0);
                long rAdR = prefs().getLong("real_ad_requests", 0);
                long rBgS = prefs().getLong("real_bg_syncs", 0);
                long instTime = prefs().getLong("install_time", 0);
                prefs().edit().clear().apply();
                prefs().edit()
                    .putLong("saved_totalRx", sRx).putLong("saved_totalTx", sTx)
                    .putLong("saved_totalSaved", sSaved).putFloat("saved_pct", sPct)
                    .putLong("real_ad_bytes", rAd).putLong("real_bg_bytes", rBg)
                    .putLong("real_ad_requests", rAdR).putLong("real_bg_syncs", rBgS)
                    .putLong("install_time", instTime)
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
                // Reset to login tab
                switchAuthTab(false, (TextView) findViewById(R.id.tabLogin), (TextView) findViewById(R.id.tabSignup));
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
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
        int size = dp(72);
        // Center-crop: scale to fill, then crop center
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startServices();
            } else {
                Toast.makeText(this, "VPN permission required for data saving", Toast.LENGTH_SHORT).show();
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
                InputStream is = getContentResolver().openInputStream(data.getData());
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                if (bmp != null) {
                    File f = new File(getFilesDir(), "profile.jpg");
                    FileOutputStream fos = new FileOutputStream(f);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                    fos.close();
                    prefs().edit().putString("photo_path", f.getAbsolutePath()).apply();
                    setCircularPhoto(bmp);
                    Toast.makeText(this, "Photo updated", Toast.LENGTH_SHORT).show();
                    // Upload to server
                    new Thread(() -> {
                        try {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                            String base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
                            URL url = new URL(SERVER_URL + "/api/user/update");
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setConnectTimeout(15000);
                            conn.setReadTimeout(15000);
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

    private String currentPlan = "basic";

    private void loadSubscriptionPlans() {
        LinearLayout container = findViewById(R.id.subsPlansContainer);
        container.removeAllViews();
        TextView header = findViewById(R.id.tvCurrentPlanHeader);

        // Fetch current plan from server
        String phone = prefs().getString("phone", "");
        if (!phone.isEmpty()) {
            new Thread(() -> {
                try {
                    URL url = new URL(SERVER_URL + "/api/subscription/" + phone);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject res = new JSONObject(sb.toString());
                    currentPlan = res.optString("plan", "basic");
                    String expires = res.optString("expires_at", "");
                    handler.post(() -> {
                        String label = currentPlan.substring(0, 1).toUpperCase() + currentPlan.substring(1);
                        if (!"basic".equals(currentPlan) && !expires.isEmpty()) {
                            String expDate = expires.length() > 10 ? expires.substring(0, 10) : expires;
                            header.setText("Current: " + label + " (expires " + expDate + ")");
                        } else {
                            header.setText("Current Plan: " + label + ("basic".equals(currentPlan) ? " (Free)" : ""));
                        }
                        buildPlanCards(container);
                    });
                } catch (Exception e) {
                    handler.post(() -> buildPlanCards(container));
                }
            }).start();
        } else {
            buildPlanCards(container);
        }
    }

    private void buildPlanCards(LinearLayout container) {
        container.removeAllViews();
        String[][] plans = {
            {"basic", "Basic", "FREE", "",
             "Block common ads (saves data)",
             "See which apps use your data",
             "Data usage tracking"},
            {"premium", "Premium", "\u20a6500", "/week",
             "Everything in Basic",
             "Block all ads and trackers",
             "Stop background apps from wasting data",
             "Detailed app-by-app analytics",
             "Priority faster connection"},
            {"professional", "Professional", "\u20a61,500", "/month",
             "Everything in Premium",
             "Maximum data protection",
             "Block all background data usage",
             "Full savings history and charts",
             "Use on up to 3 devices"},
            {"enterprise", "Enterprise", "\u20a65,000", "/month",
             "Everything in Professional",
             "Strongest data saving mode",
             "Use on up to 5 devices",
             "24/7 priority support"}
        };

        for (String[] p : plans) {
            String planId = p[0];
            String planName = p[1];
            String price = p[2];
            String period = p[3];
            boolean isCurrent = planId.equals(currentPlan);
            boolean isEnterprise = "enterprise".equals(planId);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(getResources().getDrawable(isEnterprise ? R.drawable.btn_blue_rounded : R.drawable.card_bg));
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setElevation(isEnterprise ? dp(4) : dp(3));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dp(12);
            card.setLayoutParams(cp);

            // Title row
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView tvName = new TextView(this);
            tvName.setText(planName);
            tvName.setTextSize(18);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(isEnterprise ? 0xFFFFFFFF : 0xFF333333);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleRow.addView(tvName);
            TextView tvPrice = new TextView(this);
            tvPrice.setText(price + period);
            tvPrice.setTextSize(18);
            tvPrice.setTypeface(null, Typeface.BOLD);
            tvPrice.setTextColor(isEnterprise ? 0xFFFFFFFF : ("basic".equals(planId) ? 0xFF43A047 : 0xFF1565C0));
            titleRow.addView(tvPrice);
            card.addView(titleRow);

            // Divider
            View div = new View(this);
            div.setBackgroundColor(isEnterprise ? 0xFF2979FF : 0xFFEEEEEE);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            dlp.topMargin = dp(8);
            dlp.bottomMargin = dp(8);
            div.setLayoutParams(dlp);
            card.addView(div);

            // Features
            for (int i = 4; i < p.length; i++) {
                TextView feat = new TextView(this);
                feat.setText("\u2713  " + p[i]);
                feat.setTextSize(13);
                feat.setTextColor(isEnterprise ? 0xFFE3F2FD : 0xFF555555);
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

            if (isCurrent) {
                btn.setText("CURRENT PLAN");
                btn.setTextColor(0xFFFFFFFF);
                btn.setBackgroundColor(0xFF43A047);
                btn.setEnabled(false);
            } else if ("basic".equals(planId)) {
                btn.setText("FREE PLAN");
                btn.setTextColor(0xFFFFFFFF);
                btn.setBackgroundColor(0xFF999999);
                btn.setEnabled(false);
            } else {
                btn.setText("SUBSCRIBE - " + price + period);
                btn.setTextColor(isEnterprise ? 0xFF1565C0 : 0xFFFFFFFF);
                btn.setBackgroundColor(isEnterprise ? 0xFFFFFFFF : 0xFF1565C0);
                btn.setOnClickListener(v -> subscribeToPlan(planId, planName, price + period));
            }
            card.addView(btn);
            container.addView(card);
        }
    }

    private void subscribeToPlan(String planId, String planName, String priceLabel) {
        new AlertDialog.Builder(this)
            .setTitle("Subscribe to " + planName)
            .setMessage("This will charge " + priceLabel + " from your wallet balance.\n\nContinue?")
            .setPositiveButton("Subscribe", (d, w) -> {
                String phone = prefs().getString("phone", "");
                if (phone.isEmpty()) { Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show(); return; }
                Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        URL url = new URL(SERVER_URL + "/api/subscribe");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);
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
            })
            .setNegativeButton("Cancel", null)
            .show();
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
            navItems[i].setTextColor(i == tab ? 0xFF1565C0 : 0xFF999999);
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
        }
        if (tab == 4) { loadEarnTab(); }
        if (tab == 5) { refreshProfileUI(); loadProfilePhoto(); }
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
        findViewById(R.id.btnClaimRewards).setOnClickListener(v -> claimRewards());
    }

    private void loadEarnTab() {
        if (earnTasksContainer == null) initEarnTab();
        earnTasksContainer.removeAllViews();
        tvNoTasks.setText("Loading tasks...");
        tvNoTasks.setVisibility(View.VISIBLE);
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) { tvNoTasks.setText("Login to see tasks"); return; }

        // Fetch tasks and user rewards
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/tasks?phone=" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(15000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject res = new JSONObject(sb.toString());
                JSONArray tasks = res.optJSONArray("tasks");
                int pending = res.optInt("pending_reward", 0);
                int claimable = res.optInt("claimable_reward", 0);
                handler.post(() -> {
                    tvEarnPending.setText("\u20a6" + pending);
                    tvEarnClaimable.setText("\u20a6" + claimable);
                    showTasks(tasks);
                });
            } catch (Exception e) {
                handler.post(() -> { tvNoTasks.setText("Could not load tasks. Tap Refresh."); tvNoTasks.setVisibility(View.VISIBLE); });
            }
        }).start();
    }

    private void showTasks(JSONArray tasks) {
        earnTasksContainer.removeAllViews();
        if (tasks == null || tasks.length() == 0) {
            tvNoTasks.setText("No tasks available right now. Check back later!");
            tvNoTasks.setVisibility(View.VISIBLE);
            return;
        }
        tvNoTasks.setVisibility(View.GONE);

        for (int i = 0; i < tasks.length(); i++) {
            try {
                JSONObject task = tasks.getJSONObject(i);
                String id = task.optString("id");
                String title = task.optString("title", "Task");
                String type = task.optString("type", "general"); // video, follow, advert, youtube
                int reward = task.optInt("reward", 0);
                String rewardType = task.optString("reward_type", "airtime"); // airtime or data
                String status = task.optString("user_status", "available"); // available, pending, approved

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackground(getResources().getDrawable(R.drawable.card_bg));
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                card.setElevation(dp(3));
                card.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.bottomMargin = dp(10);
                card.setLayoutParams(cp);

                // Type icon
                TextView icon = new TextView(this);
                String emoji = "\ud83d\udcf1";
                if ("video".equals(type) || "youtube".equals(type)) emoji = "\ud83c\udfac";
                else if ("follow".equals(type)) emoji = "\ud83d\udc65";
                else if ("advert".equals(type)) emoji = "\ud83d\udcfa";
                icon.setText(emoji);
                icon.setTextSize(24);
                icon.setPadding(0, 0, dp(12), 0);
                card.addView(icon);

                // Text
                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                TextView tvTitle = new TextView(this);
                tvTitle.setText(title);
                tvTitle.setTextSize(14); tvTitle.setTextColor(0xFF333333); tvTitle.setTypeface(null, Typeface.BOLD);
                textCol.addView(tvTitle);
                TextView tvReward = new TextView(this);
                tvReward.setText("Earn \u20a6" + reward + " " + rewardType);
                tvReward.setTextSize(12); tvReward.setTextColor(0xFF43A047);
                textCol.addView(tvReward);
                card.addView(textCol);

                // Status/action
                TextView tvStatus = new TextView(this);
                tvStatus.setTextSize(11); tvStatus.setTypeface(null, Typeface.BOLD);
                tvStatus.setPadding(dp(10), dp(6), dp(10), dp(6));
                if ("pending".equals(status)) {
                    tvStatus.setText("PENDING"); tvStatus.setTextColor(0xFFFF8F00); tvStatus.setBackgroundColor(0xFFFFF3E0);
                } else if ("approved".equals(status)) {
                    tvStatus.setText("CLAIM"); tvStatus.setTextColor(0xFFFFFFFF); tvStatus.setBackgroundColor(0xFF43A047);
                } else if ("claimed".equals(status)) {
                    tvStatus.setText("DONE \u2713"); tvStatus.setTextColor(0xFF43A047); tvStatus.setBackgroundColor(0xFFE8F5E9);
                } else {
                    tvStatus.setText("START"); tvStatus.setTextColor(0xFFFFFFFF); tvStatus.setBackgroundColor(0xFF1565C0);
                    final JSONObject fTask = task;
                    card.setOnClickListener(v -> showTaskDetail(fTask));
                }
                card.addView(tvStatus);
                earnTasksContainer.addView(card);
            } catch (Exception e) {}
        }
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
                // Open the link externally
                if (!link.isEmpty()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); } catch (Exception e) {}
                }
                // After a short delay, show proof upload dialog
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
                conn.setConnectTimeout(30000); conn.setReadTimeout(30000);
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

        new AlertDialog.Builder(this)
            .setTitle("Claim Rewards")
            .setMessage("Your approved rewards will be added to your wallet balance.")
            .setPositiveButton("Claim Now", (d, w) -> {
                showAlertDialog("Processing", "Claiming your rewards...");
                new Thread(() -> {
                    try {
                        URL url = new URL(SERVER_URL + "/api/tasks/claim");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(15000); conn.setReadTimeout(15000);
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
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private static final int VPN_REQUEST_CODE = 1002;

    private void toggle() {
        if (DataSaverService.isRunning) {
            Intent i = new Intent(this, DataSaverService.class);
            i.setAction(DataSaverService.ACTION_STOP);
            startService(i);
            // Stop VPN
            Intent vi = new Intent(this, DataSaverVpnService.class);
            vi.setAction("STOP");
            startService(vi);
            polling = false;
            handler.postDelayed(() -> updateUI(), 500);
        } else {
            if (!hasUsagePermission()) {
                tvAppUsageEmpty.setText("Permission required.\nFind 'DataSaver' and enable it.\nThen come back and tap the power button.");
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                return;
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
        // Request battery optimization exemption (critical for Xiaomi/Oppo/Tecno/Infinix)
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
        Intent i = new Intent(this, DataSaverService.class);
        i.setAction(DataSaverService.ACTION_START);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        // Start VPN simulation
        startService(new Intent(this, DataSaverVpnService.class));
        polling = true;
        handler.postDelayed(() -> pollUI(), 1000);
        handler.postDelayed(() -> updateUI(), 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restore savings counters in case app was backgrounded
        long savedAdBytes = prefs().getLong("real_ad_bytes", 0);
        long savedBgBytes = prefs().getLong("real_bg_bytes", 0);
        long savedAdReqs = prefs().getLong("real_ad_requests", 0);
        long savedBgSyncs = prefs().getLong("real_bg_syncs", 0);
        if (savedAdBytes > DataSaverVpnService.blockedAdBytes.get()) DataSaverVpnService.blockedAdBytes.set(savedAdBytes);
        if (savedBgBytes > DataSaverVpnService.blockedBgBytes.get()) DataSaverVpnService.blockedBgBytes.set(savedBgBytes);
        if (savedAdReqs > DataSaverVpnService.blockedAdRequests.get()) DataSaverVpnService.blockedAdRequests.set(savedAdReqs);
        if (savedBgSyncs > DataSaverVpnService.blockedBgSyncs.get()) DataSaverVpnService.blockedBgSyncs.set(savedBgSyncs);
        updateUI();
        updateSummary();
        fetchWalletBalance();
        verifyPendingPayment();
        if (DataSaverService.isRunning && !polling) {
            polling = true;
            handler.postDelayed(() -> pollUI(), 1000);
        }
    }

    private void updateUI() {
        if (DataSaverService.isRunning) {
            long ads = DataSaverVpnService.blockedAdRequests.get();
            long bgSyncs = DataSaverVpnService.blockedBgSyncs.get();
            long totalSavedKB = (DataSaverVpnService.blockedAdBytes.get() + DataSaverVpnService.blockedBgBytes.get()) / 1024;
            String statusMsg = DataSaverVpnService.isVpnRunning ? "VPN Connected" : "Connected";
            if (ads > 0 || bgSyncs > 0) {
                statusMsg += " \u2014 Saved " + formatBytes(DataSaverVpnService.blockedAdBytes.get() + DataSaverVpnService.blockedBgBytes.get());
            } else {
                statusMsg += " \u2014 Protecting your data";
            }
            tvStatus.setText(statusMsg);
            btnConnect.setText("ON");
            btnConnect.setBackgroundResource(R.drawable.circle_on);
        } else {
            tvStatus.setText("Tap to connect");
            btnConnect.setText("OFF");
            btnConnect.setBackgroundResource(R.drawable.circle_off);
        }
    }

    private void pollUI() {
        if (!polling) return;
        updateUI();
        updateSummary();
        updateAppCards();
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
        long totalAppData = 0;
        for (long[] v : DataSaverService.appDataUsage.values()) totalAppData += v[0] + v[1];
        if (totalAppData == 0) totalAppData = prefs().getLong("saved_totalRx", 0) + prefs().getLong("saved_totalTx", 0);

        // Real savings: always use the HIGHER of SharedPreferences or live VPN counters
        long spAdBytes = prefs().getLong("real_ad_bytes", 0);
        long spBgBytes = prefs().getLong("real_bg_bytes", 0);
        long spAdReqs = prefs().getLong("real_ad_requests", 0);
        long spBgSyncs = prefs().getLong("real_bg_syncs", 0);
        long liveAdBytes = DataSaverVpnService.blockedAdBytes.get();
        long liveBgBytes = DataSaverVpnService.blockedBgBytes.get();
        long liveAdReqs = DataSaverVpnService.blockedAdRequests.get();
        long liveBgSyncs = DataSaverVpnService.blockedBgSyncs.get();
        long realSaved = Math.max(spAdBytes + spBgBytes, liveAdBytes + liveBgBytes);
        long realBlocked = Math.max(spAdReqs + spBgSyncs, liveAdReqs + liveBgSyncs);
        // Never show saved > used
        if (realSaved > totalAppData && totalAppData > 0) realSaved = (long)(totalAppData * 0.10);
        double pct = totalAppData > 0 ? Math.min((realSaved * 100.0 / totalAppData), 99.9) : 0;

        tvUsed.setText(formatBytes(totalAppData));
        tvSaved.setText(formatBytes(realSaved));
        tvSavedPct.setText(String.format("%.1f%%", pct));

        // Update summary label with context
        TextView tvSummaryLabel = findViewById(R.id.tvSummaryLabel);
        if (tvSummaryLabel != null) {
            String period = "today".equals(usageFilter) ? "Today" : "week".equals(usageFilter) ? "This Week" : "This Month";
            long installTime = prefs().getLong("install_time", 0);
            long daysSinceInstall = installTime > 0 ? (System.currentTimeMillis() - installTime) / (24L * 60 * 60 * 1000) : 0;
            if (daysSinceInstall < 1) {
                tvSummaryLabel.setText("Since You Installed DataSaver");
            } else {
                tvSummaryLabel.setText("Data Summary (" + period + ")");
            }
        }

        // Show naira value of real saved data
        double nairaValue = bytesToNaira(realSaved);
        if (tvSavedValue != null) tvSavedValue.setText("Worth " + formatNaira(nairaValue));

        // Update real savings card
        TextView tvRealAds = findViewById(R.id.tvRealAdsBlocked);
        TextView tvRealData = findViewById(R.id.tvRealDataSaved);
        TextView tvRealMoney = findViewById(R.id.tvRealMoneySaved);
        if (tvRealAds != null) {
            tvRealAds.setText(String.valueOf(realBlocked));
            tvRealData.setText(formatBytes(realSaved));
            tvRealMoney.setText(formatNaira(bytesToNaira(realSaved)));
        }

        long usedBar = Math.max(totalAppData, 1);
        long savedBar = Math.max(realSaved, 1);
        barUsed.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) usedBar));
        barSaved.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) savedBar));
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
            tvUsage.setText("Used " + formatBytes(total));
            tvUsage.setTextSize(12);
            tvUsage.setTextColor(0xFF888888);
            textCol.addView(tvUsage);

            card.addView(textCol);

            // Saved badge — show MB only, no percentage (real savings only from ad blocking)
            long realAppSaved = DataSaverVpnService.blockedAdBytes.get();
            if (realAppSaved > 0 && count == 0) {
                // Only show total real savings on the top app
                LinearLayout savedCol = new LinearLayout(this);
                savedCol.setOrientation(LinearLayout.VERTICAL);
                savedCol.setGravity(Gravity.END);
                TextView tvS = new TextView(this);
                tvS.setText(formatBytes(total));
                tvS.setTextSize(12);
                tvS.setTextColor(0xFF888888);
                savedCol.addView(tvS);
                card.addView(savedCol);
            } else {
                // Show usage amount
                TextView tvAmt = new TextView(this);
                tvAmt.setText(formatBytes(total));
                tvAmt.setTextSize(12);
                tvAmt.setTextColor(0xFF888888);
                card.addView(tvAmt);
            }

            final String appNameFinal = entry.getKey();
            final long fRx = rx, fTx = tx, fSaved = saved;
            card.setOnClickListener(v -> showAppDetail(appNameFinal, fRx, fTx, fSaved));

            appUsageContainer.addView(card);
            if (++count >= limit) break;
        }
    }

    private void showAppDetail(String appName, long rx, long tx, long saved) {
        String plan = prefs().getString("subscription_plan", "basic");
        if ("basic".equals(plan)) {
            new AlertDialog.Builder(this)
                .setTitle("Premium Feature")
                .setMessage("Detailed app analytics is available for Premium subscribers and above.\n\nUpgrade your plan to see download/upload breakdown, daily usage charts, and savings value for each app.")
                .setPositiveButton("View Plans", (d, w) -> switchTab(2))
                .setNegativeButton("Later", null)
                .show();
            return;
        }

        long total = rx + tx;
        double pct = total > 0 ? (saved * 100.0 / total) : 0;
        double nairaValue = bytesToNaira(saved);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        String[][] rows = {
            {"Downloaded", formatBytes(rx)},
            {"Uploaded", formatBytes(tx)},
            {"Total Used", formatBytes(total)},
            {"Data Saved", formatBytes(saved)},
            {"Value Saved", formatNaira(nairaValue)},
        };
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(5), 0, dp(5));
            TextView l = new TextView(this); l.setText(r[0]); l.setTextSize(14); l.setTextColor(0xFF888888);
            l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView v = new TextView(this); v.setText(r[1]); v.setTextSize(14); v.setTextColor(0xFF333333); v.setTypeface(null, Typeface.BOLD);
            if (r[0].equals("Value Saved")) { v.setTextColor(0xFF43A047); v.setTextSize(16); }
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
        chartTitle.setText("Last 7 Days");
        chartTitle.setTextSize(13); chartTitle.setTextColor(0xFF1565C0); chartTitle.setTypeface(null, Typeface.BOLD);
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

        // Load daily data in background, then draw bars
        new Thread(() -> {
            long[] daily = DataSaverService.getDailyUsage(this, appName);
            handler.post(() -> drawBarChart(chartContainer, daily));
        }).start();

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
            bar.setBackgroundColor(i == 6 ? 0xFF1565C0 : 0xFF90CAF9);
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
        int[] colors = {0xFF1565C0, 0xFF43A047, 0xFFE65100, 0xFF6A1B9A, 0xFFD32F2F, 0xFF00838F};
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
        return String.format("%.1f MB", b / (1024.0 * 1024.0));
    }
}
