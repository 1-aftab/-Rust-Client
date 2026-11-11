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

package com.movtery.zalithlauncher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The launcher's two looks, and the only ones: white and dark. Clean neutrals with the
// faintest lilac cast, a soft violet for what is pressed, pink and mint beside it.
// The engine's own screens use the dark one (RustEngine patch 0007, rust_engine.xml).

/** The seed the views outside Compose derive their colours from */
const val RUST_CLIENT_SEED_LIGHT = 0xFF7C5CBF.toInt()
const val RUST_CLIENT_SEED_DARK = 0xFFD3BBFF.toInt()

val rustClientLight = lightColorScheme(
    primary = Color(0xFF7C5CBF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF2A1057),
    secondary = Color(0xFFB34D80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E6),
    onSecondaryContainer = Color(0xFF3E0A24),
    tertiary = Color(0xFF2F7F7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9F1EC),
    onTertiaryContainer = Color(0xFF00201E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B22),
    surfaceVariant = Color(0xFFECE6F2),
    onSurfaceVariant = Color(0xFF4B4556),
    outline = Color(0xFF7C7588),
    outlineVariant = Color(0xFFD5CEDD),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF322F38),
    inverseOnSurface = Color(0xFFF6EFFA),
    inversePrimary = Color(0xFFD3BBFF),
    surfaceDim = Color(0xFFE3DDEA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF7FD),
    surfaceContainer = Color(0xFFF5F1FA),
    surfaceContainerHigh = Color(0xFFEFEAF5),
    surfaceContainerHighest = Color(0xFFE9E3F0),
)

val rustClientDark = darkColorScheme(
    primary = Color(0xFFD3BBFF),
    onPrimary = Color(0xFF3A1F73),
    primaryContainer = Color(0xFF52398C),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFFFB0CD),
    onSecondary = Color(0xFF5A1238),
    secondaryContainer = Color(0xFF76294F),
    onSecondaryContainer = Color(0xFFFFD9E6),
    tertiary = Color(0xFF9FD6D0),
    onTertiary = Color(0xFF003735),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFC9F1EC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121016),
    onBackground = Color(0xFFEAE4F0),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFEAE4F0),
    surfaceVariant = Color(0xFF474250),
    onSurfaceVariant = Color(0xFFCBC3D6),
    outline = Color(0xFF958E9F),
    outlineVariant = Color(0xFF474250),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFEAE4F0),
    inverseOnSurface = Color(0xFF322F38),
    inversePrimary = Color(0xFF7C5CBF),
    surfaceDim = Color(0xFF121016),
    surfaceBright = Color(0xFF39353F),
    surfaceContainerLowest = Color(0xFF0D0B11),
    surfaceContainerLow = Color(0xFF1A181F),
    surfaceContainer = Color(0xFF1F1C24),
    surfaceContainerHigh = Color(0xFF29262F),
    surfaceContainerHighest = Color(0xFF34313A),
)
