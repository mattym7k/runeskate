package com.gielinorskate.design;

import com.gielinorskate.Text;
import com.gielinorskate.render.BakedBoardGeometry;
import java.util.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
* Where a part's design faces lie in its design layout (layout pixels, top-left origin, y down), generated from the
* geometry's corner UVs: the faces themselves (each distinct UV triangle once: the four wheels share one), their
* outline (edges no other face shares), the bounding box, a coverage mask, the truck bolts (grip and deck) and which
* end is the nose. Pure; the arrays are shared, never modified.
*/
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PartOutline
{
/**
* The truck bolts' centres in board model space (x across, z along), measured from the baked board's hardware
* (the nuts under the deck): four per truck, as x, z pairs.
*/
private static final float[] BOLTS_XZ = Text.floats("po.bolts");

public final int width;
public final int height;
/** Each distinct design face: 6 floats (x, y of A, B, C) in layout pixels. */
final float[] faces;
/** Each outline edge: 4 floats (x1, y1, x2, y2) in layout pixels. */
final float[] edges;
/** {minX, minY, maxX, maxY} of the design faces, layout pixels. */
private final double[] bounds;
/** Bolt centres {x, y} in layout pixels (none for wheels). */
final List<double[]> bolts;
/** True if the nose (model +z) is towards the top of the layout. */
public final boolean noseUp;
private boolean[] mask;

/** The outline of {@code mesh}'s design faces (the part at High detail) in {@code layout}. */
public static PartOutline of(BakedBoardGeometry.Mesh mesh, DesignLayout.Part layout, boolean withBolts)
{
int w = layout.width;
int h = layout.height;
float[] uv = mesh.cornerUv;
List<float[]> faces = new ArrayList<>();
Set<String> seenFaces = new HashSet<>();
Map<String, float[]> edgeOf = new HashMap<>();
Set<String> sharedEdges = new HashSet<>();
double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
float nearZ = Float.MAX_VALUE;
float farZ = -Float.MAX_VALUE;
double nearY = 0;
double farY = 0;
faces:
for (int t = 0; uv != null && t < mesh.faceCount(); t++)
{
long[] key = new long[3];
float[] xy = new float[6];
for (int k = 0; k < 3; k++)
{
float u = uv[t * 6 + k * 2];
float v = uv[t * 6 + k * 2 + 1];
if (Float.isNaN(u) || Float.isNaN(v))
// not a design face
continue faces;
key[k] = Math.round(u * 10000.0) * 1_000_000L + Math.round(v * 10000.0);
xy[k * 2] = u * w;
xy[k * 2 + 1] = (1 - v) * h;
}
for (int k = 0; k < 3; k++)
{
double x = xy[k * 2];
double y = xy[k * 2 + 1];
bounds[0] = Math.min(bounds[0], x);
bounds[1] = Math.min(bounds[1], y);
bounds[2] = Math.max(bounds[2], x);
bounds[3] = Math.max(bounds[3], y);
float z = mesh.vertices[mesh.faces[t * 3 + k] * 3 + 2];
if (z < nearZ)
{
nearZ = z;
nearY = y;
}
if (z > farZ)
{
farZ = z;
farY = y;
}
}
long[] sorted = key.clone();
Arrays.sort(sorted);
if (sorted[0] == sorted[1] || sorted[1] == sorted[2] || !seenFaces.add(Arrays.toString(sorted)))
// degenerate in UV, or a face another part of the mesh already painted the same way
continue;
faces.add(xy);
for (int k = 0; k < 3; k++)
{
int k2 = (k + 1) % 3;
String edge = Math.min(key[k], key[k2]) + ":" + Math.max(key[k], key[k2]);
if (edgeOf.put(edge, new float[]{xy[k * 2], xy[k * 2 + 1], xy[k2 * 2], xy[k2 * 2 + 1]}) != null)
sharedEdges.add(edge);
}
}
if (faces.isEmpty())
throw new IllegalArgumentException("the part has no design faces");
edgeOf.keySet().removeAll(sharedEdges);
return new PartOutline(w, h, flatten(faces, 6), flatten(edgeOf.values(), 4), bounds,
withBolts ? bolts(mesh, w, h) : Collections.emptyList(), farY < nearY);
}

private static float[] flatten(Collection<float[]> parts, int n)
{
float[] out = new float[parts.size() * n];
int i = 0;
for (float[] p : parts)
{
System.arraycopy(p, 0, out, i, n);
i += n;
}
return out;
}

/** Each bolt's layout position: the UV of the design face above or below it (x, z), interpolated. */
private static List<double[]> bolts(BakedBoardGeometry.Mesh mesh, int w, int h)
{
List<double[]> out = new ArrayList<>();
float[] uv = mesh.cornerUv;
float[] v = mesh.vertices;
double[] bary = new double[3];
for (int i = 0; i < BOLTS_XZ.length; i += 2)
{
float bx = BOLTS_XZ[i];
float bz = BOLTS_XZ[i + 1];
for (int t = 0; t < mesh.faceCount(); t++)
{
int ia = mesh.faces[t * 3] * 3;
int ib = mesh.faces[t * 3 + 1] * 3;
int ic = mesh.faces[t * 3 + 2] * 3;
if (!Float.isNaN(uv[t * 6]) && !Float.isNaN(uv[t * 6 + 2]) && !Float.isNaN(uv[t * 6 + 4])
&& barycentric(bx, bz, v[ia], v[ia + 2], v[ib], v[ib + 2], v[ic], v[ic + 2], bary))
{
double u = bary[0] * uv[t * 6] + bary[1] * uv[t * 6 + 2] + bary[2] * uv[t * 6 + 4];
double vv = bary[0] * uv[t * 6 + 1] + bary[1] * uv[t * 6 + 3] + bary[2] * uv[t * 6 + 5];
out.add(new double[]{u * w, (1 - vv) * h});
break;
}
}
}
return Collections.unmodifiableList(out);
}

/** True (and the weights in {@code out}) if (px, py) is inside triangle a, b, c. */
static boolean barycentric(double px, double py, double ax, double ay, double bx, double by, double cx,
double cy, double[] out)
{
double den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
if (Math.abs(den) < 1e-12)
return false;
double l1 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den;
double l2 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den;
double l3 = 1 - l1 - l2;
// a hair outside still counts
if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9)
return false;
out[0] = l1;
out[1] = l2;
out[2] = l3;
return true;
}

/** {minX, minY, maxX, maxY} of the design faces in layout pixels (shared, do not modify). */
public double[] bounds()
{
return bounds;
}

/** A pixel a triangle covers, with its weights (one per corner). */
interface Pixel
{
void at(int x, int y, double[] weights);
}

/** Each pixel of a w x h grid whose centre is inside triangle a, b, c (a hair outside counts). */
static void raster(double ax, double ay, double bx, double by, double cx, double cy, int w, int h, Pixel pixel)
{
double[] weights = new double[3];
int x1 = Math.min(w - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
int y1 = Math.min(h - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
for (int y = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy)))); y <= y1; y++)
{
for (int x = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx)))); x <= x1; x++)
{
if (barycentric(x + 0.5, y + 0.5, ax, ay, bx, by, cx, cy, weights))
pixel.at(x, y, weights);
}
}
}

/** One flag per layout pixel (row by row): true where a design face covers the pixel's centre. */
public synchronized boolean[] mask()
{
if (mask == null)
{
boolean[] m = new boolean[width * height];
for (int f = 0; f < faces.length; f += 6)
raster(faces[f], faces[f + 1], faces[f + 2], faces[f + 3], faces[f + 4], faces[f + 5], width, height,
(x, y, weights) -> m[y * width + x] = true);
mask = m;
}
return mask;
}
}
