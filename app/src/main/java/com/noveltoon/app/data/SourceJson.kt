package com.noveltoon.app.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.noveltoon.app.data.entity.BookSource
import com.noveltoon.app.data.entity.ComicSource

/**
 * Builds Room entities with the Kotlin constructor so missing JSON fields stay
 * empty strings. Gson reflection leaves those fields null and breaks [BookSource.copy].
 *
 * Field mapping follows the importer already used for Legado-style JSON.
 */
internal object SourceJson {
    fun parseBookSources(json: String): List<BookSource> {
        val objects = parseObjects(json)
        if (objects.isEmpty()) throw IllegalArgumentException("书源 JSON 为空或无法解析")
        val parsed = objects.mapNotNull { parseBookSource(it) }
        if (parsed.isEmpty()) throw IllegalArgumentException("书源 JSON 中没有可用的 name")
        return parsed
    }

    fun parseComicSources(json: String): List<ComicSource> {
        val objects = parseObjects(json)
        if (objects.isEmpty()) throw IllegalArgumentException("图源 JSON 为空或无法解析")
        val parsed = objects.mapNotNull { parseComicSource(it) }
        if (parsed.isEmpty()) throw IllegalArgumentException("图源 JSON 中没有可用的 name")
        return parsed
    }

    fun parseBookSource(o: JsonObject): BookSource? {
        return try {
            val ruleSearch = o.obj("ruleSearch")
            val ruleBookInfo = o.obj("ruleBookInfo")
            val ruleToc = o.obj("ruleToc")
            val ruleContent = o.obj("ruleContent")
            BookSource(
                name = o.str("name") ?: o.str("bookSourceName") ?: return null,
                baseUrl = normalizeBaseUrl(o.str("baseUrl") ?: o.str("bookSourceUrl") ?: ""),
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: o.get("customOrder")?.asInt ?: 0,
                searchUrl = normalizeSearchUrl(o.str("searchUrl") ?: ""),
                searchListRule = sanitizeRule(o.str("searchListRule") ?: ruleSearch?.str("bookList") ?: ""),
                searchNameRule = sanitizeRule(o.str("searchNameRule") ?: ruleSearch?.str("name") ?: ""),
                searchAuthorRule = sanitizeRule(o.str("searchAuthorRule") ?: ruleSearch?.str("author") ?: ""),
                searchCoverRule = sanitizeRule(o.str("searchCoverRule") ?: ruleSearch?.str("coverUrl") ?: ""),
                searchUrlRule = sanitizeRule(o.str("searchUrlRule") ?: ruleSearch?.str("bookUrl") ?: ""),
                searchLatestChapterRule = sanitizeRule(
                    o.str("searchLatestChapterRule") ?: ruleSearch?.str("lastChapter") ?: ""
                ),
                detailNameRule = sanitizeRule(o.str("detailNameRule") ?: ruleBookInfo?.str("name") ?: ""),
                detailAuthorRule = sanitizeRule(o.str("detailAuthorRule") ?: ruleBookInfo?.str("author") ?: ""),
                detailCoverRule = sanitizeRule(o.str("detailCoverRule") ?: ruleBookInfo?.str("coverUrl") ?: ""),
                detailDescRule = sanitizeRule(o.str("detailDescRule") ?: ruleBookInfo?.str("intro") ?: ""),
                chapterListRule = sanitizeRule(o.str("chapterListRule") ?: ruleToc?.str("chapterList") ?: ""),
                chapterNameRule = sanitizeRule(o.str("chapterNameRule") ?: ruleToc?.str("chapterName") ?: ""),
                chapterUrlRule = sanitizeRule(o.str("chapterUrlRule") ?: ruleToc?.str("chapterUrl") ?: ""),
                contentRule = sanitizeRule(o.str("contentRule") ?: ruleContent?.str("content") ?: ""),
                contentNextPageRule = sanitizeRule(
                    o.str("contentNextPageRule") ?: ruleContent?.str("nextContentUrl") ?: ""
                ),
                searchEncoding = o.str("searchEncoding") ?: o.str("charset") ?: "UTF-8",
                isBuiltIn = o.get("isBuiltIn")?.asBoolean ?: false
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseComicSource(o: JsonObject): ComicSource? {
        return try {
            val ruleSearch = o.obj("ruleSearch")
            val ruleBookInfo = o.obj("ruleBookInfo")
            val ruleToc = o.obj("ruleToc")
            val ruleContent = o.obj("ruleContent")
            ComicSource(
                name = o.str("name") ?: o.str("bookSourceName") ?: o.str("sourceName") ?: return null,
                baseUrl = normalizeBaseUrl(o.str("baseUrl") ?: o.str("bookSourceUrl") ?: o.str("sourceUrl") ?: ""),
                enabled = o.get("enabled")?.asBoolean ?: true,
                sortOrder = o.get("sortOrder")?.asInt ?: o.get("customOrder")?.asInt ?: 0,
                searchUrl = normalizeSearchUrl(o.str("searchUrl") ?: ""),
                searchListRule = sanitizeRule(o.str("searchListRule") ?: ruleSearch?.str("bookList") ?: ""),
                searchNameRule = sanitizeRule(o.str("searchNameRule") ?: ruleSearch?.str("name") ?: ""),
                searchAuthorRule = sanitizeRule(o.str("searchAuthorRule") ?: ruleSearch?.str("author") ?: ""),
                searchCoverRule = sanitizeRule(o.str("searchCoverRule") ?: ruleSearch?.str("coverUrl") ?: ""),
                searchUrlRule = sanitizeRule(o.str("searchUrlRule") ?: ruleSearch?.str("bookUrl") ?: ""),
                searchStatusRule = sanitizeRule(o.str("searchStatusRule") ?: ruleSearch?.str("kind") ?: ""),
                detailNameRule = sanitizeRule(o.str("detailNameRule") ?: ruleBookInfo?.str("name") ?: ""),
                detailAuthorRule = sanitizeRule(o.str("detailAuthorRule") ?: ruleBookInfo?.str("author") ?: ""),
                detailCoverRule = sanitizeRule(o.str("detailCoverRule") ?: ruleBookInfo?.str("coverUrl") ?: ""),
                detailDescRule = sanitizeRule(o.str("detailDescRule") ?: ruleBookInfo?.str("intro") ?: ""),
                chapterListRule = sanitizeRule(o.str("chapterListRule") ?: ruleToc?.str("chapterList") ?: ""),
                chapterNameRule = sanitizeRule(o.str("chapterNameRule") ?: ruleToc?.str("chapterName") ?: ""),
                chapterUrlRule = sanitizeRule(o.str("chapterUrlRule") ?: ruleToc?.str("chapterUrl") ?: ""),
                imageListRule = sanitizeRule(
                    o.str("imageListRule") ?: ruleContent?.str("imageList") ?: ruleContent?.str("content") ?: ""
                ),
                imageUrlRule = sanitizeRule(o.str("imageUrlRule") ?: ruleContent?.str("imageUrl") ?: ""),
                searchEncoding = o.str("searchEncoding") ?: o.str("charset") ?: "UTF-8",
                isBuiltIn = o.get("isBuiltIn")?.asBoolean ?: false
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseObjects(json: String): List<JsonObject> {
        val element = JsonParser.parseString(json.trim().removePrefix("\uFEFF"))
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
            element.isJsonObject -> listOf(element.asJsonObject)
            else -> emptyList()
        }
    }

    /** Strip Legado POST/options suffix: url,{"method":"POST",...} */
    private fun normalizeSearchUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val optionIdx = trimmed.indexOf(",{")
        val urlPart = if (optionIdx > 0) trimmed.substring(0, optionIdx).trim() else trimmed
        return urlPart
            .replace("{{key}}", "{{keyword}}")
            .replace("{{searchKey}}", "{{keyword}}")
    }

    private fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

    private fun sanitizeRule(raw: String): String {
        val rule = raw.trim()
        if (rule.isEmpty()) return ""
        if (rule.contains("@js", ignoreCase = true) || rule.contains("<js>", ignoreCase = true)) {
            return ""
        }
        val splitAt = rule.indexOf('@').let { if (it < 0) Int.MAX_VALUE else it }
        val splitHash = rule.indexOf("##").let { if (it < 0) Int.MAX_VALUE else it }
        val cut = minOf(splitAt, splitHash)
        if (cut == Int.MAX_VALUE) return normalizeLegadoCss(rule)
        return normalizeLegadoCss(rule.substring(0, cut)) + rule.substring(cut)
    }

    private fun normalizeLegadoCss(selector: String): String {
        return selector
            .replace(Regex("""\btag\."""), "")
            .replace(Regex("""\bclass\."""), ".")
            .replace(Regex("""\bid\."""), "#")
            .trim()
    }

    private fun JsonObject.str(key: String): String? {
        return try {
            val el = this.get(key)
            if (el == null || el.isJsonNull) null
            else if (el.isJsonPrimitive) el.asString
            else el.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? {
        val el = this.get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }
}
