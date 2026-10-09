package com.gielinorskate.physics;

import com.gielinorskate.tricks.Trick;

/**
* Pure helpers for body flips (front and back flips of the whole skater). The flip angle is in radians,
* + = frontflip (forward pitch about the centre of mass).
*/
public final class BodyFlip
{
/** The body must be within this of a whole rotation to land (or lock onto a rail). */
public static final float LAND_TOLERANCE = (float) Math.toRadians(40);
/** Float slack so exactly 40 degrees (converted to radians and offset by a turn) still counts. */
private static final float EPSILON = 1e-5f;

private BodyFlip()
{
}

/** Nearest whole number of rotations (signed). */
public static int rotations(float angle)
{
return Math.round(angle / Angles.TWO_PI);
}

/** Signed distance (radians) from the nearest whole rotation. */
public static float residual(float angle)
{
return angle - rotations(angle) * Angles.TWO_PI;
}

/** True when the body is within {@link #LAND_TOLERANCE} of a whole rotation. */
public static boolean landable(float angle)
{
return Math.abs(residual(angle)) <= LAND_TOLERANCE + EPSILON;
}

/** The trick for {@code rotations} landed (two or more is a double), or null for none. */
public static Trick trick(int rotations)
{
return rotations == 0 ? null : rotations >= 2 ? Trick.DOUBLE_FRONTFLIP : rotations > 0 ? Trick.FRONTFLIP
: rotations <= -2 ? Trick.DOUBLE_BACKFLIP : Trick.BACKFLIP;
}
}
