package com.movtery.zalithlauncher.game.rust

import androidx.compose.runtime.mutableStateMapOf
import java.io.File

/**
 * The Rust Edition's quality-of-life switches: the rows of the game menu's "QOL" page.
 * They are kept in `rust-client-qol.txt` in the game folder (`key=value` lines), which the
 * engine reads again twice a second (its `rc_qol.rs`), so a switch acts at once.
 */
class RustQol(gameFolder: File) {
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

    /** Switches everything off */
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
        const val FILE = "rust-client-qol.txt"

        /** Switches that start on (none) */
        private val ON_BY_DEFAULT = emptySet<String>()

        val ROWS: List<Row> = listOf(
            Switch("sprint", "Auto Sprint"),
            Switch("xray", "X-Ray (ores, chests, spawners, lava)")
        )
    }
}
