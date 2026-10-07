package com.gielinorskate.feedback;

import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.scoring.ComboScorer;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.SpotanimID;

/** Chooses which graphics (spotanims) a skate frame spawns at the skater. Pure. */
final class EffectPicker
{
	static final int LANDING_PUFF = SpotanimID.EMOTE_DUSTSTAMP_SPOT;
	static final int GRIND_SPARKS = SpotanimID.WARGUILD_SPARKS_SPOTANIM;
	static final int BAIL_SMOKE = SpotanimID.SMOKEPUFF_LARGE;
	/** The Party emote's confetti: a plain celebration for a huge combo (nothing that looks like a real drop). */
	static final int BIG_COMBO_CONFETTI = SpotanimID.FX_EMOTE_PARTY01_ACTIVE;

	/** Only landings after more than this many seconds of air raise dust (not curb drops and bumps). */
	static final float PUFF_MIN_AIRTIME = 0.4f;
	/** Seconds between spark bursts while grinding; the first comes on contact. */
	static final float SPARK_SECONDS = 0.15f;

	private final Repeater sparks = new Repeater(SPARK_SECONDS, true);

	/**
	 * @param grinding the skater is on a rail this frame
	 * @param lastAirtime seconds in the air of the latest landing
	 * @param newResult a combo that resolved this frame, or null
	 */
	List<Integer> pick(List<SkateEvent> events, boolean grinding, float lastAirtime, ComboScorer.Result newResult,
		float dt)
	{
		List<Integer> ids = new ArrayList<>(2);
		if (events.contains(SkateEvent.LAND) && lastAirtime > PUFF_MIN_AIRTIME)
		{
			ids.add(LANDING_PUFF);
		}
		if (sparks.update(grinding, dt))
		{
			ids.add(GRIND_SPARKS);
		}
		if (events.contains(SkateEvent.BAIL))
		{
			ids.add(BAIL_SMOKE);
		}
		if (newResult != null && newResult.isLanded() && Callout.forValue(newResult.value) == Callout.PET_DROP)
		{
			ids.add(BIG_COMBO_CONFETTI);
		}
		return ids;
	}
}
