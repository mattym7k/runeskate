package com.gielinorskate.feedback;

/**
 * Picks a joke chat line for a bail, at most one every 20 seconds. A wall bail and a bail that lost nothing get
 * their own lines; the rest rotate in order so the same joke never runs twice in a row. Pure.
 */
public final class BailLines
{
	public static final String WALL = "I can't reach that!";
	public static final String NOTHING_LOST = "Nothing interesting happens.";
	static final String[] ROTATION = {
		"Oh dear, you are bailed!",
		"Your board has been sent to Death's office.",
	};

	private float lastLineTime = -Float.MAX_VALUE;
	private int next;

	/**
	 * @param wall the bail ran into a blocker
	 * @param lostValue the combo value the bail threw away
	 * @return the line to post, or null while the cooldown runs
	 */
	public String onBail(float now, boolean wall, int lostValue)
	{
		if (now - lastLineTime < 20f)
			return null;
		lastLineTime = now;
		return wall ? WALL : lostValue <= 0 ? NOTHING_LOST : ROTATION[next++ % ROTATION.length];
	}
}
