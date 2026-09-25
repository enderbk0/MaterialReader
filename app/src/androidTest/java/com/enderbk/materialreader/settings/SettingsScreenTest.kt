package com.enderbk.materialreader.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enderbk.materialreader.data.InMemorySettingsStore
import com.enderbk.materialreader.ui.theme.MaterialReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsShowsCoreSections() {
        compose.setContent {
            MaterialReaderTheme {
                SettingsScreen(
                    settings = InMemorySettingsStore(),
                    onBack = {},
                    onAboutClick = {},
                    showBack = false
                )
            }
        }

        compose.onNodeWithText("Appearance").assertExists()
        compose.onNodeWithText("System").assertExists()
        compose.onNodeWithText("Dynamic color").assertExists()
        compose.onNodeWithText("Reading").assertExists()
        compose.onNodeWithText("Night mode").assertExists()
        compose.onNodeWithText("Keep screen awake while reading").assertExists()
        compose.onNodeWithText("Remember reading position").assertExists()
        compose.onNodeWithText("Privacy").assertExists()
        compose.onNodeWithText("No internet permission", substring = true).assertExists()
        compose.onNodeWithText("About MaterialReader").assertExists()
    }
}
