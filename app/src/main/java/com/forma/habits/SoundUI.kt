package com.forma.habits

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

/**
 * Settings for sound: the little sounds, Today's soundscape, how loud backgrounds play, and the
 * evening streak nudge. Turning sounds on plays the first note of the day's tune, so you hear what
 * you have chosen.
 */
@Composable fun SoundSettingCard() {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Reminders.reschedule(context, HabitStore.get(context).state.value) }
    PlayCard(Peach) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.MusicNote, Overlay); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_sound_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_sound_body), color = Quiet, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        SoundSwitch(R.string.settings_sound_effects, R.string.settings_sound_effects_body, SoundSetting.effects, "sound_switch") { on ->
            SoundSetting.setEffects(context, on)
            if (on) { Sound.prepare(); Sound.tick(0) }
        }
        SoundSwitch(R.string.settings_soundscape, R.string.settings_soundscape_body, SoundSetting.soundscape, "soundscape_switch") {
            SoundSetting.setSoundscape(context, it)
        }
        if (SoundSetting.effects || SoundSetting.soundscape) {
            val label = stringResource(R.string.cd_ambience_volume)
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = Quiet, modifier = Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, Modifier.size(20.dp), tint = Quiet)
                Slider(SoundSetting.ambience, { SoundSetting.setAmbience(context, it) }, Modifier.weight(1f).padding(horizontal = 6.dp)
                    .semantics { contentDescription = label })
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = Quiet)
            }
        }
        SoundSwitch(R.string.settings_nudge, R.string.settings_nudge_body, SoundSetting.nudge, "nudge_switch") { on ->
            SoundSetting.setNudge(context, on)
            if (on && !Reminders.allowed(context) && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            Reminders.reschedule(context, HabitStore.get(context).state.value)
        }
        Text(stringResource(R.string.settings_sound_note), color = Quiet, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable private fun SoundSwitch(title: Int, body: Int, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).testTag(tag).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 14.sp, lineHeight = 18.sp, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), color = Quiet, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * How a habit's reminder sounds, so you know which habit it is without looking. Android ties a
 * notification's sound to its channel, so each choice is a channel of its own; the choice is kept
 * on this device.
 */
enum class ReminderSound(val label: Int, val channel: String, val raw: Int?, val preview: (() -> FloatArray)?) {
    Default(R.string.reminder_sound_default, Reminders.CHANNEL, null, null),
    Chime(R.string.reminder_sound_chime, "reminders_chime", R.raw.reminder_chime, Synth::reminderChime),
    Birdsong(R.string.reminder_sound_birds, "reminders_birds", R.raw.reminder_birds, Synth::reminderBirds),
    Knock(R.string.reminder_sound_knock, "reminders_knock", R.raw.reminder_knock, Synth::reminderKnock);

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("kimi_reminder_sounds", Context.MODE_PRIVATE)
        fun of(context: Context, habitId: String): ReminderSound =
            prefs(context).getString(habitId, null)?.let { name -> entries.firstOrNull { it.name == name } } ?: Default
        fun set(context: Context, habitId: String, sound: ReminderSound) =
            prefs(context).edit { if (sound == Default) remove(habitId) else putString(habitId, sound.name) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReminderSoundPicker(selected: ReminderSound, onPick: (ReminderSound) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.reminder_sound_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReminderSound.entries.forEach { sound ->
                FilterChip(selected = sound == selected, onClick = {
                    onPick(sound)
                    // A taste of it, whatever the sound setting: it was asked for.
                    sound.preview?.let { make -> Mixer.cue("preview_${sound.name}", .8f, make = make) }
                }, label = { Text(stringResource(sound.label)) })
            }
        }
    }
}
