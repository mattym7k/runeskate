package com.gielinorskate.render;

/**
 * Where the walker's right hand is, read off the puppet's animated mesh each time it is drawn, so the carried
 * board follows the hand through the walk and run cycles instead of hanging at a fixed spot beside the body. Model
 * space of the puppet (y down, soles at 0, facing -z, the right hand on the -x side). Eased lightly toward each new
 * reading; with no reading (the mesh does not look like a body, or nothing was drawn lately) it eases back to the
 * fixed anchor {@link CarryPose#HAND_SIDE} / {@link CarryPose#HAND_HEIGHT}. Pure; client thread only.
 */
public final class HandAnchor
{
	/** The right hand: the model's -x side. */
	static final float RIGHT = -1f;
	/** Seconds (time constant) of the easing toward the hand: light, so the board keeps up with the swing. */
	static final float TAU = 0.025f;
	/** A reading older than this many frames is not used (the puppet has not been drawn since). */
	static final int STALE_FRAMES = 10;
	/** A hand further than this from the model's origin (units, any axis) is a misread. */
	static final float MAX_REACH = 160f;

	private final ArmLocator arm = new ArmLocator();
	private final float[] read = new float[3];
	private boolean haveRead;
	private int framesSinceRead = Integer.MAX_VALUE;
	private boolean active;
	private boolean placed;
	private float x;
	private float y;
	private float z;

	/** Reads the hand only while active (on foot, board carried): the scan costs a few passes over the mesh. */
	public void setActive(boolean active)
	{
		if (active && !this.active)
		{
			reset();
		}
		this.active = active;
	}

	public boolean isActive()
	{
		return active;
	}

	/** The puppet's mesh as drawn now (first {@code n} vertices). */
	public void sample(float[] xs, float[] ys, float[] zs, int n)
	{
		if (!active)
		{
			return;
		}
		if (arm.locate(xs, ys, zs, n, RIGHT) && plausible(arm.hand))
		{
			read[0] = arm.hand[0];
			read[1] = arm.hand[1];
			read[2] = arm.hand[2];
			haveRead = true;
			framesSinceRead = 0;
		}
		else
		{
			haveRead = false;
		}
	}

	/** One frame: eases the anchor toward the latest reading, or the fixed anchor without one. */
	public void step(float dt)
	{
		if (framesSinceRead < Integer.MAX_VALUE)
		{
			framesSinceRead++;
		}
		boolean useRead = haveRead && framesSinceRead <= STALE_FRAMES;
		float tx = useRead ? read[0] : -CarryPose.HAND_SIDE;
		float ty = useRead ? read[1] : -CarryPose.HAND_HEIGHT;
		float tz = useRead ? read[2] : 0f;
		if (!placed)
		{
			x = tx;
			y = ty;
			z = tz;
			placed = true;
			return;
		}
		x = PoseSmoothing.approach(x, tx, dt, TAU);
		y = PoseSmoothing.approach(y, ty, dt, TAU);
		z = PoseSmoothing.approach(z, tz, dt, TAU);
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

	private static boolean plausible(float[] p)
	{
		for (float v : p)
		{
			if (Float.isNaN(v) || Math.abs(v) > MAX_REACH)
			{
				return false;
			}
		}
		// above the soles, the right hand on the right
		return p[1] < 0f && p[0] < 0f;
	}
}
