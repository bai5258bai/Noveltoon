package com.noveltoon.app.ui.novel

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * Splits text with Compose's own text layout ([TextMeasurer]), the same engine
 * [androidx.compose.foundation.text.BasicText] draws with. Page breaks follow
 * measured line bounds so the pieces concatenate back to the original string.
 */
internal fun splitIntoPages(
    content: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int
): List<String> {
    if (content.isEmpty() || widthPx <= 0 || heightPx <= 0) return listOf(content)
    val constraints = Constraints(maxWidth = widthPx)
    fun heightOf(start: Int, end: Int): Int {
        if (start >= end) return 0
        return textMeasurer.measure(
            text = content.substring(start, end),
            style = style,
            constraints = constraints
        ).size.height
    }
    if (heightOf(0, content.length) <= heightPx) return listOf(content)

    val pages = mutableListOf<String>()
    var start = 0
    while (start < content.length) {
        var low = start + 1
        var high = content.length
        var best = (start + 1).coerceAtMost(content.length)
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (heightOf(start, mid) <= heightPx) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        pages.add(content.substring(start, best))
        if (best <= start) break
        start = best
    }
    return pages
}
