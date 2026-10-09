package com.gielinorskate.render;

/**
* The player's own idle pose while the animator replaces it with skate poses, so exiting gives back the right
* one. The game can set a new idle pose while skating (a weapon wielded from the inventory): any pose found on the
* player that is not the one last written here came from the game, and becomes the pose given back. Pure.
*/
final class IdlePoseTracker
{
private static final int NONE = Integer.MIN_VALUE;

private int saved;
private int written = NONE;

/** Skating starts with the player's idle pose {@code current}. */
void start(int current)
{
saved = current;
written = NONE;
}

/** The animator set the player's idle pose to {@code pose}. */
void wrote(int pose)
{
written = pose;
}

/**
* The player's idle pose is {@code current} now (read before writing).
*
* @return true when the game changed it since the last write
*/
boolean observe(int current)
{
if (written != NONE && current == written)
return false;
boolean changed = written != NONE;
saved = current;
written = NONE;
return changed;
}

/** The player's own idle pose, to give back on exit (and to stand in on foot). */
int restorePose()
{
return saved;
}
}
