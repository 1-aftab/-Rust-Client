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

import android.view.KeyEvent
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode

/**
 * Zalith's control layouts name keys as GLFW does (they were made for LWJGL).
 * The Rust engine takes Android key codes, which it maps to physical keys itself.
 */
object RustKeyMap {
    private val keys: Map<Int, Int> = buildMap {
        // letters and digits are contiguous in both tables
        for (i in 0 until 26) {
            put(LwjglGlfwKeycode.GLFW_KEY_A + i, KeyEvent.KEYCODE_A + i)
        }
        for (i in 0 until 10) {
            put(LwjglGlfwKeycode.GLFW_KEY_0 + i, KeyEvent.KEYCODE_0 + i)
            put(LwjglGlfwKeycode.GLFW_KEY_KP_0 + i, KeyEvent.KEYCODE_NUMPAD_0 + i)
        }
        for (i in 0 until 12) {
            put(LwjglGlfwKeycode.GLFW_KEY_F1 + i, KeyEvent.KEYCODE_F1 + i)
        }

        put(LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt(), KeyEvent.KEYCODE_SPACE)
        put(LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE.toInt(), KeyEvent.KEYCODE_APOSTROPHE)
        put(LwjglGlfwKeycode.GLFW_KEY_COMMA.toInt(), KeyEvent.KEYCODE_COMMA)
        put(LwjglGlfwKeycode.GLFW_KEY_MINUS.toInt(), KeyEvent.KEYCODE_MINUS)
        put(LwjglGlfwKeycode.GLFW_KEY_PERIOD.toInt(), KeyEvent.KEYCODE_PERIOD)
        put(LwjglGlfwKeycode.GLFW_KEY_SLASH.toInt(), KeyEvent.KEYCODE_SLASH)
        put(LwjglGlfwKeycode.GLFW_KEY_SEMICOLON.toInt(), KeyEvent.KEYCODE_SEMICOLON)
        put(LwjglGlfwKeycode.GLFW_KEY_EQUAL.toInt(), KeyEvent.KEYCODE_EQUALS)
        put(LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET.toInt(), KeyEvent.KEYCODE_LEFT_BRACKET)
        put(LwjglGlfwKeycode.GLFW_KEY_BACKSLASH.toInt(), KeyEvent.KEYCODE_BACKSLASH)
        put(LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET.toInt(), KeyEvent.KEYCODE_RIGHT_BRACKET)
        put(LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT.toInt(), KeyEvent.KEYCODE_GRAVE)

        put(LwjglGlfwKeycode.GLFW_KEY_ESCAPE.toInt(), KeyEvent.KEYCODE_ESCAPE)
        put(LwjglGlfwKeycode.GLFW_KEY_ENTER.toInt(), KeyEvent.KEYCODE_ENTER)
        put(LwjglGlfwKeycode.GLFW_KEY_TAB.toInt(), KeyEvent.KEYCODE_TAB)
        put(LwjglGlfwKeycode.GLFW_KEY_BACKSPACE.toInt(), KeyEvent.KEYCODE_DEL)
        put(LwjglGlfwKeycode.GLFW_KEY_INSERT.toInt(), KeyEvent.KEYCODE_INSERT)
        put(LwjglGlfwKeycode.GLFW_KEY_DELETE.toInt(), KeyEvent.KEYCODE_FORWARD_DEL)
        put(LwjglGlfwKeycode.GLFW_KEY_RIGHT.toInt(), KeyEvent.KEYCODE_DPAD_RIGHT)
        put(LwjglGlfwKeycode.GLFW_KEY_LEFT.toInt(), KeyEvent.KEYCODE_DPAD_LEFT)
        put(LwjglGlfwKeycode.GLFW_KEY_DOWN.toInt(), KeyEvent.KEYCODE_DPAD_DOWN)
        put(LwjglGlfwKeycode.GLFW_KEY_UP.toInt(), KeyEvent.KEYCODE_DPAD_UP)
        put(LwjglGlfwKeycode.GLFW_KEY_PAGE_UP.toInt(), KeyEvent.KEYCODE_PAGE_UP)
        put(LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN.toInt(), KeyEvent.KEYCODE_PAGE_DOWN)
        put(LwjglGlfwKeycode.GLFW_KEY_HOME.toInt(), KeyEvent.KEYCODE_MOVE_HOME)
        put(LwjglGlfwKeycode.GLFW_KEY_END.toInt(), KeyEvent.KEYCODE_MOVE_END)
        put(LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK.toInt(), KeyEvent.KEYCODE_CAPS_LOCK)
        put(LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK.toInt(), KeyEvent.KEYCODE_SCROLL_LOCK)
        put(LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK.toInt(), KeyEvent.KEYCODE_NUM_LOCK)
        put(LwjglGlfwKeycode.GLFW_KEY_PRINT_SCREEN.toInt(), KeyEvent.KEYCODE_SYSRQ)
        put(LwjglGlfwKeycode.GLFW_KEY_PAUSE.toInt(), KeyEvent.KEYCODE_BREAK)

        put(LwjglGlfwKeycode.GLFW_KEY_KP_DECIMAL.toInt(), KeyEvent.KEYCODE_NUMPAD_DOT)
        put(LwjglGlfwKeycode.GLFW_KEY_KP_DIVIDE.toInt(), KeyEvent.KEYCODE_NUMPAD_DIVIDE)
        put(LwjglGlfwKeycode.GLFW_KEY_KP_MULTIPLY.toInt(), KeyEvent.KEYCODE_NUMPAD_MULTIPLY)
        put(LwjglGlfwKeycode.GLFW_KEY_KP_SUBTRACT.toInt(), KeyEvent.KEYCODE_NUMPAD_SUBTRACT)
        put(LwjglGlfwKeycode.GLFW_KEY_KP_ADD.toInt(), KeyEvent.KEYCODE_NUMPAD_ADD)
        put(LwjglGlfwKeycode.GLFW_KEY_KP_ENTER.toInt(), KeyEvent.KEYCODE_NUMPAD_ENTER)
        put(LwjglGlfwKeycode.GLFW_KEY_KP_EQUAL.toInt(), KeyEvent.KEYCODE_NUMPAD_EQUALS)

        put(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT.toInt(), KeyEvent.KEYCODE_SHIFT_LEFT)
        put(LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL.toInt(), KeyEvent.KEYCODE_CTRL_LEFT)
        put(LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT.toInt(), KeyEvent.KEYCODE_ALT_LEFT)
        put(LwjglGlfwKeycode.GLFW_KEY_LEFT_SUPER.toInt(), KeyEvent.KEYCODE_META_LEFT)
        put(LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT.toInt(), KeyEvent.KEYCODE_SHIFT_RIGHT)
        put(LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL.toInt(), KeyEvent.KEYCODE_CTRL_RIGHT)
        put(LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT.toInt(), KeyEvent.KEYCODE_ALT_RIGHT)
        put(LwjglGlfwKeycode.GLFW_KEY_RIGHT_SUPER.toInt(), KeyEvent.KEYCODE_META_RIGHT)
        put(LwjglGlfwKeycode.GLFW_KEY_MENU.toInt(), KeyEvent.KEYCODE_MENU)
    }

    /**
     * @return the Android key code of a GLFW key code, or null when Android has none for it
     */
    fun androidKeycode(glfwKeycode: Int): Int? = keys[glfwKeycode]
}
