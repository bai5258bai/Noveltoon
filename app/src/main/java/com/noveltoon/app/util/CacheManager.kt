package com.noveltoon.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {

    fun getCacheSize(context: Context): Long {
        return getDirSize(context.cacheDir)
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        }
        return size
    }

    fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
            else -> "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        deleteDir(context.cacheDir)
    }

    suspend fun clearOldCache(context: Context, days: Int) = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        clearOldFiles(context.cacheDir, threshold)
    }

    private fun clearOldFiles(dir: File, threshold: Long) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    clearOldFiles(file, threshold)
                    if (file.listFiles()?.isEmpty() == true) file.delete()
                } else if (file.lastModified() < threshold) {
                    file.delete()
                }
            }
        }
    }

    private fun deleteDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) deleteDir(file) else file.delete()
            }
        }
    }
}
