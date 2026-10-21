package com.jm.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.ui.category.CategoriesScreen
import com.jm.reader.ui.home.HomeScreen
import com.jm.reader.ui.library.LibraryScreen
import com.jm.reader.ui.member.MemberScreen
import com.jm.reader.ui.theme.AppMotion
import com.jm.reader.ui.theme.ArchiveMotion
import com.jm.reader.ui.theme.AppTheme
import com.jm.reader.ui.theme.GlassBar
import com.jm.reader.ui.theme.LocalAppTheme
import com.jm.reader.ui.theme.GlassCapsule
import com.jm.reader.ui.theme.GlassKind
import com.jm.reader.ui.theme.GlassSizes
import com.jm.reader.ui.theme.glassClickable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Vertical space the floating navigation pill occupies, reserved by scrolling content. */
val BottomNavReserve = 96.dp

/**
 * Tab shell.
 *
 * The page content is recorded into a [GraphicsLayer] and the navigation bar is drawn as a
 * **sibling overlay outside that recording**, so the bar can sample and blur the live page behind
 * it (the "liquid glass" look of the skill's glass bottom bar) without sampling itself.
 */
@Composable
fun MainScreen(navController: NavHostController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pageLayer = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize()) {
        // The recording box starts at the Compose root origin, so a glass pane's
        // `positionInRoot` maps 1:1 onto the recorded layer when it samples it.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    pageLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(pageLayer)
                },
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                when (selectedTab) {
                    0 -> HomeScreen(navController, Modifier.fillMaxSize())
                    1 -> CategoriesScreen(navController, Modifier.fillMaxSize())
                    2 -> LibraryScreen(navController, Modifier.fillMaxSize())
                    else -> MemberScreen(navController, Modifier.fillMaxSize())
                }
            }
        }

        GlassBottomNav(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            backdropLayer = pageLayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * The reference's floating dock, ported.
 *
 * A fixed capsule of four 62 dp bays in which every destination keeps its icon *and* its label.
 * The previous version captioned only the selected tab, which read as a toolbar rather than as the
 * dock Wenku8 ships. Selection is a continuous position, not a boolean: the caption turns bold,
 * the tint lerps toward ink as the bay approaches the finger, and a 12 x 1.5 dp rule under it
 * fades in with proximity. Dragging across the capsule previews the target and commits on release;
 * the whole capsule dips for 120 ms on contact, the same press language as the rest of the app.
 */
@Composable
private fun GlassBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdropLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val items = listOf(
        s.home to Icons.Filled.Home,
        s.categories to Icons.Filled.Category,
        s.library to Icons.Filled.LibraryBooks,
        s.member to Icons.Filled.Person,
    )
    val n = items.size
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val itemWidth = 62.dp
    val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }

    val position = remember { Animatable(selected.toFloat()) }
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // The gesture detector runs in a restricted coroutine scope and may not call `snapTo`, so the
    // finger writes a plain float and the animation follows it once the drag ends.
    var dragValue by remember { mutableFloatStateOf(selected.toFloat()) }
    val scope = rememberCoroutineScope()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(120),
        label = "dockPress",
    )

    LaunchedEffect(selected) {
        if (!dragging) {
            position.animateTo(
                targetValue = selected.toFloat(),
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
            )
        }
    }

    GlassBar(
        modifier = modifier
            .width(itemWidth * n + 12.dp)
            .height(54.dp)
            .graphicsLayer {
                translationY = -press * 0.6.dp.toPx()
                scaleX = 1f + press * 0.022f
                scaleY = 1f + press * 0.065f
            },
        shape = GlassCapsule,
        kind = GlassKind.BottomBar,
        backdropLayer = backdropLayer,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .pointerInput(n) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        var dragged = false
                        var lastX = down.position.x
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastX = change.position.x
                            if (!dragged && abs(lastX - down.position.x) > viewConfiguration.touchSlop) {
                                dragged = true
                                dragging = true
                            }
                            if (dragged) {
                                dragValue = ((lastX / itemWidthPx) - 0.5f).coerceIn(0f, (n - 1).toFloat())
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        pressed = false
                        if (dragged) {
                            val target = dragValue.roundToInt().coerceIn(0, n - 1)
                            dragging = false
                            scope.launch {
                                // Follow the finger from where it let go, then settle on the
                                // nearest bay. Snapping to the integer first would jump by up to
                                // half a bay before the spring even started.
                                position.snapTo(dragValue)
                                position.animateTo(
                                    targetValue = target.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                                )
                                onSelect(target)
                            }
                        } else {
                            onSelect((down.position.x / itemWidthPx).toInt().coerceIn(0, n - 1))
                        }
                    }
                },
        ) {
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, (label, icon) ->
                    // Distance from the *live* position, so the colours slide with the finger
                    // instead of snapping one bay at a time.
                    val live = if (dragging) dragValue else position.value
                    val proximity = (1f - abs(live - index)).coerceIn(0f, 1f)
                    val tint = lerp(idle, accent, proximity)
                    val active = index == selected
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            // Four bays at 62 dp leave ~54 dp of text; "Categories" at 11 sp
                            // overflows, so long captions drop half a point instead of clipping.
                            fontSize = if (label.length > 6) 9.5.sp else 10.5.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .width(12.dp)
                                .height(1.5.dp)
                                .background(ink.copy(alpha = proximity)),
                        )
                    }
                }
            }
        }
    }
}
