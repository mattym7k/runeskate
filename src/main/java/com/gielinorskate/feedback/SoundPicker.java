package com.gielinorskate.feedback;

import static net.runelite.api.SoundEffectID.*;

import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.*;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * Chooses which game sounds a skate frame makes, and how loud relative to the player's sound-effect volume.
 * All IDs are the client's own sound effects (net.runelite.api.SoundEffectID; there is no gameval sound list
 * in the API). Pure.
 */
final class SoundPicker
{
	/** One sound to play: its ID and a volume fraction 0..1 of the player's sound-effect volume. */
	@AllArgsConstructor
	@ToString
	static final class Cue
	{
		final int id;
		final float volume;
	}

	static final int COMBO_LANDED = GE_COIN_TINKLE;

	/** Grind tinks every 0.25 s while grinding. */
	private final Repeater grindTinks = new Repeater(0.25f, false);

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
			cues.add(new Cue(UI_BOOP, 0.8f));
		for (TrickEvent e : trickEvents)
		{
			if (e.type == TrickEvent.Type.TRICK && isBig(e.trick))
				cues.add(new Cue(FIRE_WOOSH, 1f));
			else if (e.type == TrickEvent.Type.HOLD_START && e.trick != null && e.trick.kind == TrickKind.GRIND)
			{
				cues.add(new Cue(SMITH_ANVIL_TONK, 1f)); // grind lock
			}
		}
		if (events.contains(SkateEvent.LAND))
			// full volume at a landing speed of 1400 u/s down: a flat-ground ollie lands at about its pop speed,
			// ollieImpulse = 820 u/s (0.59 of full); a drop off a ledge lands harder
			cues.add(new Cue(ITEM_DROP, Math.max(0.3f, Math.min(1f, landingSpeed / 1400f))));
		if (grindTinks.update(grinding, dt))
			cues.add(new Cue(MINING_TINK, 0.5f));
		if (events.contains(SkateEvent.BAIL))
			cues.add(new Cue(MAGIC_SPLASH_BOING, 1f));
		if (newResult != null && newResult.isLanded())
			// a combo worth a "Gz!" callout gets the bigger chime
			cues.add(new Cue(newResult.value >= Callout.GZ.minValue ? GE_ADD_OFFER_DINGALING : COMBO_LANDED, 1f));
		return cues;
	}

	/** A full (360) board spin, a double flip, or a body flip: worth a woosh. */
	static boolean isBig(Trick trick)
	{
		return trick != null && (Math.abs(trick.yawTurns) >= 1f || Math.abs(trick.rollTurns) >= 2f
			|| trick.bodyFlipTurns != 0f);
	}
}
