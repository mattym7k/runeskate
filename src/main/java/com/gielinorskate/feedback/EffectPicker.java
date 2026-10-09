package com.gielinorskate.feedback;

import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.scoring.ComboScorer;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.SpotanimID;

/** Chooses which graphics (spotanims) a skate frame spawns at the skater. Pure. */
final class EffectPicker
{
/** Spark bursts every 0.15 s while grinding; the first comes on contact. */
private final Repeater sparks = new Repeater(0.15f, true);

/**
* @param grinding the skater is on a rail this frame
* @param lastAirtime seconds in the air of the latest landing
* @param newResult a combo that resolved this frame, or null
*/
List<Integer> pick(List<SkateEvent> events, boolean grinding, float lastAirtime, ComboScorer.Result newResult,
float dt)
{
List<Integer> ids = new ArrayList<>(2);
// only landings after more than 0.4 s of air raise dust (not curb drops and bumps)
if (events.contains(SkateEvent.LAND) && lastAirtime > 0.4f)
ids.add(SpotanimID.EMOTE_DUSTSTAMP_SPOT);
if (sparks.update(grinding, dt))
ids.add(SpotanimID.WARGUILD_SPARKS_SPOTANIM);
if (events.contains(SkateEvent.BAIL))
ids.add(SpotanimID.SMOKEPUFF_LARGE);
if (newResult != null && newResult.isLanded() && Callout.forValue(newResult.value) == Callout.PET_DROP)
// the Party emote's confetti: a plain celebration for a huge combo (nothing that looks like a real drop)
ids.add(SpotanimID.FX_EMOTE_PARTY01_ACTIVE);
return ids;
}
}
