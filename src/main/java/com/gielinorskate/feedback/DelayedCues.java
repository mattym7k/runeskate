package com.gielinorskate.feedback;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.SpotanimID;

/**
* Sounds queued for a later frame: the level-up bells (ding, then dong 0.3 s later) and the session-goal chime,
* which waits for the landing's own chime to finish. Pure.
*/
final class DelayedCues
{
@AllArgsConstructor
private static final class Pending
{
final SoundPicker.Cue cue;
final float at;
}

private final List<Pending> pending = new ArrayList<>();

/** The level-up bells, starting at {@code now}. */
void levelUp(float now)
{
add(SoundEffectID.TOWN_CRIER_BELL_DING, now);
add(SoundEffectID.TOWN_CRIER_BELL_DONG, now + 0.3f);
}

/**
* The session-goal chime (the combo-landed sound), 0.4 s after the landing's own (it would be throttled at the
* same time).
*/
void goalComplete(float now)
{
add(SoundPicker.COMBO_LANDED, now + 0.4f);
}

private void add(int id, float at)
{
pending.add(new Pending(new SoundPicker.Cue(id, 1f), at));
}

/** Removes and returns the cues due at {@code now}, in the order queued. */
List<SoundPicker.Cue> due(float now)
{
List<SoundPicker.Cue> out = new ArrayList<>(2);
pending.removeIf(p -> p.at <= now && out.add(p.cue));
return out;
}

void clear()
{
pending.clear();
}

/** The level-up graphic over the skater: the bigger one for 99. */
static int levelUpGraphic(int level)
{
return level >= 99 ? SpotanimID.LEVELUP_99_ANIM : SpotanimID.LEVELUP_ANIM;
}
}
