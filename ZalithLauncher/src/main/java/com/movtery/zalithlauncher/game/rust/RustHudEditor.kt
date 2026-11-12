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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A HUD box of a built-in mod that the player places freely.
 *
 * The engine and this screen talk through two small files in the mod's settings folder
 * (`<game folder>/horizon-mods/.horizon-mod-data/<mod>/`, engine patch 0010):
 * - `rc_box`, written by the engine: where the box is drawn, "x y w h" as fractions of the screen
 * - `rc_place`, written here: "1 across down" (free, percent of the way across and down)
 *
 * The engine reads `rc_place` again every few frames, so the real box follows the finger.
 */
private class HudBox(val title: Int, private val folder: File) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var w by mutableFloatStateOf(0f)
    var h by mutableFloatStateOf(0f)
    /** The engine told where it is: the mod is on and was drawn at least once */
    var known by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    /** The engine's last word is older than our own until then */
    private var ownUntil = 0L
    private var lastWrite = 0L

    fun read() {
        if (dragging || System.currentTimeMillis() < ownUntil) return
        val values = runCatching {
            File(folder, BOX_FILE).readText().trim().split(Regex("\\s+")).map { it.toFloat() }
        }.getOrNull()
        if (values == null || values.size != 4 || values.any { !it.isFinite() } ||
            values[2] <= 0f || values[3] <= 0f
        ) return
        w = values[2].coerceAtMost(1f)
        h = values[3].coerceAtMost(1f)
        x = values[0].coerceIn(0f, 1f - w)
        y = values[1].coerceIn(0f, 1f - h)
        known = true
    }

    fun moveBy(dx: Float, dy: Float) {
        x = (x + dx).coerceIn(0f, 1f - w)
        y = (y + dy).coerceIn(0f, 1f - h)
        write(force = false)
    }

    fun drop() {
        dragging = false
        write(force = true)
    }

    /** Back into the corner its mod's settings name */
    fun reset() {
        ownUntil = 0L
        store("0 ${percent(x, w)} ${percent(y, h)}")
    }

    private fun write(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWrite < WRITE_STEP) return
        lastWrite = now
        ownUntil = now + OWN_TIME
        store("1 ${percent(x, w)} ${percent(y, h)}")
    }

    private fun store(text: String) {
        runCatching {
            folder.mkdirs()
            File(folder, PLACE_FILE).writeText(text)
        }
    }

    /** The engine's measure: how far of the room the box leaves it is, in percent */
    private fun percent(at: Float, length: Float): String {
        val room = 1f - length
        val value = if (room > 0f) (at / room * 100f).coerceIn(0f, 100f) else 0f
        return String.format(Locale.US, "%.2f", value)
    }

    companion object {
        const val BOX_FILE = "rc_box"
        const val PLACE_FILE = "rc_place"
        /** Milliseconds between two writes while dragging */
        const val WRITE_STEP = 25L
        /** Milliseconds the engine gets to tell the box's new place after a drag */
        const val OWN_TIME = 1500L
    }
}

/** The mods whose HUD box is placed freely (RustEngine/tools/mod_tweaks.py) */
private val HUD_MODS = listOf(
    "fps_overlay" to R.string.rust_hud_fps,
    "keystrokes" to R.string.rust_hud_keystrokes,
    "minimap" to R.string.rust_hud_minimap
)

/**
 * "Move HUD": the built-in mods' HUD boxes (FPS Counter, Keystrokes, Minimap) dragged
 * anywhere on the screen, over the running game. Each box that the game shows gets a frame
 * here, exactly over it; dragging the frame moves the box.
 */
@Composable
fun RustHudEditor(gameFolder: File, onDone: () -> Unit) {
    val boxes = remember(gameFolder) {
        val data = File(File(gameFolder, "horizon-mods"), ".horizon-mod-data")
        HUD_MODS.map { (id, title) -> HudBox(title, File(data, id)) }
    }
    LaunchedEffect(boxes) {
        while (true) {
            boxes.forEach { it.read() }
            delay(300)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            // nothing reaches the game's controls underneath
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val grip = with(density) { 48.dp.toPx() }

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 340.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.rust_hud_editor_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        if (boxes.any { it.known }) R.string.rust_hud_editor_hint
                        else R.string.rust_hud_editor_empty
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { boxes.forEach { if (it.known) it.reset() } }) {
                        Text(text = stringResource(R.string.rust_hud_editor_reset))
                    }
                    Button(onClick = onDone) {
                        Text(text = stringResource(R.string.rust_hud_editor_done))
                    }
                }
            }
        }

        // the frames come last: one that lies over the card can still be taken
        boxes.forEach { box ->
            if (!box.known || width <= 0f || height <= 0f) return@forEach
            val boxW = box.w * width
            val boxH = box.h * height
            // a small box (the FPS counter) is still easy to take: the grip is larger than it
            val gripW = maxOf(boxW, grip)
            val gripH = maxOf(boxH, grip)
            val left = box.x * width - (gripW - boxW) / 2f
            val top = box.y * height - (gripH - boxH) / 2f
            val colour = if (box.dragging) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { gripW.toDp() }, with(density) { gripH.toDp() })
                    .pointerInput(box, width, height) {
                        detectDragGestures(
                            onDragStart = { box.dragging = true },
                            onDragEnd = { box.drop() },
                            onDragCancel = { box.drop() }
                        ) { change, amount ->
                            change.consume()
                            box.moveBy(amount.x / width, amount.y / height)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
                        .background(colour.copy(alpha = 0.18f))
                        .border(2.dp, colour)
                )
            }

            // its name, under the box, or over it at the bottom of the screen
            val labelHeight = with(density) { 20.dp.toPx() }
            val below = box.y * height + boxH + labelHeight < height
            val labelTop = if (below) box.y * height + boxH else box.y * height - labelHeight
            val right = box.x + box.w / 2f > 0.5f
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            if (right) 0 else (box.x * width).roundToInt(),
                            labelTop.roundToInt()
                        )
                    }
                    .then(
                        if (right) Modifier.size(
                            with(density) { (box.x * width + boxW).toDp() },
                            20.dp
                        ) else Modifier
                    ),
                contentAlignment = if (right) Alignment.TopEnd else Alignment.TopStart
            ) {
                Text(
                    modifier = Modifier
                        .background(colour, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    text = stringResource(box.title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1
                )
            }
        }
    }
}
