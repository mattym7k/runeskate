package com.gielinorskate.feedback;

import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.tricks.TrickKind;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.SoundEffectID;

/**
 * Chooses which game sounds a skate frame makes, and how loud relative to the player's sound-effect volume.
 * All IDs are the client's own sound effects (net.runelite.api.SoundEffectID; there is no gameval sound list
 * in the API). Pure.
 */
final class SoundPicker
{
	/** One sound to play: its ID and a volume fraction 0..1 of the player's sound-effect volume. */
	static final class Cue
	{
		final int id;
		final float volume;

		Cue(int id, float volume)
		{
			this.id = id;
			this.volume = volume;
		}

		@Override
		public String toString()
		{
			return id + "@" + volume;
		}
	}

	static final int POP = SoundEffectID.UI_BOOP;
	/** A trick with a full board spin or double flip, or a body flip. */
	static final int BIG_TRICK = SoundEffectID.FIRE_WOOSH;
	static final int LAND = SoundEffectID.ITEM_DROP;
	static final int GRIND_LOCK = SoundEffectID.SMITH_ANVIL_TONK;
	static final int GRIND_TINK = SoundEffectID.MINING_TINK;
	static final int BAIL = SoundEffectID.MAGIC_SPLASH_BOING;
	static final int COMBO_LANDED = SoundEffectID.GE_COIN_TINKLE;
	static final int BIG_COMBO_LANDED = SoundEffectID.GE_ADD_OFFER_DINGALING;

	/** Seconds between tinks while grinding. */
	static final float GRIND_TINK_SECONDS = 0.25f;
	/** A combo worth this much gets the bigger chime (the "Gz!" callout). */
	static final int BIG_COMBO_VALUE = Callout.GZ.minValue;
	/**
	 * Landing speed (u/s, downward) at full landing volume. A flat-ground ollie lands at about its pop speed,
	 * ollieImpulse = 820 u/s (0.59 of full); a drop off a ledge lands harder.
	 */
	static final float LOUD_LANDING_SPEED = 1400f;
	static final float MIN_LANDING_VOLUME = 0.3f;
	static final float GRIND_TINK_VOLUME = 0.5f;
	static final float POP_VOLUME = 0.8f;

	private final Repeater grindTinks = new Repeater(GRIND_TINK_SECONDS, false);

	/**
	 * @param grinding the skater is on a rail this frame
	 * @param landingSpeed downward speed of the latest landing (u/s)
	 * @param newResult a combo that resolved this frame, or null
	 */
	List<Cue> pick(List<SkateEvent> events, List<TrickEvent> trickEvents, boolean grinding, float landingSpeed,
		ComboScorer.Result newResult, float dt)
	{
		List<Cue> cues = new ArrayList<>(4);
		if (events.contains(SkateEvent.POP))
		{
			cues.add(new Cue(POP, POP_VOLUME));
		}
		for (TrickEvent e : trickEvents)
		{
			if (e.type == TrickEvent.Type.TRICK && isBig(e.trick))
			{
				cues.add(new Cue(BIG_TRICK, 1f));
			}
			else if (e.type == TrickEvent.Type.HOLD_START && e.trick != null && e.trick.kind == TrickKind.GRIND)
			{
				cues.add(new Cue(GRIND_LOCK, 1f));
			}
		}
		if (events.contains(SkateEvent.LAND))
		{
			float v = Math.max(MIN_LANDING_VOLUME, Math.min(1f, landingSpeed / LOUD_LANDING_SPEED));
			cues.add(new Cue(LAND, v));
		}
		if (grindTinks.update(grinding, dt))
		{
			cues.add(new Cue(GRIND_TINK, GRIND_TINK_VOLUME));
		}
		if (events.contains(SkateEvent.BAIL))
		{
			cues.add(new Cue(BAIL, 1f));
		}
		if (newResult != null && newResult.isLanded())
		{
			cues.add(new Cue(newResult.value >= BIG_COMBO_VALUE ? BIG_COMBO_LANDED : COMBO_LANDED, 1f));
		}
		return cues;
	}

	/** A full (360) board spin, a double flip, or a body flip: worth a woosh. */
	static boolean isBig(Trick trick)
	{
		return trick != null && (Math.abs(trick.yawTurns) >= 1f || Math.abs(trick.rollTurns) >= 2f
			|| trick.bodyFlipTurns != 0f);
	}
}
