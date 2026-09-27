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
    @Volatile
    private var isDownloading = false
    private val TAG = "UpdateManager"

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.4.0"
        } catch (_: Exception) {
            "1.4.0"
        }
    }

    fun getUpdatesDir(): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Checks if the update APK has already been downloaded and verified
     */
    fun getCachedApk(version: String, expectedSize: Long): File? {
        try {
            val file = File(getUpdatesDir(), "FloatInput_v$version.apk")
            if (file.exists() && file.isFile) {
                if (expectedSize <= 0 || file.length() == expectedSize) {
                    return file
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCachedApk error", e)
        }
        return null
    }

    /**
     * Automatically deletes old or already-installed APK files in cacheDir/updates
     */
    fun autoCleanOldApks() {
        thread {
            try {
                val dir = File(context.cacheDir, "updates")
                if (!dir.exists()) return@thread
                val currentVer = getCurrentVersionName()
                dir.listFiles()?.forEach { file ->
                    val name = file.name
                    // Remove stale .tmp partial downloads
                    if (name.endsWith(".tmp")) {
                        file.delete()
                        Log.d(TAG, "Cleaned partial download: $name")
                        return@forEach
                    }
                    // Remove any file not matching our naming convention (stray duplicates)
                    if (!(name.startsWith("FloatInput_v") && name.endsWith(".apk"))) {
                        file.delete()
                        Log.d(TAG, "Cleaned stray file: $name")
                        return@forEach
                    }
                    // Remove APKs for current or older versions
                    val fileVer = name.removePrefix("FloatInput_v").removeSuffix(".apk")
                    if (!isNewerVersion(fileVer, currentVer)) {
                        file.delete()
                        Log.d(TAG, "Auto-cleaned outdated update APK: $name")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "autoCleanOldApks error", e)
            }
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
     * Downloads the APK file to cacheDir/updates/FloatInput_v{version}.apk
     * If the file is already completely downloaded, skips downloading and returns it immediately!
     */
    fun downloadApk(
        info: UpdateInfo,
        onProgress: (progress: Int, totalBytes: Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        // 1. Check if already downloaded!
        val cached = getCachedApk(info.latestVersion, info.apkSize)
        if (cached != null) {
            Log.d(TAG, "APK already cached and valid, skipping download: ${cached.absolutePath}")
            onComplete(cached)
            return
        }

        // 2. Prevent concurrent downloads
        if (isDownloading) {
            Log.w(TAG, "Download already in progress, ignoring duplicate request")
            return
        }
        isDownloading = true

        thread {
            try {
                val url = URL(info.downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "FloatInput-Android")

                val totalLength = conn.contentLength.toLong()
                val targetFile = File(getUpdatesDir(), "FloatInput_v${info.latestVersion}.apk")
                val tempFile = File(getUpdatesDir(), "FloatInput_v${info.latestVersion}.tmp")
                if (tempFile.exists()) tempFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
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

                // Rename tmp to targetFile after complete download
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                isDownloading = false

                mainHandler.post {
                    onComplete(targetFile)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "downloadApk error", e)
                isDownloading = false
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
