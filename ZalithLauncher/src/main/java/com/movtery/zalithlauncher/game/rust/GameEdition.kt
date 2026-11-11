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

/**
 * The two games Rust Client can start.
 *
 * [JAVA] is Minecraft: Java Edition, launched by the Zalith launch pipeline
 * (a JVM, LWJGL, a renderer plugin). [RUST] is the Rust Edition: the native engine
 * of the `:RustEngine` module, which needs no JVM and draws with Vulkan.
 */
enum class GameEdition {
    JAVA,
    RUST
}
