package com.gielinorskate.feedback;

import java.util.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Preferences;

/**
* Plays {@link SoundPicker} cues through the client, following the player's sound-effect volume (muted means
* silent) scaled by the plugin's volume setting. Each sound plays at most once per {@link #MIN_GAP_SECONDS}, so
* bursts of events never stack one sound. Client thread.
*/
final class SkateSounds
{
static final float MIN_GAP_SECONDS = 0.1f;

@Inject
private Client client;
private final Map<Integer, Float> lastPlayed = new HashMap<>();

/** @param volumePercent the plugin's "Sound volume" setting, 0..100 */
void play(List<SoundPicker.Cue> cues, float now, int volumePercent)
{
if (cues.isEmpty() || volumePercent <= 0)
return;
Preferences prefs = client.getPreferences();
int playerVolume = prefs.getSoundEffectVolume();
if (playerVolume <= 0)
return;
for (SoundPicker.Cue cue : cues)
{
int volume = Math.round(playerVolume * cue.volume * Math.min(100, volumePercent) / 100f);
if (volume <= 0 || !allow(cue.id, now))
continue;
// playSoundEffect(id, volume) uses the player's own volume unless it is muted, so set it for the one
// sound and put it straight back, as the core Metronome plugin does
prefs.setSoundEffectVolume(volume);
try
{
client.playSoundEffect(cue.id, volume);
}
finally
{
prefs.setSoundEffectVolume(playerVolume);
}
}
}

/** True (and remembered) when {@code soundId} may play at {@code now}. */
boolean allow(int soundId, float now)
{
Float last = lastPlayed.get(soundId);
if (last != null && now - last < MIN_GAP_SECONDS)
return false;
lastPlayed.put(soundId, now);
return true;
}
}
