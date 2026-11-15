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

    private const val GITHUB_REPO_OWNER = "gathamediaofficial-lab"
    private const val GITHUB_REPO_NAME = "AetherCall"

    data class GitHubRelease(
        val tagName: String,
        val version: String,
        val htmlUrl: String,
        val body: String,
    )

    sealed interface UpdateCheckResult {
        data class UpdateAvailable(val release: GitHubRelease) : UpdateCheckResult
        data class UpToDate(val currentVersion: String) : UpdateCheckResult
        data class Error(val message: String) : UpdateCheckResult
    }

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
    )

    suspend fun checkForUpdate(appVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AetherCall-AndroidApp")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseString)

                val rawTag = json.optString("tag_name", "").trim()
                val remoteVersion = rawTag.removePrefix("v").removePrefix("V").trim()
                val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/tag/$rawTag")
                val body = json.optString("body", "")

                val release = GitHubRelease(
                    tagName = rawTag,
                    version = remoteVersion,
                    htmlUrl = htmlUrl,
                    body = body,
                )

                val currentVersion = appVersionName.removePrefix("v").removePrefix("V").trim()

                if (compareVersions(remoteVersion, currentVersion) > 0) {
                    UpdateCheckResult.UpdateAvailable(release)
                } else {
                    UpdateCheckResult.UpToDate(currentVersion)
                }
            } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                val currentVersion = appVersionName.removePrefix("v").removePrefix("V").trim()
                UpdateCheckResult.UpToDate(currentVersion)
            } else {
                UpdateCheckResult.Error("HTTP error $code")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Network request failed")
        }
    }

    suspend fun checkForUpdates(context: Context, currentVersionName: String): UpdateInfo? {
        return when (val result = checkForUpdate(currentVersionName)) {
            is UpdateCheckResult.UpdateAvailable -> UpdateInfo(
                hasUpdate = true,
                latestVersion = result.release.version,
                releaseNotes = result.release.body,
                downloadUrl = result.release.htmlUrl,
            )
            is UpdateCheckResult.UpToDate -> UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersionName,
                releaseNotes = "",
                downloadUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases",
            )
            is UpdateCheckResult.Error -> null
        }
    }

    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.removePrefix("v").removePrefix("V").trim()
        val clean2 = v2.removePrefix("v").removePrefix("V").trim()
        val parts1 = clean1.split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    fun openReleasePage(context: Context, urlString: String) = openWebPage(context, urlString)

    fun openWebPage(context: Context, urlString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
