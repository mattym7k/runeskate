package com.gielinorskate.render;

import com.gielinorskate.Text;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
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
* Files are read once and kept (they are shared: never modify the arrays).
*/
@Slf4j
public final class DesignColours
{
private static final String DIR = "/com/gielinorskate/designs/";
private static final int VERSION = 1;
private static final byte[] MAGIC = {'R', 'S', 'K', 'C'};
/** "ID.high" -> colours, or a zero-length array for a file that is missing or broken. */
static final Map<String, int[]> CACHE = new HashMap<>();

private DesignColours()
{
}

/** The colour file of {@code part} (0 grip, 1 deck, 2 wheels) holding these corner colours (3 per triangle). */
public static byte[] pack(int part, int[] rgb)
{
if (rgb.length % 3 != 0)
throw new IllegalArgumentException(Text.get("designs.whole"));
ByteBuffer b = ByteBuffer.allocate(12 + rgb.length * 3).put(MAGIC).put((byte) VERSION).put((byte) part)
.putShort((short) 0).putInt(rgb.length / 3);
for (int c : rgb)
b.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c);
return b.array();
}

/** The colour file's corner colours; IllegalArgumentException unless it is a valid file for {@code part}. */
public static int[] parse(byte[] data, int part)
{
if (data.length < 12 || !Arrays.equals(Arrays.copyOf(data, 4), MAGIC))
throw new IllegalArgumentException("not a design colour file");
if (data[4] != VERSION || data[5] != part)
throw new IllegalArgumentException(Text.get("designs.version", data[4], data[5]));
int n = ByteBuffer.wrap(data).getInt(8);
if (n < 0 || data.length != 12 + 9L * n)
throw new IllegalArgumentException(Text.get("designs.size", n));
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
synchronized (CACHE)
{
c = CACHE.get(key);
if (c == null)
{
c = load(key, mesh.part);
CACHE.put(key, c);
}
}
return c.length == mesh.cornerRgb.length ? c : null;
}

private static int[] load(String key, int part)
{
try (InputStream in = DesignColours.class.getResourceAsStream(DIR + key + ".rgb"))
{
return in == null ? new int[0] : parse(in.readAllBytes(), part);
}
catch (IOException | IllegalArgumentException e)
{
log.warn("RuneSkate: design colours {} could not be read", key, e);
return new int[0];
}
}
}
