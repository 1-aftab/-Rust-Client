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

package com.movtery.zalithlauncher.ui.screens.content.elements

import io.github.juanluci.starling.EngineCatalog
import com.movtery.zalithlauncher.game.rust.RustHudEditor
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.filemanager.FileManagerLauncher
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.rust.GameEdition
import com.movtery.zalithlauncher.game.rust.RustEdition
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.BackgroundCard

/** The text that explains why the Rust Edition cannot start */
@Composable
fun rustEditionProblemText(problem: RustEdition.Problem): String = when (problem) {
    RustEdition.Problem.NOT_INCLUDED -> stringResource(R.string.rust_edition_problem_not_included)
    RustEdition.Problem.NO_VULKAN -> stringResource(R.string.rust_edition_problem_no_vulkan)
}

/**
 * Home screen card: chooses between the Java Edition and the Rust Edition.
 * The choice is kept in the settings and decides what the launch button starts.
 */
@Composable
fun GameEditionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val edition = AllSettings.gameEdition.state
    val problem = remember { runCatching { RustEdition.problem(context) }.getOrNull() }

    BackgroundCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.game_edition_title),
                style = MaterialTheme.typography.titleSmall
            )

            // Two plain buttons: the chosen edition is the filled one.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.game_edition_java),
                    selected = edition == GameEdition.JAVA,
                    onClick = { AllSettings.gameEdition.save(GameEdition.JAVA) }
                )
                EditionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.game_edition_rust),
                    selected = edition == GameEdition.RUST,
                    onClick = { AllSettings.gameEdition.save(GameEdition.RUST) }
                )
            }

            when (edition) {
                GameEdition.JAVA -> Text(
                    modifier = Modifier.alpha(0.8f),
                    text = stringResource(R.string.game_edition_java_summary),
                    style = MaterialTheme.typography.bodySmall
                )

                GameEdition.RUST -> {
                    Text(
                        modifier = Modifier.alpha(0.8f),
                        text = stringResource(
                            R.string.game_edition_rust_summary,
                            RustEdition.MINECRAFT_VERSION,
                            RustEdition.ENGINE_NAME
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (problem != null) {
                        Text(
                            text = rustEditionProblemText(problem),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    RustFolderRow()
                }
            }
        }
    }
}

@Composable
private fun EditionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Home screen card of Rust Client Lite, where the edition selector is in Rust Client: there is
 * one edition, so the card holds what belongs to it. "Customise HUD" is the launcher's own
 * editor (the frames are where the game last drew its HUD); the pack downloads are the
 * launcher's screen over the engine's catalog, which needs the engine running, so they start
 * the game and open over it.
 */
@Composable
fun RustLiteCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val problem = remember { runCatching { RustEdition.problem(context) }.getOrNull() }
    var hudEditor by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<RustEdition.Problem?>(null) }
    shown?.let { current ->
        RustEditionProblemDialog(problem = current, onDismiss = { shown = null })
    }
    val download: (Int) -> Unit = { shelf ->
        if (problem != null) {
            shown = problem
        } else {
            runCatching {
                RustEdition.launch(
                    context = context,
                    account = AccountsManager.currentAccountFlow.value,
                    catalog = shelf
                )
            }
        }
    }

    BackgroundCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.game_edition_rust),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                modifier = Modifier.alpha(0.8f),
                text = stringResource(
                    R.string.rust_lite_summary,
                    RustEdition.MINECRAFT_VERSION,
                    RustEdition.ENGINE_NAME
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (problem != null) {
                Text(
                    text = rustEditionProblemText(problem),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { hudEditor = true }) {
                    Text(text = stringResource(R.string.rust_lite_customise_hud), maxLines = 1)
                }
                OutlinedButton(onClick = { download(EngineCatalog.SHELF_SHADER_PACKS) }) {
                    Text(text = stringResource(R.string.rust_lite_shader_packs), maxLines = 1)
                }
                OutlinedButton(onClick = { download(EngineCatalog.SHELF_RESOURCE_PACKS) }) {
                    Text(text = stringResource(R.string.rust_lite_resource_packs), maxLines = 1)
                }
            }
            Text(
                modifier = Modifier.alpha(0.8f),
                text = stringResource(R.string.rust_lite_downloads_note),
                style = MaterialTheme.typography.labelSmall
            )
            RustFolderRow()
        }
    }

    if (hudEditor) {
        Dialog(
            onDismissRequest = { hudEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            RustHudEditor(
                gameFolder = remember(context) { RustEdition.gameFolder(context) },
                onDone = { hudEditor = false }
            )
        }
    }
}

/**
 * The bottom of the home screen's action menu while the Rust Edition is chosen: what will
 * start, and the launch button. Resources not prepared yet: the launcher's own download
 * screen opens first (RustSetupActivity).
 */
@Composable
fun RustVersionsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<RustEdition.Problem?>(null) }
    problem?.let { current ->
        RustEditionProblemDialog(problem = current, onDismiss = { problem = null })
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.game_edition_rust),
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.rust_edition_version, RustEdition.MINECRAFT_VERSION),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            onClick = {
                val found = runCatching { RustEdition.problem(context) }.getOrNull()
                if (found != null) {
                    problem = found
                } else {
                    runCatching {
                        RustEdition.launch(
                            context = context,
                            account = AccountsManager.currentAccountFlow.value
                        )
                    }
                }
            }
        ) {
            Text(
                text = stringResource(R.string.main_launch_game),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shortcuts to the Rust Edition's own folders, opened in the launcher's file manager */
@Composable
private fun RustFolderRow() {
    val context = LocalContext.current
    val folders = listOf(
        R.string.rust_edition_folder_game to null,
        R.string.rust_edition_folder_worlds to RustEdition.FOLDER_SAVES,
        R.string.rust_edition_folder_resource_packs to RustEdition.FOLDER_RESOURCE_PACKS,
        R.string.rust_edition_folder_shader_packs to RustEdition.FOLDER_SHADER_PACKS,
        R.string.rust_edition_folder_data_packs to RustEdition.FOLDER_DATA_PACKS,
        R.string.rust_edition_folder_screenshots to RustEdition.FOLDER_SCREENSHOTS
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        folders.forEach { (label, folder) ->
            AssistChip(
                onClick = {
                    runCatching {
                        val root = RustEdition.gameFolder(context).apply { mkdirs() }
                        val current = folder?.let { RustEdition.subFolder(context, it) }
                        FileManagerLauncher.launch(
                            context = context,
                            rootPath = root.absolutePath,
                            currentPath = current?.absolutePath
                        )
                    }
                },
                label = { Text(text = stringResource(label)) }
            )
        }
    }
}

/** Shown instead of starting the Rust Edition when it cannot start */
@Composable
fun RustEditionProblemDialog(
    problem: RustEdition.Problem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.game_edition_rust)) },
        text = { Text(text = rustEditionProblemText(problem)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.generic_confirm))
            }
        }
    )
}
