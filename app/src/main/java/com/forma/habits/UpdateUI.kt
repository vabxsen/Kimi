package com.forma.habits

import android.app.Application
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** What the update card is showing. Only [Ready] means an installable file exists on disk. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Working : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: UpdateRelease) : UpdateState
    data class Downloading(val release: UpdateRelease, val fraction: Float) : UpdateState
    data class Ready(val release: UpdateRelease, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private fun fail(error: Throwable) {
        state = UpdateState.Failed(getApplication<Application>().kimiMessage(error, R.string.err_update_unavailable))
    }

    fun check() {
        if (state is UpdateState.Working || state is UpdateState.Downloading) return
        state = UpdateState.Working
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { Updates.fetchLatest() }
                state = if (isNewerVersion(release.version, BuildConfig.VERSION_NAME))
                    UpdateState.Available(release) else UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun download(release: UpdateRelease) {
        if (state is UpdateState.Downloading) return
        state = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val file = withContext(Dispatchers.IO) {
                    Updates.download(context, release) { written, total ->
                        val fraction = if (total > 0) written.toFloat() / total else 0f
                        // Progress arrives from the IO thread; the UI reads it on the main one.
                        viewModelScope.launch {
                            if (state is UpdateState.Downloading) state = UpdateState.Downloading(release, fraction)
                        }
                    }
                }
                val matched = withContext(Dispatchers.IO) { Updates.signedLikeInstalled(context, file) }
                if (!matched) {
                    file.delete()
                    state = UpdateState.Failed(context.getString(R.string.err_update_signature))
                    return@launch
                }
                state = UpdateState.Ready(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun reset() { state = UpdateState.Idle }
}

/**
 * Kimi is not distributed through a store, so nothing tells a person a new build exists. This card
 * asks GitHub only when pressed, then hands the downloaded file to Android's package installer.
 */
@Composable fun UpdateCard(vm: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val state = vm.state
    PlayCard(Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.SystemUpdate, Overlay)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.update_current, BuildConfig.VERSION_NAME),
                    color = Quiet, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            is UpdateState.Working -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
            }
            is UpdateState.UpToDate -> Text(
                stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyMedium
            )
            is UpdateState.Available -> {
                Text(stringResource(R.string.update_available, state.release.version), style = MaterialTheme.typography.titleMedium)
                if (state.release.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(state.release.notes.lineSequence().take(6).joinToString("\n"),
                        color = Quiet, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                MainButton(stringResource(R.string.action_update_download, megabytes(state.release.size))) {
                    vm.download(state.release)
                }
            }
            is UpdateState.Downloading -> {
                if (state.fraction > 0f) LinearProgressIndicator({ state.fraction }, Modifier.fillMaxWidth(), color = Accent)
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.update_downloading, (state.fraction * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium)
            }
            is UpdateState.Ready -> {
                Text(stringResource(R.string.update_ready, state.release.version), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                MainButton(stringResource(R.string.action_update_install)) {
                    // Android only shows the installer once Kimi itself is a trusted source.
                    val intent = if (Updates.canInstall(context)) Updates.installIntent(context, state.file)
                    else Updates.installPermissionIntent(context)
                    runCatching { context.startActivity(intent) }
                        .onFailure { if (it is ActivityNotFoundException) vm.reset() }
                }
                if (!Updates.canInstall(context)) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_needs_permission), color = Quiet,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.bodyMedium)
            UpdateState.Idle -> Unit
        }
        if (state !is UpdateState.Working && state !is UpdateState.Downloading && state !is UpdateState.Available) {
            Spacer(Modifier.height(if (state is UpdateState.Idle) 0.dp else 10.dp))
            MainButton(stringResource(R.string.action_check_updates)) { vm.check() }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.update_source), color = Quiet, fontSize = 11.sp, textAlign = TextAlign.Start)
    }
}

/**
 * One decimal is enough to set an expectation about a download, and never shows "0.0 MB".
 * The locale is explicit so the separator follows the reader, and so tests can pin one.
 */
internal fun megabytes(bytes: Long, locale: Locale = Locale.getDefault()): String =
    if (bytes <= 0) "?" else String.format(locale, "%.1f", maxOf(bytes, 51_200L) / 1_048_576.0)
