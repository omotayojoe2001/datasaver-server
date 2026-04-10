package com.datasaver

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Routes HTTP requests through our compression server.
 * Instead of fetching images/pages directly, we ask our server to fetch + compress them.
 */
object CompressionProxy {

    private const val TAG = "CompressionProxy"

    val savedBytes = AtomicLong(0)
    val totalRequests = AtomicLong(0)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var serverUrl = "http://10.0.2.2:3000" // default for emulator

    fun configure(host: String, port: Int) {
        serverUrl = "http://$host:$port"
        Log.i(TAG, "Proxy configured: $serverUrl")
    }

    /**
     * Fetch a URL through our compression server.
     * Returns the compressed bytes + metadata about savings.
     */
    suspend fun fetch(url: String, quality: Int = 40): ProxyResult = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val proxyUrl = "$serverUrl/proxy?url=$encodedUrl&quality=$quality"

            val request = Request.Builder()
                .url(proxyUrl)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.bytes() ?: ByteArray(0)

            val originalSize = response.header("X-Original-Size")?.toLongOrNull() ?: body.size.toLong()
            val compressedSize = response.header("X-Compressed-Size")?.toLongOrNull() ?: body.size.toLong()
            val saved = originalSize - compressedSize

            savedBytes.addAndGet(saved)
            totalRequests.incrementAndGet()

            ProxyResult(
                data = body,
                contentType = response.header("Content-Type") ?: "application/octet-stream",
                originalSize = originalSize,
                compressedSize = compressedSize,
                savedBytes = saved,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Proxy fetch failed: ${e.message}")
            ProxyResult(success = false, error = e.message)
        }
    }

    /** Get stats from the server */
    suspend fun getStats(): ServerStats? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$serverUrl/stats").build()
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return@withContext null
            // Simple manual parse to avoid adding Gson dependency
            val original = Regex("\"originalBytes\":(\\d+)").find(json)?.groupValues?.get(1)?.toLong() ?: 0
            val compressed = Regex("\"compressedBytes\":(\\d+)").find(json)?.groupValues?.get(1)?.toLong() ?: 0
            val savedPct = Regex("\"savedPercent\":(\\d+\\.?\\d*)").find(json)?.groupValues?.get(1)?.toFloat() ?: 0f
            ServerStats(original, compressed, original - compressed, savedPct)
        } catch (e: Exception) {
            Log.e(TAG, "Stats fetch failed: ${e.message}")
            null
        }
    }
}

data class ProxyResult(
    val data: ByteArray? = null,
    val contentType: String = "",
    val originalSize: Long = 0,
    val compressedSize: Long = 0,
    val savedBytes: Long = 0,
    val success: Boolean = false,
    val error: String? = null
)

data class ServerStats(
    val originalBytes: Long,
    val compressedBytes: Long,
    val savedBytes: Long,
    val savedPercent: Float
)
