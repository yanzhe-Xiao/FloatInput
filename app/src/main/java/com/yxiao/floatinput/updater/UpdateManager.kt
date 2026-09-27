package com.yxiao.floatinput.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val changelog: String,
    val downloadUrl: String,
    val apkSize: Long
)

class UpdateManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "UpdateManager"

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    /**
     * Checks GitHub API for the latest release
     */
    fun checkUpdate(callback: (Result<UpdateInfo>) -> Unit) {
        thread {
            try {
                val apiUrl = "https://api.github.com/repos/yanzhe-Xiao/FloatInput/releases/latest"
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "FloatInput-Android")

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)

                    val tagName = json.optString("tag_name", "").removePrefix("v")
                    val body = json.optString("body", "优化与性能提升")

                    var downloadUrl = ""
                    var apkSize = 0L

                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.contains("release", ignoreCase = true) && name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                        // Fallback to any apk asset if specific release apk not found
                        if (downloadUrl.isEmpty()) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk")) {
                                    downloadUrl = asset.optString("browser_download_url", "")
                                    apkSize = asset.optLong("size", 0L)
                                    break
                                }
                            }
                        }
                    }

                    val currentVer = getCurrentVersionName()
                    val hasUpdate = isNewerVersion(tagName, currentVer)

                    val info = UpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersion = tagName,
                        currentVersion = currentVer,
                        changelog = body,
                        downloadUrl = downloadUrl,
                        apkSize = apkSize
                    )

                    mainHandler.post {
                        callback(Result.success(info))
                    }
                } else {
                    mainHandler.post {
                        callback(Result.failure(Exception("HTTP $responseCode from GitHub API")))
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "checkUpdate error", e)
                mainHandler.post {
                    callback(Result.failure(e))
                }
            }
        }
    }

    /**
     * Downloads the APK file with progress callback
     */
    fun downloadApk(
        downloadUrl: String,
        onProgress: (progress: Int, totalBytes: Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        thread {
            try {
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "FloatInput-Android")

                val totalLength = conn.contentLength.toLong()
                val outFile = File(context.cacheDir, "FloatInput_latest.apk")
                if (outFile.exists()) outFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalLength > 0) {
                                val progress = ((downloaded * 100) / totalLength).toInt()
                                mainHandler.post {
                                    onProgress(progress, totalLength)
                                }
                            }
                        }
                    }
                }

                mainHandler.post {
                    onComplete(outFile)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "downloadApk error", e)
                mainHandler.post {
                    onError(e)
                }
            }
        }
    }

    /**
     * Prompts the Android PackageInstaller to install the update APK
     */
    fun installApk(apkFile: File): Boolean {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "installApk: file does not exist ${apkFile.absolutePath}")
                return false
            }

            // Android 8.0+ unknown app install permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "installApk failed", e)
            return false
        }
    }

    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        if (remoteVersion.isEmpty()) return false
        val rParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
