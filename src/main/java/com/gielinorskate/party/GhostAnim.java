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
GET_UP(AnimationID.HUMAN_GETUP, false),
/** A duel tantrum's wind-up: stamping the feet (paced by the wind-up's progress). */
STOMP(TantrumSequence.STOMP_ANIMATION, false),
/** A duel win, riding: the cheer (paced by the celebration's progress). */
CHEER(CelebrationSequence.ON_BOARD_ANIMATION, false),
/** A duel win, on foot: jump for joy. */
JOY(CelebrationSequence.ON_FOOT_ANIMATION, false);

private final int defaultId;
/** Plays round and round at its own pace (else it is set by a progress). */
final boolean loops;

/** The animation ID: the member's own walk or run when known (their weapon's), else the game's; -1 for none. */
int id(int memberWalk, int memberRun)
{
return this == WALK && memberWalk >= 0 ? memberWalk : this == RUN && memberRun >= 0 ? memberRun : defaultId;
}

/**
* A duel ending's animation over what the ghost would play, or null for none: the tantrum ({@code tantrumAge}
* seconds in; NaN or past it when none) plays the stamp in its wind-up and stands still the rest of it; the
* celebration ({@code celebrateAge} in) cheers on the board and jumps for joy on foot. The tantrum wins.
*/
static GhostAnim ending(float tantrumAge, float celebrateAge, boolean onFoot)
{
if (tantrumAge >= 0f && tantrumAge < TantrumSequence.DURATION)
return TantrumSequence.animation(tantrumAge) >= 0 ? STOMP : NONE;
return !CelebrationSequence.playing(celebrateAge) ? null : onFoot ? JOY : CHEER;
}

/** How far through {@link #ending}'s animation is (its frame is set by it). */
static float endingProgress(GhostAnim a, float tantrumAge, float celebrateAge)
{
return a == STOMP ? TantrumSequence.progress(tantrumAge)
: a == CHEER || a == JOY ? CelebrationSequence.progress(celebrateAge) : 0f;
}

/** What a ghost plays: on foot by its gait, on the board the stance, knocked down the lie-down and get-up. */
static GhostAnim pick(boolean onFoot, FootBody.Gait gait, KnockdownPose.Stage knock)
{
return knock == KnockdownPose.Stage.LIE ? LIE : knock == KnockdownPose.Stage.GET_UP ? GET_UP
: knock != null || !onFoot ? STANCE : gait == FootBody.Gait.WALK ? WALK : gait == FootBody.Gait.RUN ? RUN
: NONE;
}
}
