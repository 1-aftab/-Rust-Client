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
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.wardrobe.SkinModelType
import io.github.juanluci.starling.EngineHost
import io.github.juanluci.starling.EngineSetup
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
    const val ENGINE_NAME = "Rust"

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
    private const val ARG_HOST_ACCOUNTS = "RUST_CLIENT_HOST_ACCOUNTS"

    /** The engine's player file and skins folder, beside its options */
    private const val PLAYER_FILE = "horizon-player.txt"
    private const val SKINS_FOLDER = "skins"
    private const val LAUNCHER_SKIN = "launcher.png"

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
     * Starts the Rust Edition as the launcher's current account.
     *
     * The launcher is the only place accounts and skins are managed: the engine is told so
     * and shows no account or skin screens of its own. The account's name and skin are
     * handed over here, at every start.
     */
    /** Intent extra of [RustGameActivity]: the catalog shelf to open once the game is up */
    const val EXTRA_CATALOG = "rust_client_catalog"

    /**
     * Starts the game. With [catalog] (a shelf of the engine's catalog: resource packs, shader
     * packs) the launcher's download screen for it opens over the game as soon as it is up:
     * the catalog is the engine's own, so it needs the engine running.
     */
    fun launch(context: Context, account: Account?, catalog: Int? = null) {
        runCatching { writeArguments(context, account?.username) }
        runCatching { writePlayer(context, account) }
        gameFolder(context).mkdirs()
        // Game files not prepared yet: the launcher's own download screen first. The engine's
        // window opens only once there is a game to show.
        val intent = if (EngineSetup.prepared(context)) {
            Intent(context, RustGameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { catalog?.let { putExtra(EXTRA_CATALOG, it) } }
        } else {
            RustSetupActivity.intent(context)
        }
        context.startActivity(intent)
    }

    /**
     * The engine's arguments file: the player's name, and that the accounts are the
     * launcher's. Lines the player wrote there are kept.
     */
    private fun writeArguments(context: Context, playerName: String?) {
        val file = File(EngineHost.filesDir(context), ARGS_FILE)
        val ours = listOf("$ARG_PLAYER_NAME=", "$ARG_HOST_ACCOUNTS=")
        val kept = if (file.isFile) {
            file.readLines().filterNot { line -> ours.any { line.trim().startsWith(it) } }
        } else {
            emptyList()
        }
        val lines = kept.toMutableList()
        lines += "$ARG_HOST_ACCOUNTS=1"
        playerName?.takeIf { VALID_NAME.matches(it) }?.let { lines += "$ARG_PLAYER_NAME=$it" }
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    /**
     * The engine's player file in the game folder (`key:value` lines): the name worlds open
     * with, and the skin the player wears, copied from the launcher's account.
     */
    private fun writePlayer(context: Context, account: Account?) {
        val folder = gameFolder(context).apply { mkdirs() }
        val skins = File(folder, SKINS_FOLDER).apply { mkdirs() }
        val skin = File(skins, LAUNCHER_SKIN)
        val source = account?.getSkinFile()?.takeIf { it.isFile }
        if (source != null) {
            source.copyTo(skin, overwrite = true)
        } else {
            skin.delete()
        }

        val values = LinkedHashMap<String, String?>()
        val name = account?.username?.takeIf { VALID_NAME.matches(it) }
        if (name != null) {
            values["name"] = name
            values["chosen"] = "true"
        }
        values["skin"] = if (source != null) LAUNCHER_SKIN else null
        values["model"] = if (account?.skinModelType == SkinModelType.ALEX) "slim" else "classic"

        val file = File(folder, PLAYER_FILE)
        val lines = if (file.isFile) file.readLines().toMutableList() else mutableListOf()
        lines.removeAll { line -> values.keys.any { line.startsWith("$it:") } }
        values.forEach { (key, value) -> if (value != null) lines += "$key:$value" }
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    /** What Minecraft accepts as a player name */
    private val VALID_NAME = Regex("[A-Za-z0-9_]{3,16}")
}
