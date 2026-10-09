package com.gielinorskate.render;

import com.gielinorskate.physics.FootPhysics;

/**
* Plays the walker's OSRS walk or run pose animation faster than the game does, so its feet keep up with the
* walker's (faster than OSRS) speed: the game advances the pose animation at its own rate, and this adds the
* missing frames on top ({@code Actor.setPoseAnimationFrame}). Frame lengths are client ticks (20 ms). Pure.
*/
public final class GaitPlayback
{

/** The frame stepped, and the extra client ticks owed to the animation that did not yet make a whole frame. */
private final AnimClock clock = new AnimClock();

/**
* How many times the game's speed the gait animation should play at for a walker at {@code speed} u/s: the
* walk against the game's walk, the run against the game's run. Never below 1 (frames are only ever added).
*/
public static float rate(FootBody.Gait gait, float speed)
{
float r = gait == FootBody.Gait.IDLE ? 1f
: speed / (gait == FootBody.Gait.WALK ? FootPhysics.GAME_WALK_SPEED : FootPhysics.GAME_RUN_SPEED);
return r > 1f ? Math.min(3f, r) : 1f;
}

/**
* One frame of {@code dt} seconds at {@code rate} with the animation at {@code frame}: the frame to jump to, or
* -1 to leave the animation alone (nothing owed yet, or an animation this cannot step).
*/
public int advance(int frame, int[] frameLengths, float rate, float dt)
{
if (frameLengths == null || frame < 0 || frame >= frameLengths.length || !(dt > 0f))
{
reset();
return -1;
}
clock.frame = frame;
clock.ticks += Math.max(0f, rate - 1f) * dt * AnimClock.TICKS_PER_SECOND;
return clock.run(frameLengths) ? clock.frame : -1;
}

/** Forgets the time owed (a new animation). */
public void reset()
{
clock.reset();
}
}
