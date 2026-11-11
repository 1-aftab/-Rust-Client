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

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Zalith's on-screen controls over the Rust Edition.
 *
 * The engine is a `NativeActivity`: it owns its window's surface, so no view can be drawn
 * in that window. The controls therefore live in a window of their own above it. That
 * window never takes the focus, so the keyboard, the back key and the input method stay
 * the game's.
 *
 * It is shown only while the game holds the pointer, that is while a world is played
 * ([RustGameActivity.capturePointer]). Menus, the inventory and every other screen of the
 * engine are touched directly.
 */
class RustControlsOverlay(
    activity: Activity,
    private val adapter: RustInputAdapter
) : ComponentDialog(activity, android.R.style.Theme_Translucent_NoTitleBar) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
                        Controls(adapter)
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

/** The control layout chosen in the launcher's settings, the same the Java Edition uses */
private fun controlLayoutFile(): File? =
    AllSettings.controlLayout.getValue()
        .takeIf { it.isNotEmpty() }
        ?.let { File(PathManager.DIR_CONTROL_LAYOUTS, it) }
        ?.takeIf { it.isFile }

@Composable
private fun Controls(adapter: RustInputAdapter) {
    var layout by remember { mutableStateOf<ObservableControlLayout?>(null) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { controlLayoutFile()?.let(::loadLayoutFromFile) }.getOrNull()
                ?: EmptyControlLayout
        }
        layout = ObservableControlLayout(loaded)
    }

    val eventHandler = remember(adapter) {
        EventHandler { event, pressed -> adapter.onLayoutEvent(event, pressed) }
    }
    val occupiedPointers = remember { HashSet<PointerId>() }

    ControlBoxLayout(
        modifier = Modifier.fillMaxSize(),
        observedLayout = layout,
        eventHandler = eventHandler,
        // the overlay is only there while the game holds the pointer
        isCursorGrabbing = true,
        checkOccupiedPointers = { occupiedPointers.contains(it) },
        opacity = { (AllSettings.controlsOpacity.state.toFloat() / 100f).coerceIn(0f, 1f) },
        onOccupiedPointer = { occupiedPointers.add(it) },
        onReleasePointer = { occupiedPointers.remove(it) },
        isDark = isLauncherInDarkTheme()
    ) {
        LookArea(
            modifier = Modifier.fillMaxSize(),
            adapter = adapter,
            isOccupied = { occupiedPointers.contains(it) }
        )
    }
}

/**
 * Everything that is not a control: a drag turns the view, a tap is a right click
 * (use, place), holding still is the left button held (attack, break) until the finger lifts.
 * These are the gestures of the Java Edition in Zalith.
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
