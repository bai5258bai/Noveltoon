package com.noveltoon.app.ui

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.noveltoon.app.data.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the installed UI against the public-domain Wikisource source.
 * Records the chapter heading shown on first open, after the next chapter,
 * after returning, and after leaving and reopening the book.
 */
@RunWith(AndroidJUnit4::class)
class NovelReaderStartUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openBook_thenNextAndBack_thenReenter() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        runBlocking {
            AppPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
                .setNovelFontSize(26f)
        }
        if (rule.onAllNodesWithText("Search Novel").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Search Novel").performClick()
        } else {
            rule.onNodeWithContentDescription("Search").performClick()
        }
        rule.onNode(hasSetTextAction()).performTextReplacement("聊齋")
        rule.onNodeWithTag("novel_search_go").performClick()
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodes(hasText("聊齋志異", substring = false)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText("聊齋志異", substring = false))[0].performClick()
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodes(hasTestTag("chapter_heading")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("chapter_heading").assertIsDisplayed()
        val first = heading()
        Log.i("NovelStart", "first=$first")
        assertTrue(first, first.contains("第一卷"))
        shot(device, "01-first-open")

        device.click(device.displayWidth / 2, device.displayHeight / 2)
        rule.waitForIdle()
        rule.onNodeWithText("Next").performClick()
        rule.waitUntil(timeoutMillis = 20_000) { heading().contains("第二卷") }
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        rule.waitForIdle()
        Thread.sleep(1_000)
        val next = heading()
        Log.i("NovelStart", "next=$next")
        assertTrue(next, next.contains("第二卷"))
        shot(device, "02-next-chapter")

        device.click(device.displayWidth / 2, device.displayHeight / 2)
        rule.waitForIdle()
        rule.onNodeWithText("Previous").performClick()
        rule.waitUntil(timeoutMillis = 20_000) { heading().contains("第一卷") }
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        rule.waitForIdle()
        val back = heading()
        Log.i("NovelStart", "back=$back")
        assertTrue(back, back.contains("第一卷"))
        shot(device, "03-back-to-previous")

        device.pressBack()
        rule.waitForIdle()
        Thread.sleep(1_000)
        rule.onAllNodesWithText("聊齋志異", substring = true)[0].performClick()
        rule.waitUntil(timeoutMillis = 20_000) { heading().contains("第一卷") }
        val again = heading()
        Log.i("NovelStart", "reenter=$again")
        assertTrue(again, again.contains("第一卷"))
        shot(device, "04-reenter")
    }

    private fun heading(): String {
        val node = rule.onNodeWithTag("chapter_heading").fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }

    private fun shot(device: UiDevice, name: String) {
        val dir = File("/sdcard/Download")
        dir.mkdirs()
        val file = File(dir, "noveltoon-$name.png")
        device.takeScreenshot(file)
    }
}
