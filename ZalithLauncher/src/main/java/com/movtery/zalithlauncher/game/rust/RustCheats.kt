package com.movtery.zalithlauncher.game.rust

import androidx.compose.runtime.mutableStateMapOf
import java.io.File

/**
 * The Rust Edition's cheat modules: the switches and values of the game menu's "Cheats" page.
 * They are kept in `rust-client-cheats.txt` in the game folder (`key=value` lines), which the
 * engine reads again twice a second (its `rc_cheats.rs`), so a switch acts at once.
 */
class RustCheats(gameFolder: File) {
    /** A row of the page: a switch, or a slider whose value is [scale] times what is written */
    sealed interface Row {
        val key: String
        val label: String
    }
    data class Switch(override val key: String, override val label: String) : Row
    data class Slider(
        override val key: String,
        override val label: String,
        val min: Int,
        val max: Int,
        val default: Int,
        /** The slider's steps in one unit of the file's value (10: tenths) */
        val scale: Int = 1,
        val suffix: String? = null,
        /** The switch this value belongs to */
        val of: String
    ) : Row
    data class Header(override val label: String) : Row {
        override val key: String = ""
    }

    private val file = File(gameFolder, FILE)
    private val values = mutableStateMapOf<String, String>()

    init {
        runCatching {
            if (file.isFile) {
                file.readLines().forEach { line ->
                    val at = line.indexOf('=')
                    if (at > 0) values[line.substring(0, at).trim()] = line.substring(at + 1).trim()
                }
            }
        }
    }

    fun isOn(key: String): Boolean =
        values[key]?.let { it == "true" || it == "1" } ?: (key in ON_BY_DEFAULT)

    fun setOn(key: String, on: Boolean) {
        values[key] = on.toString()
        save()
    }

    /** A slider's place, in its own steps */
    fun value(row: Slider): Int =
        values[row.key]?.toFloatOrNull()?.let { (it * row.scale).toInt().coerceIn(row.min, row.max) }
            ?: row.default

    /** Moves a slider; [write] is false while it is still being dragged */
    fun setValue(row: Slider, value: Int, write: Boolean) {
        values[row.key] =
            if (row.scale == 1) value.toString() else (value.toFloat() / row.scale).toString()
        if (write) save()
    }

    /** Switches every module off (the values stay) */
    fun allOff() {
        ROWS.filterIsInstance<Switch>().filter { it.key !in ON_BY_DEFAULT }.forEach { values[it.key] = "false" }
        save()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE.tmp")
            temp.writeText(values.entries.sortedBy { it.key }.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    companion object {
        const val FILE = "rust-client-cheats.txt"

        /** The choices of what to attack and show, which start on */
        private val ON_BY_DEFAULT = setOf("target_players", "target_hostile", "show_players", "show_hostile")

        val ROWS: List<Row> = listOf(
            Header("Combat"),
            Switch("killaura", "Kill Aura"),
            Slider("aura_range", "Kill Aura range", 10, 60, 35, 10, " blocks", "killaura"),
            Switch("triggerbot", "Trigger Bot"),
            Slider("trigger_delay", "Trigger Bot delay", 0, 20, 1, 1, " ticks", "triggerbot"),
            Switch("target_players", "Attack players"),
            Switch("target_hostile", "Attack monsters"),
            Switch("target_passive", "Attack animals"),
            Switch("criticals", "Criticals"),
            Switch("reach", "Reach"),
            Slider("reach_extra", "Extra reach", 0, 30, 15, 10, " blocks", "reach"),
            Switch("velocity", "Velocity (less knockback)"),
            Slider("velocity_percent", "Knockback taken", 0, 100, 0, 1, "%", "velocity"),
            Header("Movement"),
            Switch("sprint", "Sprint (always)"),
            Switch("fly", "Fly"),
            Slider("fly_speed", "Fly speed", 2, 100, 10, 10, "x", "fly"),
            Switch("speed", "Speed"),
            Slider("speed_factor", "Speed factor", 10, 50, 15, 10, "x", "speed"),
            Switch("nofall", "No Fall"),
            Switch("step", "Step"),
            Slider("step_height", "Step height", 6, 100, 15, 10, " blocks", "step"),
            Switch("spider", "Spider (climb walls)"),
            Switch("jesus", "Jesus (water walk)"),
            Header("World"),
            Switch("esp", "ESP (boxes through walls)"),
            Switch("tracers", "Tracers"),
            Switch("show_players", "Show players"),
            Switch("show_hostile", "Show monsters"),
            Switch("show_passive", "Show animals"),
            Switch("xray", "X-Ray (ore and chest finder)"),
            Switch("automine", "Auto Mine"),
            Switch("antiafk", "Anti AFK")
        )
    }
}
