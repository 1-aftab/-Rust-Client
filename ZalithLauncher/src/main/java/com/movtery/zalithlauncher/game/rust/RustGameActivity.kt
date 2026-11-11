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

import android.os.Bundle
import io.github.juanluci.starling.GameActivity

/**
 * The Rust Edition's game: the engine's native activity, with Zalith's controls over it.
 *
 * It runs in its own process (`:rust`), so nothing of the Java Edition (JVM, LWJGL,
 * renderer plugins) is ever loaded beside the engine, and a crash of one cannot take the
 * launcher down.
 */
class RustGameActivity : GameActivity() {
    private val adapter = RustInputAdapter()
    private var overlay: RustControlsOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter.setEnabled(true)
    }

    /**
     * Called by the engine when it takes the pointer for the mouse look (a world is
     * played) and when it lets it go (a menu, the inventory, the chat).
     */
    override fun capturePointer(capture: Boolean) {
        super.capturePointer(capture)
        runOnUiThread { showControls(capture) }
    }

    private fun showControls(show: Boolean) {
        if (isFinishing || isDestroyed) return
        if (show) {
            val current = overlay ?: RustControlsOverlay(this, adapter).also { overlay = it }
            if (!current.isShowing) {
                runCatching { current.show() }
            }
        } else {
            adapter.releaseAll()
            overlay?.takeIf { it.isShowing }?.let { runCatching { it.hide() } }
        }
    }

    override fun onPause() {
        // nothing stays pressed behind the player's back
        adapter.releaseAll()
        super.onPause()
    }

    override fun onDestroy() {
        overlay?.let { runCatching { it.dismiss() } }
        overlay = null
        super.onDestroy()
    }
}
