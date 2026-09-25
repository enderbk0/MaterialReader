package com.enderbk.materialreader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Expressive-feel motion shared across the app.
 *
 * Material 3's own [MotionScheme][androidx.compose.material3.MaterialTheme.motionScheme]
 * is internal in the stable 1.4.0 release, so these equivalents are built on
 * stable animation-core APIs: a gently bouncy spring for spatial movement
 * (position/size/shape morphs) and quick fades for effects — the same roles
 * the official motion guidance assigns to spatial vs. effects specs.
 */
fun <T> expressiveSpatial() = spring<T>(
    // Low bounce: settles quickly on low-end devices while keeping the
    // playful expressive feel (MediumBouncy oscillates too long here).
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium
)

fun <T> expressiveEffects(durationMillis: Int = 200) = tween<T>(durationMillis)
