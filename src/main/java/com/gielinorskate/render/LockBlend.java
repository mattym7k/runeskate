package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;

/**
 * Physics snaps the skater onto a rail in one step (up to the grind snap distance sideways and down).
 * This draws that lock as a short slide instead: on the frame the pose enters GRINDING, the drawn
 * position and heading start from the last drawn pose and ease onto the rail over {@link #DURATION}.
 * Pure; feed it every frame's pose.
 */
public final class LockBlend
{
	static final float DURATION = 0.09f;

	private RenderPose last;
	private boolean active;
	private float elapsed;
	private float offX;
	private float offY;
	private float offH;
	private float offHeading;

	/** Forgets the last drawn pose (a new session). */
	public void reset()
	{
		last = null;
		active = false;
	}

	/** The pose to draw this frame. */
	public RenderPose apply(RenderPose pose, float dt)
	{
		if (last != null && pose.state == SkaterState.GRINDING && last.state != SkaterState.GRINDING)
		{
			offX = last.x - pose.x;
			offY = last.y - pose.y;
			offH = last.h - pose.h;
			offHeading = Angles.wrap(last.heading - pose.heading);
			elapsed = 0f;
			active = true;
		}
		else if (active)
		{
			elapsed += dt;
		}

		RenderPose out = pose;
		if (active && elapsed >= DURATION)
		{
			active = false;
		}
		else if (active)
		{
			float u = elapsed / DURATION;
			float k = 1f - u * u * (3f - 2f * u);
			out = new RenderPose(pose.x + offX * k, pose.y + offY * k, pose.h + offH * k,
				Angles.wrap(pose.heading + offHeading * k), pose.cameraHeading, pose.boardRoll, pose.boardYaw,
				pose.boardPitch, pose.state, pose.hold);
		}
		last = out;
		return out;
	}
}
