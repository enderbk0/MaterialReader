package com.enderbk.materialreader.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side navigation check: external URIs survive route encoding. */
@RunWith(AndroidJUnit4::class)
class ReaderRouteDeviceTest {

    @Test
    fun externalUriIsEncodedIntoReaderRoute() {
        val uri = "content://com.example.provider/document/my file.pdf"
        val route = Routes.readerForUri(uri)
        assertTrue(route.startsWith("reader?"))
        assertTrue(route.contains("uri="))
        assertFalse(route.contains(" "))
        assertTrue(route.contains("my%20file.pdf"))
    }
}
