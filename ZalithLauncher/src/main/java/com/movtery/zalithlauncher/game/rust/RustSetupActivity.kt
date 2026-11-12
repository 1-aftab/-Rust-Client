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
import android.os.Bundle
import android.os.Process
import android.text.format.Formatter
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import io.github.juanluci.starling.EngineSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The launcher's download screen of the Rust Edition.
 *
 * The Rust engine ships none of Minecraft's files. Before the first game they are fetched
 * from Mojang and prepared on the device. The engine does that work itself
 * ([EngineSetup]); this screen starts it and shows how far it is, so that the player sees
 * the launcher here and the engine's window only once there is a game to show.
 *
 * It runs in a process of its own (`:rustsetup`), which ends with the screen: the engine
 * library is loaded here without its game, and the game needs a process it was not loaded
 * in that way.
 */
class RustSetupActivity : ComponentActivity() {
    /** What the screen shows */
    private sealed interface Step {
        /** Nothing started yet, or stopped by the player */
        data object Ready : Step
        /** Loading the engine */
        data object Starting : Step
        data class Working(val stage: String, val done: Long, val total: Long, val text: String) : Step
        data class Failed(val reason: String) : Step
        data object Done : Step
    }

    private var step by mutableStateOf<Step>(Step.Ready)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZalithLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Screen(
                        step = step,
                        onStart = ::start,
                        onCancel = ::cancel,
                        onPlay = ::play,
                        onClose = ::finish
                    )
                }
            }
        }
        if (EngineSetup.prepared(this)) {
            step = Step.Done
        }
    }

    private fun start() {
        if (step is Step.Starting || step is Step.Working) return
        step = Step.Starting
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    EngineSetup.init(applicationContext)
                    EngineSetup.start()
                }.exceptionOrNull()
            }
            if (error != null) {
                finished(Step.Failed(error.message ?: error.toString()))
                return@launch
            }
            // the work starts on a thread of the engine's: give it the time to say so
            var idle = 0
            while (isActive) {
                delay(300)
                val parts = runCatching { EngineSetup.status() }.getOrNull()
                    ?.split('\t', limit = 4)
                    ?: continue
                val stage = parts.getOrElse(0) { "" }
                val text = parts.getOrElse(3) { "" }
                when (stage) {
                    "done" -> {
                        finished(Step.Done)
                        return@launch
                    }
                    "failed" -> {
                        finished(Step.Failed(text))
                        return@launch
                    }
                    "idle" -> {
                        idle++
                        if (idle > 10) {
                            finished(if (EngineSetup.prepared(this@RustSetupActivity)) Step.Done else Step.Ready)
                            return@launch
                        }
                    }
                    else -> {
                        idle = 0
                        step = Step.Working(
                            stage = stage,
                            done = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                            total = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                            text = text
                        )
                    }
                }
            }
        }
    }

    private fun finished(result: Step) {
        step = result
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun cancel() {
        runCatching { EngineSetup.cancel() }
    }

    private fun play() {
        startActivity(
            Intent(this, RustGameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // The engine's work has no other way to stop, and a later set-up needs a process
            // of its own. What was fetched stays; an unfinished preparation starts over.
            Process.killProcess(Process.myPid())
        }
    }

    @Composable
    private fun Screen(
        step: Step,
        onStart: () -> Unit,
        onCancel: () -> Unit,
        onPlay: () -> Unit,
        onClose: () -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.rust_setup_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    modifier = Modifier.alpha(0.8f),
                    text = stringResource(R.string.rust_setup_summary, RustEdition.MINECRAFT_VERSION),
                    style = MaterialTheme.typography.bodyMedium
                )

                when (step) {
                    Step.Ready -> {}
                    Step.Starting -> Progress(stringResource(R.string.rust_setup_stage_starting), null, "")
                    is Step.Working -> Progress(stageTitle(step.stage), fraction(step), detail(step))
                    is Step.Failed -> Text(
                        text = stringResource(R.string.rust_setup_failed, step.reason),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Step.Done -> Text(
                        text = stringResource(R.string.rust_setup_done),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    when (step) {
                        Step.Ready, is Step.Failed -> {
                            OutlinedButton(onClick = onClose) {
                                Text(text = stringResource(R.string.rust_setup_close))
                            }
                            Button(onClick = onStart) {
                                Text(
                                    text = stringResource(
                                        if (step is Step.Failed) R.string.rust_setup_retry
                                        else R.string.rust_setup_download
                                    )
                                )
                            }
                        }
                        Step.Starting -> {}
                        is Step.Working -> {
                            // only the download can be stopped; the preparation runs to its end
                            if (step.stage == "download" || step.stage == "checking") {
                                OutlinedButton(onClick = onCancel) {
                                    Text(text = stringResource(R.string.rust_setup_stop))
                                }
                            }
                        }
                        Step.Done -> {
                            OutlinedButton(onClick = onClose) {
                                Text(text = stringResource(R.string.rust_setup_close))
                            }
                            Button(onClick = onPlay) {
                                Text(text = stringResource(R.string.main_launch_game))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Progress(title: String, fraction: Float?, detail: String) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (detail.isNotEmpty()) {
                Text(
                    modifier = Modifier.alpha(0.8f),
                    text = detail,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    private fun stageTitle(stage: String): String = stringResource(
        when (stage) {
            "checking" -> R.string.rust_setup_stage_checking
            "download" -> R.string.rust_setup_stage_download
            "java" -> R.string.rust_setup_stage_java
            "unpacking" -> R.string.rust_setup_stage_unpacking
            else -> R.string.rust_setup_stage_preparing
        }
    )

    private fun fraction(step: Step.Working): Float? =
        if (step.total > 0) (step.done.toFloat() / step.total.toFloat()).coerceIn(0f, 1f) else null

    @Composable
    private fun detail(step: Step.Working): String {
        val context = LocalContext.current
        return when {
            step.total > 0 -> {
                val bytes = "${Formatter.formatShortFileSize(context, step.done)} / " +
                        Formatter.formatShortFileSize(context, step.total)
                if (step.stage == "download" && step.text.isNotEmpty()) {
                    stringResource(R.string.rust_setup_files, bytes, step.text)
                } else {
                    bytes
                }
            }
            step.stage == "preparing" -> step.text
            else -> ""
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, RustSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
