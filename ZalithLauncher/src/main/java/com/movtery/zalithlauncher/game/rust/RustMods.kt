package com.movtery.zalithlauncher.game.rust

import androidx.compose.runtime.mutableStateMapOf
import java.io.File

/**
 * The mods built into the Rust Edition, in categories, and which of them are on: the game
 * menu's "Mods" page. Every mod is off until the player turns it on. The choice is kept in the
 * engine's own `horizon-mods.txt` in the game folder (`mod.<id>:true` lines), which the engine
 * reads again when it changes, so a switch acts in the running game and stays after a restart.
 */
class RustMods(gameFolder: File) {
    data class Mod(val id: String, val name: String, val about: String)
    data class Category(val title: String, val mods: List<Mod>)

    private val file = File(gameFolder, FILE)
    private val on = mutableStateMapOf<String, Boolean>()

    init {
        reload()
    }

    /** Reads the file again (the game's own Options > Mods page writes it too) */
    fun reload() {
        runCatching {
            val found = HashMap<String, Boolean>()
            if (file.isFile) {
                file.readLines().forEach { line ->
                    val at = line.lastIndexOf(':')
                    if (at > 0 && line.startsWith(PREFIX)) {
                        found[line.substring(PREFIX.length, at).trim()] = line.substring(at + 1).trim() == "true"
                    }
                }
            }
            ALL.forEach { on[it.id] = found[it.id] == true }
        }
    }

    fun isOn(mod: Mod): Boolean = on[mod.id] == true

    fun setOn(mod: Mod, value: Boolean) {
        on[mod.id] = value
        save()
    }

    /** How many of a category's mods are on */
    fun countOn(category: Category): Int = category.mods.count { isOn(it) }

    fun setAll(category: Category, value: Boolean) {
        category.mods.forEach { on[it.id] = value }
        save()
    }

    fun allOff() {
        ALL.forEach { on[it.id] = false }
        save()
    }

    private fun save() {
        runCatching {
            // the engine's other lines (the keys chosen for mods, mods this page doesn't know) stay
            val known = ALL.map { it.id }.toSet()
            val kept = if (file.isFile) {
                file.readLines().filter { line ->
                    val at = line.lastIndexOf(':')
                    line.isNotBlank() &&
                        !(at > 0 && line.startsWith(PREFIX) && line.substring(PREFIX.length, at).trim() in known)
                }
            } else {
                emptyList()
            }
            val lines = ALL.map { "$PREFIX${it.id}:${on[it.id] == true}" } + kept
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE.tmp")
            temp.writeText(lines.joinToString("\n", postfix = "\n"))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    companion object {
        const val FILE = "horizon-mods.txt"
        private const val PREFIX = "mod."

        // The ids are RustEngine/tools/builtin-mods.txt's: a mod added there gets a row here.
        val CATEGORIES: List<Category> = listOf(
            Category(
                "HUD",
                listOf(
                    Mod("fps_overlay", "FPS Counter", "Frames per second in a corner"),
                    Mod("keystrokes", "Keystrokes", "Movement keys and clicks, lit while held"),
                    Mod("minimap", "Minimap", "A map of the world around you"),
                    Mod("hud_plus", "HUD+", "One configurable HUD with its own editor"),
                    Mod("appleskin", "AppleSkin", "Food and saturation details"),
                    Mod("armor_status", "Armor Status", "Your armour and its durability"),
                    Mod("coords_hud", "Coordinates HUD", "Your position and direction"),
                    Mod("held_item_info", "Held Item Info", "Details of the item in your hand"),
                    Mod("crosshair_indicator", "Crosshair Indicator", "Hints at the crosshair"),
                    Mod("status_effect_hud", "Status Effect HUD", "Active effects with their time"),
                    Mod("toggle_sprint", "Toggle Sprint", "Toggle sprint and sneak, with a display"),
                    Mod("lookat", "LookAt", "What your crosshair is on, as a tooltip"),
                    Mod("smartf3", "SmartF3", "A tidier debug screen")
                )
            ),
            Category(
                "Camera and view",
                listOf(
                    Mod("zoom", "Zoom", "Hold a key to zoom in"),
                    Mod("freelook", "Freelook", "Look around without turning"),
                    Mod("fullbright_horizon", "Fullbright", "See in the dark (key G)"),
                    Mod("light_overlay", "Light Overlay", "Marks where mobs can spawn"),
                    Mod("waypoints", "Waypoints", "Named places with their distance")
                )
            ),
            Category(
                "Inventory and items",
                listOf(
                    Mod("recipes", "Recipes", "Every item with its recipes and uses"),
                    Mod("mousetricks", "Mouse Tricks", "Drag and wheel tricks in inventories"),
                    Mod("inventory_profiles", "Inventory Profiles", "Sorting and auto-refill"),
                    Mod("itemnew", "Item New", "New pick-ups glow until you look at them"),
                    Mod("shulker_peek", "ShulkerPeek", "A shulker box's contents in its tooltip"),
                    Mod("rarity_borders", "Rarity Borders", "Coloured borders by rarity"),
                    Mod("map_preview", "Map Preview", "A map's picture in its tooltip"),
                    Mod("enchantmenthints", "Enchantment Hints", "What an enchantment does"),
                    Mod("fast_trading", "Fast Trading", "Quicker trades with villagers"),
                    Mod("fast_items", "Fast Items", "Simpler drawing of dropped items")
                )
            ),
            Category(
                "Chat",
                listOf(
                    Mod("chat_heads_rust", "Chat Heads", "The sender's head beside a message"),
                    Mod("chat_animation", "Chat Animation", "Messages slide in")
                )
            ),
            Category(
                "Visuals and effects",
                listOf(
                    Mod("eatinganimation", "Eating Animation", "Food is eaten bite by bite"),
                    Mod("blood_particles", "Blood Particles", "Particles on hits"),
                    Mod("visuality_horizon", "Visuality", "Small extra particles"),
                    Mod("elytratrails", "ElytraTrails", "Trails behind elytra wings"),
                    Mod("smoothie_capes", "Smoothie Capes", "Capes that wave"),
                    Mod("connected_textures", "Connected Textures", "Glass and more joined up")
                )
            ),
            Category(
                "Gameplay helpers",
                listOf(
                    Mod("right_click_crops", "Right-Click Crops", "Harvest and replant in one click"),
                    Mod("veinminer", "Veinminer", "Mine a whole vein at once"),
                    Mod("treefall", "TreeFall", "Cut one log, the tree comes down"),
                    Mod("accurate_block_placement", "Accurate Block Placement", "Place blocks as fast as you move")
                )
            )
        )
        val ALL: List<Mod> = CATEGORIES.flatMap { it.mods }
    }
}
