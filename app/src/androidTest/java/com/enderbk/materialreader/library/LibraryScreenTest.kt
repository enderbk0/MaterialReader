package com.enderbk.materialreader.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enderbk.materialreader.FakeMetaSource
import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.InMemoryDocumentStore
import com.enderbk.materialreader.data.InMemorySettingsStore
import com.enderbk.materialreader.ui.theme.MaterialReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun content(
        documents: InMemoryDocumentStore,
        onOpenReader: (String) -> Unit = {}
    ) {
        compose.setContent {
            MaterialReaderTheme {
                LibraryScreen(
                    documents = documents,
                    settings = InMemorySettingsStore(),
                    meta = FakeMetaSource(),
                    onOpenReader = onOpenReader,
                    onOpenSettings = {}
                )
            }
        }
    }

    @Test
    fun emptyStateExplainsPrivacyAndOffersOpenPdf() {
        content(InMemoryDocumentStore())

        compose.onNodeWithText("No PDFs yet").assertIsDisplayed()
        compose.onNodeWithText("nothing is uploaded, ever", substring = true).assertIsDisplayed()
        // FAB + empty-state button both offer the primary action.
        compose.onAllNodesWithText("Open PDF").fetchSemanticsNodes().let {
            assert(it.size >= 2) { "Expected FAB and empty-state Open PDF actions" }
        }
    }

    @Test
    fun documentsAppearUnderPinnedAndRecentSections() {
        val store = InMemoryDocumentStore(
            listOf(
                DocumentEntry("1", "u1", "Annual Report.pdf", pageCount = 12, pinned = true),
                DocumentEntry("2", "u2", "Notes.pdf", pageCount = 3)
            )
        )
        content(store)

        compose.onNodeWithText("Pinned").assertIsDisplayed()
        compose.onNodeWithText("Annual Report.pdf").assertIsDisplayed()
        compose.onNodeWithText("Notes.pdf").assertIsDisplayed()
    }
}
