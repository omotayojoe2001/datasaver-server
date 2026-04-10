package com.datasaver

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 100
    private var statsJob: Job? = null

    private lateinit var btnToggle: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvTestResult: TextView
    private lateinit var etServerHost: EditText
    private lateinit var etServerPort: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        tvTestResult = findViewById(R.id.tvTestResult)
        etServerHost = findViewById(R.id.etServerHost)
        etServerPort = findViewById(R.id.etServerPort)

        // Default server address — change for real device testing
        etServerHost.setText("10.0.2.2") // emulator localhost
        etServerPort.setText("3000")

        btnToggle.setOnClickListener { toggleVpn() }
        btnTest.setOnClickListener { runCompressionTest() }

        updateUI()
    }

    private fun toggleVpn() {
        if (DataSaverVpnService.isRunning.get()) {
            stopVpn()
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        val host = etServerHost.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 3000
        CompressionProxy.configure(host, port)

        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            launchVpnService()
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, DataSaverVpnService::class.java).apply {
            action = DataSaverVpnService.ACTION_STOP
        }
        startService(intent)
        statsJob?.cancel()
        updateUI()
    }

    private fun launchVpnService() {
        val host = etServerHost.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 3000

        val intent = Intent(this, DataSaverVpnService::class.java).apply {
            action = DataSaverVpnService.ACTION_START
            putExtra("server_host", host)
            putExtra("server_port", port)
        }
        startForegroundService(intent)

        // Start polling stats
        statsJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateUI()
                delay(2000)
            }
        }
    }

    @Deprecated("Use registerForActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            launchVpnService()
        }
    }

    /** Quick test: fetch an image through the proxy and show savings */
    private fun runCompressionTest() {
        val host = etServerHost.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 3000
        CompressionProxy.configure(host, port)

        tvTestResult.text = "Testing compression..."

        CoroutineScope(Dispatchers.Main).launch {
            // Test with a real image
            val testUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a7/Camponotus_flavomarginatus_ant.jpg/800px-Camponotus_flavomarginatus_ant.jpg"
            val result = CompressionProxy.fetch(testUrl)

            if (result.success) {
                val savedPct = if (result.originalSize > 0)
                    ((1.0 - result.compressedSize.toDouble() / result.originalSize) * 100).toInt()
                else 0

                tvTestResult.text = buildString {
                    append("✅ Compression working!\n")
                    append("Original: ${formatBytes(result.originalSize)}\n")
                    append("Compressed: ${formatBytes(result.compressedSize)}\n")
                    append("Saved: ${formatBytes(result.savedBytes)} ($savedPct%)")
                }
            } else {
                tvTestResult.text = "❌ Failed: ${result.error}\n\nMake sure server is running!"
            }

            // Also fetch server-side stats
            val stats = CompressionProxy.getStats()
            if (stats != null) {
                tvStats.text = buildString {
                    append("Server total: ${formatBytes(stats.originalBytes)} → ${formatBytes(stats.compressedBytes)}\n")
                    append("Total saved: ${formatBytes(stats.savedBytes)} (${stats.savedPercent}%)")
                }
            }
        }
    }

    private fun updateUI() {
        val isOn = DataSaverVpnService.isRunning.get()
        btnToggle.text = if (isOn) "⏹ STOP DATA SAVER" else "▶ START DATA SAVER"
        tvStatus.text = if (isOn) "🟢 Active — Saving your data" else "🔴 Off"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
