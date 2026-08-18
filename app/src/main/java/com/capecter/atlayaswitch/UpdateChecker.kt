package com.capecter.atlayaswitch

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fragt den maschinenlesbaren Update-Feed der Webseite ab
 * (https://atlaya.capecter.com/atlayaswitch/updates/latest.json, siehe
 * write_update_feed_atlayaswitch() in D:\Atlaya\scripts\build_website.py) und
 * vergleicht die dort genannte Version mit der installierten App-Version.
 * Reine Anzeige/Verlinkung - kein Selbst-Updater, AtlayaSwitch lädt/installiert
 * nichts automatisch (Downloads bleiben eine bewusste Nutzeraktion im Browser).
 */
object UpdateChecker {

    private const val FEED_URL = "https://atlaya.capecter.com/atlayaswitch/updates/latest.json"
    private val mainHandler = Handler(Looper.getMainLooper())

    data class UpdateResult(
        val currentVersion: String,
        val latestVersion: String,
        val updateAvailable: Boolean,
        val downloadUrl: String
    )

    fun check(context: Context, onResult: (UpdateResult) -> Unit, onError: (Exception) -> Unit) {
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0"
        } catch (e: Exception) {
            "0.0"
        }

        Thread {
            try {
                val connection = URL(FEED_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val latestVersion = json.optString("version", currentVersion)
                val downloadUrl = json.optString("url", "")
                val result = UpdateResult(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    updateAvailable = isNewer(latestVersion, currentVersion),
                    downloadUrl = downloadUrl
                )
                mainHandler.post { onResult(result) }
            } catch (e: Exception) {
                mainHandler.post { onError(e) }
            }
        }.start()
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }
}
