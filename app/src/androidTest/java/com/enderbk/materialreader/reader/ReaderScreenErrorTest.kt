package com.enderbk.materialreader.reader

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enderbk.materialreader.FakeBackend
import com.enderbk.materialreader.data.InMemoryDocumentStore
import com.enderbk.materialreader.data.InMemorySettingsStore
import com.enderbk.materialreader.data.ReaderBackground
import com.enderbk.materialreader.ui.theme.MaterialReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Primary reading-flow UI states: an unknown document must surface a
 * human-readable error with retry/back actions instead of a blank screen.
 */
@RunWith(AndroidJUnit4::class)
class ReaderScreenErrorTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unknownDocumentShowsReadableError() {
        compose.setContent {
            MaterialReaderTheme {
                ReaderScreen(
                    docId = "does-not-exist",
                    rawUri = null,
                    documents = InMemoryDocumentStore(),
                    settings = InMemorySettingsStore(),
                    backend = FakeBackend(),
                    keepScreenAwake = false,
                    readerBackground = ReaderBackground.DEFAULT,
                    darkTheme = false,
                    onBack = {},
                )
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Couldn't open this PDF")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Back to library").assertExists()
    }
}
