package com.enderbk.materialreader.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enderbk.materialreader.ui.theme.MaterialReaderTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcomeShowsFossPromiseAndContinues() {
        var continued = false
        compose.setContent {
            MaterialReaderTheme {
                WelcomeScreen(onContinue = { continued = true })
            }
        }

        compose.onNodeWithText("Welcome to MaterialReader").assertIsDisplayed()
        compose.onNodeWithText("Get started").assertIsDisplayed()
        compose.onNodeWithText("Get started").performClick()
        assertTrue(continued)
    }
}
