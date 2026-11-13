package com.movtery.zalithlauncher.game.rust

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.ui.components.FloatingBall
import com.movtery.zalithlauncher.ui.components.MenuState
import kotlin.math.roundToInt

/**
 * "Forge": the look of the Rust Edition's in-game menu, its own and not the launcher's.
 *
 * It is a sheet of dark iron over the game: cut corners instead of round ones, a rust-orange
 * edge that lights up for what is on, numbered sections and monospaced figures like a spec
 * plate. It is always dark (it sits over the game, whatever the launcher's theme is), and it
 * has no cards inside cards: a row is a plate, and its left edge says whether it is on.
 */
internal object Forge {
    val Iron = Color(0xFF12100E)
    val Plate = Color(0xFF1E1916)
    val PlateHigh = Color(0xFF2B2420)
    val Line = Color(0xFF3D332C)
    val Rust = Color(0xFFE0632A)
    val Ember = Color(0xFFFFA866)
    val Ink = Color(0xFFF4EBE2)
    val Muted = Color(0xFFA8998C)
    val Danger = Color(0xFFE5484D)

    val PanelShape = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
    val PlateShape = CutCornerShape(topEnd = 9.dp)
    val ChipShape = CutCornerShape(4.dp)

    /** Figures, tags and section numbers */
    val Mono = FontFamily.Monospace

    val Scheme = darkColorScheme(
        primary = Rust,
        onPrimary = Iron,
        primaryContainer = Color(0xFF5A2A12),
        onPrimaryContainer = Ember,
        secondary = Ember,
        onSecondary = Iron,
        tertiary = Ember,
        onTertiary = Iron,
        background = Iron,
        onBackground = Ink,
        surface = Iron,
        onSurface = Ink,
        surfaceVariant = PlateHigh,
        onSurfaceVariant = Muted,
        surfaceContainer = Plate,
        surfaceContainerHigh = PlateHigh,
        surfaceContainerHighest = PlateHigh,
        outline = Line,
        outlineVariant = Line,
        error = Danger,
        onError = Ink
    )
}

/** The Forge colours for anything drawn inside (the menu, the ball, the HUD editor) */
@Composable
internal fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Forge.Scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        CompositionLocalProvider(LocalContentColor provides Forge.Ink, content = content)
    }
}

/**
 * The menu's sheet, on the trailing side of the screen. A tap beside it closes it.
 */
@Composable
internal fun ForgePanel(
    state: MenuState,
    closeScreen: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val visible = state == MenuState.SHOW
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = closeScreen
                    )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(fraction = 0.38f)
                .padding(top = 10.dp, end = 10.dp, bottom = 10.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(140)) + slideInHorizontally(tween(180)) { if (isRtl) -it / 3 else it / 3 },
                exit = fadeOut(tween(120)) + slideOutHorizontally(tween(140)) { if (isRtl) -it / 3 else it / 3 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(Forge.PanelShape)
                        .background(Forge.Iron.copy(alpha = 0.94f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(listOf(Forge.Rust, Forge.Line, Forge.Line)),
                            shape = Forge.PanelShape
                        )
                        // the sheet takes its own taps: they do not close it
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {}
                        ),
                    content = content
                )
            }
        }
    }
}

/**
 * The top of a page: the mark, the page's name, and the way back when it is not the first page.
 */
@Composable
internal fun ForgeHeader(title: String, trail: String? = null, onBack: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 12.dp, end = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(Forge.ChipShape)
                        .background(Forge.PlateHigh)
                        .border(1.dp, Forge.Rust, Forge.ChipShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "<",
                        color = Forge.Ember,
                        style = TextStyle(fontFamily = Forge.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (trail == null) "RUST CLIENT" else trail.uppercase(),
                    color = Forge.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Forge.Mono, fontSize = 10.sp, letterSpacing = 2.sp)
                )
                Text(
                    text = title.uppercase(),
                    color = Forge.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = Forge.Mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                )
            }
            // the mark: an ember in an iron frame
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(1.dp, Forge.Line, Forge.ChipShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(6.dp).background(Forge.Rust))
            }
        }
        // a weld seam: a short hot stretch, then cold iron
        Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            Box(modifier = Modifier.width(64.dp).fillMaxHeight().background(Forge.Rust))
            Box(modifier = Modifier.width(12.dp).fillMaxHeight().background(Forge.Ember))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Forge.Line))
        }
    }
}

/** A numbered section: "02  CONTROLS ————", and a line of small print under it when given */
@Composable
internal fun ForgeSection(index: Int, title: String, note: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = index.toString().padStart(2, '0'),
                color = Forge.Rust,
                style = TextStyle(fontFamily = Forge.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            )
            Text(
                text = title.uppercase(),
                color = Forge.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = Forge.Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            )
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Forge.Line))
        }
        if (note != null) {
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = note,
                color = Forge.Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * A plate: what every row of the menu is. Its leading edge is lit when [lit] (the row is on,
 * or it is the chosen one).
 */
@Composable
private fun ForgePlate(
    modifier: Modifier,
    lit: Boolean,
    enabled: Boolean,
    edge: Color = Forge.Rust,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bar = if (lit) edge else Forge.Line
    var plate = modifier
        .heightIn(min = 44.dp)
        .alpha(if (enabled) 1f else 0.45f)
        .clip(Forge.PlateShape)
        .background(if (lit) Forge.PlateHigh else Forge.Plate)
        .drawBehind {
            val wide = 3.dp.toPx()
            drawRect(
                color = bar,
                topLeft = Offset(if (isRtl) size.width - wide else 0f, 0f),
                size = Size(wide, size.height)
            )
        }
    if (onClick != null) {
        plate = plate.clickable(enabled = enabled, onClick = onClick)
    }
    Box(modifier = plate, contentAlignment = Alignment.CenterStart) {
        content()
    }
}

/** The text of a row: its name, and small print under it when given */
@Composable
private fun ForgeLabel(modifier: Modifier, text: String, note: String?, color: Color = Forge.Ink) {
    Column(modifier = modifier) {
        Text(
            text = text,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall
        )
        if (note != null) {
            Text(
                text = note,
                color = Forge.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * A row that does something. [mark] is the figure at its end: ">" opens a page, "+" and "-"
 * unfold and fold, none for a plain action. [danger] is for what ends the game.
 */
@Composable
internal fun ForgeButton(
    modifier: Modifier = Modifier,
    text: String,
    note: String? = null,
    mark: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit = {}
) {
    ForgePlate(
        modifier = modifier,
        lit = danger,
        enabled = enabled,
        edge = if (danger) Forge.Danger else Forge.Rust,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ForgeLabel(
                modifier = Modifier.weight(1f),
                text = text,
                note = note,
                color = if (danger) Forge.Danger else Forge.Ink
            )
            if (mark != null) {
                Text(
                    text = mark,
                    color = Forge.Rust,
                    style = TextStyle(fontFamily = Forge.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                )
            }
        }
    }
}

/** A row with a switch: a square slug in a slot, hot when on */
@Composable
internal fun ForgeSwitch(
    modifier: Modifier = Modifier,
    text: String,
    switch: Boolean,
    onSwitch: (Boolean) -> Unit,
    note: String? = null,
    enabled: Boolean = true
) {
    ForgePlate(modifier = modifier, lit = switch, enabled = enabled, onClick = { onSwitch(!switch) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ForgeLabel(modifier = Modifier.weight(1f), text = text, note = note)
            Text(
                text = if (switch) "ON" else "OFF",
                color = if (switch) Forge.Ember else Forge.Muted,
                style = TextStyle(fontFamily = Forge.Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            )
            val at by animateDpAsState(
                targetValue = if (switch) 20.dp else 2.dp,
                animationSpec = tween(120),
                label = "forge switch"
            )
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 20.dp)
                    .clip(Forge.ChipShape)
                    .background(if (switch) Forge.Rust else Forge.Iron)
                    .border(1.dp, if (switch) Forge.Ember else Forge.Line, Forge.ChipShape),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = at)
                        .size(16.dp)
                        .clip(Forge.ChipShape)
                        .background(if (switch) Forge.Iron else Forge.Muted)
                )
            }
        }
    }
}

/** A row with a slider; its figure is shown at the end of its name */
@Composable
internal fun ForgeSlider(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChangeFinished: (Int) -> Unit = {},
    suffix: String? = null
) {
    val latest by rememberUpdatedState(value)
    ForgePlate(modifier = modifier, lit = false, enabled = enabled, onClick = null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, end = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ForgeLabel(modifier = Modifier.weight(1f), text = title, note = null)
                Text(
                    text = "$value${suffix ?: ""}",
                    color = Forge.Ember,
                    style = TextStyle(fontFamily = Forge.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                )
            }
            Slider(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                value = value.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { onValueChange(it.roundToInt()) },
                onValueChangeFinished = { onValueChangeFinished(latest) },
                valueRange = valueRange,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = Forge.Ember,
                    activeTrackColor = Forge.Rust,
                    inactiveTrackColor = Forge.Line,
                    disabledThumbColor = Forge.Muted,
                    disabledActiveTrackColor = Forge.Muted,
                    disabledInactiveTrackColor = Forge.Line
                )
            )
        }
    }
}

/** A row that unfolds into its choices; the chosen one is named on the row */
@Composable
internal fun <E> ForgeList(
    modifier: Modifier = Modifier,
    title: String,
    items: List<E>,
    currentItem: E,
    onItemChange: (E) -> Unit,
    getItemText: (E) -> String
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ForgePlate(modifier = Modifier.fillMaxWidth(), lit = open, enabled = true, onClick = { open = !open }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Forge.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = getItemText(currentItem),
                        color = Forge.Ember,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = if (open) "-" else "+",
                    color = Forge.Rust,
                    style = TextStyle(fontFamily = Forge.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                )
            }
        }
        if (open) {
            items.forEach { item ->
                val chosen = item == currentItem
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                        .heightIn(min = 34.dp)
                        .background(if (chosen) Forge.PlateHigh else Forge.Plate.copy(alpha = 0.6f))
                        .clickable {
                            onItemChange(item)
                            open = false
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (chosen) Forge.Rust else Forge.Line)
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = getItemText(item),
                        color = if (chosen) Forge.Ember else Forge.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/** A line of figures, like "3 OF 18 ON" */
@Composable
internal fun ForgeReadout(text: String) {
    Text(
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        text = text.uppercase(),
        color = Forge.Muted,
        style = TextStyle(fontFamily = Forge.Mono, fontSize = 11.sp, letterSpacing = 1.sp)
    )
}

/**
 * The floating menu button: a cut iron tag with the client's "R", which turns into a cross
 * while the menu is open. Dragged and remembered like the launcher's.
 */
@Composable
internal fun ForgeBall(
    position: Offset,
    onPositionChanged: (Offset) -> Unit,
    onSavePos: () -> Unit,
    opened: Boolean,
    alpha: Float,
    onClick: () -> Unit
) {
    val shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    FloatingBall(
        modifier = Modifier.focusProperties { canFocus = false },
        position = position,
        onPositionChanged = onPositionChanged,
        onSavePos = onSavePos,
        onClick = onClick,
        alpha = alpha,
        color = Forge.Iron.copy(alpha = 0.82f),
        contentColor = Forge.Ink,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(1.5.dp, if (opened) Forge.Ember else Forge.Rust, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (opened) "X" else "R",
                color = if (opened) Forge.Ember else Forge.Rust,
                style = TextStyle(fontFamily = Forge.Mono, fontWeight = FontWeight.Black, fontSize = 20.sp)
            )
            Spacer(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 5.dp)
                    .size(4.dp)
                    .background(Forge.Ember)
            )
        }
    }
}
