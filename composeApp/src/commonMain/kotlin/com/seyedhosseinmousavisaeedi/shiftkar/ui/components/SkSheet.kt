package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import kotlinx.coroutines.launch

private val SheetEnterEase = CubicBezierEasing(0.22f, 0.8f, 0.36f, 1f)

@Composable
fun SkSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    expandable: Boolean = true,
    bodyScroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    var alive by remember { mutableStateOf(visible) }
    if (visible && !alive) alive = true

    var fullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            fullscreen = false
        } else {
            kotlinx.coroutines.delay(270)
            alive = false
        }
    }

    if (!alive) return

    val sk = LocalSk.current
    val density = LocalDensity.current
    val screenH = 820
    val collapsedH = (screenH * 0.86f).dp
    val dark = sk.sheetSolid.luminance() < 0.5f

    val fsP by animateFloatAsState(
        targetValue = if (fullscreen) 1f else 0f,
        animationSpec = tween(260, easing = SheetEnterEase),
        label = "sheetFs"
    )
    val maxH = collapsedH + ((screenH.dp - collapsedH) * fsP)
    val radiusDp = 26f * (1f - fsP)

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx > 0 && expandable && !fullscreen) fullscreen = true
    }

    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }

    LaunchedEffect(expandable) {
        snapshotFlow { bodyScroll.value to bodyScroll.maxValue }
            .collect { (v, max) ->
                if (!fullscreen && expandable && max > 0 && v >= max - 6) {
                    fullscreen = true
                }
            }
    }

    var sheetBounds by remember { mutableStateOf(Rect.Zero) }

    Box(Modifier.fillMaxSize()) {
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
                            if (sheetBounds != Rect.Zero && !sheetBounds.contains(pos)) onDismiss()
                        }
                    }
            )
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(260, easing = SheetEnterEase)
                ) { it },
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
                        .background(sk.sheetSolid)
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
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(sk.specular.copy(alpha = 0.12f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height * 0.22f
                                )
                            )
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { }
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
                    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    Spacer(Modifier.height(lerp(8.dp, statusTop, fsP)))

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
                        SheetButton(icon = SkIcons.Close, desc = "بستن") { onDismiss() }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(bodyScroll)
                            .padding(bottom = 8.dp),
                        content = content
                    )

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
            .glass(RoundedCornerShape(12.dp))
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size(17.dp), tint = sk.textMuted)
    }
}
