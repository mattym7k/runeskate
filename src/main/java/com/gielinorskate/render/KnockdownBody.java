package com.gielinorskate.render;

/**
 * The knocked-off skater's procedural body: the whole-body tumble about the centre of mass (the body flip's
 * rotation) with a sideways roll and tucked knees in the air; lying flat at the tumble's angle; the knees bending
 * as it rises. With an OSRS lie-down or get-up animation playing, the body is left as animated. Pure.
 */
public final class KnockdownBody
{
	/** Knees tucked in the air (1 - legScale), and bent through the middle of a procedural get-up. */
	static final float AIR_TUCK = 0.25f;
	static final float GET_UP_BEND = 0.3f;
	/** Upper body folded in the air. */
	static final float AIR_BEND = 0.3f;

	private KnockdownBody()
	{
	}

	/** @param animated an OSRS animation draws this stage (the lie-down or get-up), not the procedural pose */
	public static void write(KnockdownPose k, boolean animated, BodyPose out)
	{
		out.neutral();
		if (animated && k.stage != KnockdownPose.Stage.AIR)
		{
			return;
		}
		out.flip = k.bodyAngle;
		out.flipPivotY = BoardPlacement.puppetFlipPivotY();
		switch (k.stage)
		{
			case AIR:
				out.roll = k.bodyRoll;
				out.legScale = 1f - AIR_TUCK;
				out.torsoBend = AIR_BEND;
				break;
			case GET_UP:
				float u = Math.max(0f, Math.min(1f, k.progress));
				out.legScale = 1f - GET_UP_BEND * (float) Math.sin(Math.PI * u);
				break;
			default:
				break;
		}
	}
}
