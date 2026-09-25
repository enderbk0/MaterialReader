package com.enderbk.materialreader.navigation

import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.enderbk.materialreader.data.DocumentEntry

/**
 * Navigation routes. The reader takes either a library [docId] or a raw
 * content [uri] (external VIEW/SEND intents), so cold-starting from another
 * app works without touching the library first.
 */
object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val READER = "reader"
    const val ARG_DOC_ID = "docId"
    const val ARG_URI = "uri"

    const val READER_PATTERN = "reader?docId={docId}&uri={uri}"

    fun readerForDocument(id: String): String = "reader?docId=$id"

    fun readerForUri(uri: String): String {
        val id = DocumentEntry.idForUri(uri)
        return "reader?docId=$id&uri=${Uri.encode(uri)}"
    }

    val readerArguments = listOf(
        navArgument(ARG_DOC_ID) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument(ARG_URI) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )
}
