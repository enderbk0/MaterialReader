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

/** String resource (with `%1$s` = display name, `%2$s` = detail) for each failure. */
fun PdfOpenFailure.messageRes(): Int = when (this) {
    PdfOpenFailure.FileNotFound -> com.enderbk.materialreader.R.string.reader_pdf_error_not_found
    PdfOpenFailure.PasswordProtected -> com.enderbk.materialreader.R.string.reader_pdf_error_password
    PdfOpenFailure.CorruptOrUnsupported -> com.enderbk.materialreader.R.string.reader_pdf_error_corrupt
    PdfOpenFailure.PermissionLost -> com.enderbk.materialreader.R.string.reader_pdf_error_permission
    is PdfOpenFailure.Unknown -> com.enderbk.materialreader.R.string.reader_pdf_error_unknown
}

/** Format arguments matching [messageRes]. */
fun PdfOpenFailure.formatArgs(displayName: String): Array<out Any> = when (this) {
    is PdfOpenFailure.Unknown -> arrayOf(displayName, debugDetail)
    else -> arrayOf(displayName)
}
