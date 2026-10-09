package com.gielinorskate.physics;

/** Heading convention: 0 = north (+y), PI/2 = east (+x). */
public final class Angles
{
public static final float PI = (float) Math.PI;
public static final float TWO_PI = 2 * PI;

private Angles()
{
}

/** Wraps to (-PI, PI]. */
public static float wrap(float a)
{
a = a % TWO_PI;
if (a <= -PI)
a += TWO_PI;
else if (a > PI)
a -= TWO_PI;
return a;
}

/** Smallest absolute difference between two angles, in [0, PI]. */
public static float absDiff(float a, float b)
{
return Math.abs(wrap(a - b));
}

/** Turns {@code from} toward {@code to} by at most {@code maxStep} radians the short way round, without overshooting. */
static float turnToward(float from, float to, float maxStep)
{
float diff = wrap(to - from);
return wrap(Math.abs(diff) <= maxStep ? to : from + Math.copySign(maxStep, diff));
}

/** Converts a heading to OSRS orientation units (0 = south, 512 = west, 1024 = north, 1536 = east). */
public static int toJau(float heading)
{
return Math.floorMod(Math.round(1024 + heading * 1024 / PI), 2048);
}

public static float fromJau(int jau)
{
return wrap((jau - 1024) * PI / 1024);
}
}
