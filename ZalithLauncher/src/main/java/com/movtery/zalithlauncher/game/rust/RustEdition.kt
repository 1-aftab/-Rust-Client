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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.juanluci.starling.EngineHost
import java.io.File

/**
 * Entry point of the Rust Edition for the launcher: whether it can run on this device,
 * where its files are, and how to start it.
 *
 * The Rust Edition keeps its files apart from the Java Edition:
 *
 * | Folder | Content |
 * |---|---|
 * | `<externalFilesDir>/rust/` | the game folder: worlds, resource packs, shader packs, options |
 * | `<filesDir>/rust-engine/`  | the engine's own files: prepared game resources, caches |
 * | `<externalFilesDir>/.minecraft/` | (Java Edition, untouched) |
 *
 * Only the control layouts are shared: the Rust Edition is played with the same
 * Zalith control layout as the Java Edition.
 */
object RustEdition {
    /** The only Minecraft version the engine implements */
    const val MINECRAFT_VERSION = "26.3"

    /** The engine the Rust Edition is built from */
    const val ENGINE_NAME = "Starling"

    /** `VK_API_VERSION_1_3`, as `PackageManager.FEATURE_VULKAN_HARDWARE_VERSION` words it */
    private const val VULKAN_1_3 = 0x400003

    /** Sub folders of the game folder the launcher offers to open */
    const val FOLDER_SAVES = "saves"
    const val FOLDER_RESOURCE_PACKS = "resourcepacks"
    const val FOLDER_SHADER_PACKS = "shaderpacks"
    const val FOLDER_DATA_PACKS = "datapacks"
    const val FOLDER_SCREENSHOTS = "screenshots"

    /** The engine's arguments file, read at each start (android.rs `command_line`) */
    private const val ARGS_FILE = "horizon-args.txt"
    private const val ARG_PLAYER_NAME = "HORIZON_PLAYER_NAME"

    /** Why the Rust Edition cannot be started, if it cannot */
    enum class Problem {
        /** This APK was built without the engine (it is ARM64 only) */
        NOT_INCLUDED,
        /** The device has no Vulkan 1.3 */
        NO_VULKAN
    }

    /**
     * @return what prevents the Rust Edition from starting, or null when nothing does
     */
    fun problem(context: Context): Problem? = when {
        !EngineHost.isAvailable(context) -> Problem.NOT_INCLUDED
        !context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_3
        ) -> Problem.NO_VULKAN
        else -> null
    }

    /**
     * The game folder: worlds, resource packs, shader packs and options.
     * Falls back to the engine's own files when the external storage is not there,
     * as the engine itself does.
     */
    fun gameFolder(context: Context): File =
        EngineHost.gameFolder(context) ?: EngineHost.filesDir(context)

    /**
     * A folder of the game folder, created when missing so that it can be opened
     * before the game ran once
     */
    fun subFolder(context: Context, name: String): File =
        File(gameFolder(context), name).apply { mkdirs() }

    /**
     * Starts the Rust Edition.
     * @param playerName the name to play with (the launcher's current account), or null to
     * leave the choice to the engine
     */
    fun launch(context: Context, playerName: String?) {
        runCatching { writePlayerName(context, playerName) }
        gameFolder(context).mkdirs()
        val intent = Intent(context, RustGameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Hands the launcher's account name to the engine through its arguments file.
     * Lines the player wrote there are kept; only the name line is replaced.
     */
    private fun writePlayerName(context: Context, playerName: String?) {
        val file = File(EngineHost.filesDir(context), ARGS_FILE)
        val kept = if (file.isFile) {
            file.readLines().filterNot { it.trim().startsWith("$ARG_PLAYER_NAME=") }
        } else {
            emptyList()
        }
        val name = playerName?.takeIf { VALID_NAME.matches(it) }
        val lines = if (name != null) kept + "$ARG_PLAYER_NAME=$name" else kept
        if (lines.isEmpty()) {
            file.delete()
        } else {
            file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    /** What Minecraft accepts as a player name */
    private val VALID_NAME = Regex("[A-Za-z0-9_]{3,16}")
}
