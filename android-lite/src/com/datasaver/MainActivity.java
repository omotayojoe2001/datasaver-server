package com.datasaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private Button btnConnect;
    private TextView tvStatus, tvUsed, tvSaved, tvSavedPct, tvAppUsageEmpty;
    private View barUsed, barSaved;
    private LinearLayout appUsageContainer;
    private TextView navHome, navAirtime, navData, navTransactions, navProfile;
    private ScrollView tabHome, tabAirtime, tabData, tabTransactions, tabProfile;
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
    private ArrayList<JSONObject> fetchedPlans = new ArrayList<>();
    private TextView tvWalletBalance;

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
        navProfile = findViewById(R.id.navProfile);

        tabHome = findViewById(R.id.contentArea);
        tabAirtime = findViewById(R.id.tabAirtime);
        tabData = findViewById(R.id.tabData);
        tabTransactions = findViewById(R.id.tabTransactions);
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
        navProfile.setOnClickListener(v -> switchTab(4));

        btnMTN.setOnClickListener(v -> selectNetwork("MTN"));
        btnAirtel.setOnClickListener(v -> selectNetwork("AIRTEL"));
        btnGlo.setOnClickListener(v -> selectNetwork("GLO"));
        btn9mobile.setOnClickListener(v -> selectNetwork("9MOBILE"));

        toggleAirtime.setOnClickListener(v -> setMode(false));
        toggleData.setOnClickListener(v -> setMode(true));

        setupAirtimeButtons();
        btnBuy.setOnClickListener(v -> onBuy());
        initHistoryTab();
        initProfileTab();
        initLogin();
        tvWalletBalance = findViewById(R.id.tvWalletBalance);
        fetchWalletBalance();
        updateUI();
    }

    // ==================== LOGIN ====================

    private ScrollView loginOverlay;

    private void initLogin() {
        loginOverlay = findViewById(R.id.loginOverlay);
        String phone = prefs().getString("phone", "");
        if (phone.isEmpty()) {
            loginOverlay.setVisibility(View.VISIBLE);
        }

        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvLoginStatus = findViewById(R.id.tvLoginStatus);
        btnLogin.setOnClickListener(v -> {
            String name = ((EditText) findViewById(R.id.loginName)).getText().toString().trim();
            String ph = ((EditText) findViewById(R.id.loginPhone)).getText().toString().trim();
            String pin = ((EditText) findViewById(R.id.loginPin)).getText().toString().trim();

            if (name.isEmpty()) { tvLoginStatus.setText("Enter your name"); tvLoginStatus.setVisibility(View.VISIBLE); return; }
            if (ph.length() < 10) { tvLoginStatus.setText("Enter a valid phone number"); tvLoginStatus.setVisibility(View.VISIBLE); return; }
            if (pin.length() < 4) { tvLoginStatus.setText("Enter a 4-digit PIN"); tvLoginStatus.setVisibility(View.VISIBLE); return; }

            btnLogin.setEnabled(false);
            btnLogin.setText("Please wait...");
            tvLoginStatus.setText("Connecting...");
            tvLoginStatus.setTextColor(0xFF999999);
            tvLoginStatus.setVisibility(View.VISIBLE);

            // Save locally first so app works immediately
            prefs().edit()
                .putString("name", name)
                .putString("phone", ph)
                .putString("password", pin)
                .apply();

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
                    body.put("phone", ph);
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

                    if (code < 400 && res.has("user_id")) {
                        prefs().edit().putString("user_id", res.optString("user_id")).apply();
                    }
                    handler.post(() -> {
                        loginOverlay.setVisibility(View.GONE);
                        refreshProfileUI();
                        fetchWalletBalance();
                        Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    // Server failed but local save worked — let them in
                    handler.post(() -> {
                        loginOverlay.setVisibility(View.GONE);
                        refreshProfileUI();
                        Toast.makeText(this, "Welcome, " + name + "! (offline mode)", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
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
                handler.post(() -> tvWalletBalance.setText(String.format("\u20a6%.0f", bal)));
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
        btnMTN.setAlpha(0.4f);
        btnAirtel.setAlpha(0.4f);
        btnGlo.setAlpha(0.4f);
        btn9mobile.setAlpha(0.4f);
        if ("MTN".equals(network)) btnMTN.setAlpha(1.0f);
        else if ("AIRTEL".equals(network)) btnAirtel.setAlpha(1.0f);
        else if ("GLO".equals(network)) btnGlo.setAlpha(1.0f);
        else if ("9MOBILE".equals(network)) btn9mobile.setAlpha(1.0f);

        if (isDataMode) fetchAndLoadPlans();
    }

    private void setMode(boolean dataMode) {
        isDataMode = dataMode;
        selectedDataPlanIndex = -1;
        if (dataMode) {
            toggleData.setBackgroundColor(0xFF1565C0);
            toggleData.setTextColor(0xFFFFFFFF);
            toggleAirtime.setBackgroundColor(0x00000000);
            toggleAirtime.setTextColor(0xFF666666);
            airtimeSection.setVisibility(View.GONE);
            dataSection.setVisibility(View.VISIBLE);
            btnBuy.setText("BUY DATA");
            if (selectedNetwork != null) fetchAndLoadPlans();
        } else {
            toggleAirtime.setBackgroundColor(0xFF1565C0);
            toggleAirtime.setTextColor(0xFFFFFFFF);
            toggleData.setBackgroundColor(0x00000000);
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

    private void showDataPlans(ArrayList<JSONObject> plans) {
        dataPlansContainer.removeAllViews();
        fetchedPlans = plans;
        if (plans.isEmpty()) {
            tvSelectNetwork.setText("No plans available for " + selectedNetwork);
            tvSelectNetwork.setVisibility(View.VISIBLE);
            return;
        }
        tvSelectNetwork.setVisibility(View.GONE);

        // Group plans by category based on validity
        ArrayList<JSONObject> daily = new ArrayList<>();
        ArrayList<JSONObject> weekly = new ArrayList<>();
        ArrayList<JSONObject> monthly = new ArrayList<>();
        ArrayList<JSONObject> special = new ArrayList<>();

        for (int i = 0; i < plans.size(); i++) {
            try {
                JSONObject p = plans.get(i);
                String v = p.getString("validity").toLowerCase();
                if (v.contains("1 day") || v.contains("2 day") || v.contains("3 day")) daily.add(p);
                else if (v.contains("7 day") || v.contains("7day")) weekly.add(p);
                else if (v.contains("30") || v.contains("month")) monthly.add(p);
                else special.add(p);
            } catch (Exception e) {}
        }

        if (!daily.isEmpty()) addPlanSection("Daily Plans", daily);
        if (!weekly.isEmpty()) addPlanSection("Weekly Plans", weekly);
        if (!monthly.isEmpty()) addPlanSection("Monthly Plans", monthly);
        if (!special.isEmpty()) addPlanSection("Special Plans", special);
    }

    private void addPlanSection(String title, ArrayList<JSONObject> plans) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(14);
        header.setTextColor(0xFF1565C0);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, dp(12), 0, dp(8));
        dataPlansContainer.addView(header);

        for (JSONObject plan : plans) {
            try {
                final int idx = fetchedPlans.indexOf(plan);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackgroundColor(0xFFFFFFFF);
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setElevation(2);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.bottomMargin = dp(6);
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
            child.setBackgroundColor(child.getTag() != null && (int) child.getTag() == idx ? 0xFFE3F2FD : 0xFFFFFFFF);
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
                else body.put("amount", value);

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
                        showReceiptDialog(false, res.optString("error", "Unknown error"),
                            phone, network, isData ? "Data" : "Airtime", "");
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

    private LinearLayout usageHistoryContainer, txnHistoryContainer;
    private TextView tvNoTxn, tvHistorySaved, tvHistorySavedPct;
    private boolean showAllUsage = false;

    private void initHistoryTab() {
        usageHistoryContainer = findViewById(R.id.usageHistoryContainer);
        txnHistoryContainer = findViewById(R.id.txnHistoryContainer);
        tvNoTxn = findViewById(R.id.tvNoTxn);
        tvHistorySaved = findViewById(R.id.tvHistorySaved);
        tvHistorySavedPct = findViewById(R.id.tvHistorySavedPct);

        findViewById(R.id.btnSeeAllUsage).setOnClickListener(v -> {
            showAllUsage = !showAllUsage;
            ((TextView) v).setText(showAllUsage ? "Show Less" : "See All >");
            refreshUsageHistory();
        });
        findViewById(R.id.btnSeeAllTxn).setOnClickListener(v -> fetchTransactions());
        findViewById(R.id.btnRefreshHistory).setOnClickListener(v -> {
            refreshUsageHistory();
            fetchTransactions();
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshUsageHistory() {
        usageHistoryContainer.removeAllViews();
        Map<String, long[]> usage = DataSaverService.appDataUsage;
        if (usage.isEmpty()) {
            addUsageCard("No data yet", "Start monitoring to see usage", "");
            return;
        }

        ArrayList<Map.Entry<String, long[]>> sorted = new ArrayList<>(usage.entrySet());
        Collections.sort(sorted, (a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));

        int limit = showAllUsage ? sorted.size() : Math.min(3, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, long[]> e = sorted.get(i);
            long total = e.getValue()[0] + e.getValue()[1];
            long saved = e.getValue()[2];
            if (total < 1024) continue;
            String pct = saved > 0 ? String.format("Saved: %s (%.1f%%)", formatBytes(saved), saved * 100.0 / total) : "";
            addUsageCard(e.getKey(), formatBytes(total) + " used", pct);
        }

        // Update savings summary
        tvHistorySaved.setText(formatBytes(DataSaverService.totalSavedBytes));
        tvHistorySavedPct.setText(String.format("%.1f%% efficiency", DataSaverService.savedPercent));
    }

    private void addUsageCard(String title, String amount, String savedText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setElevation(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(0xFF333333);
        t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView a = new TextView(this);
        a.setText(amount);
        a.setTextSize(14);
        a.setTextColor(0xFF1565C0);
        a.setTypeface(null, Typeface.BOLD);
        row.addView(t);
        row.addView(a);
        card.addView(row);

        if (!savedText.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(savedText);
            s.setTextSize(12);
            s.setTextColor(0xFF43A047);
            s.setPadding(0, dp(4), 0, 0);
            card.addView(s);
        }
        usageHistoryContainer.addView(card);
    }

    private void fetchTransactions() {
        String phone = getSharedPreferences("datasaver", MODE_PRIVATE).getString("phone", "");
        if (phone.isEmpty()) {
            tvNoTxn.setText("Set your phone in Profile to see transactions");
            tvNoTxn.setVisibility(View.VISIBLE);
            return;
        }
        tvNoTxn.setText("Loading...");
        tvNoTxn.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/transactions/" + phone);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONArray arr = new JSONArray(sb.toString());
                handler.post(() -> showTransactions(arr));
            } catch (Exception e) {
                handler.post(() -> {
                    tvNoTxn.setText("Failed to load transactions");
                    tvNoTxn.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void showTransactions(JSONArray arr) {
        txnHistoryContainer.removeAllViews();
        if (arr.length() == 0) {
            tvNoTxn.setText("No transactions yet");
            tvNoTxn.setVisibility(View.VISIBLE);
            return;
        }
        tvNoTxn.setVisibility(View.GONE);
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject txn = arr.getJSONObject(i);
                String type = txn.getString("type");
                String network = txn.getString("network");
                String amt = txn.getString("amount");
                String status = txn.getString("status");
                String date = txn.getString("created_at");
                String planSize = txn.optString("plan_size", "");

                String title = type.equals("data") ? network + " " + planSize + " Data" : network + " \u20a6" + amt + " Airtime";
                String dateShort = date.length() > 10 ? date.substring(0, 10) : date;

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(0xFFFFFFFF);
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                card.setElevation(2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(8);
                card.setLayoutParams(lp);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView t = new TextView(this);
                t.setText(title);
                t.setTextSize(14);
                t.setTextColor(0xFF333333);
                t.setTypeface(null, Typeface.BOLD);
                t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                TextView a = new TextView(this);
                a.setText("-\u20a6" + amt);
                a.setTextSize(14);
                a.setTextColor(status.equals("success") ? 0xFFD32F2F : 0xFF999999);
                a.setTypeface(null, Typeface.BOLD);
                row.addView(t);
                row.addView(a);
                card.addView(row);

                TextView d = new TextView(this);
                d.setText(dateShort + " \u2022 " + status.toUpperCase());
                d.setTextSize(11);
                d.setTextColor(status.equals("success") ? 0xFF43A047 : status.equals("failed") ? 0xFFD32F2F : 0xFF999999);
                d.setPadding(0, dp(2), 0, 0);
                card.addView(d);

                txnHistoryContainer.addView(card);
            } catch (Exception e) {}
        }
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

        profilePhoto.setOnClickListener(v -> Toast.makeText(this, "Photo upload coming in v2", Toast.LENGTH_SHORT).show());
        findViewById(R.id.rowEditProfile).setOnClickListener(v -> showEditProfileDialog());
        findViewById(R.id.rowChangePassword).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.rowManageSub).setOnClickListener(v -> switchTab(2));
        findViewById(R.id.rowPushNotif).setOnClickListener(v -> togglePref("push_notif", tvPushNotif));
        findViewById(R.id.rowDailyAlerts).setOnClickListener(v -> togglePref("daily_alerts", tvDailyAlerts));
        findViewById(R.id.rowImageQuality).setOnClickListener(v -> cycleImageQuality());
        findViewById(R.id.rowWifiCompress).setOnClickListener(v -> togglePref("wifi_compress", tvWifiCompress));
        findViewById(R.id.rowPrivacy).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://datasaver-server.onrender.com/privacy"))));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
    }

    private void refreshProfileUI() {
        SharedPreferences sp = prefs();
        String name = sp.getString("name", "DataSaver User");
        String phone = sp.getString("phone", "");
        tvProfileName.setText(name);
        tvProfilePhone.setText(phone.isEmpty() ? "Basic Plan" : phone + " - Basic Plan");
        profilePhoto.setText(name.isEmpty() ? "U" : name.substring(0, 1).toUpperCase());

        tvPushNotif.setText(sp.getBoolean("push_notif", true) ? "ON" : "OFF");
        tvPushNotif.setTextColor(sp.getBoolean("push_notif", true) ? 0xFF43A047 : 0xFFD32F2F);
        tvDailyAlerts.setText(sp.getBoolean("daily_alerts", true) ? "ON" : "OFF");
        tvDailyAlerts.setTextColor(sp.getBoolean("daily_alerts", true) ? 0xFF43A047 : 0xFFD32F2F);

        String quality = sp.getString("image_quality", "Medium");
        tvImageQuality.setText(quality + " >");

        tvWifiCompress.setText(sp.getBoolean("wifi_compress", false) ? "ON" : "OFF");
        tvWifiCompress.setTextColor(sp.getBoolean("wifi_compress", false) ? 0xFF43A047 : 0xFFD32F2F);

        tvServerAddr.setText(SERVER_URL.replace("https://", ""));
        try { tvAppVersion.setText("v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName); } catch (Exception e) { tvAppVersion.setText("v1.0.0"); }
    }

    private void togglePref(String key, TextView tv) {
        SharedPreferences sp = prefs();
        boolean current = sp.getBoolean(key, key.equals("wifi_compress") ? false : true);
        sp.edit().putBoolean(key, !current).apply();
        tv.setText(!current ? "ON" : "OFF");
        tv.setTextColor(!current ? 0xFF43A047 : 0xFFD32F2F);
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

    private void showEditProfileDialog() {
        SharedPreferences sp = prefs();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(4));

        EditText etName = new EditText(this);
        etName.setHint("Name");
        etName.setText(sp.getString("name", ""));
        layout.addView(etName);

        EditText etPh = new EditText(this);
        etPh.setHint("Phone");
        etPh.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        etPh.setText(sp.getString("phone", ""));
        layout.addView(etPh);

        EditText etEmail = new EditText(this);
        etEmail.setHint("Email");
        etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etEmail.setText(sp.getString("email", ""));
        layout.addView(etEmail);

        new AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                sp.edit()
                    .putString("name", etName.getText().toString().trim())
                    .putString("phone", etPh.getText().toString().trim())
                    .putString("email", etEmail.getText().toString().trim())
                    .apply();
                refreshProfileUI();
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
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
                prefs().edit().clear().apply();
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
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ==================== TABS / HOME ====================

    private void switchTab(int tab) {
        tabHome.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        tabAirtime.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        tabData.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        tabTransactions.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);
        tabProfile.setVisibility(tab == 4 ? View.VISIBLE : View.GONE);

        TextView[] navItems = {navHome, navAirtime, navData, navTransactions, navProfile};
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
        if (tab == 3) {
            refreshUsageHistory();
            fetchTransactions();
        }
        if (tab == 4) refreshProfileUI();
    }

    private boolean hasUsagePermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void toggle() {
        if (DataSaverService.isRunning) {
            Intent i = new Intent(this, DataSaverService.class);
            i.setAction(DataSaverService.ACTION_STOP);
            startService(i);
            polling = false;
            handler.postDelayed(() -> updateUI(), 500);
        } else {
            if (!hasUsagePermission()) {
                tvAppUsageEmpty.setText("Permission required.\nFind 'DataSaver' and enable it.\nThen come back and tap the power button.");
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                return;
            }
            Intent i = new Intent(this, DataSaverService.class);
            i.setAction(DataSaverService.ACTION_START);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            polling = true;
            handler.postDelayed(() -> pollUI(), 1000);
            handler.postDelayed(() -> updateUI(), 500);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
        fetchWalletBalance();
        if (DataSaverService.isRunning && !polling) {
            polling = true;
            handler.postDelayed(() -> pollUI(), 1000);
        }
    }

    private void updateUI() {
        if (DataSaverService.isRunning) {
            tvStatus.setText("Connected \u2014 Saving Data");
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

    private void updateSummary() {
        long rx = DataSaverService.totalBytesRx;
        long tx = DataSaverService.totalBytesTx;
        long total = rx + tx;
        long saved = DataSaverService.totalSavedBytes;
        double pct = DataSaverService.savedPercent;

        tvUsed.setText(formatBytes(total));
        tvSaved.setText(formatBytes(saved));
        tvSavedPct.setText(String.format("%.1f%%", pct));

        long used = Math.max(total - saved, 1);
        long savedBar = Math.max(saved, 1);
        barUsed.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) used));
        barSaved.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) savedBar));
    }

    private void updateAppCards() {
        Map<String, long[]> usage = DataSaverService.appDataUsage;
        if (usage.isEmpty()) {
            tvAppUsageEmpty.setVisibility(View.VISIBLE);
            tvAppUsageEmpty.setText("Use your apps to see data here...");
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
        for (Map.Entry<String, long[]> entry : sorted) {
            long rx = entry.getValue()[0], tx = entry.getValue()[1], saved = entry.getValue()[2];
            long total = rx + tx;
            if (total < 1024) continue;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFFFFFFFF);
            card.setElevation(2);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dp(8);
            card.setLayoutParams(cp);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView tvName = new TextView(this);
            tvName.setText(entry.getKey());
            tvName.setTextSize(14);
            tvName.setTextColor(0xFF333333);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView tvAmt = new TextView(this);
            tvAmt.setText(formatBytes(total));
            tvAmt.setTextSize(14);
            tvAmt.setTextColor(0xFF1565C0);
            tvAmt.setTypeface(null, Typeface.BOLD);
            topRow.addView(tvName);
            topRow.addView(tvAmt);
            card.addView(topRow);

            TextView tvDetail = new TextView(this);
            tvDetail.setText(formatBytes(rx) + " down  /  " + formatBytes(tx) + " up");
            tvDetail.setTextSize(11);
            tvDetail.setTextColor(0xFF888888);
            card.addView(tvDetail);

            if (saved > 0) {
                TextView tvS = new TextView(this);
                tvS.setText("Saved: " + formatBytes(saved) + " (" + String.format("%.1f%%", saved * 100.0 / total) + ")");
                tvS.setTextSize(11);
                tvS.setTextColor(0xFF1B5E20);
                tvS.setTypeface(null, Typeface.BOLD);
                card.addView(tvS);
            }

            LinearLayout miniBar = new LinearLayout(this);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
            bp.topMargin = dp(6);
            miniBar.setLayoutParams(bp);
            View u = new View(this);
            u.setBackgroundColor(0xFF1565C0);
            u.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float)(total - saved)));
            View s = new View(this);
            s.setBackgroundColor(0xFF43A047);
            s.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) Math.max(saved, 1)));
            miniBar.addView(u);
            miniBar.addView(s);
            card.addView(miniBar);

            appUsageContainer.addView(card);
            if (++count >= 10) break;
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format("%.1f MB", b / (1024.0 * 1024.0));
    }
}
