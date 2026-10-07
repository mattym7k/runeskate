package com.gielinorskate.render;

/**
 * Detects when an animation leaves the player with no model (some animations don't work on player
 * models), which would make the skater invisible. Pure: feed it one sample per frame.
 */
final class PoseGuard
{
	static final int MISSING_FRAMES_BEFORE_REVERT = 3;

	private int missingFrames;

	/** @return true when the current animations should be reverted to the defaults */
	boolean frame(boolean hasModel)
	{
		if (hasModel)
		{
			missingFrames = 0;
			return false;
		}
		if (++missingFrames >= MISSING_FRAMES_BEFORE_REVERT)
		{
			missingFrames = 0;
			return true;
		}
		return false;
	}
}
