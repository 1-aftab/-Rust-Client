package com.movtery.zalithlauncher.game.rust

import androidx.compose.runtime.mutableStateMapOf
import java.io.File

/**
 * The Rust Edition's quality-of-life features: what the game menu's "QOL" page shows and
 * switches. A feature has a page of its own, in sections. The choices are kept in
 * `rust-client-qol.txt` in the game folder (`key=value` lines), which the engine reads again
 * twice a second (its `rc_qol.rs`), so a switch acts at once.
 */
class RustQol(gameFolder: File) {
    /** A feature: its own switch ([key]) and the sections of its page */
    data class Feature(
        val key: String,
        val title: String,
        val summary: String,
        val sections: List<Section> = emptyList()
    )
    data class Section(val title: String, val rows: List<Row>)
    sealed interface Row {
        val key: String
        val label: String
    }
    data class Switch(
        override val key: String,
        override val label: String,
        val default: Boolean = false
    ) : Row
    data class Slider(
        override val key: String,
        override val label: String,
        val min: Int,
        val max: Int,
        val default: Int,
        val suffix: String? = null
    ) : Row

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

    /** Whether the feature itself is switched on */
    fun isOn(feature: Feature): Boolean = values[feature.key] == "true"

    fun setOn(feature: Feature, on: Boolean) {
        values[feature.key] = on.toString()
        save()
    }

    fun isOn(row: Switch): Boolean = values[row.key]?.let { it == "true" } ?: row.default

    fun setOn(row: Switch, on: Boolean) {
        values[row.key] = on.toString()
        save()
    }

    fun value(row: Slider): Int =
        values[row.key]?.toIntOrNull()?.coerceIn(row.min, row.max) ?: row.default

    /** Moves a slider; [write] is false while it is still being dragged */
    fun setValue(row: Slider, value: Int, write: Boolean) {
        values[row.key] = value.toString()
        if (write) save()
    }

    /** Sets every switch of a section at once */
    fun setAll(section: Section, on: Boolean) {
        section.rows.filterIsInstance<Switch>().forEach { values[it.key] = on.toString() }
        save()
    }

    /** Puts a feature's page back to how it comes (the feature's own switch stays) */
    fun reset(feature: Feature) {
        feature.sections.forEach { section -> section.rows.forEach { values.remove(it.key) } }
        save()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE.tmp")
            temp.writeText(
                values.entries.sortedBy { it.key }
                    .joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }
            )
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    companion object {
        const val FILE = "rust-client-qol.txt"

        // The keys and what they start as are the engine's (rc_qol.rs: `Qol::off`, `XRAY_GROUPS`).
        val FEATURES: List<Feature> = listOf(
            Feature(
                key = "sprint",
                title = "Auto Sprint",
                summary = "Sprints whenever you move forward"
            ),
            Feature(
                key = "xray",
                title = "X-Ray",
                summary = "Draws only the blocks you choose",
                sections = listOf(
                    Section(
                        "Look",
                        listOf(Switch("xray_bright", "Full brightness", true))
                    ),
                    Section(
                        "Overworld ores",
                        listOf(
                            Switch("xray_diamond", "Diamond", true),
                            Switch("xray_emerald", "Emerald", true),
                            Switch("xray_gold", "Gold", true),
                            Switch("xray_iron", "Iron", true),
                            Switch("xray_copper", "Copper", true),
                            Switch("xray_redstone", "Redstone", true),
                            Switch("xray_lapis", "Lapis lazuli", true),
                            Switch("xray_coal", "Coal", true)
                        )
                    ),
                    Section(
                        "Nether ores",
                        listOf(
                            Switch("xray_debris", "Ancient debris", true),
                            Switch("xray_quartz", "Nether quartz", true),
                            Switch("xray_nether_gold", "Nether gold", true)
                        )
                    ),
                    Section(
                        "Storage",
                        listOf(
                            Switch("xray_chests", "Chests (also trapped and ender)", true),
                            Switch("xray_barrels", "Barrels", true),
                            Switch("xray_shulkers", "Shulker boxes", true)
                        )
                    ),
                    Section(
                        "Structures",
                        listOf(
                            Switch("xray_spawners", "Spawners and vaults", true),
                            Switch("xray_portal", "Portals and portal frames", true),
                            Switch("xray_amethyst", "Amethyst", false)
                        )
                    ),
                    Section(
                        "Liquids",
                        listOf(
                            Switch("xray_lava", "Lava", true),
                            Switch("xray_water", "Water", false)
                        )
                    )
                )
            ),
            Feature(
                key = "trigger",
                title = "Trigger Bot",
                summary = "Attacks what your crosshair is on",
                sections = listOf(
                    Section(
                        "Targets",
                        listOf(
                            Switch("trigger_players", "Players", true),
                            Switch("trigger_monsters", "Monsters", true),
                            Switch("trigger_animals", "Animals", false)
                        )
                    ),
                    Section(
                        "Conditions",
                        listOf(
                            Switch("trigger_weapon_only", "Only with a weapon in hand", false),
                            Switch("trigger_cooldown", "Wait for the attack to recharge", true),
                            Switch("trigger_falling", "Only while falling (critical hits)", false)
                        )
                    ),
                    Section(
                        "Timing",
                        listOf(
                            Slider("trigger_delay", "Delay before the hit", 0, 40, 1, " ticks"),
                            Slider("trigger_random", "Random extra delay, up to", 0, 40, 0, " ticks")
                        )
                    )
                )
            )
        )
    }
}
