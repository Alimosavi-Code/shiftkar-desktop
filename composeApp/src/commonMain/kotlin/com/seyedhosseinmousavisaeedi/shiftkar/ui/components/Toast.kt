package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import kotlinx.coroutines.delay

/**
 * Top-center toast host — sonner parity (position top-center).
 * Usage: ToastHost(state) rendered above all content in the root box.
 */

object ToastCenter {
    var message by mutableStateOf<String?>(null)
        private set

    private var seq = 0
    private var seqState by mutableStateOf(0)

    fun show(text: String) {
        message = text
        seq++
        seqState = seq
    }

    internal val currentSeq: Int get() = seqState
}

@Composable
fun ToastHost() {
    val msg = ToastCenter.message
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(ToastCenter.currentSeq) {
        if (msg != null) {
            visible = true
            delay(2600)
            visible = false
            delay(250)
            if (!visible) ToastCenter.clearForAnimationDone()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && msg != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            val sk = LocalSk.current
            Text(
                msg.orEmpty(),
                Modifier
                    .padding(top = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(sk.sheetSolid.copy(alpha = 0.97f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = sk.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX
            )
        }
    }
}

private fun ToastCenter.clearForAnimationDone() {
    // keep the last message; cleared on next show — no-op placeholder for symmetry
}
