package com.gielinorskate.render;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * A board design's corner colours (designs/ID.high.rgb and ID.low.rgb, written by tools/blend_to_board_baked.py):
 * a design is only colours over the shared geometry of {@link BakedBoardGeometry}, in its triangle order. Format
 * (big-endian; documented with the writer in tools/designs.py):
 * <pre>
 * 0   "RSKC"
 * 4   version (1), part (0 grip, 1 deck, 2 wheels), 2 bytes reserved
 * 8   int32 triangle count N
 * 12  N * 9 bytes: corners A, B, C of each triangle, R G B each
 * </pre>
 * Files are read once and kept (they are shared: never modify the arrays). A player's own design is baked at
 * runtime and {@link #register registered} instead of read from the plugin's resources.
 */
@Slf4j
public final class DesignColours
{
	private static final String DIR = "/com/gielinorskate/designs/";
	private static final int VERSION = 1;
	private static final Object LOCK = new Object();
	/** "ID.high" -> colours, or a zero-length array for a file that is missing or broken. */
	private static final Map<String, int[]> CACHE = new HashMap<>();

	private DesignColours()
	{
	}

	/** The colour file of {@code part} (0 grip, 1 deck, 2 wheels) holding these corner colours (3 per triangle). */
	public static byte[] pack(int part, int[] rgb)
	{
		if (rgb.length % 3 != 0)
		{
			throw new IllegalArgumentException("colours must come in whole triangles");
		}
		int n = rgb.length / 3;
		byte[] out = new byte[12 + rgb.length * 3];
		out[0] = 'R';
		out[1] = 'S';
		out[2] = 'K';
		out[3] = 'C';
		out[4] = VERSION;
		out[5] = (byte) part;
		out[8] = (byte) (n >>> 24);
		out[9] = (byte) (n >>> 16);
		out[10] = (byte) (n >>> 8);
		out[11] = (byte) n;
		for (int i = 0; i < rgb.length; i++)
		{
			int o = 12 + i * 3;
			out[o] = (byte) (rgb[i] >> 16);
			out[o + 1] = (byte) (rgb[i] >> 8);
			out[o + 2] = (byte) rgb[i];
		}
		return out;
	}

	/**
	 * Makes {@code high} and {@code low} design {@code id}'s colours (a player's own design, baked at runtime),
	 * replacing any before. Either may be null (that detail then draws the geometry's own colours).
	 */
	public static void register(String id, int[] high, int[] low)
	{
		synchronized (LOCK)
		{
			CACHE.put(id + ".high", high != null ? high : new int[0]);
			CACHE.put(id + ".low", low != null ? low : new int[0]);
		}
	}

	/** Forgets a registered design's colours. */
	public static void unregister(String id)
	{
		synchronized (LOCK)
		{
			CACHE.remove(id + ".high");
			CACHE.remove(id + ".low");
		}
	}

	/** The colour file's corner colours; IllegalArgumentException unless it is a valid file for {@code part}. */
	public static int[] parse(byte[] data, int part)
	{
		if (data.length < 12 || data[0] != 'R' || data[1] != 'S' || data[2] != 'K' || data[3] != 'C')
		{
			throw new IllegalArgumentException("not a design colour file");
		}
		if (data[4] != VERSION || data[5] != part)
		{
			throw new IllegalArgumentException("design colour file of version " + data[4] + " / part " + data[5]);
		}
		int n = (data[8] & 255) << 24 | (data[9] & 255) << 16 | (data[10] & 255) << 8 | (data[11] & 255);
		if (n < 0 || data.length != 12 + 9L * n)
		{
			throw new IllegalArgumentException("design colour file size does not match its " + n + " triangles");
		}
		int[] out = new int[n * 3];
		for (int i = 0; i < out.length; i++)
		{
			int o = 12 + i * 3;
			out[i] = (data[o] & 255) << 16 | (data[o + 1] & 255) << 8 | (data[o + 2] & 255);
		}
		return out;
	}

	/**
	 * Design {@code id}'s corner colours for {@code mesh} (its part, at high or low detail), or null when there is
	 * no such file or it doesn't fit the mesh (the mesh's own colours are drawn then).
	 */
	public static int[] colours(String id, boolean high, BakedBoardGeometry.Mesh mesh)
	{
		String key = id + (high ? ".high" : ".low");
		int[] c;
		synchronized (LOCK)
		{
			c = CACHE.get(key);
			if (c == null)
			{
				if (runtimeOnly(id))
				{
					// a player's or party member's design not registered (yet, or any more): nothing to load, and
					// nothing kept for it
					return null;
				}
				c = load(key, mesh.part);
				CACHE.put(key, c);
			}
		}
		if (c.length != mesh.cornerRgb.length)
		{
			if (c.length > 0)
			{
				log.debug("RuneSkate: design {} has {} corners, the board part {}", key, c.length,
					mesh.cornerRgb.length);
			}
			return null;
		}
		return c;
	}

	/** Ids only ever {@link #register registered} at runtime, never shipped as files. */
	private static boolean runtimeOnly(String id)
	{
		return id.startsWith("CUSTOM_") || id.startsWith("PARTY_");
	}

	/** Whether anything is kept for design {@code id} (tests). */
	static boolean held(String id)
	{
		synchronized (LOCK)
		{
			return CACHE.containsKey(id + ".high") || CACHE.containsKey(id + ".low");
		}
	}

	private static int[] load(String key, int part)
	{
		try (InputStream in = DesignColours.class.getResourceAsStream(DIR + key + ".rgb"))
		{
			if (in == null)
			{
				return new int[0];
			}
			return parse(in.readAllBytes(), part);
		}
		catch (IOException | IllegalArgumentException e)
		{
			log.warn("RuneSkate: design colours {} could not be read", key, e);
			return new int[0];
		}
	}
}
