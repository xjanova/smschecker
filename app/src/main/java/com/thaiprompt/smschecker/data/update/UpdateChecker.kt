package com.thaiprompt.smschecker.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.thaiprompt.smschecker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val isUpToDate: Boolean = false,
    val latestVersion: String = "",
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val fileName: String = "",
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val error: String = ""
)

/**
 * Checks for app updates from xman4289.com.
 *
 * API: GET https://xman4289.com/api/v1/product/smschecker/update/check?current_version=X.Y.Z
 * Response: {
 *   "has_update": true,
 *   "latest_version": "2.0.50",
 *   "download_url": "https://xman4289.com/downloads/smschecker/SmsChecker-v2.0.50.apk",
 *   "changelog": "..."
 * }
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val UPDATE_CHECK_URL = "https://xman4289.com/api/v1/product/smschecker/update/check"

    private const val PREFS_NAME = "smschecker_update"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"
    private const val KEY_AUTO_UPDATE = "auto_update_enabled"

    private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    private val _autoUpdateEnabled = MutableStateFlow(false)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled

    fun initAutoUpdatePref(context: Context) {
        _autoUpdateEnabled.value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE, false)
    }

    fun setAutoUpdate(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
        _autoUpdateEnabled.value = enabled
    }

    private val _updateInfo = MutableStateFlow(UpdateInfo())
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    fun dismissVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISMISSED_VERSION, version).apply()
        _updateInfo.value = _updateInfo.value.copy(hasUpdate = false)
    }

    private fun getDismissedVersion(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED_VERSION, null)
    }

    /**
     * Check for updates from xman4289.com.
     */
    suspend fun checkForUpdate(
        context: Context,
        shouldThrottle: Boolean = false,
        isManual: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (shouldThrottle && !isManual) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                Log.d(TAG, "Skipping update check — checked recently")
                return@withContext
            }
        }

        _updateInfo.value = _updateInfo.value.copy(isChecking = true, error = "", isUpToDate = false)

        try {
            val currentVersion = BuildConfig.VERSION_NAME
            val url = "$UPDATE_CHECK_URL?current_version=$currentVersion"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "SmsChecker-Android/$currentVersion")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"

            if (!response.isSuccessful) {
                _updateInfo.value = UpdateInfo(error = "เช็คอัพเดทไม่ได้: HTTP ${response.code}")
                return@withContext
            }

            val json = gson.fromJson(body, JsonObject::class.java)
            val hasUpdate = json.get("has_update")?.asBoolean ?: false
            val latestVersion = json.get("latest_version")?.asString ?: ""
            val downloadUrl = json.get("download_url")?.asString ?: ""
            val changelog = json.get("changelog")?.asString ?: ""

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            val dismissed = getDismissedVersion(context)
            val showUpdate = hasUpdate && (latestVersion != dismissed || _autoUpdateEnabled.value)

            _updateInfo.value = UpdateInfo(
                hasUpdate = showUpdate,
                isUpToDate = !hasUpdate,
                latestVersion = if (hasUpdate) latestVersion else currentVersion,
                currentVersion = currentVersion,
                downloadUrl = downloadUrl,
                releaseNotes = changelog,
                fileName = "SmsChecker-v${latestVersion}.apk"
            )

            Log.d(TAG, "Update check: current=$currentVersion latest=$latestVersion hasUpdate=$hasUpdate")

        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            _updateInfo.value = UpdateInfo(error = "เช็คอัพเดทไม่ได้: ${e.message}")
        }
    }

    /**
     * Download APK from xman4289.com and trigger install.
     */
    suspend fun downloadAndInstall(context: Context) = withContext(Dispatchers.IO) {
        val info = _updateInfo.value
        if (info.downloadUrl.isBlank()) {
            _updateInfo.value = info.copy(error = "ไม่มีลิงก์ดาวน์โหลด")
            return@withContext
        }

        withContext(Dispatchers.Main) {
            _updateInfo.value = info.copy(isDownloading = true, downloadProgress = -1, error = "")
        }

        try {
            val request = Request.Builder()
                .url(info.downloadUrl)
                .addHeader("User-Agent", "SmsChecker-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    _updateInfo.value = _updateInfo.value.copy(
                        isDownloading = false,
                        error = "ดาวน์โหลดไม่สำเร็จ: HTTP ${response.code}"
                    )
                }
                return@withContext
            }

            val contentLength = response.body?.contentLength() ?: -1L
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val fileName = info.fileName.ifBlank { "SmsChecker-v${info.latestVersion}.apk" }
            val apkFile = File(downloadDir, fileName)

            if (apkFile.exists()) apkFile.delete()

            withContext(Dispatchers.Main) {
                _updateInfo.value = _updateInfo.value.copy(downloadProgress = 0)
            }

            // Use Content-Length if available, otherwise use file_size from version info
            val effectiveLength = if (contentLength > 0) contentLength else {
                // Fallback: estimate ~35MB for typical APK
                35L * 1024 * 1024
            }

            response.body?.byteStream()?.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var read: Int
                    var lastEmittedProgress = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        // Cap at 95% during download — 100% only after file verified
                        val progress = if (contentLength > 0) {
                            (bytesRead * 95 / contentLength).toInt().coerceIn(0, 95)
                        } else {
                            (bytesRead * 95 / effectiveLength).toInt().coerceIn(0, 95)
                        }
                        if (progress != lastEmittedProgress) {
                            lastEmittedProgress = progress
                            withContext(Dispatchers.Main) {
                                _updateInfo.value = _updateInfo.value.copy(downloadProgress = progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            val fileSize = apkFile.length()
            Log.d(TAG, "APK downloaded: ${apkFile.absolutePath} ($fileSize bytes)")

            // Verify file is valid (at least 1MB for a real APK)
            if (fileSize < 1_000_000) {
                apkFile.delete()
                withContext(Dispatchers.Main) {
                    _updateInfo.value = _updateInfo.value.copy(
                        isDownloading = false,
                        error = "ไฟล์ดาวน์โหลดไม่สมบูรณ์ (${fileSize / 1024} KB) กรุณาลองใหม่"
                    )
                }
                return@withContext
            }

            withContext(Dispatchers.Main) {
                _updateInfo.value = _updateInfo.value.copy(downloadProgress = 100)
            }

            // Small delay for UI to show 100%
            kotlinx.coroutines.delay(500)

            withContext(Dispatchers.Main) {
                _updateInfo.value = _updateInfo.value.copy(isDownloading = false, downloadProgress = 100)
            }

            installApk(context, apkFile)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            withContext(Dispatchers.Main) {
                _updateInfo.value = _updateInfo.value.copy(
                    isDownloading = false,
                    error = "ดาวน์โหลดผิดพลาด: ${e.message}"
                )
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            // Check if app has permission to install from unknown sources
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "No install permission — opening settings")
                    _updateInfo.value = _updateInfo.value.copy(
                        error = "กรุณาเปิดสิทธิ์ \"ติดตั้งแอปที่ไม่รู้จัก\" แล้วลองอีกครั้ง"
                    )
                    try {
                        val settingsIntent = Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                    } catch (_: Exception) {}
                    return
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install APK failed", e)
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Install APK fallback failed", e2)
                _updateInfo.value = _updateInfo.value.copy(
                    error = "เปิดตัวติดตั้งไม่ได้ — ลองเปิดไฟล์ APK จาก File Manager"
                )
            }
        }
    }
}
