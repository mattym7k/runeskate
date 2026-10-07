package com.gielinorskate.feedback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.SpotanimID;

/**
 * Sounds queued for a later frame: the level-up bells (ding, then dong {@link #BELL_GAP_SECONDS} later) and the
 * session-goal chime, which waits for the landing's own chime to finish. Pure.
 */
final class DelayedCues
{
	static final int BELL_DING = SoundEffectID.TOWN_CRIER_BELL_DING;
	static final int BELL_DONG = SoundEffectID.TOWN_CRIER_BELL_DONG;
	static final float BELL_GAP_SECONDS = 0.3f;
	/** The goal chime follows the combo-landed chime this much later (it would be throttled at the same time). */
	static final float GOAL_CHIME_DELAY = 0.4f;
	static final int LEVEL_UP_GRAPHIC = SpotanimID.LEVELUP_ANIM;
	static final int LEVEL_99_GRAPHIC = SpotanimID.LEVELUP_99_ANIM;

	private static final class Pending
	{
		final SoundPicker.Cue cue;
		final float at;

		Pending(SoundPicker.Cue cue, float at)
		{
			this.cue = cue;
			this.at = at;
		}
	}

	private final List<Pending> pending = new ArrayList<>();

	/** The level-up bells, starting at {@code now}. */
	void levelUp(float now)
	{
		add(BELL_DING, 1f, now);
		add(BELL_DONG, 1f, now + BELL_GAP_SECONDS);
	}

	/** The session-goal chime (the combo-landed sound), just after the landing's own. */
	void goalComplete(float now)
	{
		add(SoundPicker.COMBO_LANDED, 1f, now + GOAL_CHIME_DELAY);
	}

	void add(int id, float volume, float at)
	{
		pending.add(new Pending(new SoundPicker.Cue(id, volume), at));
	}

	/** Removes and returns the cues due at {@code now}, in the order queued. */
	List<SoundPicker.Cue> due(float now)
	{
		if (pending.isEmpty())
		{
			return Collections.emptyList();
		}
		List<SoundPicker.Cue> out = new ArrayList<>(2);
		for (Iterator<Pending> it = pending.iterator(); it.hasNext(); )
		{
			Pending p = it.next();
			if (p.at <= now)
			{
				out.add(p.cue);
				it.remove();
			}
		}
		return out;
	}

	void clear()
	{
		pending.clear();
	}

	/** The level-up graphic over the skater: the bigger one for 99. */
	static int levelUpGraphic(int level)
	{
		return level >= 99 ? LEVEL_99_GRAPHIC : LEVEL_UP_GRAPHIC;
	}
}
