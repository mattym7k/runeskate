package com.gielinorskate.party;

import com.gielinorskate.render.FootBody;
import com.gielinorskate.render.CelebrationSequence;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.render.TantrumSequence;
import net.runelite.api.gameval.AnimationID;

/**
 * The OSRS animation a ghost's body plays under its procedural pose: the local skater's (the default skate poses),
 * on the ghost's own copy of the member's model, never on their character. Pure.
 */
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
	GET_UP(AnimationID.HUMAN_GETUP, false),
	/** A duel tantrum's wind-up: stamping the feet (paced by the wind-up's progress). */
	STOMP(TantrumSequence.STOMP_ANIMATION, false),
	/** A duel win, riding: the cheer (paced by the celebration's progress). */
	CHEER(CelebrationSequence.ON_BOARD_ANIMATION, false),
	/** A duel win, on foot: jump for joy. */
	JOY(CelebrationSequence.ON_FOOT_ANIMATION, false);

	private final int defaultId;
	private final boolean loops;

	GhostAnim(int defaultId, boolean loops)
	{
		this.defaultId = defaultId;
		this.loops = loops;
	}

	/** Plays round and round at its own pace (else it is set by a progress). */
	boolean loops()
	{
		return loops;
	}

	/** The animation ID: the member's own walk or run when known (their weapon's), else the game's; -1 for none. */
	int id(int memberWalk, int memberRun)
	{
		if (this == WALK && memberWalk >= 0)
		{
			return memberWalk;
		}
		if (this == RUN && memberRun >= 0)
		{
			return memberRun;
		}
		return defaultId;
	}

	/**
	 * A duel ending's animation over what the ghost would play, or null for none: the tantrum ({@code tantrumAge}
	 * seconds in; NaN or past it when none) plays the stamp in its wind-up and stands still the rest of it; the
	 * celebration ({@code celebrateAge} in) cheers on the board and jumps for joy on foot. The tantrum wins.
	 */
	static GhostAnim ending(float tantrumAge, float celebrateAge, boolean onFoot)
	{
		if (tantrumAge >= 0f && tantrumAge < TantrumSequence.DURATION)
		{
			return TantrumSequence.animation(tantrumAge) >= 0 ? STOMP : NONE;
		}
		if (CelebrationSequence.playing(celebrateAge))
		{
			return onFoot ? JOY : CHEER;
		}
		return null;
	}

	/** How far through {@link #ending}'s animation is (its frame is set by it). */
	static float endingProgress(GhostAnim a, float tantrumAge, float celebrateAge)
	{
		if (a == STOMP)
		{
			return TantrumSequence.progress(tantrumAge);
		}
		return a == CHEER || a == JOY ? CelebrationSequence.progress(celebrateAge) : 0f;
	}

	/** What a ghost plays: on foot by its gait, on the board the stance, knocked down the lie-down and get-up. */
	static GhostAnim pick(boolean onFoot, FootBody.Gait gait, KnockdownPose.Stage knock)
	{
		if (knock == KnockdownPose.Stage.LIE)
		{
			return LIE;
		}
		if (knock == KnockdownPose.Stage.GET_UP)
		{
			return GET_UP;
		}
		if (knock != null || !onFoot)
		{
			return STANCE;
		}
		switch (gait)
		{
			case WALK:
				return WALK;
			case RUN:
				return RUN;
			default:
				return NONE;
		}
	}
}
