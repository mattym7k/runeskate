package com.gielinorskate.tricks;

import static com.gielinorskate.tricks.Trick.*;

import com.gielinorskate.tricks.Gesture.Direction;

/** Maps a recognized {@link Gesture} to the {@link Trick} it performs. */
public final class TrickCatalog
{
private TrickCatalog()
{
}

/** Returns the trick for a fresh gesture, or null when nothing matches. */
public static Trick forGesture(Gesture g)
{
Trick hard = g.modified ? modifiedTrick(g) : null;
if (hard != null)
return hard;
// a curve of 150 degrees or more makes the 360 forms, 60 or more the varials
boolean full = g.turnDegrees >= 150f;
if (g.nollie)
{
switch (g.direction)
{
case DOWN:
return NOLLIE;
case DOWN_LEFT:
return full ? NOLLIE_TRE_FLIP : NOLLIE_KICKFLIP;
case DOWN_RIGHT:
return NOLLIE_HEELFLIP;
case LEFT:
return NOLLIE_SHOVE_IT;
case RIGHT:
return NOLLIE_FS_SHOVE_IT;
default:
return null;
}
}
boolean varial = g.turnDegrees >= 60f;
switch (g.direction)
{
case UP:
return OLLIE;
case UP_LEFT:
return full ? TRE_FLIP : varial ? VARIAL_KICKFLIP : KICKFLIP;
case UP_RIGHT:
return full ? LASER_FLIP : varial ? VARIAL_HEELFLIP : HEELFLIP;
case LEFT:
return full ? SHOVE_IT_360 : POP_SHOVE_IT;
case RIGHT:
return full ? FS_SHOVE_IT_360 : FS_POP_SHOVE_IT;
default:
return null;
}
}

/**
* The Shift form of a flick, whatever its curve: ollie -> impossible, kickflip family -> hardflip, heelflip
* family -> inward heelflip, shove-its -> bigspins, and the nollie kickflip / heelflip -> nollie hardflip /
* nollie inward heelflip. Null when the flick has no Shift form (it is then the plain trick).
*/
private static Trick modifiedTrick(Gesture g)
{
switch (g.direction)
{
case DOWN_LEFT:
return g.nollie ? NOLLIE_HARDFLIP : null;
case DOWN_RIGHT:
return g.nollie ? NOLLIE_INWARD_HEELFLIP : null;
case UP:
return g.nollie ? null : IMPOSSIBLE;
case UP_LEFT:
return g.nollie ? null : HARDFLIP;
case UP_RIGHT:
return g.nollie ? null : INWARD_HEELFLIP;
case LEFT:
return g.nollie ? null : BIGSPIN;
case RIGHT:
return g.nollie ? null : FS_BIGSPIN;
default:
return null;
}
}

/** Returns the upgraded trick for a quick re-flick following {@code current}, or null. */
public static Trick upgrade(Trick current, Gesture g)
{
boolean kick = g.direction == Direction.UP_LEFT;
boolean heel = g.direction == Direction.UP_RIGHT;
if (current == null)
return null;
switch (current)
{
case KICKFLIP:
return kick ? DOUBLE_KICKFLIP : null;
case HEELFLIP:
return heel ? DOUBLE_HEELFLIP : null;
case DOUBLE_KICKFLIP:
return kick ? TRIPLE_KICKFLIP : null;
case DOUBLE_HEELFLIP:
return heel ? TRIPLE_HEELFLIP : null;
default:
return null;
}
}
}
