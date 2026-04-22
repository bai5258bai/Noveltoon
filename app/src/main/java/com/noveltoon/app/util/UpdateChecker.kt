package com.noveltoon.app.util

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val body: String
)

object UpdateChecker {
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/bai5258bai/Noveltoon/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun fetchLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NovelToon-Android")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null
            val release = gson.fromJson(body, GitHubRelease::class.java)
            UpdateInfo(
                versionName = release.tagName.removePrefix("v"),
                releaseUrl = release.htmlUrl,
                body = release.body ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrNull(i) ?: 0
            val cv = c.getOrNull(i) ?: 0
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> {
        return v.split('.', '-').mapNotNull { it.toIntOrNull() }
    }

    private data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("html_url") val htmlUrl: String,
        @SerializedName("body") val body: String?
    )
}
