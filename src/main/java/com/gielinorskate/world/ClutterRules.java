package com.gielinorskate.world;

/**
 * Decides what a blocking scene object becomes for the skater: a {@link BlockerSet#SOLID} box, a
 * {@link BlockerSet#LOW} box you can land on, or {@link BlockerSet#PASS} clutter you ride straight through.
 * Also the model-slice footprint math the boxes are cut from. Pure; no client dependency.
 */
public final class ClutterRules
{
	/** Vertices up to this height (local units above the model base) make the footprint: the skater's height. */
	public static final float SLICE_HEIGHT = 90f;
	/** Models this tall or lower are stepped over (the physics max step up is 24). */
	public static final float STEP_HEIGHT = 24f;
	/** Footprints with a shorter side than this are thin clutter (posts, stalks): ridden through. */
	public static final float MIN_SOLID_SIDE = 30f;
	/** A LOW box needs this much shorter side to be stood on. */
	public static final float MIN_LOW_SIDE = 40f;
	/** Above this a box is SOLID rather than LOW; the same limit as full-tile platforms. */
	public static final float LOW_MAX_HEIGHT = GridCollisionWorld.PLATFORM_MAX_HEIGHT;

	private static final float JAU_TO_RAD = (float) (2 * Math.PI / 2048);

	private ClutterRules()
	{
	}

	/**
	 * Kind of a blocker cut from a model: PASS when {@code passByName} (plants, open doors), for anything
	 * that can be stepped over (height 24 or lower) and for thin slices (shorter side under 30); LOW for up
	 * to 120 tall with a shorter side of at least 40; SOLID otherwise.
	 */
	public static byte classify(boolean passByName, float height, float sliceMinSide)
	{
		if (passByName || height <= STEP_HEIGHT || sliceMinSide < MIN_SOLID_SIDE)
		{
			return BlockerSet.PASS;
		}
		if (height <= LOW_MAX_HEIGHT && sliceMinSide >= MIN_LOW_SIDE)
		{
			return BlockerSet.LOW;
		}
		return BlockerSet.SOLID;
	}

	/** Kind of a name-grindable object's box (always landable, so a missed grind lands on top). */
	public static byte classifyGrindable(float height)
	{
		return height <= LOW_MAX_HEIGHT ? BlockerSet.LOW : BlockerSet.SOLID;
	}

	/**
	 * {minX, maxX, minZ, maxZ} of the model vertices at most {@code maxHeight} above the base (RuneLite
	 * model y is negative-up, so height = -y), or null when none are that low.
	 */
	public static float[] sliceExtents(float[] xs, float[] ys, float[] zs, int count, float maxHeight)
	{
		if (xs == null || ys == null || zs == null)
		{
			return null;
		}
		int n = Math.min(count, Math.min(xs.length, Math.min(ys.length, zs.length)));
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		boolean any = false;
		for (int i = 0; i < n; i++)
		{
			if (-ys[i] <= maxHeight)
			{
				any = true;
				minX = Math.min(minX, xs[i]);
				maxX = Math.max(maxX, xs[i]);
				minZ = Math.min(minZ, zs[i]);
				maxZ = Math.max(maxZ, zs[i]);
			}
		}
		return any ? new float[]{minX, maxX, minZ, maxZ} : null;
	}

	/** Shorter side of extents {minX, maxX, minZ, maxZ}. */
	public static float minSide(float[] ext)
	{
		return Math.min(ext[1] - ext[0], ext[3] - ext[2]);
	}

	/**
	 * Oriented box {cx, cy, hx, hy, cos, sin} for extents {minX, maxX, minZ, maxZ} of an object at local
	 * (originX, originY) drawn with the given orientation (JAU), using the same rotation as
	 * {@link ObjectRailShape}: x' = x cos + z sin, y' = z cos - x sin, so the model x axis (hx) runs along
	 * (cos, -sin) of the angle. Half extents below {@code minHalf} are inflated to it.
	 */
	public static float[] box(float originX, float originY, float[] ext, int orientation, float minHalf)
	{
		float[] c = ObjectRailShape.centre(originX, originY, ext, orientation);
		float a = (orientation & 2047) * JAU_TO_RAD;
		float hx = Math.max(minHalf, (ext[1] - ext[0]) / 2f);
		float hy = Math.max(minHalf, (ext[3] - ext[2]) / 2f);
		return new float[]{c[0], c[1], hx, hy, (float) Math.cos(a), (float) -Math.sin(a)};
	}
}
