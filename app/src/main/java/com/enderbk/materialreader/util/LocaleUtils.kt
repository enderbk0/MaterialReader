package com.enderbk.materialreader.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies a per-app language tag ("" = system default) through every layer:
 *
 * - Framework [LocaleManager] on API 33+: this is exactly what the system
 *   Settings → App info → Language UI reads, so forcing here keeps it in
 *   agreement instead of showing a stale "System default".
 * - [AppCompatDelegate]: backports the choice below API 33 and recreates the
 *   activity where supported.
 *
 * Both calls are idempotent; failures are contained (a failed apply simply
 * leaves the current locale, never a crash).
 */
fun applyAppLanguage(activity: Activity, tag: String) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isBlank()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
        }
    }
    runCatching {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
        )
    }
}

/**
 * Full process restart onto the launcher activity: the only deterministic
 * way to re-read the stored locale everywhere. Used after a language change
 * is confirmed.
 */
fun restartApp(context: Context) {
    val intent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } ?: return
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

/** Primary language subtag of a configuration ("" when absent). */
fun systemPrimaryLanguage(config: Configuration): String {
    if (config.locales.isEmpty) return ""
    return config.locales.get(0)?.language.orEmpty()
}

/**
 * True when a stored in-app override must yield to a system-side change:
 * the user picked a language in Android Settings → App info while we held
 * a different forced tag. Clearing the override hands control back to the
 * system. Pure logic, unit-tested.
 */
fun shouldFollowSystemLocale(storedTag: String, systemLanguage: String): Boolean =
    storedTag.isNotBlank() && systemLanguage.isNotBlank() && systemLanguage != storedTag
