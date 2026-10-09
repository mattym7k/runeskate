package com.gielinorskate.ui;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import lombok.AllArgsConstructor;

/**
* The geometry of a small picture of a mouse flick, in a unit box (x right, y down, both 0..1): a thin wind-up
* stroke (pull down, or push up for a nollie) into a hook, then the thick flick with an arrowhead. A flick with
* path turn bends: a little for a varial (60+ degrees), a lot for a 360 (150+), the thresholds of
* {@link com.gielinorskate.tricks.TrickCatalog}. Pure; drawn by {@link GestureGlyphPainter}.
*/
@AllArgsConstructor
public final class GestureGlyph
{
/** Length of the flick in box units. */
private static final float FLICK_LENGTH = 0.42f;

/** Wind-up stroke start and end (the hook, where the flick starts). */
public final float[] windUpStart;
public final float[] hook;
/** Quadratic flick: start = {@link #hook}, control point (equal to the midpoint when straight), end. */
public final float[] control;
public final float[] flickEnd;
/** Arrowhead: the two side tips (the point is {@link #flickEnd}). */
public final float[] arrowLeft;
public final float[] arrowRight;
/** The flick bends (a varial or 360). */
public final boolean curved;
/** Shift held: drawn with a small Shift mark. */
public final boolean shift;

/** Unit vector of a flick direction in screen terms (y down); the directions go anticlockwise from up. */
static float[] vector(Direction d)
{
double a = Math.toRadians(45 * d.ordinal());
return new float[]{(float) -Math.sin(a), (float) -Math.cos(a)};
}

public static GestureGlyph of(Gesture g)
{
float[] dir = vector(g.direction);
// the hook sits opposite the flick so the whole picture fits the box: a flick up starts low, and so on
float hx = clamp(0.5f - dir[0] * FLICK_LENGTH / 2f);
float hy = clamp(0.5f - dir[1] * FLICK_LENGTH / 2f);
// wind-up (0.34 long): pulled down into the hook (it starts above it), or pushed up for a nollie (starts
// below); drawn slanted from the side away from the flick, so it never hides under an up or down flick
float side = dir[0] > 0.01f ? -1f : 1f;
float[] windUpStart = {clamp(hx + side * 0.34f * 0.45f), clamp(hy + (g.nollie ? 0.34f : -0.34f))};
float[] end = {clamp(hx + dir[0] * FLICK_LENGTH), clamp(hy + dir[1] * FLICK_LENGTH)};

boolean curved = g.turnDegrees >= 60f;
// the control point's sideways offset, as a fraction of the flick length: a 360, a varial, straight
float bend = g.turnDegrees >= 150f ? 0.75f : curved ? 0.35f : 0f;
// bend to the flick's left for a leftward flick, its right otherwise (perpendicular (dy, -dx) is left)
float bendSign = dir[0] < -0.01f ? 1f : -1f;
float len = (float) Math.hypot(end[0] - hx, end[1] - hy) * bend * bendSign;
float[] control = {clamp((hx + end[0]) / 2f + dir[1] * len), clamp((hy + end[1]) / 2f - dir[0] * len)};

// arrowhead along the curve's end tangent (control -> end), sides 0.14 long opening 28 degrees from it
double tx = end[0] - control[0];
double ty = end[1] - control[1];
double back = Math.hypot(tx, ty) < 1e-6 ? Math.atan2(-dir[1], -dir[0]) : Math.atan2(-ty, -tx);
double spread = Math.toRadians(28);
return new GestureGlyph(windUpStart, new float[]{hx, hy}, control, end, tip(end, back + spread),
tip(end, back - spread), curved, g.modified);
}

private static float[] tip(float[] end, double angle)
{
return new float[]{(float) (end[0] + 0.14 * Math.cos(angle)), (float) (end[1] + 0.14 * Math.sin(angle))};
}

private static float clamp(float v)
{
return Math.max(0.04f, Math.min(0.96f, v));
}
}
