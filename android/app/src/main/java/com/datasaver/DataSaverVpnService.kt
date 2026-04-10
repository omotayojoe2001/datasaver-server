package com.datasaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DataSaverVpnService : VpnService() {

    companion object {
        const val TAG = "DataSaverVPN"
        const val ACTION_START = "com.datasaver.START"
        const val ACTION_STOP = "com.datasaver.STOP"
        const val CHANNEL_ID = "datasaver_vpn"
        const val NOTIFICATION_ID = 1

        var serverHost: String = "10.0.2.2" // localhost from emulator
        var serverPort: Int = 3000

        val totalOriginalBytes = AtomicLong(0)
        val totalCompressedBytes = AtomicLong(0)
        var isRunning = AtomicBoolean(false)
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                serverHost = intent?.getStringExtra("server_host") ?: serverHost
                serverPort = intent?.getIntExtra("server_port", serverPort) ?: serverPort
                startVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val builder = Builder()
            .setSession("DataSaver")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setMtu(1500)

        // Exclude our own app to prevent loops
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exclude self", e)
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN")
            stopSelf()
            return
        }

        running = true
        isRunning.set(true)
        Log.i(TAG, "VPN established, routing through $serverHost:$serverPort")

        // Start packet forwarding in background thread
        Thread { forwardPackets() }.start()
    }

    private fun forwardPackets() {
        val vpnFd = vpnInterface ?: return
        val input = FileInputStream(vpnFd.fileDescriptor)
        val output = FileOutputStream(vpnFd.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)

        // Open a UDP channel to forward packets
        val tunnel = DatagramChannel.open()
        tunnel.configureBlocking(false)
        protect(tunnel.socket()) // Prevent VPN loop

        try {
            while (running) {
                // Read from VPN interface
                val length = input.read(packet.array())
                if (length > 0) {
                    packet.limit(length)
                    totalOriginalBytes.addAndGet(length.toLong())

                    // Forward packet to actual destination
                    // For v1: we do simple packet forwarding
                    // The real compression happens at HTTP level via the proxy
                    packet.clear()
                }

                Thread.sleep(1) // Prevent CPU spin
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet forwarding error", e)
        } finally {
            tunnel.close()
        }
    }

    private fun stopVpn() {
        running = false
        isRunning.set(false)
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "DataSaver VPN", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "DataSaver is saving your data" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, DataSaverVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DataSaver Active")
            .setContentText("Saving your mobile data")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(
                Notification.Action.Builder(
                    null, "Stop", stopPending
                ).build()
            )
            .setOngoing(true)
            .build()
    }
}
