package com.seyedhosseinmousavisaeedi.shiftkar.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.seyedhosseinmousavisaeedi.shiftkar.platform.triggerPlatformHaptic

/**
 * Multiplatform Haptic helper — triggers native platform haptics on Android & iOS
 * and is a safe no-op on Desktop.
 */
class Haptics(private val enabled: Boolean = true) {
    fun tick(ms: Int = 8) {
        if (!enabled) return
        runCatching { triggerPlatformHaptic(ms) }
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics = remember(enabled) { Haptics(enabled) }
