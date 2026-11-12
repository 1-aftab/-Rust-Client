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

import com.movtery.inputmap.keycodes.ControlEventKeycode
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN_SINGLE
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SCROLL_UP
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SCROLL_UP_SINGLE
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SWITCH_IME
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SWITCH_MENU
import io.github.juanluci.starling.StarlingActivity

/**
 * Turns what Zalith's control layout does into the Rust engine's keyboard and mouse.
 *
 * The Java Edition's counterpart is `CallbackBridge` (LWJGL); nothing here touches it.
 * A layout's events are handled as follows:
 *
 * | Layout event | Rust Edition |
 * |---|---|
 * | key (`GLFW_KEY_*`) | the Android key code of the same key |
 * | mouse button (`GLFW_MOUSE_BUTTON_*`, as a key or a launcher event) | left / right / middle button, at the pointer |
 * | scroll up / down (single or held) | one wheel line per press |
 * | switch / show / hide layer | handled by the layout itself |
 * | switch IME | the game's on-screen keyboard |
 * | open the game menu | the launcher's menu over the game ([onSwitchMenu]) |
 * | send text | not available (ignored) |
 */
class RustInputAdapter {
    private val heldKeys = HashSet<Int>()
    private val heldButtons = HashSet<Int>()
    private val heldFingers = HashSet<Int>()

    /** The layout's "game menu" control was pressed */
    var onSwitchMenu: (() -> Unit)? = null

    /**
     * Where the mouse pointer shows on a screen of the game, in the game window's pixels, or
     * null while a world is played. A button is pressed there: the game is told the place
     * again right before, so the click lands under the pointer the player sees.
     */
    var pointerPlace: (() -> Pair<Float, Float>?)? = null

    /** Tells the engine the host's controls are in use, so it puts its own touch controls away */
    fun setEnabled(enabled: Boolean) = native {
        StarlingActivity.nativeExternalControls(enabled)
    }

    /** A control of the layout was pressed or released */
    fun onLayoutEvent(event: ClickEvent, pressed: Boolean) {
        when (event.type) {
            ClickEvent.Type.Key -> {
                val code = ControlEventKeycode.getKeycodeFromEvent(event.key)?.toInt() ?: return
                if (event.key.startsWith("GLFW_MOUSE_")) {
                    button(code, pressed)
                } else {
                    key(code, pressed)
                }
            }
            ClickEvent.Type.LauncherEvent -> {
                // the layouts' mouse buttons are launcher events named after the GLFW button
                if (event.key.startsWith("GLFW_MOUSE_")) {
                    val code = ControlEventKeycode.getKeycodeFromEvent(event.key)?.toInt() ?: return
                    button(code, pressed)
                    return
                }
                if (!pressed) return
                when (event.key) {
                    LAUNCHER_EVENT_SCROLL_UP, LAUNCHER_EVENT_SCROLL_UP_SINGLE -> wheel(1f)
                    LAUNCHER_EVENT_SCROLL_DOWN, LAUNCHER_EVENT_SCROLL_DOWN_SINGLE -> wheel(-1f)
                    LAUNCHER_EVENT_SWITCH_IME -> keyboard()
                    LAUNCHER_EVENT_SWITCH_MENU -> onSwitchMenu?.invoke()
                }
            }
            else -> {}
        }
    }

    /** @param glfwKey a `GLFW_KEY_*` code */
    fun key(glfwKey: Int, pressed: Boolean) {
        val keycode = RustKeyMap.androidKeycode(glfwKey) ?: return
        if (pressed) heldKeys.add(keycode) else heldKeys.remove(keycode)
        native { StarlingActivity.nativeExternalKey(keycode, pressed, 0) }
    }

    /** @param button 0 left, 1 right, 2 middle (GLFW's numbers; others are ignored) */
    fun button(button: Int, pressed: Boolean) {
        if (button !in BUTTON_LEFT..BUTTON_MIDDLE) return
        if (pressed) {
            pointerPlace?.invoke()?.let { (x, y) -> cursor(x, y) }
            heldButtons.add(button)
        } else {
            heldButtons.remove(button)
        }
        native { StarlingActivity.nativeExternalButton(button, pressed) }
    }

    /** Turns the view by a finger's move, in pixels */
    fun look(dx: Float, dy: Float) = native {
        StarlingActivity.nativeExternalMouse(MOUSE_RELATIVE, dx, dy)
    }

    /** Puts the mouse pointer at a place of the game's window, in its pixels */
    fun cursor(x: Float, y: Float) = native {
        StarlingActivity.nativeExternalMouse(MOUSE_ABSOLUTE, x, y)
    }

    /**
     * A finger on the controls' window, handed to the game as a touch on its own.
     * @param phase [TOUCH_DOWN], [TOUCH_MOVE], [TOUCH_UP] or [TOUCH_CANCEL]
     */
    fun touch(finger: Int, phase: Int, x: Float, y: Float) {
        when (phase) {
            TOUCH_DOWN -> heldFingers.add(finger)
            TOUCH_UP, TOUCH_CANCEL -> heldFingers.remove(finger)
        }
        native { StarlingActivity.nativeExternalTouch(finger, phase, x, y) }
    }

    /** Shows the game's on-screen keyboard for the text field in use */
    fun keyboard() = native { StarlingActivity.nativeExternalKeyboard() }

    /** Whether the game takes the controls' input now (not while it starts or is paused) */
    fun ready(): Boolean = try {
        StarlingActivity.nativeExternalReady()
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    /** Turns the mouse wheel by [lines]; up is positive */
    fun wheel(lines: Float) = native {
        StarlingActivity.nativeExternalMouse(MOUSE_WHEEL, 0f, lines)
    }

    /** Lets go of everything still held, e.g. when the controls are put away mid-press */
    fun releaseAll() {
        heldKeys.toList().forEach { keycode ->
            native { StarlingActivity.nativeExternalKey(keycode, false, 0) }
        }
        heldKeys.clear()
        heldButtons.toList().forEach { button ->
            native { StarlingActivity.nativeExternalButton(button, false) }
        }
        heldButtons.clear()
        heldFingers.toList().forEach { finger ->
            native { StarlingActivity.nativeExternalTouch(finger, TOUCH_CANCEL, 0f, 0f) }
        }
        heldFingers.clear()
    }

    /** An engine built without the Rust Client patch has none of these functions */
    private inline fun native(call: () -> Unit) {
        try {
            call()
        } catch (_: UnsatisfiedLinkError) {
        }
    }

    companion object {
        const val BUTTON_LEFT = 0
        const val BUTTON_RIGHT = 1
        const val BUTTON_MIDDLE = 2

        const val TOUCH_DOWN = 0
        const val TOUCH_MOVE = 1
        const val TOUCH_UP = 2
        const val TOUCH_CANCEL = 3

        private const val MOUSE_ABSOLUTE = 0
        private const val MOUSE_RELATIVE = 1
        private const val MOUSE_WHEEL = 2
    }
}
