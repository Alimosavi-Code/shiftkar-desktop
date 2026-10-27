package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding.TourRegistry

/**
 * Registers a composable's live LayoutCoordinates and root bounds under a tour key
 * so TourOverlay can compute exact relative coordinates regardless of header bars,
 * window resizing, or DPI scaling.
 */
fun Modifier.tourTarget(key: String): Modifier = composed {
    DisposableEffect(key) {
        onDispose {
            TourRegistry.coords.remove(key)
        }
    }
    this.onGloballyPositioned { c ->
        if (c.isAttached) {
            TourRegistry.coords[key] = c
            TourRegistry.bounds[key] = c.boundsInRoot()
            TourRegistry.version += 1
        }
    }
}
