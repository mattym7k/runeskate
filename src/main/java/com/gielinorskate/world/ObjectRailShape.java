package com.gielinorskate.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Grind segments for a scene object, from its model's horizontal extents. Pure; no client dependency.
 * <p>
 * Model space x maps to local x and model z to local y, rotated by the object's orientation in JAU
 * (0..2047, 2048 = a full turn) the way the client draws it: x' = x cos + z sin, y' = z cos - x sin.
 * <ul>
 * <li>A long object (shorter extent below {@link #SQUARE_RATIO} of the longer, e.g. a fence or railing)
 * gives one rail along its long axis through the middle of the model, as long as that extent: fences
 * are drawn through the middle of their tile, not on its edges.</li>
 * <li>A square-ish object (bench, crate, table) gives the four edges of its rotated bounding rectangle,
 * so ledges sit on the object's real edges rather than the tile's.</li>
 * </ul>
 */
public final class ObjectRailShape
{
	/** Shorter extent at least this fraction of the longer: square-ish (extents within 25% of each other). */
	public static final float SQUARE_RATIO = 0.75f;
	private static final float JAU_TO_RAD = (float) (2 * Math.PI / 2048);

	private ObjectRailShape()
	{
	}

	/** {minX, maxX, minZ, maxZ} of the first {@code count} vertices, or null when there are none. */
	public static float[] extents(float[] xs, float[] zs, int count)
	{
		if (xs == null || zs == null)
		{
			return null;
		}
		int n = Math.min(count, Math.min(xs.length, zs.length));
		if (n <= 0)
		{
			return null;
		}
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++)
		{
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minZ = Math.min(minZ, zs[i]);
			maxZ = Math.max(maxZ, zs[i]);
		}
		return new float[]{minX, maxX, minZ, maxZ};
	}

	/** Local (x, y) of the centre of the extents, for an object at (originX, originY). */
	public static float[] centre(float originX, float originY, float[] ext, int orientation)
	{
		return toLocal(originX, originY, (ext[0] + ext[1]) / 2f, (ext[2] + ext[3]) / 2f, orientation);
	}

	/**
	 * The grind segments of an object at local (originX, originY) whose model has extents
	 * {@code ext} = {minX, maxX, minZ, maxZ}, at absolute height {@code top}.
	 */
	public static List<GrindSegment> segments(float originX, float originY, float[] ext, int orientation, float top)
	{
		float lenX = ext[1] - ext[0];
		float lenZ = ext[3] - ext[2];
		float longer = Math.max(lenX, lenZ);
		if (longer <= 0f)
		{
			return Collections.emptyList();
		}
		List<GrindSegment> out = new ArrayList<>();
		float cx = (ext[0] + ext[1]) / 2f;
		float cz = (ext[2] + ext[3]) / 2f;
		if (Math.min(lenX, lenZ) >= SQUARE_RATIO * longer)
		{
			float[][] corners = {
				{ext[0], ext[2]}, {ext[1], ext[2]}, {ext[1], ext[3]}, {ext[0], ext[3]},
			};
			for (int i = 0; i < 4; i++)
			{
				float[] a = corners[i];
				float[] b = corners[(i + 1) % 4];
				out.add(segment(originX, originY, a[0], a[1], b[0], b[1], orientation, top));
			}
		}
		else if (lenX >= lenZ)
		{
			out.add(segment(originX, originY, ext[0], cz, ext[1], cz, orientation, top));
		}
		else
		{
			out.add(segment(originX, originY, cx, ext[2], cx, ext[3], orientation, top));
		}
		return out;
	}

	private static GrindSegment segment(float ox, float oy, float ax, float az, float bx, float bz, int orientation, float top)
	{
		float[] a = toLocal(ox, oy, ax, az, orientation);
		float[] b = toLocal(ox, oy, bx, bz, orientation);
		return new GrindSegment(a[0], a[1], b[0], b[1], top);
	}

	private static float[] toLocal(float ox, float oy, float mx, float mz, int orientation)
	{
		float a = (orientation & 2047) * JAU_TO_RAD;
		float sin = (float) Math.sin(a);
		float cos = (float) Math.cos(a);
		return new float[]{ox + mx * cos + mz * sin, oy + mz * cos - mx * sin};
	}
}
