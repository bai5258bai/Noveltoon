package com.noveltoon.app.data.parser

/**
 * Some catalogs list the newest chapter first. Reading order is the ascending
 * chapter number. Unnumbered titles keep their relative place when the list is reversed.
 */
internal object ChapterOrder {
    fun arrange(chapters: List<ChapterInfo>): List<ChapterInfo> {
        if (!needsReverse(chapters.map { it.title })) return chapters
        return chapters.asReversed()
    }

    fun needsReverse(titles: List<String>): Boolean {
        val numbers = titles.mapNotNull { chapterNumber(it) }
        if (numbers.size < 2) return false
        return numbers.first() > numbers.last()
    }

    /**
     * Index 0 is the start of the book. A saved index above 0 is kept by matching
     * the chapter title, so a real bookmark survives a reorder.
     */
    fun restoredIndex(savedIndex: Int, savedTitle: String, orderedTitles: List<String>): Int {
        if (orderedTitles.isEmpty()) return 0
        if (savedIndex <= 0) return 0
        val match = orderedTitles.indexOf(savedTitle)
        if (match >= 0) return match
        return savedIndex.coerceIn(0, orderedTitles.lastIndex)
    }

    fun chapterNumber(title: String): Int? {
        val arabic = Regex("第\\s*(\\d+)\\s*[章节回卷话部]").find(title)
        if (arabic != null) return arabic.groupValues[1].toIntOrNull()
        val chinese = Regex("第\\s*([零一二三四五六七八九十百千两]+)\\s*[章节回卷话部]").find(title)
            ?: return null
        return parseChineseNumber(chinese.groupValues[1])
    }

    private fun parseChineseNumber(raw: String): Int? {
        if (raw.isEmpty()) return null
        val digit = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        var total = 0
        var current = 0
        for (ch in raw) {
            when (ch) {
                '十' -> {
                    current = if (current == 0) 1 else current
                    total += current * 10
                    current = 0
                }
                '百' -> {
                    current = if (current == 0) 1 else current
                    total += current * 100
                    current = 0
                }
                '千' -> {
                    current = if (current == 0) 1 else current
                    total += current * 1000
                    current = 0
                }
                else -> {
                    val value = digit[ch] ?: return null
                    current = value
                }
            }
        }
        return total + current
    }
}
