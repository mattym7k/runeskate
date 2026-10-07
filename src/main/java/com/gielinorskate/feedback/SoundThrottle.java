package com.gielinorskate.feedback;

import java.util.HashMap;
import java.util.Map;

/** Lets each sound play at most once per {@link #MIN_GAP_SECONDS}, so bursts of events never stack one sound. Pure. */
final class SoundThrottle
{
	static final float MIN_GAP_SECONDS = 0.1f;

	private final Map<Integer, Float> lastPlayed = new HashMap<>();

	/** True (and remembered) when {@code soundId} may play at {@code now}. */
	boolean allow(int soundId, float now)
	{
		Float last = lastPlayed.get(soundId);
		if (last != null && now - last < MIN_GAP_SECONDS)
		{
			return false;
		}
		lastPlayed.put(soundId, now);
		return true;
	}
}
