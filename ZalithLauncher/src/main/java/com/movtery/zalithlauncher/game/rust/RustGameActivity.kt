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
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.unit.IntSize
import com.movtery.zalithlauncher.setting.loadAllSettings
import com.movtery.zalithlauncher.ui.activities.startEditorActivity
import io.github.juanluci.starling.GameActivity

/**
 * The Rust Edition's game: the engine's native activity, with the launcher's controls,
 * mouse pointer and game menu over it ([RustControlsOverlay]).
 *
 * It runs in its own process (`:rust`), so nothing of the Java Edition (JVM, LWJGL,
 * renderer plugins) is ever loaded beside the engine, and a crash of one cannot take the
 * launcher down.
 */
class RustGameActivity : GameActivity() {
    private val adapter = RustInputAdapter()
    private val state = RustOverlayState()
    private var overlay: RustControlsOverlay? = null
    private var catalog: RustCatalogDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    /** Shows the controls once the game takes their input, and puts them away when it stops */
    private val watch = object : Runnable {
        override fun run() {
            showControls(resumed && adapter.ready())
            handler.postDelayed(this, WATCH_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { loadAllSettings(this, true) }
        adapter.setEnabled(true)
    }

    /**
     * Called by the engine when it takes the pointer for the mouse look (a world is
     * played) and when it lets it go (a menu, the inventory, the chat).
     */
    override fun capturePointer(capture: Boolean) {
        super.capturePointer(capture)
        runOnUiThread {
            if (state.captured != capture) {
                adapter.releaseAll()
                state.captured = capture
            }
        }
    }

    /**
     * Called by the engine, from its own thread, where it would show its catalog of mods,
     * resource packs and shader packs: the launcher's own screen is shown instead.
     */
    override fun openCatalog(shelf: Int): Boolean {
        runOnUiThread {
            if (isFinishing || isDestroyed || catalog?.isShowing == true) return@runOnUiThread
            adapter.releaseAll()
            catalog = RustCatalogDialog(this, shelf).also { runCatching { it.show() } }
        }
        return true
    }

    private fun showControls(show: Boolean) {
        if (isFinishing || isDestroyed) return
        if (show) {
            val current = overlay ?: RustControlsOverlay(
                activity = this,
                adapter = adapter,
                state = state,
                gameSize = {
                    val view = window.decorView
                    IntSize(view.width, view.height)
                },
                onEditLayout = { file ->
                    startEditorActivity(this, file, RustControlEditorActivity::class.java)
                },
                onQuit = { finish() }
            ).also { overlay = it }
            if (!current.isShowing) {
                runCatching { current.show() }
            }
        } else {
            overlay?.takeIf { it.isShowing }?.let {
                adapter.releaseAll()
                runCatching { it.hide() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // this process has its own copy of the settings: what the launcher (or the editor)
        // saved is read here, or the controls would start from the defaults every time
        runCatching { loadAllSettings(this, true) }
        // back from the layout editor: the layout on disk may have changed
        state.reload++
        handler.removeCallbacks(watch)
        handler.post(watch)
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(watch)
        // nothing stays pressed behind the player's back
        adapter.releaseAll()
        // and the controls are not shown under what covers the game (the layout editor)
        showControls(false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(watch)
        overlay?.let { runCatching { it.dismiss() } }
        overlay = null
        catalog?.let { runCatching { it.dismiss() } }
        catalog = null
        super.onDestroy()
    }

    private companion object {
        const val WATCH_MILLIS = 300L
    }
}
