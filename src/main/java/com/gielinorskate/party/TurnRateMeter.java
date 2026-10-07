package com.gielinorskate.party;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.tricks.Trick;

/**
 * The local skater's heading turn rate for the party: what carving and air spins turn, measured over short
 * windows of physics time, so render frames without a physics step (or with two) don't make it jitter. The part of a flip's
 * own body turn (a bigspin's 180) is left out: receivers animate that from the trick table. Heading snaps (a
 * reset, a rail lock) are not turns. Pure.
 */
final class TurnRateMeter
{
	/**
	 * Faster than any real turn: a Shift tight carve is carveRate 2.6 * tightCarveMult 2.2 = 5.7 rad/s at
	 * standstill, and air spins are slower than that.
	 */
	static final float MAX_RATE = 10f;
	/** The rate is the mean over windows this long: five 0.02 s physics steps. */
	static final float WINDOW = 0.1f;
	/**
	 * A frame's turn above MAX_RATE times this (or times the frame, when longer) is a snap: one long frame of
	 * several physics steps can't be told from a jump below it.
	 */
	private static final float MIN_SNAP_TIME = 0.05f;

	private float lastHeading = Float.NaN;
	private Trick lastTrick;
	private float lastBodySpin;
	private float turned;
	private float elapsed;
	private float rate;

	/**
	 * One render frame that ran {@code dt} seconds of physics steps (0 when it ran none: the heading can't have
	 * changed, and counting render time instead would make the rate jitter with the frame rate); returns the
	 * turn rate (rad/s, + = clockwise from above).
	 *
	 * @param flipTrick the flip turning the board now, or null
	 * @param flipProgress its progress 0..1
	 */
	float update(float heading, Trick flipTrick, float flipProgress, float dt)
	{
		float bodySpin = GhostCodec.bodySpin(flipTrick, flipProgress);
		float before = flipTrick == lastTrick ? lastBodySpin : 0f;
		if (Float.isNaN(lastHeading) || dt <= 0f)
		{
			lastHeading = heading;
			lastTrick = flipTrick;
			lastBodySpin = bodySpin;
			return rate;
		}
		float delta = Angles.wrap(heading - lastHeading) - (bodySpin - before);
		lastHeading = heading;
		lastTrick = flipTrick;
		lastBodySpin = bodySpin;
		if (Math.abs(delta) > MAX_RATE * Math.max(dt, MIN_SNAP_TIME))
		{
			turned = 0f;
			elapsed = 0f;
			rate = 0f;
			return rate;
		}
		turned += delta;
		elapsed += dt;
		// a little slack: five 0.02 s steps summed in float fall just short of 0.1
		if (elapsed >= WINDOW - 1e-4f)
		{
			rate = Math.max(-MAX_RATE, Math.min(MAX_RATE, turned / elapsed));
			turned = 0f;
			elapsed = 0f;
		}
		return rate;
	}

	void reset()
	{
		lastHeading = Float.NaN;
		lastTrick = null;
		lastBodySpin = 0f;
		turned = 0f;
		elapsed = 0f;
		rate = 0f;
	}
}
