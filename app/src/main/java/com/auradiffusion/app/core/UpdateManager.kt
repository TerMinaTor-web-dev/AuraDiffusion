package com.auradiffusion.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.auradiffusion.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "aura_update_prefs"
        private const val KEY_SNOOZE_TIMESTAMP = "key_snooze_timestamp"
        private const val SNOOZE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 Hours

        // Remote version metadata URL on GitHub
        const val VERSION_CHECK_URL =
            "https://raw.githubusercontent.com/TerMinaTor-web-dev/AuraDiffusion/main/version.json"
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val changelog: String
    )

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(val info: UpdateInfo) : UpdateState()
        data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateState()
        data class ReadyToInstall(val apkFile: File) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /**
     * Checks if a new release exists on GitHub.
     * Honors the 24-hour snooze if the user previously selected 'Update Later'.
     */
    suspend fun checkForUpdates(forceCheck: Boolean = false) = withContext(Dispatchers.IO) {
        val lastSnooze = prefs.getLong(KEY_SNOOZE_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()

        // If postponed and 24 hours have not passed, skip unless forced
        if (!forceCheck && (now - lastSnooze < SNOOZE_DURATION_MS)) {
            Log.i(TAG, "Update check snoozed for 24 hours (Remaining: ${(SNOOZE_DURATION_MS - (now - lastSnooze)) / (1000 * 60)} mins)")
            return@withContext
        }

        _updateState.value = UpdateState.Checking

        try {
            val url = URL(VERSION_CHECK_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                setRequestProperty("User-Agent", "AuraDiffusion-App")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", "")
                val downloadUrl = json.optString("downloadUrl", "")
                val changelog = json.optString("changelog", "New update available with bug fixes and performance improvements.")

                val currentVersionCode = BuildConfig.VERSION_CODE

                if (remoteVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                    val info = UpdateInfo(remoteVersionCode, remoteVersionName, downloadUrl, changelog)
                    _updateState.value = UpdateState.Available(info)
                    Log.i(TAG, "Update available: v$remoteVersionName ($remoteVersionCode > $currentVersionCode)")
                } else {
                    _updateState.value = UpdateState.Idle
                    Log.i(TAG, "App is up to date: current v${BuildConfig.VERSION_NAME}")
                }
            } else {
                _updateState.value = UpdateState.Idle
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Notice checking for updates: ${e.message}")
            _updateState.value = UpdateState.Idle
        }
    }

    /**
     * Called when the user clicks 'Update Later'.
     * Saves timestamp to snooze the popup for 24 hours.
     */
    fun postponeUpdate() {
        prefs.edit().putLong(KEY_SNOOZE_TIMESTAMP, System.currentTimeMillis()).apply()
        _updateState.value = UpdateState.Idle
        Log.i(TAG, "Update postponed for 24 hours")
    }

    /**
     * Called when the user clicks 'Update Now'.
     * Downloads the APK with progress updates, then launches package installer.
     */
    suspend fun downloadAndInstallUpdate(info: UpdateInfo) = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Downloading(0, 0, 0)

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "AuraDiffusion_v${info.versionName}.apk")

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val url = URL(info.downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AuraDiffusion-Updater")
            }
            connection.connect()

            val totalBytes = connection.contentLengthLong
            inputStream = connection.inputStream
            outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalRead: Long = 0
            var lastReport = System.currentTimeMillis()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastReport > 150) {
                    val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else 0
                    _updateState.value = UpdateState.Downloading(percent, totalRead, totalBytes)
                    lastReport = now
                }
            }
            outputStream.flush()

            _updateState.value = UpdateState.ReadyToInstall(apkFile)
            launchInstaller(apkFile)
        } catch (e: Throwable) {
            Log.e(TAG, "Update download failed", e)
            apkFile.delete()
            _updateState.value = UpdateState.Error(e.message ?: "Download failed")
        } finally {
            outputStream?.close()
            inputStream?.close()
            connection?.disconnect()
        }
    }

    /**
     * Launches the system APK package installer via FileProvider.
     */
    fun launchInstaller(apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch installer intent", e)
            _updateState.value = UpdateState.Error("Unable to open installer: ${e.message}")
        }
    }

    fun dismissError() {
        _updateState.value = UpdateState.Idle
    }
}
