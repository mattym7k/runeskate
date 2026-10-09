package com.gielinorskate.render;

/**
* Detects when an animation leaves the player with no model (some animations don't work on player
* models), which would make the skater invisible. Pure: feed it one sample per frame.
*/
final class PoseGuard
{

private int missingFrames;

/** @return true when the current animations should be reverted to the defaults */
boolean frame(boolean hasModel)
{
missingFrames = hasModel ? 0 : missingFrames + 1;
if (missingFrames >= 3)
{
missingFrames = 0;
return true;
}
return false;
}
}
