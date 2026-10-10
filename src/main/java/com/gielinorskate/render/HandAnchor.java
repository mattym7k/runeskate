package com.gielinorskate.render;

import lombok.Getter;

/**
 * Where the walker's right hand is, read off the puppet's animated mesh each time it is drawn, so the carried
 * board follows the hand through the walk and run cycles instead of hanging at a fixed spot beside the body. Model
 * space of the puppet (y down, soles at 0, facing -z, the right hand on the -x side). Eased lightly toward each new
 * reading (time constant 0.025 s, so the board keeps up with the swing); with no reading (the mesh does not look like
 * a body, or nothing was drawn for 10 frames) it eases back to the fixed anchor {@link CarryPose#HAND_SIDE} /
 * {@link CarryPose#HAND_HEIGHT}. Pure; client thread only.
 */
public final class HandAnchor
{
	/** The latest reading is in its {@code hand}. */
	private final ArmLocator arm = new ArmLocator();
	private boolean haveRead;
	private int framesSinceRead = Integer.MAX_VALUE;
	/** Reads the hand only while active (on foot, board carried): the scan costs a few passes over the mesh. */
	@Getter
	private boolean active;
	private boolean placed;
	private float x;
	private float y;
	private float z;

	public void setActive(boolean active)
	{
		if (active && !this.active)
			reset();
		this.active = active;
	}

	/** The puppet's mesh as drawn now (first {@code n} vertices). */
	public void sample(float[] xs, float[] ys, float[] zs, int n)
	{
		if (active)
		{
			// the right hand: the model's -x side
			haveRead = arm.locate(xs, ys, zs, n, -1f) && plausible(arm.hand);
			framesSinceRead = haveRead ? 0 : framesSinceRead;
		}
	}

	/** One frame: eases the anchor toward the latest reading, or the fixed anchor without one. */
	public void step(float dt)
	{
		if (framesSinceRead < Integer.MAX_VALUE)
			framesSinceRead++;
		boolean useRead = haveRead && framesSinceRead <= 10;
		float tx = useRead ? arm.hand[0] : -CarryPose.HAND_SIDE;
		float ty = useRead ? arm.hand[1] : -CarryPose.HAND_HEIGHT;
		float tz = useRead ? arm.hand[2] : 0f;
		x = placed ? PoseSmoothing.approach(x, tx, dt, 0.025f) : tx;
		y = placed ? PoseSmoothing.approach(y, ty, dt, 0.025f) : ty;
		z = placed ? PoseSmoothing.approach(z, tz, dt, 0.025f) : tz;
		placed = true;
	}

	/** Forgets the readings: the next step starts at the fixed anchor (or a reading taken since). */
	public void reset()
	{
		haveRead = false;
		framesSinceRead = Integer.MAX_VALUE;
		placed = false;
	}

	/** The hand, puppet model space. */
	public float x()
	{
		return x;
	}

	public float y()
	{
		return y;
	}

	public float z()
	{
		return z;
	}

	/** Above the soles, the right hand on the right, and no further than 160 units from the origin on any axis. */
	private static boolean plausible(float[] p)
	{
		for (float v : p)
		{
			if (!(Math.abs(v) <= 160f))
				return false;
		}
		return p[1] < 0f && p[0] < 0f;
	}
}
