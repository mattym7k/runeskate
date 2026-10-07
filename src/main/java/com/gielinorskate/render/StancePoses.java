package com.gielinorskate.render;

import net.runelite.api.gameval.AnimationID;

/**
 * Mutable holder of the animation IDs the skater uses, so the {@code ::skatepose} dev command can
 * swap them live. Defaults are gameval constants; -1 means "no animation" (keep the riding stance).
 */
public final class StancePoses
{
	public static final int NONE = -1;

	public int stance;
	public int crouch;
	public int push;
	public int jump;
	public int bail;
	public int grind;
	public int manual;
	/** Lying down after being knocked off the board (a loop), and getting up (squeezed into the get-up's time). */
	public int knockdown;
	public int getUp;

	public StancePoses()
	{
		resetToDefaults();
	}

	public void resetToDefaults()
	{
		stance = AnimationID.HUMAN_SKI_IDLE;
		// crouching keeps the bent-knee stance: HUMAN_CRATE_SQUAT made the skater vanish and
		// AGILITYARENA_DARTDUCK is a limbo-style lean far back
		crouch = NONE;
		// the walk cycle looked like walking on the board; pushing keeps the riding stance
		push = NONE;
		// the pop extension, air tuck and bail fall are procedural (SkaterPoseRig) on the riding stance:
		// switching to HUMAN_SPOT_JUMP / HUMAN_WOBBLEANDFALL_L cut hard, since OSRS animations cannot blend
		jump = NONE;
		bail = NONE;
		grind = AnimationID.HUMAN_SKI_IDLE;
		manual = AnimationID.HUMAN_SKI_IDLE;
		// knocked off the board: the tumble in the air is procedural; the cut to these comes at the impact, where
		// the smoke and the camera kick hide it. The no-delay knockdown loop lies down from its first frame
		knockdown = AnimationID.HUMAN_KNOCKDOWN_LOOP_NODELAY;
		getUp = AnimationID.HUMAN_GETUP;
	}
}
