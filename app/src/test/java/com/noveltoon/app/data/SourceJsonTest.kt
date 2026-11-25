package com.noveltoon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceJsonTest {
    @Test
    fun missingFields_useEmptyStrings_andCopySucceeds() {
        val json = """[{"name":"仅名称","baseUrl":"https://example.com","enabled":true}]"""

        val parsed = SourceJson.parseBookSources(json)
        val copied = parsed.map { it.copy(id = 0, isBuiltIn = true) }

        assertEquals(1, copied.size)
        assertEquals("", copied[0].detailNameRule)
        assertEquals("", copied[0].contentNextPageRule)
        assertEquals("", copied[0].searchCoverRule)
        assertEquals("", copied[0].chapterListRule)
    }

    @Test
    fun bundledBookSources_loadWithoutNullFields() {
        val file = listOf(
            File("src/main/assets/default_book_sources.json"),
            File("app/src/main/assets/default_book_sources.json")
        ).first { it.exists() }

        val sources = SourceJson.parseBookSources(file.readText())
        val copied = sources.map { it.copy(id = 0, enabled = it.enabled, isBuiltIn = true) }

        assertEquals(4, copied.size)
        assertTrue(copied.all { it.name.isNotBlank() })
        assertEquals("维基文库", copied.first().name)
        assertTrue(copied.first().enabled)
        assertEquals(1, copied.count { it.enabled })
        assertFalse(copied.any { it.detailNameRule.isEmpty() && it.name.isBlank() })
        copied.forEach { source ->
            assertEquals("", source.contentNextPageRule)
            source.copy(enabled = !source.enabled)
        }
    }

    @Test
    fun bundledComicSources_missingFieldsStillCopy() {
        val file = listOf(
            File("src/main/assets/default_comic_sources.json"),
            File("app/src/main/assets/default_comic_sources.json")
        ).first { it.exists() }
        val sources = SourceJson.parseComicSources(file.readText())
        val copied = sources.map { it.copy(id = 0, isBuiltIn = true) }
        assertEquals(3, copied.size)
        assertTrue(copied.all { it.detailNameRule == "" })
        assertEquals("", copied.first { it.name.startsWith("漫画猫") }.imageUrlRule)
    }

    @Test
    fun invalidJson_reportsInsteadOfReturningEmpty() {
        try {
            SourceJson.parseBookSources("not-json")
            throw AssertionError("expected parse failure")
        } catch (e: Exception) {
            assertTrue(e.message?.isNotBlank() == true)
        }
    }
}
