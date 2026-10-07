package com.gielinorskate.ui;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;

/**
 * The geometry of a small picture of a mouse flick, in a unit box (x right, y down, both 0..1): a thin wind-up
 * stroke (pull down, or push up for a nollie) into a hook, then the thick flick with an arrowhead. A flick with
 * path turn bends: a little for a varial (60+ degrees), a lot for a 360 (150+). Pure; drawn by
 * {@link GestureGlyphPainter}.
 */
public final class GestureGlyph
{
	/** Turn thresholds, as in {@link com.gielinorskate.tricks.TrickCatalog}. */
	static final float VARIAL_TURN = 60f;
	static final float FULL_TURN = 150f;
	/** Length of the flick, of the wind-up, and of an arrowhead side, in box units. */
	static final float FLICK_LENGTH = 0.42f;
	static final float WIND_UP_LENGTH = 0.34f;
	static final float ARROW_LENGTH = 0.14f;
	/** Sideways offset of the control point, as a fraction of the flick length: a varial and a 360. */
	static final float VARIAL_BEND = 0.35f;
	static final float FULL_BEND = 0.75f;
	/** Arrowhead sides open this far from the backward tangent, radians (about 28 degrees). */
	static final double ARROW_SPREAD = Math.toRadians(28);

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

	private GestureGlyph(float[] windUpStart, float[] hook, float[] control, float[] flickEnd, float[] arrowLeft,
		float[] arrowRight, boolean curved, boolean shift)
	{
		this.windUpStart = windUpStart;
		this.hook = hook;
		this.control = control;
		this.flickEnd = flickEnd;
		this.arrowLeft = arrowLeft;
		this.arrowRight = arrowRight;
		this.curved = curved;
		this.shift = shift;
	}

	/** Unit vector of a flick direction in screen terms (y down). */
	static float[] vector(Direction d)
	{
		float s = (float) Math.sqrt(0.5);
		switch (d)
		{
			case UP:
				return new float[]{0f, -1f};
			case UP_LEFT:
				return new float[]{-s, -s};
			case LEFT:
				return new float[]{-1f, 0f};
			case DOWN_LEFT:
				return new float[]{-s, s};
			case DOWN:
				return new float[]{0f, 1f};
			case DOWN_RIGHT:
				return new float[]{s, s};
			case RIGHT:
				return new float[]{1f, 0f};
			default:
				return new float[]{s, -s};
		}
	}

	public static GestureGlyph of(Gesture g)
	{
		float[] dir = vector(g.direction);
		// the hook sits opposite the flick so the whole picture fits the box: a flick up starts low, and so on
		float hx = clamp(0.5f - dir[0] * FLICK_LENGTH / 2f);
		float hy = clamp(0.5f - dir[1] * FLICK_LENGTH / 2f);
		// wind-up: pulled down into the hook (it starts above it), or pushed up for a nollie (starts below); drawn
		// slanted from the side away from the flick, so it never hides under an up or down flick
		float side = dir[0] > 0.01f ? -1f : 1f;
		float wy = g.nollie ? 1f : -1f;
		float[] windUpStart = {
			clamp(hx + side * WIND_UP_LENGTH * 0.45f),
			clamp(hy + wy * WIND_UP_LENGTH)
		};
		float[] hook = {hx, hy};
		float[] end = {clamp(hx + dir[0] * FLICK_LENGTH), clamp(hy + dir[1] * FLICK_LENGTH)};

		boolean curved = g.turnDegrees >= VARIAL_TURN;
		float bend = g.turnDegrees >= FULL_TURN ? FULL_BEND : curved ? VARIAL_BEND : 0f;
		// bend to the flick's left for a leftward flick, its right otherwise (perpendicular (dy, -dx) is left)
		float bendSign = dir[0] < -0.01f ? 1f : -1f;
		float mx = (hx + end[0]) / 2f;
		float my = (hy + end[1]) / 2f;
		float len = (float) Math.hypot(end[0] - hx, end[1] - hy);
		float[] control = {
			clamp(mx + bendSign * dir[1] * bend * len),
			clamp(my - bendSign * dir[0] * bend * len)
		};

		// arrowhead along the curve's end tangent (control -> end)
		double tx = end[0] - control[0];
		double ty = end[1] - control[1];
		double tl = Math.hypot(tx, ty);
		if (tl < 1e-6)
		{
			tx = dir[0];
			ty = dir[1];
			tl = 1;
		}
		double back = Math.atan2(-ty / tl, -tx / tl);
		float[] left = {
			(float) (end[0] + ARROW_LENGTH * Math.cos(back + ARROW_SPREAD)),
			(float) (end[1] + ARROW_LENGTH * Math.sin(back + ARROW_SPREAD))
		};
		float[] right = {
			(float) (end[0] + ARROW_LENGTH * Math.cos(back - ARROW_SPREAD)),
			(float) (end[1] + ARROW_LENGTH * Math.sin(back - ARROW_SPREAD))
		};
		return new GestureGlyph(windUpStart, hook, control, end, left, right, curved, g.modified);
	}

	private static float clamp(float v)
	{
		return Math.max(0.04f, Math.min(0.96f, v));
	}
}
