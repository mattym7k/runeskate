package com.gielinorskate.session;

import com.gielinorskate.duel.DuelEnding;
import com.gielinorskate.input.FootControls;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.render.CelebrationSequence;
import com.gielinorskate.render.TantrumSequence;

/**
* When the local skater plays a finished duel's ending, and how far through it is. An ending waits for a moment it
* can start: a tantrum on the ground (on the board it steps off first; knocked down, it waits for the get-up), a
* celebration on the ground on the board or on foot; one that cannot start in time is dropped. The tantrum takes no
* input (only Esc, which ends skating); the celebration ends early on any input. The "Duel endings" setting off or a
* PvP area drops whatever is waiting or playing. Pure.
*/
final class DuelEndingDirector
{
/** A knocked-out skater is still lying or getting up for a while: the tantrum waits this long for it. */
static final float TANTRUM_WAIT = 12f;
/** A celebration waits this long for the winner to come down from a trick. */
static final float CELEBRATE_WAIT = 4f;

/** Where the skater is this frame. */
enum Where
{
/** On the board rolling or in a manual. */
BOARD_GROUND,
/** On the board in the air, grinding or bailing. */
BOARD_BUSY,
FOOT_GROUND,
FOOT_AIR,
/** Knocked off by a bail: tumbling, lying or getting up. */
KNOCKED_DOWN
}

private DuelEnding pending = DuelEnding.NONE;
private float pendingSince;
private float tantrum = Float.NaN;
private float celebration = Float.NaN;

/** A duel just ended with {@code ending} at {@code now}: it waits for its moment (replacing any waiting). */
void offer(DuelEnding ending, float now)
{
if (ending != null && ending != DuelEnding.NONE)
{
pending = ending;
pendingSince = now;
}
}

DuelEnding pending()
{
return pending;
}

/**
* The ending to start now (NONE: nothing, or keep waiting). A started one is no longer pending; the caller starts
* it ({@link #startTantrum} or {@link #startCelebration}).
*
* @param enabled the "Duel endings" setting
* @param blocked the skater is in a PvP area (or anywhere nothing is shared from)
*/
DuelEnding poll(float now, Where where, boolean enabled, boolean blocked)
{
if (!enabled || blocked)
{
pending = DuelEnding.NONE;
return DuelEnding.NONE;
}
if (pending == DuelEnding.NONE || isPlaying())
return DuelEnding.NONE;
// on the ground it starts; one that cannot start in time is dropped
boolean ground = where == Where.BOARD_GROUND || where == Where.FOOT_GROUND;
DuelEnding e = ground ? pending : DuelEnding.NONE;
if (ground || now - pendingSince > (pending == DuelEnding.TANTRUM ? TANTRUM_WAIT : CELEBRATE_WAIT))
pending = DuelEnding.NONE;
return e;
}

void startTantrum()
{
tantrum = 0f;
celebration = Float.NaN;
}

void startCelebration()
{
celebration = 0f;
}

boolean inTantrum()
{
return !Float.isNaN(tantrum);
}

boolean celebrating()
{
return !Float.isNaN(celebration);
}

boolean isPlaying()
{
return inTantrum() || celebrating();
}

/** Seconds into the tantrum (NaN when none). */
float tantrumTime()
{
return tantrum;
}

/** Seconds into the celebration (NaN when none). */
float celebrationTime()
{
return celebration;
}

/**
* The tantrum goes on by {@code dt}: true on the step the board hits the ground and snaps (once). Over at
* {@link TantrumSequence#DURATION} ({@link #inTantrum} false from then).
*/
boolean advanceTantrum(float dt)
{
if (!inTantrum())
return false;
float before = tantrum;
tantrum += Math.max(0f, dt);
boolean snap = before < TantrumSequence.SNAP_AT && tantrum >= TantrumSequence.SNAP_AT;
if (tantrum >= TantrumSequence.DURATION)
tantrum = Float.NaN;
return snap;
}

/**
* The celebration goes on by {@code dt}; any input ends it at once. False once it is over (it is then no longer
* {@link #celebrating}); the caller stops the emote.
*/
boolean advanceCelebration(float dt, boolean input)
{
if (!celebrating())
return false;
celebration += Math.max(0f, dt);
if (input || !CelebrationSequence.playing(celebration))
{
celebration = Float.NaN;
return false;
}
return true;
}

/** Everything stops and nothing waits (skating ended, the setting turned off). */
void clear()
{
pending = DuelEnding.NONE;
tantrum = Float.NaN;
celebration = Float.NaN;
}

/** Any control on the board this frame (the celebration ends on it). */
static boolean anyInput(SkateInput in, boolean boardKey)
{
return boardKey || in.steer != 0f || in.crouch || in.charge || in.powerslide || in.leanBack || in.pushPressed
|| in.pushHeld || in.resetRequested || !in.gestures.isEmpty() || in.manualHeld || in.noseManualHeld
|| in.grabLeft || in.grabRight || in.leanForwardKey || in.leanBackKey || in.grindHeld;
}

/** Any control on foot this frame. */
static boolean anyInput(FootControls fc, boolean boardKey)
{
return boardKey || fc.forward || fc.back || fc.left || fc.right || fc.jumpPressed || fc.dropPickupPressed;
}
}
