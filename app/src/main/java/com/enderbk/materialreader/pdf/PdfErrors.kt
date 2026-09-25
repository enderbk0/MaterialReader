package com.enderbk.materialreader.pdf

import java.io.FileNotFoundException

/**
 * Human-meaningful PDF open failures. The UI maps these to plain-language
 * messages; nothing here is ever uploaded anywhere.
 */
sealed interface PdfOpenFailure {
    data object FileNotFound : PdfOpenFailure
    data object PasswordProtected : PdfOpenFailure
    data object CorruptOrUnsupported : PdfOpenFailure
    data object PermissionLost : PdfOpenFailure
    data class Unknown(val debugDetail: String) : PdfOpenFailure
}

class PdfOpenException(val failure: PdfOpenFailure, cause: Throwable? = null) :
    Exception(failure.toString(), cause)

/** Maps a raw exception to a [PdfOpenFailure]. Pure function, unit-tested. */
fun mapOpenError(error: Throwable): PdfOpenFailure {
    val message = (error.message ?: "") + " " + (error.cause?.message ?: "")
    return when {
        error is PdfOpenException -> error.failure
        error is FileNotFoundException -> PdfOpenFailure.FileNotFound
        error is SecurityException -> PdfOpenFailure.PermissionLost
        message.contains("password", ignoreCase = true) ||
            message.contains("encrypted", ignoreCase = true) ||
            message.contains("decrypt", ignoreCase = true) -> PdfOpenFailure.PasswordProtected
        message.contains("permission", ignoreCase = true) ||
            message.contains("grant", ignoreCase = true) -> PdfOpenFailure.PermissionLost
        else -> PdfOpenFailure.CorruptOrUnsupported
    }
}

/** Plain-language message shown when a PDF cannot be opened. */
fun PdfOpenFailure.userMessage(displayName: String): String = when (this) {
    PdfOpenFailure.FileNotFound ->
        "\"$displayName\" could not be found. It may have been moved, renamed, or deleted."
    PdfOpenFailure.PasswordProtected ->
        "\"$displayName\" is password protected. MaterialReader cannot open encrypted PDFs yet."
    PdfOpenFailure.CorruptOrUnsupported ->
        "\"$displayName\" could not be opened. The file may be damaged or use features this reader does not support yet."
    PdfOpenFailure.PermissionLost ->
        "MaterialReader no longer has permission to read \"$displayName\". Please open it again with \"Open PDF\"."
    is PdfOpenFailure.Unknown ->
        "\"$displayName\" could not be opened ($debugDetail)."
}
