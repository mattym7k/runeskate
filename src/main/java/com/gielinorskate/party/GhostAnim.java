package com.gielinorskate.party;

import com.gielinorskate.render.*;
import lombok.AllArgsConstructor;
import net.runelite.api.gameval.AnimationID;

/**
 * The OSRS animation a ghost's body plays under its procedural pose: the local skater's (the default skate poses),
 * on the ghost's own copy of the member's model, never on their character. Pure.
 */
@AllArgsConstructor
enum GhostAnim
{
	/** On foot standing: the member's own standing frame, as snapshotted. */
	NONE(-1, true),
	/** The riding stance (also grinds, manuals and the tumble). */
	STANCE(AnimationID.HUMAN_SKI_IDLE, true),
	WALK(AnimationID.HUMAN_WALK_F, true),
	RUN(AnimationID.HUMAN_RUNNING, true),
	/** Knocked down: lying (a loop), then getting up (paced by the get-up's progress). */
	LIE(AnimationID.HUMAN_KNOCKDOWN_LOOP_NODELAY, true),
	GET_UP(AnimationID.HUMAN_GETUP, false);

	private final int defaultId;
	/** Plays round and round at its own pace (else it is set by a progress). */
	final boolean loops;

	/** The animation ID: the member's own walk or run when known (their weapon's), else the game's; -1 for none. */
	int id(int memberWalk, int memberRun)
	{
		return this == WALK && memberWalk >= 0 ? memberWalk : this == RUN && memberRun >= 0 ? memberRun : defaultId;
	}

	/** What a ghost plays: on foot by its gait, on the board the stance, knocked down the lie-down and get-up. */
	static GhostAnim pick(boolean onFoot, FootBody.Gait gait, KnockdownPose.Stage knock)
	{
		return knock == KnockdownPose.Stage.LIE ? LIE : knock == KnockdownPose.Stage.GET_UP ? GET_UP
			: knock != null || !onFoot ? STANCE : gait == FootBody.Gait.WALK ? WALK : gait == FootBody.Gait.RUN ? RUN
			: NONE;
	}
}
