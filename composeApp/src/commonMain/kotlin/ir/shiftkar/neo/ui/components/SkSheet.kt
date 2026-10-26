package ir.shiftkar.neo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import ir.shiftkar.neo.ui.theme.IRANSansX
import ir.shiftkar.neo.ui.theme.LocalSk
import kotlinx.coroutines.launch

/**
 * Sheet — native port of the original ShiftKar bottom sheet (sheet.tsx),
 * v2.2.3 interaction overhaul (owner's spec):
 *
 *  - ENTRANCE/EXIT: sheet slides up from the bottom edge (0.26s, the PWA's
 *    cubic-bezier(.22,.8,.36,1)); the scrim cross-fades (0.22s). The composable
 *    lingers through the exit animation (PWA `alive` pattern) — callers keep it
 *    composed and just flip `visible`.
 *  - PEEK (minimized, ≤86dvh): NO header buttons at all — only the drag
 *    handle («نوارک»); close = swipe the handle down, back button, or a tap
 *    outside. The old ✕/ ChevronUp pair is gone (owner's request).
 *  - FULLSCREEN: only the ✕ (close). No collapse chevron again.
 *  - Swipe UP on the handle/header → fullscreen; scrolling the body to its
 *    very end ALSO expands (PWA parity). Swipe DOWN on the handle → peek.
 *  - BACK BUTTON: fullscreen → minimize (the animated height collapse), peek
 *    → dismiss. (Owner: «back = minimize with a beautiful animation».)
 *  - KEYBOARD: opening the IME automatically goes fullscreen so the note
 *    editor gets the whole screen.
 *  - Drag follow + release: the sheet tracks the finger (clamped, the PWA's
 *    170px rule) and springs back on release.
 *  - Dismiss ONLY from the backdrop/outside tap — taps on the sheet body
 *    are consumed and never reach the scrim (web parity).
 *  - Opaque body: the PWA relies on backdrop-filter blur behind the sheet;
 *    Compose has no backdrop blur, so the body paints an opaque underlay
 *    with the frosted tint on top — no sharp content bleeding through.
 */

/* v2.2.3 PWA entrance curve: cubic-bezier(0.22, 0.8, 0.36, 1) */
private val SheetEnterEase = CubicBezierEasing(0.22f, 0.8f, 0.36f, 1f)

@Composable
fun SkSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    expandable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    /* ---- alive-linger: stay composed through the exit animation ---- */
    var alive by remember { mutableStateOf(visible) }
    if (visible && !alive) alive = true

    var fullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            fullscreen = false // fresh open always starts peeked
        } else {
            kotlinx.coroutines.delay(270) // > exit duration (240ms)
            alive = false
        }
    }

    if (!alive) return

    val sk = LocalSk.current
    val density = LocalDensity.current

    /* v2.2.5 (مالک: «Make it that the background numbers and words are not
     * visible at all! And make a gradient for it like the home screen!»):
     * بدنهٔ پنجره کاملاً مات است — هیچ متن، عدد یا شکلِ پس‌زمینه از پشتش دیده
     * نمی‌شود — و به‌جای شیشهٔ شفاف، گرادیانی مثل هالهٔ صفحهٔ اصلی دارد. */
    val dark = sk.sheetSolid.luminance() < 0.5f

    /* ---- peek ↔ fullscreen progress (animated height + radius) ---- */
    val fsP by animateFloatAsState(
        targetValue = if (fullscreen) 1f else 0f,
        animationSpec = tween(260, easing = SheetEnterEase),
        label = "sheetFs"
    )
    val radiusDp = 26f * (1f - fsP)

    /* ---- keyboard open → fullscreen (owner request) ---- */
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx > 0 && expandable && !fullscreen) fullscreen = true
    }

    /* ---- drag follow (PWA: clamp 170px down / 48px up, spring back) ---- */
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }

    /* ---- body scroll state — scroll-to-end expands (PWA onBodyScroll) ---- */
    val bodyScroll = rememberScrollState()
    LaunchedEffect(expandable) {
        snapshotFlow { bodyScroll.value to bodyScroll.maxValue }
            .collect { (v, max) ->
                if (!fullscreen && expandable && max > 0 && v >= max - 6) {
                    fullscreen = true
                }
            }
    }

    /* مرزهای فعلی شیت — برای تشخیص هندسی «ضربهٔ بیرون» */
    var sheetBounds by remember { mutableStateOf(Rect.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight.value
        val collapsedH = (screenH * 0.86f).dp
        val maxH = collapsedH + ((screenH.dp - collapsedH) * fsP)
        /* ---- backdrop (dismiss area) — cross-fades with open/close ----
         * 0x9E081024 ≈ rgba(8,16,36,0.62): the PWA overlays rgba(8,12,26,0.45)
         * PLUS backdrop-blur(7px); without blur the extra opacity reproduces the
         * same soft, low-contrast look instead of sharp content bleeding through.
         * Dismiss is GEOMETRY-GUARDED: only a tap OUTSIDE the sheet's real bounds
         * closes it — a tap anywhere on the sheet body can never bubble into a
         * dismissal (web parity: e.target === e.currentTarget). */
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(220))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x9E081024))
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            /* فقط ضربهٔ بیرون از شیت می‌بندد؛ تا وقتی مرزها نامشخص‌اند
                             * (Rect.Zero) هیچ‌چیز بسته نمی‌شود — fail-safe */
                            if (sheetBounds != Rect.Zero && !sheetBounds.contains(pos)) onDismiss()
                        }
                    }
            )
        }

        /* ---- the sheet itself — slides up on open, slides down on close ---- */
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(260, easing = SheetEnterEase)
                ) { it } ,
                exit = slideOutVertically(
                    animationSpec = tween(240, easing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.4f))
                ) { it }
            ) {
                Column(
                    modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { sheetBounds = it.boundsInRoot() }
                        .heightIn(min = maxH * fsP, max = maxH)
                        .graphicsLayer { translationY = drag.value }
                        .clip(RoundedCornerShape(topStart = radiusDp.dp, topEnd = radiusDp.dp))
                        /* v2.2.5: opaque base — ZERO bleed-through of calendar
                         * numbers/words behind the sheet … */
                        .background(sk.sheetSolid)
                        /* … plus a home-screen-like aurora gradient pouring
                         * from the top edge (Frost signature, fully opaque). */
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(
                                        sk.primary.copy(alpha = if (dark) 0.20f else 0.12f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.38f
                                )
                            )
                        }
                        /* specular top sheen — the Frost signature highlight */
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(sk.specular.copy(alpha = 0.12f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height * 0.22f
                                )
                            )
                        }
                        /* consume taps INSIDE the sheet so they never dismiss it */
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* no-op — swallow taps on the sheet body */ }
                        .border(
                            width = 1.dp,
                            color = sk.border,
                            shape = RoundedCornerShape(topStart = radiusDp.dp, topEnd = radiusDp.dp)
                        )
                        .pointerInput(fullscreen, expandable) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    val min = if (fullscreen) 0f else -60f
                                    val max = if (fullscreen) 400f else 170f
                                    val next = (drag.value + amount).coerceIn(min, max)
                                    scope.launch { drag.snapTo(next) }
                                },
                                onDragEnd = {
                                    val v = drag.value
                                    when {
                                        v > 90 -> if (fullscreen) fullscreen = false else onDismiss()
                                        v < -24 && expandable && !fullscreen -> fullscreen = true
                                    }
                                    scope.launch {
                                        drag.animateTo(
                                            0f,
                                            spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        drag.animateTo(
                                            0f,
                                            spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    /* edge-to-edge: fullscreen content must clear the status bar/notch;
                     * both modes must clear the gesture navigation bar — the top
                     * spacer morphs smoothly between the two states.
                     * v2.2.4: chrome tightened (owner: «the cross could be a bit
                     * higher! and the navbar too! in a safe position») — the handle
                     * now sits immediately under the status-bar inset. */
                    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    Spacer(Modifier.height(lerp(8.dp, statusTop, fsP)))

                    // handle — the only «navbar thing» left; tap toggles fullscreen
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(100))
                            .background(sk.textFaint.copy(alpha = 0.45f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                if (expandable) fullscreen = !fullscreen
                            }
                    )

                    // header — peek shows NO buttons (owner request);
                    // fullscreen shows ONLY the close ✕
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                            .heightIn(min = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            title,
                            Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = IRANSansX,
                            color = sk.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fullscreen) {
                            SheetButton(icon = SkIcons.Close, desc = "بستن") { onDismiss() }
                        }
                    }

                    // body
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(bodyScroll)
                            .padding(bottom = 8.dp),
                        content = content
                    )

                    /* bottom inset — the LARGER of navigation bar and keyboard
                     * (union = per-edge max): with the IME open the sheet body shrinks
                     * above the keyboard, so the note editor stays visible and the
                     * focused text field scrolls into view (adjustResize is a no-op
                     * under edge-to-edge — insets must be consumed here). */
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars
                                .union(WindowInsets.ime)
                                .asPaddingValues()
                                .calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SheetButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    val sk = LocalSk.current
    Box(
        Modifier
            .size(36.dp)
            .glass(RoundedCornerShape(12.dp)) /* v2.2.4: شیشه‌ای مثل بقیهٔ سطوح */
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size(17.dp), tint = sk.textMuted)
    }
}
