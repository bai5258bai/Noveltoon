package com.noveltoon.app.util

import android.content.Context
import android.net.Uri
import com.noveltoon.app.data.entity.Comic
import com.noveltoon.app.data.entity.ComicChapter
import com.noveltoon.app.data.entity.Novel
import com.noveltoon.app.data.entity.NovelChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object FileImporter {

    suspend fun importTxtNovel(context: Context, uri: Uri): Pair<Novel, List<NovelChapter>>? =
        withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(context, uri) ?: "Unknown"
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, "UTF-8")).readText()
                } ?: return@withContext null

                val chapters = parseTxtChapters(content)
                val novel = Novel(
                    title = fileName.removeSuffix(".txt"),
                    isLocal = true,
                    localPath = uri.toString(),
                    totalChapters = chapters.size
                )
                Pair(novel, chapters)
            } catch (e: Exception) {
                null
            }
        }

    private fun parseTxtChapters(content: String): List<NovelChapter> {
        val chapterPattern = Regex("^\\s*(第[零一二三四五六七八九十百千万\\d]+[章节回卷]|Chapter\\s+\\d+|CHAPTER\\s+\\d+).*", RegexOption.MULTILINE)
        val matches = chapterPattern.findAll(content).toList()

        if (matches.isEmpty()) {
            val chunkSize = 5000
            return content.chunked(chunkSize).mapIndexed { index, chunk ->
                NovelChapter(
                    novelId = 0,
                    title = "第${index + 1}部分",
                    content = chunk,
                    index = index,
                    isCached = true
                )
            }
        }

        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else content.length
            NovelChapter(
                novelId = 0,
                title = match.value.trim(),
                content = content.substring(start, end).trim(),
                index = index,
                isCached = true
            )
        }
    }

    suspend fun importLocalComic(context: Context, uri: Uri): Pair<Comic, List<ComicChapter>>? =
        withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(context, uri) ?: "Unknown"

                val tempDir = File(context.cacheDir, "comic_import_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                val imageFiles = mutableListOf<String>()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(stream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && isImageFile(entry.name)) {
                                val outFile = File(tempDir, entry.name)
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out -> zip.copyTo(out) }
                                imageFiles.add(outFile.absolutePath)
                            }
                            entry = zip.nextEntry
                        }
                    }
                }

                imageFiles.sort()

                val comic = Comic(
                    title = fileName.removeSuffix(".zip").removeSuffix(".cbz"),
                    isLocal = true,
                    localPath = tempDir.absolutePath,
                    totalChapters = 1
                )

                val chapter = ComicChapter(
                    comicId = 0,
                    title = "全部",
                    url = tempDir.absolutePath,
                    index = 0
                )

                Pair(comic, listOf(chapter))
            } catch (e: Exception) {
                null
            }
        }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif")
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex("_display_name")
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex)
                else uri.lastPathSegment
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
}
