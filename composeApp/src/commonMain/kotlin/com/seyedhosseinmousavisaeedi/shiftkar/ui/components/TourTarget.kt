package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding.TourRegistry

/**
 * Registers a composable's window bounds under a tour key so the guided
 * tour can spotlight it (Compose port of the PWA's data-tour attributes).
 */
fun Modifier.tourTarget(key: String): Modifier = composed {
    this.onGloballyPositioned { coords ->
        TourRegistry.bounds[key] = coords.boundsInWindow()
    }
}
