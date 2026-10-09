package com.gielinorskate.render;

import com.gielinorskate.Text;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.AllArgsConstructor;

/**
* The baked board v3 (board_v3_high.txt and board_v3_low.txt, written by tools/blend_to_board_baked.py): up to four
* parts (grip, deck, wheels, hardware), each an indexed mesh drawn as its own model, all in the one board model
* space (as {@link BoardGeometry}: long axis z, y negative up, wheels on y = 0, mid-deck top at y = -16), so posing
* every part with the same transform puts them together as one board. Each part's image texture was baked into a
* colour at every triangle corner. Format, one record per line ('#' comments and blank lines skipped):
* <pre>
* part NAME                        starts a part: grip, deck, wheels or hardware, each at most once
* v X Y Z                          a vertex of the current part
* t A B C RRGGBB RRGGBB RRGGBB [UA VA UB VB UC VC]
*                                  a triangle of the current part: 0-based indices into the part's own vertices,
*                                  the sRGB colour at each corner and, on a design face, each corner's UV in the
*                                  part's design image (v up)
* </pre>
* The colours are the default designs; every design of a part is a colour file over the same triangles
* ({@link DesignColours}), sampled at those UVs.
* Within a part, vertices must be numbered in first-use order over its triangles (the merged runtime model numbers
* them so). High detail fills each part's budget (about 6000 triangles); low detail (about 1500 for grip and deck)
* is for the "Normal" board detail setting and for party ghosts.
*/
public final class BakedBoardGeometry
{
public static final int GRIP = 0;
public static final int DECK = 1;
public static final int WHEELS = 2;
public static final int HARDWARE = 3;
static final String[] PART_NAMES = {"grip", "deck", "wheels", "hardware"};

private BakedBoardGeometry()
{
}

/** One part of the board. {@link #of} makes one in code (tests, tools): the arrays are used as they are. */
@AllArgsConstructor(staticName = "of")
public static final class Mesh
{
/** {@link #GRIP} ... {@link #HARDWARE}. */
public final int part;
/** 3 floats (x, y, z) per vertex. */
public final float[] vertices;
/** 3 vertex indices per face. */
public final int[] faces;
/** 0xRRGGBB per face corner, 3 per face in the order of {@link #faces}. */
public final int[] cornerRgb;
/**
* Each corner's design-image UV (u, v; v up), 6 per face; NaN on faces without a design (wheel hubs,
* hardware). Null when the part has none.
*/
public final float[] cornerUv;

public int vertexCount()
{
return vertices.length / 3;
}

public int faceCount()
{
return faces.length / 3;
}
}

/** Parsed variants by detail (high true); null for one that isn't bundled. */
private static final Map<Boolean, Mesh[]> CACHE = new HashMap<>();

/**
* The bundled board's parts in high or low detail, parsed once; null if that variant isn't bundled. Shared: never
* modify them.
*/
public static Mesh[] sharedBoard(boolean high)
{
synchronized (CACHE)
{
if (!CACHE.containsKey(high))
{
InputStream in = BakedBoardGeometry.class.getResourceAsStream(
"/com/gielinorskate/board_v3_" + (high ? "high" : "low") + ".txt");
try (Reader r = in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8))
{
CACHE.put(high, r == null ? null : parse(r));
}
catch (IOException e)
{
throw new UncheckedIOException(e);
}
}
return CACHE.get(high);
}
}

/** The parts, in file order. Throws IllegalArgumentException on a malformed board. */
public static Mesh[] parse(Reader reader) throws IOException
{
List<Mesh> parts = new ArrayList<>();
boolean[] seen = new boolean[PART_NAMES.length];
PartBuilder cur = null;
BufferedReader br = new BufferedReader(reader);
String line;
int lineNo = 0;
while ((line = br.readLine()) != null)
{
lineNo++;
String[] p = line.trim().split("\\s+");
if (p[0].isEmpty() || p[0].charAt(0) == '#')
continue;
if (p[0].equals("part"))
{
check(p.length == 2, lineNo, "a part needs one name");
int part = Arrays.asList(PART_NAMES).indexOf(p[1]);
check(part >= 0 && !seen[part], lineNo, "unknown or repeated part " + p[1]);
seen[part] = true;
if (cur != null)
parts.add(cur.build());
cur = new PartBuilder(part);
}
else
{
check(cur != null && (p[0].equals("v") || p[0].equals("t")), lineNo, Text.get("board3.record"));
if (p[0].equals("v"))
cur.vertex(p, lineNo);
else
cur.triangle(p, lineNo);
}
}
check(cur != null, lineNo, "no parts");
parts.add(cur.build());
return parts.toArray(new Mesh[0]);
}

private static final class PartBuilder
{
private final int part;
private float[] verts = new float[3 * 1024];
private int[] faces = new int[3 * 1024];
private int[] rgb = new int[3 * 1024];
private float[] uv = new float[6 * 1024];
private boolean anyUv;
private int vCount;
private int fCount;
private int nextUnused;

PartBuilder(int part)
{
this.part = part;
}

void vertex(String[] p, int lineNo)
{
check(p.length == 4, lineNo, "a vertex needs 3 coordinates");
if (vCount * 3 + 3 > verts.length)
verts = Arrays.copyOf(verts, verts.length * 2);
for (int i = 0; i < 3; i++)
verts[vCount * 3 + i] = finite(p[1 + i], lineNo);
vCount++;
}

void triangle(String[] p, int lineNo)
{
check(p.length == 7 || p.length == 13, lineNo, Text.get("board3.triangleSize"));
if (fCount * 3 + 3 > faces.length)
{
faces = Arrays.copyOf(faces, faces.length * 2);
rgb = Arrays.copyOf(rgb, rgb.length * 2);
uv = Arrays.copyOf(uv, uv.length * 2);
}
anyUv |= p.length == 13;
for (int i = 0; i < 6; i++)
uv[fCount * 6 + i] = p.length == 13 ? finite(p[7 + i], lineNo) : Float.NaN;
for (int i = 0; i < 3; i++)
{
int v = Integer.parseInt(p[1 + i]);
check(v >= 0 && v < vCount && v <= nextUnused, lineNo,
Text.get("board3.vertexOrder", v));
if (v == nextUnused)
nextUnused++;
faces[fCount * 3 + i] = v;
check(p[4 + i].length() == 6, lineNo, "colour " + p[4 + i] + " is not RRGGBB");
rgb[fCount * 3 + i] = Integer.parseInt(p[4 + i], 16);
}
int a = faces[fCount * 3];
int b = faces[fCount * 3 + 1];
int c = faces[fCount * 3 + 2];
check(a != b && b != c && a != c, lineNo, "a triangle uses a vertex twice");
fCount++;
}

Mesh build()
{
check(fCount > 0 && nextUnused == vCount, -1, Text.get("board3.partEmpty", PART_NAMES[part]));
return new Mesh(part, Arrays.copyOf(verts, vCount * 3), Arrays.copyOf(faces, fCount * 3),
Arrays.copyOf(rgb, fCount * 3), anyUv ? Arrays.copyOf(uv, fCount * 6) : null);
}
}

/** A finite number (NumberFormatException, an IllegalArgumentException, when it is no number). */
private static float finite(String s, int lineNo)
{
float c = Float.parseFloat(s);
check(Float.isFinite(c), lineNo, s + " is not finite");
return c;
}

private static void check(boolean ok, int lineNo, String msg)
{
if (!ok)
throw new IllegalArgumentException("board v3 line " + lineNo + ": " + msg);
}

/** Every corner's colour (0xRRGGBB) as packed HSL at {@code brightness}, in corner order. */
public static short[] cornerHsl(int[] cornerRgb, double brightness)
{
short[] out = new short[cornerRgb.length];
// boards repeat colours a lot: convert each distinct colour once
Map<Integer, Short> seen = new HashMap<>();
for (int i = 0; i < out.length; i++)
out[i] = seen.computeIfAbsent(cornerRgb[i], rgb -> (short) OsrsColor.rgbToHsl(rgb, brightness));
return out;
}
}
