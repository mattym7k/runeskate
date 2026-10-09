package com.gielinorskate.world;

import com.gielinorskate.physics.Angles;

/**
* A straight grindable edge (rail, fence top, ledge lip). Absolute local coordinates; the top (up-positive) is
* {@link #top0} at end 0 and {@link #top1} at end 1, interpolated in between, so a handrail can follow a slope.
*/
public final class GrindSegment
{
public final float x0;
public final float y0;
public final float x1;
public final float y1;
/** Absolute height of the grindable top at end 0 and end 1, up-positive. */
public final float top0;
public final float top1;
/** Mean of {@link #top0} and {@link #top1} (the top of a flat segment). */
public final float top;

/** A flat segment at {@code top}. */
public GrindSegment(float x0, float y0, float x1, float y1, float top)
{
this(x0, y0, x1, y1, top, top);
}

public GrindSegment(float x0, float y0, float x1, float y1, float top0, float top1)
{
this.x0 = x0;
this.y0 = y0;
this.x1 = x1;
this.y1 = y1;
this.top0 = top0;
this.top1 = top1;
top = (top0 + top1) / 2f;
}

public float length()
{
return (float) Math.hypot(x1 - x0, y1 - y0);
}

/** Height of the top at parameter t (0 = end 0, 1 = end 1). */
public float topAt(float t)
{
return top0 + (top1 - top0) * t;
}

/** Rise of the top per unit moved from end 0 toward end 1 (0 for a flat or zero-length segment). */
public float slope()
{
float len = length();
return len > 0f ? (top1 - top0) / len : 0f;
}

/** Parameter of the closest point on the segment to (x, y), clamped to [0, 1]. */
public float project(float x, float y)
{
float dx = x1 - x0;
float dy = y1 - y0;
float len2 = dx * dx + dy * dy;
if (len2 <= 0f)
return 0f;
return Math.max(0f, Math.min(1f, ((x - x0) * dx + (y - y0) * dy) / len2));
}

/** Horizontal distance from (x, y) to the point at parameter t. */
float distance(float x, float y, float t)
{
return (float) Math.hypot(x - xAt(t), y - yAt(t));
}

public float xAt(float t)
{
return x0 + (x1 - x0) * t;
}

public float yAt(float t)
{
return y0 + (y1 - y0) * t;
}

/** Heading from end 0 to end 1 (0 = north, clockwise), radians. */
public float heading()
{
return (float) Math.atan2(x1 - x0, y1 - y0);
}

/** Angle in [0, PI/2] between a heading and this segment's line, ignoring which way along it. */
public float lineAngle(float heading)
{
float a = Angles.absDiff(heading, heading());
return Math.min(a, Angles.PI - a);
}
}
