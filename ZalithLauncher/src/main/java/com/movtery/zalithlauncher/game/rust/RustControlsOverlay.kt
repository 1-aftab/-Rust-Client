/*
 * Rust Client
 * An unofficial modified version of Zalith Launcher 2.
 * Zalith Launcher 2 is Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.rust

import io.github.juanluci.starling.EngineCatalog
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.bridge.CursorShape
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.MouseControlMode
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.setting.unit.floatRange
import com.movtery.zalithlauncher.ui.components.MenuListLayout
import com.movtery.zalithlauncher.ui.components.MenuSliderLayout
import com.movtery.zalithlauncher.ui.components.MenuState
import com.movtery.zalithlauncher.ui.components.MenuSubscreen
import com.movtery.zalithlauncher.ui.components.MenuSwitchButton
import com.movtery.zalithlauncher.ui.components.MenuTextButton
import com.movtery.zalithlauncher.ui.control.mouse.MousePointer
import com.movtery.zalithlauncher.ui.control.mouse.getMouseFile
import com.movtery.zalithlauncher.ui.control.mouse.mouseFixedPosition
import com.movtery.zalithlauncher.ui.screens.game.elements.DraggableGameBall
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import com.movtery.zalithlauncher.utils.file.ifExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** What the game's activity tells the controls over it */
class RustOverlayState {
    /** The game holds the pointer: a world is played. Otherwise a screen of the game shows */
    var captured by mutableStateOf(false)

    /** Raised when the control layout on disk may have changed (back from the editor) */
    var reload by mutableIntStateOf(0)
}

/**
 * The launcher's on-screen controls over the Rust Edition, the same the Java Edition has:
 * the control layout, the mouse pointer on the game's screens, and the game menu with its
 * floating button, where the layout is chosen and edited.
 *
 * The engine is a `NativeActivity`: it owns its window's surface, so no view can be drawn
 * in that window. The controls therefore live in a window of their own above it. That
 * window never takes the focus, so the keyboard, the back key and the input method stay
 * the game's.
 *
 * It is shown while the game takes the controls' input ([RustInputAdapter.ready]); the
 * screens before the game starts are touched directly.
 */
class RustControlsOverlay(
    activity: Activity,
    private val adapter: RustInputAdapter,
    private val state: RustOverlayState,
    /** The size of the game's window in pixels, which the pointer's place is given in */
    private val gameSize: () -> IntSize,
    private val onEditLayout: (File) -> Unit,
    /** Opens the launcher's download screen for a shelf of the engine's catalog */
    private val onCatalog: (Int) -> Unit,
    private val onQuit: () -> Unit
) : ComponentDialog(activity, android.R.style.Theme_Translucent_NoTitleBar) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(
                // without the focus, and under the input method like the game's window
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            )
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        setContentView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ZalithLauncherTheme {
                        Overlay(adapter, state, gameSize, onEditLayout, onCatalog, onQuit)
                    }
                }
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}

/** A control layout of the launcher and the name it shows by */
private data class LayoutEntry(val file: File, val name: String)

/** The launcher's control layouts, the same the Java Edition lists */
private fun controlLayouts(locale: java.util.Locale): List<LayoutEntry> =
    (PathManager.DIR_CONTROL_LAYOUTS.listFiles() ?: emptyArray())
        .filter { it.isFile && it.extension.equals("json", true) }
        .mapNotNull { file ->
            runCatching {
                val name = loadLayoutFromFile(file).info.name.translate(locale)
                LayoutEntry(file, name.ifBlank { file.nameWithoutExtension })
            }.getOrNull()
        }
        .sortedBy { it.name }

/**
 * The gyroscope reports radians a second; the engine's look takes pixels of mouse movement a
 * sample. This many pixels for one radian a second at 100 % sensitivity (1 is what the Java
 * Edition's gyroscope sends).
 */
private const val GYRO_SCALE = 1f

@Composable
private fun Overlay(
    adapter: RustInputAdapter,
    state: RustOverlayState,
    gameSize: () -> IntSize,
    onEditLayout: (File) -> Unit,
    onCatalog: (Int) -> Unit,
    onQuit: () -> Unit
) {
    val captured = state.captured
    // touched where the finger is, with no pointer; or a touchpad that moves the pointer
    val mode = if (AllSettings.rustTouchScreen.state) MouseControlMode.CLICK else MouseControlMode.SLIDE
    val locale = LocalConfiguration.current.locales[0]

    // the layout chosen in the launcher's settings, the same the Java Edition uses; the first
    // one there is when none was chosen yet
    var chosen by remember { mutableStateOf(AllSettings.controlLayout.getValue()) }
    var layouts by remember { mutableStateOf<List<LayoutEntry>>(emptyList()) }
    var current by remember { mutableStateOf<LayoutEntry?>(null) }
    var layout by remember { mutableStateOf<ObservableControlLayout?>(null) }
    LaunchedEffect(chosen, state.reload) {
        val (found, entry, loaded) = withContext(Dispatchers.IO) {
            val found = controlLayouts(locale)
            val entry = found.find { it.file.name == chosen } ?: found.firstOrNull()
            val loaded = runCatching { entry?.file?.let(::loadLayoutFromFile) }.getOrNull()
                ?: EmptyControlLayout
            Triple(found, entry, loaded)
        }
        layouts = found
        current = entry
        layout = ObservableControlLayout(loaded)
    }

    var menu by remember { mutableStateOf(MenuState.NONE) }
    // "Move HUD": the built-in mods' HUD boxes dragged over the running game
    var hudEditor by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(adapter) {
        adapter.onSwitchMenu = { menu = menu.next() }
        onDispose { adapter.onSwitchMenu = null }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    // where the mouse pointer is, in this window's pixels
    var cursor by remember { mutableStateOf(Offset.Unspecified) }
    // this window and the game's cover the same screen; should their sizes differ, the
    // pointer's place is scaled
    fun toGame(at: Offset): Offset {
        val game = gameSize()
        if (size.width <= 0 || size.height <= 0 || game.width <= 0 || game.height <= 0) return at
        return Offset(at.x * game.width / size.width, at.y * game.height / size.height)
    }
    fun moveCursor(at: Offset) {
        val placed = Offset(
            at.x.coerceIn(0f, size.width.toFloat()),
            at.y.coerceIn(0f, size.height.toFloat())
        )
        cursor = placed
        val game = toGame(placed)
        adapter.cursor(game.x, game.y)
    }
    // the layout's mouse buttons click where the pointer shows
    DisposableEffect(adapter, captured) {
        adapter.pointerPlace = {
            if (!captured && cursor.isSpecified) {
                val game = toGame(cursor)
                game.x to game.y
            } else null
        }
        onDispose { adapter.pointerPlace = null }
    }
    // a screen of the game opened: the pointer starts in its middle, as in the Java Edition
    LaunchedEffect(captured, size, mode) {
        if (!captured && size != IntSize.Zero && mode == MouseControlMode.SLIDE) {
            moveCursor(Offset(size.width / 2f, size.height / 2f))
        }
    }
    // nothing stays pressed when the way the screen is touched changes
    DisposableEffect(captured, mode) {
        onDispose { adapter.releaseAll() }
    }

    val eventHandler = remember(adapter) {
        EventHandler { event, pressed -> adapter.onLayoutEvent(event, pressed) }
    }
    val occupiedPointers = remember { HashSet<PointerId>() }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { size = it }) {
        ControlBoxLayout(
            modifier = Modifier.fillMaxSize(),
            observedLayout = layout,
            eventHandler = eventHandler,
            isCursorGrabbing = captured,
            checkOccupiedPointers = { occupiedPointers.contains(it) },
            opacity = { (AllSettings.controlsOpacity.state.toFloat() / 100f).coerceIn(0f, 1f) },
            onOccupiedPointer = { occupiedPointers.add(it) },
            onReleasePointer = { occupiedPointers.remove(it) },
            isDark = isLauncherInDarkTheme()
        ) {
            when {
                captured -> LookArea(
                    modifier = Modifier.fillMaxSize(),
                    adapter = adapter,
                    isOccupied = { occupiedPointers.contains(it) }
                )

                mode == MouseControlMode.SLIDE -> TouchpadArea(
                    modifier = Modifier.fillMaxSize(),
                    adapter = adapter,
                    isOccupied = { occupiedPointers.contains(it) },
                    cursor = { cursor },
                    onCursor = ::moveCursor
                )

                else -> FingerArea(
                    modifier = Modifier.fillMaxSize(),
                    adapter = adapter,
                    isOccupied = { occupiedPointers.contains(it) },
                    toGame = ::toGame,
                    onFinger = { cursor = it }
                )
            }
        }

        // the launcher's mouse pointer, the one the Java Edition shows
        val showCursor = !captured && cursor.isSpecified && mode == MouseControlMode.SLIDE
        if (showCursor) {
            val mouseSize = AllSettings.mouseSize.state.dp
            MousePointer(
                modifier = Modifier.mouseFixedPosition(
                    mouseSize = mouseSize,
                    cursorShape = CursorShape.Arrow,
                    pointerPosition = cursor
                ),
                cursorShape = CursorShape.Arrow,
                mouseSize = mouseSize,
                mouseFile = getMouseFile(CursorShape.Arrow).ifExists()
            )
        }

        // the launcher's gyroscope aiming (Settings > Controls > Gyroscope), as in the Java
        // Edition: while the game holds the pointer, turning the phone turns the view
        if (captured && AllSettings.gyroscopeControl.state &&
            com.movtery.zalithlauncher.ui.control.gyroscope.isGyroscopeAvailable(androidx.compose.ui.platform.LocalContext.current)
        ) {
            com.movtery.zalithlauncher.ui.control.gyroscope.GyroscopeReader(
                xEvent = { delta ->
                    adapter.look(if (AllSettings.gyroscopeInvertX.state) -delta else delta, 0f)
                },
                yEvent = { delta ->
                    adapter.look(0f, if (AllSettings.gyroscopeInvertY.state) delta else -delta)
                },
                sampleRate = AllSettings.gyroscopeSampleRate.state,
                smoothing = AllSettings.gyroscopeSmoothing.state,
                smoothingWindow = AllSettings.gyroscopeSmoothingWindow.state,
                sensitivity = AllSettings.gyroscopeSensitivity.state / 100f * GYRO_SCALE
            )
        }

        // on the game's screens: switches between touching the screen and the mouse pointer
        if (!captured) {
            TouchModeButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
                touchScreen = mode == MouseControlMode.CLICK,
                alpha = (AllSettings.menuBallOpacity.state / 100f).coerceIn(0.35f, 1f),
                onSwitch = { AllSettings.rustTouchScreen.save(mode != MouseControlMode.CLICK) }
            )
        }

        GameMenu(
            state = menu,
            close = { menu = MenuState.HIDE },
            layouts = layouts,
            current = current,
            onLayout = { entry ->
                AllSettings.controlLayout.save(entry.file.name)
                chosen = entry.file.name
            },
            onEditLayout = { current?.file?.let(onEditLayout) },
            onKeyboard = { adapter.keyboard() },
            qol = remember(context) { RustQol(RustEdition.gameFolder(context)) },
            mods = remember(context) { RustMods(RustEdition.gameFolder(context)) },
            onMoveHud = {
                adapter.releaseAll()
                hudEditor = true
            },
            onCatalog = { shelf ->
                adapter.releaseAll()
                onCatalog(shelf)
            },
            onQuit = onQuit
        )

        // the floating menu button; always there, so the menu can be reached with any layout
        DraggableGameBall(
            position = AllSettings.menuBallPos.state,
            onPositionChanged = { AllSettings.menuBallPos.updateState(it) },
            onSavePos = { AllSettings.menuBallPos.save() },
            gameFps = { null },
            fpsDisplayMode = AllSettings.fpsDisplayMode.state,
            fpsHistory = { emptyList() },
            fpsMax = { 0 },
            fpsMin = { 0 },
            showMemory = false,
            opened = menu == MenuState.SHOW,
            alpha = AllSettings.menuBallOpacity.state / 100f,
            onClick = { menu = menu.next() }
        )

        if (hudEditor) {
            RustHudEditor(
                gameFolder = remember(context) { RustEdition.gameFolder(context) },
                onDone = { hudEditor = false }
            )
        }
    }
}

/**
 * Switches how the game's screens are used: the finger on the screen itself (shown as a hand:
 * a tap is a tap where the finger is, a swipe scrolls), or the screen as a touchpad for the
 * mouse pointer (shown as a mouse).
 */
@Composable
private fun TouchModeButton(
    modifier: Modifier,
    touchScreen: Boolean,
    alpha: Float,
    onSwitch: () -> Unit
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .alpha(alpha),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = onSwitch
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(
                    if (touchScreen) R.drawable.ic_touch_app_filled else R.drawable.ic_mouse_filled
                ),
                contentDescription = stringResource(R.string.rust_edition_touch_screen)
            )
        }
    }
}

/**
 * The game menu of the Rust Edition: what the Java Edition's has for the controls.
 */
@Composable
private fun GameMenu(
    state: MenuState,
    close: () -> Unit,
    layouts: List<LayoutEntry>,
    current: LayoutEntry?,
    onLayout: (LayoutEntry) -> Unit,
    onEditLayout: () -> Unit,
    onKeyboard: () -> Unit,
    qol: RustQol,
    mods: RustMods,
    onMoveHud: () -> Unit,
    onCatalog: (Int) -> Unit,
    onQuit: () -> Unit
) {
    // the "QOL" page takes the menu's place while it is open
    var qolPage by remember { mutableStateOf(false) }
    var modsPage by remember { mutableStateOf(false) }
    MenuSubscreen(
        state = state,
        closeScreen = close
    ) {
        if (modsPage) {
            ModsPage(mods = mods, onBack = { modsPage = false })
        } else if (qolPage) {
            QolPage(qol = qol, onBack = { qolPage = false })
        } else {
        Text(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
            text = stringResource(R.string.game_menu_title),
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(all = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.game_menu_option_input_method),
                    onClick = {
                        onKeyboard()
                        close()
                    }
                )
            }
            item {
                if (current != null) {
                    MenuListLayout(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.game_menu_option_replacement_control),
                        items = layouts,
                        currentItem = current,
                        onItemChange = onLayout,
                        getItemText = { it.name }
                    )
                } else {
                    MenuTextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.control_manage_list_empty),
                        enabled = false
                    )
                }
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.control_manage_info_edit),
                    enabled = current != null,
                    onClick = {
                        close()
                        onEditLayout()
                    }
                )
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "QOL",
                    onClick = { qolPage = true }
                )
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Mods",
                    onClick = { modsPage = true }
                )
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.rust_hud_editor_title),
                    onClick = {
                        close()
                        onMoveHud()
                    }
                )
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.rust_lite_shader_packs),
                    onClick = {
                        close()
                        onCatalog(EngineCatalog.SHELF_SHADER_PACKS)
                    }
                )
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.rust_lite_resource_packs),
                    onClick = {
                        close()
                        onCatalog(EngineCatalog.SHELF_RESOURCE_PACKS)
                    }
                )
            }
            item {
                MenuSliderLayout(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.game_menu_option_controls_opacity),
                    value = AllSettings.controlsOpacity.state,
                    valueRange = AllSettings.controlsOpacity.floatRange,
                    onValueChange = { AllSettings.controlsOpacity.updateState(it) },
                    onValueChangeFinished = { AllSettings.controlsOpacity.save(it) },
                    suffix = "%"
                )
            }
            item {
                MenuSwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.rust_edition_touch_screen),
                    switch = AllSettings.rustTouchScreen.state,
                    onSwitch = { AllSettings.rustTouchScreen.save(it) }
                )
            }
            item {
                MenuSwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.settings_control_gyroscope_title),
                    switch = AllSettings.gyroscopeControl.state,
                    onSwitch = { AllSettings.gyroscopeControl.save(it) }
                )
            }
            item {
                MenuSliderLayout(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.settings_control_gyroscope_sensitivity_title),
                    value = AllSettings.gyroscopeSensitivity.state,
                    valueRange = AllSettings.gyroscopeSensitivity.floatRange,
                    enabled = AllSettings.gyroscopeControl.state,
                    onValueChange = { AllSettings.gyroscopeSensitivity.updateState(it) },
                    onValueChangeFinished = { AllSettings.gyroscopeSensitivity.save(it) },
                    suffix = "%"
                )
            }
            item {
                MenuSwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.settings_control_mouse_enable_click_title),
                    switch = AllSettings.enableMouseClick.state,
                    onSwitch = { AllSettings.enableMouseClick.save(it) },
                    enabled = !AllSettings.rustTouchScreen.state
                )
            }
            item {
                MenuSliderLayout(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.settings_control_mouse_size_title),
                    value = AllSettings.mouseSize.state,
                    valueRange = AllSettings.mouseSize.floatRange,
                    onValueChange = { AllSettings.mouseSize.updateState(it) },
                    onValueChangeFinished = { AllSettings.mouseSize.save(it) },
                    suffix = "Dp"
                )
            }
            item {
                MenuSliderLayout(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.settings_control_mouse_sensitivity_title),
                    value = AllSettings.cursorSensitivity.state,
                    valueRange = AllSettings.cursorSensitivity.floatRange,
                    onValueChange = { AllSettings.cursorSensitivity.updateState(it) },
                    onValueChangeFinished = { AllSettings.cursorSensitivity.save(it) },
                    suffix = "%"
                )
            }
            item {
                MenuSliderLayout(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.settings_control_mouse_capture_sensitivity_title),
                    value = AllSettings.mouseCaptureSensitivity.state,
                    valueRange = AllSettings.mouseCaptureSensitivity.floatRange,
                    onValueChange = { AllSettings.mouseCaptureSensitivity.updateState(it) },
                    onValueChangeFinished = { AllSettings.mouseCaptureSensitivity.save(it) },
                    suffix = "%"
                )
            }
            item {
                MenuSliderLayout(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.settings_control_mouse_long_press_delay_title),
                    value = AllSettings.mouseLongPressDelay.state,
                    valueRange = AllSettings.mouseLongPressDelay.floatRange,
                    onValueChange = { AllSettings.mouseLongPressDelay.updateState(it) },
                    onValueChangeFinished = { AllSettings.mouseLongPressDelay.save(it) },
                    suffix = "ms"
                )
            }
            item {
                MenuSliderLayout(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.game_menu_option_menu_ball_opacity),
                    value = AllSettings.menuBallOpacity.state,
                    valueRange = AllSettings.menuBallOpacity.floatRange,
                    onValueChange = { AllSettings.menuBallOpacity.updateState(it) },
                    onValueChangeFinished = { AllSettings.menuBallOpacity.save(it) },
                    suffix = "%"
                )
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.game_button_force_close),
                    onClick = onQuit
                )
            }
        }
        }
    }
}

/**
 * The game menu's "Mods" page: the built-in mods by category. A category has a switch for all
 * of its mods and opens to show them, each with its own switch. Everything starts off.
 */
@Composable
private fun ModsPage(mods: RustMods, onBack: () -> Unit) {
    var open by remember { mutableStateOf(setOf<String>()) }
    // the game's own Options > Mods page may have changed the file
    LaunchedEffect(Unit) { mods.reload() }
    Text(
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        text = "Mods",
        style = MaterialTheme.typography.titleMedium
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "< Back",
                onClick = onBack
            )
        }
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Turn every mod off",
                onClick = { mods.allOff() }
            )
        }
        RustMods.CATEGORIES.forEach { category ->
            val shown = category.title in open
            item {
                QolHeading(
                    title = category.title,
                    note = "${mods.countOn(category)} of ${category.mods.size} on"
                )
            }
            item {
                MenuSwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "All ${category.title} mods",
                    switch = mods.countOn(category) > 0,
                    onSwitch = { mods.setAll(category, it) }
                )
            }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (shown) "Hide the mods  ^" else "Show the mods  v",
                    onClick = { open = if (shown) open - category.title else open + category.title }
                )
            }
            if (shown) {
                category.mods.forEach { mod ->
                    item {
                        MenuSwitchButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = "${mod.name}: ${mod.about}",
                            switch = mods.isOn(mod),
                            onSwitch = { mods.setOn(mod, it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The game menu's "QOL" page: the features, each with its switch, and behind "Options" a
 * feature's own page with its choices in sections.
 */
@Composable
private fun QolPage(qol: RustQol, onBack: () -> Unit) {
    var open by remember { mutableStateOf<RustQol.Feature?>(null) }
    val feature = open
    // a feature's key may have switched it in the game since the page was last shown
    LaunchedEffect(Unit) { qol.reload() }
    Text(
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        text = if (feature == null) "QOL" else "QOL  >  ${feature.title}",
        style = MaterialTheme.typography.titleMedium
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "< Back",
                onClick = { if (feature == null) onBack() else open = null }
            )
        }
        if (feature == null) {
            RustQol.FEATURES.forEach { each ->
                item { QolHeading(title = each.title, note = each.summary) }
                item {
                    MenuSwitchButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Enabled",
                        switch = qol.isOn(each),
                        onSwitch = { qol.setOn(each, it) }
                    )
                }
                if (each.sections.isNotEmpty()) {
                    item {
                        MenuTextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = "${each.title} options  >",
                            onClick = { open = each }
                        )
                    }
                }
            }
        } else {
            item {
                MenuSwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Enabled",
                    switch = qol.isOn(feature),
                    onSwitch = { qol.setOn(feature, it) }
                )
            }
            feature.sections.forEach { section ->
                item { QolHeading(title = section.title, note = null) }
                val switches = section.rows.count { it is RustQol.Switch }
                if (switches > 2) {
                    item {
                        val all = section.rows.all { it !is RustQol.Switch || qol.isOn(it) }
                        MenuTextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = if (all) "Clear all" else "Select all",
                            onClick = { qol.setAll(section, !all) }
                        )
                    }
                }
                section.rows.forEach { row ->
                    item {
                        when (row) {
                            is RustQol.Switch -> MenuSwitchButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = row.label,
                                switch = qol.isOn(row),
                                onSwitch = { qol.setOn(row, it) }
                            )
                            is RustQol.KeyRow -> MenuListLayout(
                                modifier = Modifier.fillMaxWidth(),
                                title = row.label,
                                items = RustQol.KEYS,
                                currentItem = qol.keyOf(row),
                                onItemChange = { qol.setKey(row, it) },
                                getItemText = { it.label }
                            )
                            is RustQol.Slider -> MenuSliderLayout(
                                modifier = Modifier.fillMaxWidth(),
                                title = row.label,
                                value = qol.value(row),
                                valueRange = row.min.toFloat()..row.max.toFloat(),
                                onValueChange = { qol.setValue(row, it, write = false) },
                                onValueChangeFinished = { qol.setValue(row, it, write = true) },
                                suffix = row.suffix
                            )
                        }
                    }
                }
            }
            item { QolHeading(title = "Reset", note = null) }
            item {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Reset ${feature.title} options",
                    onClick = { qol.reset(feature) }
                )
            }
        }
    }
}

/** A section's heading on the "QOL" page, with a line of explanation under a feature's */
@Composable
private fun QolHeading(title: String, note: String?) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 10.dp, end = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (note != null) {
            Text(text = note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** How far two fingers move for one line of the mouse wheel, in pixels */
private const val SCROLL_STEP = 48f

/**
 * A screen of the game, with the mouse pointer moved as on a touchpad (the Java Edition's
 * "slide" mouse mode): a drag moves the pointer, a tap is a left click, holding still holds
 * the left button (to drag), and a second finger turns the drag into the mouse wheel.
 */
@Composable
private fun TouchpadArea(
    modifier: Modifier,
    adapter: RustInputAdapter,
    isOccupied: (PointerId) -> Boolean,
    cursor: () -> Offset,
    onCursor: (Offset) -> Unit
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.pointerInput(adapter) {
            val slop = viewConfiguration.touchSlop
            awaitPointerEventScope {
                var finger: PointerId? = null
                var travelled = 0f
                var holding = false
                var scrolling = false
                var scrolled = 0f
                var longPress: Job? = null

                while (true) {
                    val event = awaitPointerEvent()
                    // the window was hidden under the finger: its lift never came
                    if (finger != null && event.changes.none { it.id == finger }) {
                        longPress?.cancel()
                        if (holding) adapter.button(RustInputAdapter.BUTTON_LEFT, false)
                        finger = null
                    }
                    for (change in event.changes) {
                        val landed = change.pressed && !change.previousPressed
                        when {
                            finger == null -> {
                                if (!landed || change.isConsumed || isOccupied(change.id)) continue
                                finger = change.id
                                travelled = 0f
                                holding = false
                                scrolling = false
                                scrolled = 0f
                                longPress = scope.launch {
                                    delay(AllSettings.mouseLongPressDelay.state.toLong())
                                    holding = true
                                    adapter.button(RustInputAdapter.BUTTON_LEFT, true)
                                }
                                change.consume()
                            }

                            change.id != finger -> {
                                // a second finger: the two scroll
                                if (landed && !change.isConsumed && !isOccupied(change.id) && !holding) {
                                    longPress?.cancel()
                                    scrolling = true
                                    change.consume()
                                }
                            }

                            change.pressed -> {
                                val delta = change.positionChange()
                                if (scrolling) {
                                    scrolled += delta.y
                                    while (abs(scrolled) >= SCROLL_STEP) {
                                        val line = if (scrolled > 0f) 1f else -1f
                                        adapter.wheel(line)
                                        scrolled -= line * SCROLL_STEP
                                    }
                                } else if (delta.x != 0f || delta.y != 0f) {
                                    val speed = AllSettings.cursorSensitivity.state / 100f
                                    val at = cursor()
                                    if (at.isSpecified) {
                                        onCursor(Offset(at.x + delta.x * speed, at.y + delta.y * speed))
                                    }
                                    travelled += abs(delta.x) + abs(delta.y)
                                    if (travelled > slop && !holding) longPress?.cancel()
                                }
                                change.consume()
                            }

                            else -> {
                                longPress?.cancel()
                                if (holding) {
                                    adapter.button(RustInputAdapter.BUTTON_LEFT, false)
                                } else if (!scrolling && travelled <= slop &&
                                    AllSettings.enableMouseClick.state
                                ) {
                                    adapter.button(RustInputAdapter.BUTTON_LEFT, true)
                                    adapter.button(RustInputAdapter.BUTTON_LEFT, false)
                                }
                                finger = null
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * A screen of the game touched where the finger is (the Java Edition's "click" mouse mode):
 * every finger that is not on a control is handed to the game as a touch of its own, so its
 * buttons, lists and sliders are used directly; the mouse pointer shows where it was.
 */
@Composable
private fun FingerArea(
    modifier: Modifier,
    adapter: RustInputAdapter,
    isOccupied: (PointerId) -> Boolean,
    toGame: (Offset) -> Offset,
    onFinger: (Offset) -> Unit
) {
    Box(
        modifier = modifier.pointerInput(adapter) {
            awaitPointerEventScope {
                val fingers = HashSet<PointerId>()
                fun number(id: PointerId) = (id.value and 0x7fffffff).toInt()

                while (true) {
                    val event = awaitPointerEvent()
                    // the window was hidden under a finger: its lift never came
                    fingers.filter { held -> event.changes.none { it.id == held } }.forEach { gone ->
                        fingers.remove(gone)
                        adapter.touch(number(gone), RustInputAdapter.TOUCH_CANCEL, 0f, 0f)
                    }
                    for (change in event.changes) {
                        val at = toGame(change.position)
                        when {
                            change.id !in fingers -> {
                                if (!change.pressed || change.previousPressed) continue
                                if (change.isConsumed || isOccupied(change.id)) continue
                                fingers.add(change.id)
                                adapter.touch(number(change.id), RustInputAdapter.TOUCH_DOWN, at.x, at.y)
                                onFinger(change.position)
                                change.consume()
                            }

                            change.pressed -> {
                                val delta = change.positionChange()
                                if (delta.x != 0f || delta.y != 0f) {
                                    adapter.touch(number(change.id), RustInputAdapter.TOUCH_MOVE, at.x, at.y)
                                    onFinger(change.position)
                                }
                                change.consume()
                            }

                            else -> {
                                fingers.remove(change.id)
                                adapter.touch(number(change.id), RustInputAdapter.TOUCH_UP, at.x, at.y)
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * Everything that is not a control: a drag turns the view, a tap is a right click
 * (use, place), holding still is the left button held (attack, break) until the finger lifts.
 * These are the gestures of the Java Edition.
 */
@Composable
private fun LookArea(
    modifier: Modifier,
    adapter: RustInputAdapter,
    isOccupied: (PointerId) -> Boolean
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.pointerInput(adapter) {
            val slop = viewConfiguration.touchSlop
            awaitPointerEventScope {
                var lookPointer: PointerId? = null
                var travelled = 0f
                var holding = false
                var longPress: Job? = null

                while (true) {
                    val event = awaitPointerEvent()
                    // the window was hidden under the finger: its lift never came
                    if (lookPointer != null && event.changes.none { it.id == lookPointer }) {
                        longPress?.cancel()
                        lookPointer = null
                    }
                    for (change in event.changes) {
                        when {
                            lookPointer == null -> {
                                if (!change.pressed || change.previousPressed) continue
                                if (change.isConsumed || isOccupied(change.id)) continue
                                lookPointer = change.id
                                travelled = 0f
                                holding = false
                                longPress = scope.launch {
                                    delay(AllSettings.mouseLongPressDelay.state.toLong())
                                    holding = true
                                    adapter.button(RustInputAdapter.BUTTON_LEFT, true)
                                }
                                change.consume()
                            }

                            change.id != lookPointer -> {}

                            change.pressed -> {
                                val delta = change.positionChange()
                                if (delta.x != 0f || delta.y != 0f) {
                                    val speed = AllSettings.mouseCaptureSensitivity.state / 100f
                                    adapter.look(delta.x * speed, delta.y * speed)
                                    travelled += abs(delta.x) + abs(delta.y)
                                    if (travelled > slop && !holding) longPress?.cancel()
                                }
                                change.consume()
                            }

                            else -> {
                                longPress?.cancel()
                                if (holding) {
                                    adapter.button(RustInputAdapter.BUTTON_LEFT, false)
                                } else if (travelled <= slop) {
                                    adapter.button(RustInputAdapter.BUTTON_RIGHT, true)
                                    adapter.button(RustInputAdapter.BUTTON_RIGHT, false)
                                }
                                lookPointer = null
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
    )
}
