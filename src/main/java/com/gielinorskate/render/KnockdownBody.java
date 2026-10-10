package com.gielinorskate.render;

/**
 * The knocked-off skater's procedural body: the whole-body tumble about the centre of mass (the body flip's
 * rotation) with a sideways roll and tucked knees in the air; lying flat at the tumble's angle; the knees bending
 * as it rises. With an OSRS lie-down or get-up animation playing, the body is left as animated. Pure.
 */
public final class KnockdownBody
{
	private KnockdownBody()
	{
	}

	/**
	 * @param animated an OSRS animation draws this stage (the lie-down or get-up), not the procedural pose. In the air
	 * the knees tuck 0.25 (1 - legScale) and the upper body folds 0.3; the knees bend 0.3 through the middle of a
	 * procedural get-up.
	 */
	public static void write(KnockdownPose k, boolean animated, BodyPose out)
	{
		out.neutral();
		if (animated && k.stage != KnockdownPose.Stage.AIR)
			return;
		out.flip = k.bodyAngle;
		out.flipPivotY = BoardPlacement.puppetFlipPivotY();
		if (k.stage == KnockdownPose.Stage.AIR)
		{
			out.roll = k.bodyRoll;
			out.legScale = 1f - 0.25f;
			out.torsoBend = 0.3f;
		}
		else if (k.stage == KnockdownPose.Stage.GET_UP)
			out.legScale = 1f - 0.3f * (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, k.progress)));
	}
}
