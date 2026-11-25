package com.noveltoon.app.ui.novel

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NovelPaginatorTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun pagesJoinToTheOriginalAndEachPageFits() {
        val content = buildString {
            repeat(40) { index ->
                append("第${index + 1}行：山高水长，文字不应被页脚裁掉。")
                append('\n')
            }
        }
        val cases = listOf(
            Quad(18f, 1.5f, 900, 420),
            Quad(28f, 1.8f, 700, 360),
            Quad(16f, 1.2f, 480, 240)
        )
        val results = arrayOfNulls<List<String>>(cases.size)
        val heights = arrayOfNulls<List<Int>>(cases.size)
        rule.setContent {
            val measurer = rememberTextMeasurer()
            cases.forEachIndexed { index, case ->
                val style = TextStyle(
                    fontSize = case.font.sp,
                    lineHeight = (case.font * case.spacing).sp
                )
                val pages = splitIntoPages(content, measurer, style, case.width, case.height)
                results[index] = pages
                heights[index] = pages.map { page ->
                    measurer.measure(
                        text = page,
                        style = style,
                        constraints = Constraints(maxWidth = case.width)
                    ).size.height
                }
            }
        }
        rule.waitForIdle()
        cases.forEachIndexed { index, case ->
            val pages = results[index] ?: error("missing pages")
            assertEquals(content, pages.joinToString(""))
            assertTrue(pages.size > 1)
            heights[index].orEmpty().forEach { height ->
                assertTrue("page height $height exceeds ${case.height}", height <= case.height)
            }
        }
    }

    private data class Quad(val font: Float, val spacing: Float, val width: Int, val height: Int)
}
