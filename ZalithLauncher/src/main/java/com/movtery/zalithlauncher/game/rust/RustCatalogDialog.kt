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

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.addon.platform.Platform
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.MarqueeText
import com.movtery.zalithlauncher.ui.components.ScalingLabel
import com.movtery.zalithlauncher.ui.components.SmallOutlinedEditField
import com.movtery.zalithlauncher.ui.screens.content.download.addon.elements.AddonIcon
import com.movtery.zalithlauncher.ui.screens.content.download.addon.elements.InstalledModBadge
import com.movtery.zalithlauncher.ui.screens.content.download.addon.elements.PlatformIdentifier
import com.movtery.zalithlauncher.ui.screens.content.elements.backgroundGlass
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import com.movtery.zalithlauncher.utils.animation.getAnimateTween
import com.movtery.zalithlauncher.utils.formatNumberByLocale
import io.github.juanluci.starling.EngineCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Rust Edition's catalog of mods, resource packs and shader packs, as the launcher's own
 * screen: the same result list, cards, type and words the launcher's downloads have.
 *
 * The game asks for it ([RustGameActivity.openCatalog]) where it would show its own; what is
 * listed, installed and removed is still the engine's doing ([EngineCatalog]), so the game
 * finds it all where it expects.
 */
class RustCatalogDialog(
    activity: Activity,
    private val shelf: Int
) : ComponentDialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        setContentView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ZalithLauncherTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            CatalogScreen(firstShelf = shelf, onBack = { dismiss() })
                        }
                    }
                }
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}

/** A project the engine's catalog found */
private data class CatalogRow(
    /** What the engine knows it by when it is installed or removed */
    val handle: Int,
    val id: String,
    val title: String,
    val summary: String,
    val author: String,
    val downloads: Long,
    val icon: String?,
    val source: String,
    /** What the engine wants said before it is installed, if anything */
    val caution: String?
)

private sealed interface CatalogResult {
    data object Searching : CatalogResult
    data class Failed(val message: String) : CatalogResult
    data class Found(val rows: List<CatalogRow>, val more: Boolean) : CatalogResult
}

/** The engine's page size (starling-catalog's list::PAGE) */
private const val PAGE_SIZE = 20

private fun JSONObject.text(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun parsePage(json: String): Pair<CatalogResult, Set<String>> {
    val root = JSONObject(json)
    root.text("error")?.let { return CatalogResult.Failed(it) to emptySet() }
    val list = root.optJSONArray("rows") ?: JSONArray()
    val installed = HashSet<String>()
    val rows = (0 until list.length()).map { index ->
        val row = list.getJSONObject(index)
        val id = row.optString("id")
        if (row.optBoolean("installed")) installed.add(id)
        CatalogRow(
            handle = row.optInt("handle"),
            id = id,
            title = row.optString("title"),
            summary = row.optString("summary"),
            author = row.optString("author"),
            downloads = row.optLong("downloads"),
            icon = row.text("icon"),
            source = row.optString("source"),
            caution = row.text("caution")
        )
    }
    return CatalogResult.Found(rows, root.optBoolean("more")) to installed
}

private fun parseInstalled(json: String): Set<String> {
    val list = JSONArray(json)
    return (0 until list.length()).mapTo(HashSet()) { list.getString(it) }
}

@Composable
private fun CatalogScreen(
    firstShelf: Int,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var shelf by remember { mutableIntStateOf(firstShelf) }
    var typed by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }

    var result by remember { mutableStateOf<CatalogResult>(CatalogResult.Searching) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(shelf, asked, page, reload) {
        result = CatalogResult.Searching
        val found = withContext(Dispatchers.IO) {
            runCatching { parsePage(EngineCatalog.search(shelf, asked, false, page * PAGE_SIZE)) }
        }
        found.onSuccess { (rows, has) ->
            result = rows
            if (rows is CatalogResult.Found) {
                installed = installed - rows.rows.map { it.id }.toSet() + has
            }
        }.onFailure {
            result = CatalogResult.Failed(it.message ?: it.toString())
        }
    }

    // the project that was tapped, the one being installed or removed, and what came of it
    var picked by remember { mutableStateOf<CatalogRow?>(null) }
    var working by remember { mutableStateOf<CatalogRow?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressLine by remember { mutableStateOf("") }

    LaunchedEffect(working) {
        progress = 0f
        progressLine = ""
        while (working != null) {
            runCatching {
                val parts = EngineCatalog.progress().split('\t', limit = 2)
                progress = (parts.getOrNull(0)?.toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
                progressLine = parts.getOrNull(1).orEmpty()
            }
            delay(150)
        }
    }

    fun change(row: CatalogRow, remove: Boolean) {
        if (working != null) return
        working = row
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                runCatching {
                    val answer = JSONObject(
                        if (remove) EngineCatalog.remove(row.handle)
                        else EngineCatalog.install(row.handle, false)
                    )
                    answer.text("error") to parseInstalled(EngineCatalog.installed())
                }
            }
            done.onSuccess { (error, now) ->
                failure = error
                installed = now
            }.onFailure {
                failure = it.message ?: it.toString()
            }
            working = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CatalogResults(
            modifier = Modifier
                .weight(7f)
                .fillMaxHeight(),
            result = result,
            page = page,
            installed = installed,
            onReload = { reload++ },
            onPreviousPage = { if (page > 0) page-- },
            onNextPage = { page++ },
            onPick = { picked = it }
        )

        CatalogFilter(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight(),
            shelf = shelf,
            typed = typed,
            onTyped = { typed = it },
            onSearch = {
                page = 0
                asked = typed.trim()
                reload++
            },
            onShelf = {
                if (shelf != it) {
                    page = 0
                    shelf = it
                }
            },
            onBack = onBack
        )
    }

    picked?.let { row ->
        val isInstalled = row.id in installed
        AlertDialog(
            onDismissRequest = { picked = null },
            title = {
                Text(text = row.title, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = row.summary, style = MaterialTheme.typography.bodyMedium)
                    row.caution?.takeIf { !isInstalled }?.let { caution ->
                        Text(
                            text = caution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        picked = null
                        change(row, remove = isInstalled)
                    }
                ) {
                    Text(
                        text = stringResource(
                            if (isInstalled) R.string.generic_delete else R.string.download_install
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { picked = null }) {
                    Text(text = stringResource(R.string.generic_cancel))
                }
            }
        )
    }

    working?.let { row ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(text = row.title, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = { progress }
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = progressLine.ifBlank { stringResource(R.string.generic_loading) },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {}
        )
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = {
                Text(
                    text = stringResource(R.string.generic_error),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { failure = null }) {
                    Text(text = stringResource(R.string.generic_confirm))
                }
            }
        )
    }
}

/** The result list of the launcher's downloads: the cards, and the page controller over them */
@Composable
private fun CatalogResults(
    modifier: Modifier,
    result: CatalogResult,
    page: Int,
    installed: Set<String>,
    onReload: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPick: (CatalogRow) -> Unit
) {
    Box(modifier = modifier) {
        when (result) {
            is CatalogResult.Searching -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(168.dp)
                        .align(Alignment.Center)
                )
            }

            is CatalogResult.Failed -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(onClick = onReload),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScalingLabel(text = stringResource(R.string.download_addon_failed_to_get_result))
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = result.message,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            is CatalogResult.Found -> {
                if (result.rows.isEmpty()) {
                    ScalingLabel(
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(R.string.generic_no_matching_items)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 60.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(result.rows, key = { it.handle }) { row ->
                            CatalogProject(
                                modifier = Modifier.fillMaxWidth(),
                                row = row,
                                isInstalled = row.id in installed,
                                onClick = { onPick(row) }
                            )
                        }
                    }
                }
            }
        }

        CatalogPageController(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(54.dp),
            page = page,
            hasNext = result is CatalogResult.Found && result.more,
            onPreviousPage = onPreviousPage,
            onNextPage = onNextPage
        )
    }
}

@Composable
private fun CatalogPageController(
    modifier: Modifier,
    page: Int,
    hasNext: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    val color = cardColor()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = onCardColor()
    ) {
        Row(
            modifier = Modifier
                .backgroundGlass(AllSettings.backgroundBlur.state, color, true)
                .padding(all = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = "${page + 1}",
                style = MaterialTheme.typography.labelLarge
            )

            IconButton(
                enabled = page > 0,
                onClick = onPreviousPage
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left_rounded),
                    contentDescription = stringResource(R.string.download_addon_result_previous_page)
                )
            }

            IconButton(
                enabled = hasNext,
                onClick = onNextPage
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right_rounded),
                    contentDescription = stringResource(R.string.download_addon_result_next_page)
                )
            }
        }
    }
}

/** A project's card, laid out as the launcher's downloads lay theirs out */
@Composable
private fun CatalogProject(
    modifier: Modifier,
    row: CatalogRow,
    isInstalled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val color = cardColor()

    val scale = remember { Animatable(initialValue = 0.95f) }
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = getAnimateTween())
    }

    Surface(
        modifier = modifier.graphicsLayer(scaleY = scale.value, scaleX = scale.value),
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = onCardColor(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .backgroundGlass(AllSettings.backgroundBlur.state, color, true)
                .padding(all = 8.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AddonIcon(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(10.dp))
                    .align(Alignment.CenterVertically),
                size = 72.dp,
                iconUrl = row.icon
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarqueeText(
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(iterations = Int.MAX_VALUE),
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    when (row.source) {
                        "modrinth" -> PlatformIdentifier(platform = Platform.MODRINTH)
                        "curseforge" -> PlatformIdentifier(platform = Platform.CURSEFORGE)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .alpha(0.7f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (row.author.isNotBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    modifier = Modifier.size(14.dp),
                                    painter = painterResource(R.drawable.ic_person_filled),
                                    contentDescription = null
                                )
                                MarqueeText(
                                    text = row.author,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }

                        if (row.downloads > 0L) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    modifier = Modifier.size(14.dp),
                                    painter = painterResource(R.drawable.ic_download_2_filled),
                                    contentDescription = null
                                )
                                Text(
                                    text = formatNumberByLocale(context, row.downloads),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Text(
                        text = row.summary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isInstalled) {
                        InstalledModBadge()
                    }
                }
            }
        }
    }
}

/** Beside the results, where the launcher's downloads have their filters: search and kind */
@Composable
private fun CatalogFilter(
    modifier: Modifier,
    shelf: Int,
    typed: String,
    onTyped: (String) -> Unit,
    onSearch: () -> Unit,
    onShelf: (Int) -> Unit,
    onBack: () -> Unit
) {
    val color = cardColor()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = onCardColor()
    ) {
        Column(
            modifier = Modifier
                .backgroundGlass(AllSettings.backgroundBlur.state, color, true)
                .padding(all = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.generic_back)
                    )
                }
                Text(
                    text = stringResource(R.string.generic_download),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            SmallOutlinedEditField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                value = typed,
                onValueChange = onTyped,
                placeholder = {
                    Text(
                        text = stringResource(R.string.generic_search),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.generic_search)
                        )
                    }
                },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                singleLine = true
            )

            CatalogShelf(
                text = stringResource(R.string.mods_manage),
                selected = shelf == EngineCatalog.SHELF_MODS,
                onClick = { onShelf(EngineCatalog.SHELF_MODS) }
            )
            CatalogShelf(
                text = stringResource(R.string.resource_pack_manage),
                selected = shelf == EngineCatalog.SHELF_RESOURCE_PACKS,
                onClick = { onShelf(EngineCatalog.SHELF_RESOURCE_PACKS) }
            )
            CatalogShelf(
                text = stringResource(R.string.shader_pack_manage),
                selected = shelf == EngineCatalog.SHELF_SHADER_PACKS,
                onClick = { onShelf(EngineCatalog.SHELF_SHADER_PACKS) }
            )
        }
    }
}

@Composable
private fun CatalogShelf(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
