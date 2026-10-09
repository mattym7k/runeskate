package com.gielinorskate.render;

import net.runelite.api.gameval.AnimationID;

/**
* The winner's celebration after a Skate Duel: the game's cheer emote while riding on (the board keeps rolling and
* steering is the skater's: the arms go up over the riding stance), or the jump-for-joy emote on foot, for
* {@link #DURATION} seconds, with a sparkle at the start. Any input ends it early on the local skater; a party ghost
* plays it through. Pure.
*/
public final class CelebrationSequence
{
public static final float DURATION = 1.8f;
/** Riding: the cheer (both arms up), which reads well over the board stance. */
public static final int ON_BOARD_ANIMATION = AnimationID.EMOTE_CHEER;
/** On foot: jump for joy. */
public static final int ON_FOOT_ANIMATION = AnimationID.EMOTE_JUMP_WITH_JOY;

private CelebrationSequence()
{
}

/** The emote for a skater on the board or on foot. */
public static int animation(boolean onFoot)
{
return onFoot ? ON_FOOT_ANIMATION : ON_BOARD_ANIMATION;
}

/** Still celebrating {@code t} seconds in. */
public static boolean playing(float t)
{
return t >= 0f && t < DURATION;
}

/** 0..1 of the way through, for pacing the emote into {@link #DURATION}. */
public static float progress(float t)
{
return t > 0f ? Math.min(1f, t / DURATION) : 0f;
}
}
