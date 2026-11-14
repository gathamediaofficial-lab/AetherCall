package com.aether.call.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdateChecker {

    // Replace with your actual GitHub username and repository name
    private const val GITHUB_REPO_OWNER = "NareshKumarMeena"
    private const val GITHUB_REPO_NAME = "Aether-Call"

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
    )

    suspend fun checkForUpdates(context: Context, currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseString)

                val tagName = json.optString("tag_name", "v1.0.0").removePrefix("v").trim()
                val body = json.optString("body", "Bug fixes and performance improvements.")
                val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases")

                val cleanCurrent = currentVersionName.removePrefix("v").trim()
                val hasNewer = compareVersions(tagName, cleanCurrent) > 0

                return@withContext UpdateInfo(
                    hasUpdate = hasNewer,
                    latestVersion = tagName,
                    releaseNotes = body,
                    downloadUrl = htmlUrl,
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    fun openReleasePage(context: Context, urlString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}