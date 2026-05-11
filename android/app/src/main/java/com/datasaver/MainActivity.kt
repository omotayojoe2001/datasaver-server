package com.datasaver

import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 100
    private var statsJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupToggle()
        requestUsagePermission()
        loadApps()
    }

    private fun setupToggle() {
        val btnToggle = findViewById<Button>(R.id.btnToggle)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvSaved = findViewById<TextView>(R.id.tvSaved)

        fun updateToggle() {
            val isOn = DataSaverVpnService.isRunning.get()
            btnToggle.text = if (isOn) "⏹ STOP DATA SAVER" else "▶ START DATA SAVER"
            tvStatus.text = if (isOn) "🟢 Active — Saving your data" else "🔴 Off"
            val saved = DataSaverVpnService.totalOriginalBytes.get() - DataSaverVpnService.totalCompressedBytes.get()
            if (saved > 0) tvSaved.text = "Saved: ${formatBytes(saved)}"
        }

        btnToggle.setOnClickListener {
            if (DataSaverVpnService.isRunning.get()) {
                val intent = Intent(this, DataSaverVpnService::class.java).apply {
                    action = DataSaverVpnService.ACTION_STOP
                }
                startService(intent)
                statsJob?.cancel()
            } else {
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
                } else {
                    launchVpn()
                }
            }
            updateToggle()
        }

        // Poll stats
        statsJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateToggle()
                delay(2000)
            }
        }
    }

    private fun launchVpn() {
        val intent = Intent(this, DataSaverVpnService::class.java).apply {
            action = DataSaverVpnService.ACTION_START
        }
        startForegroundService(intent)
    }

    @Deprecated("Use registerForActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) launchVpn()
    }

    private fun requestUsagePermission() {
        if (!hasUsagePermission()) {
            Toast.makeText(this, "Grant usage access to see app data usage", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadApps() {
        val recycler = findViewById<RecyclerView>(R.id.rvApps)
        val tvLoading = findViewById<TextView>(R.id.tvLoading)
        recycler.layoutManager = LinearLayoutManager(this)

        CoroutineScope(Dispatchers.Main).launch {
            tvLoading.visibility = View.VISIBLE
            val apps = withContext(Dispatchers.IO) {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (30L * 24 * 60 * 60 * 1000) // last 30 days
                DataUsageHelper.getAppUsages(this@MainActivity, startTime, endTime)
            }
            tvLoading.visibility = View.GONE

            if (apps.isEmpty()) {
                tvLoading.text = "No usage data. Grant usage access permission."
                tvLoading.visibility = View.VISIBLE
            }

            recycler.adapter = AppListAdapter(apps) { app ->
                val intent = Intent(this@MainActivity, AppDetailActivity::class.java).apply {
                    putExtra("package_name", app.packageName)
                    putExtra("app_name", app.appName)
                }
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasUsagePermission()) loadApps()
    }

    override fun onDestroy() {
        statsJob?.cancel()
        super.onDestroy()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}

/** RecyclerView adapter for the app list */
class AppListAdapter(
    private val apps: List<AppDataUsage>,
    private val onClick: (AppDataUsage) -> Unit
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val usage: TextView = view.findViewById(R.id.tvAppUsage)
        val detail: TextView = view.findViewById(R.id.tvAppDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.usage.text = formatBytes(app.totalBytes)
        holder.detail.text = "↓ ${formatBytes(app.rxBytes)}  ↑ ${formatBytes(app.txBytes)}"
        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount() = apps.size

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
