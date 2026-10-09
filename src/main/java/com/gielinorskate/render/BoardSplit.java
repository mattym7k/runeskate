package com.gielinorskate.render;

import java.util.IdentityHashMap;
import java.util.Map;

/**
* A board snapped in two across the deck at its middle: the plane z = 0 of board model space (the long axis is z,
* + toward the nose). Every triangle of every part goes to exactly one half by its centroid (z >= 0: the nose half),
* so nothing is cut and each half keeps its triangles' corner colours (designs, custom designs) as they are: a half
* is the whole part's mesh with the other half's faces hidden. Each half has one centre shared by all its parts (so
* the grip, deck, wheels and hardware of a half stay together as it spins), its parts' vertices are given relative to
* that centre, and a vertex no face of the half uses is parked at the centre. Split once per mesh and kept
* ({@link #of}). Pure.
*/
public final class BoardSplit
{
/** The nose half (+z) and the tail half. */
public final Half nose;
public final Half tail;

/**
* Splits a board's parts. {@code vertices[p]}: part p's (x, y, z) per vertex; {@code faces[p]}: three vertex
* indices per face.
*/
static BoardSplit split(float[][] vertices, int[][] faces)
{
return new BoardSplit(new Half(true, vertices, faces), new Half(false, vertices, faces));
}

private BoardSplit(Half nose, Half tail)
{
this.nose = nose;
this.tail = tail;
}

public Half half(boolean nose)
{
return nose ? this.nose : tail;
}

/** One half of the board, all its parts. */
public static final class Half
{
/** Per part, per face: the face belongs to this half. */
public final boolean[][] faceIn;
/** Per part, the mesh's vertices (x, y, z) relative to {@link #cx} ...; unused ones are 0. */
public final float[][] vertices;
/**
* The half's centre in board model space (the middle of its bounds over the vertices of its faces; y down),
* and the bounds' lowest point (largest y). An empty half (no faces) is a point at the origin.
*/
public final float cx;
public final float cy;
public final float cz;
public final float maxY;
/** Faces in this half, all parts. */
public final int faceCount;

Half(boolean nose, float[][] vertices, int[][] faces)
{
faceIn = new boolean[vertices.length][];
this.vertices = new float[vertices.length][];
float inf = Float.POSITIVE_INFINITY;
float[] b = {inf, inf, inf, -inf, -inf, -inf};
int count = 0;
for (int p = 0; p < vertices.length; p++)
{
float[] v = vertices[p];
int[] f = faces[p];
faceIn[p] = new boolean[f.length / 3];
for (int i = 0; i < f.length; i += 3)
{
// the centroid's side of the cut: z = 0 itself goes to the nose
if (v[f[i] * 3 + 2] + v[f[i + 1] * 3 + 2] + v[f[i + 2] * 3 + 2] >= 0f == nose)
{
faceIn[p][i / 3] = true;
count++;
for (int k = 0; k < 9; k++)
{
float c = v[f[i + k / 3] * 3 + k % 3];
b[k % 3] = Math.min(b[k % 3], c);
b[k % 3 + 3] = Math.max(b[k % 3 + 3], c);
}
}
}
}
if (count == 0)
b = new float[6];
faceCount = count;
cx = (b[0] + b[3]) / 2f;
cy = (b[1] + b[4]) / 2f;
cz = (b[2] + b[5]) / 2f;
maxY = b[4];
for (int p = 0; p < vertices.length; p++)
{
float[] v = vertices[p];
int[] f = faces[p];
float[] o = new float[v.length];
for (int i = 0; i < f.length; i++)
{
if (faceIn[p][i / 3])
{
int k = f[i] * 3;
o[k] = v[k] - cx;
o[k + 1] = v[k + 1] - cy;
o[k + 2] = v[k + 2] - cz;
}
}
this.vertices[p] = o;
}
}

/** Units from the centre down to the half's lowest point (the wheels' bottoms): it rests on that. */
public float bottom()
{
return maxY - cy;
}
}

private static final Map<BakedBoardGeometry.Mesh[], BoardSplit> CACHE = new IdentityHashMap<>();

/** The baked board's parts split, once per (shared) mesh array. */
public static BoardSplit of(BakedBoardGeometry.Mesh[] meshes)
{
synchronized (CACHE)
{
return CACHE.computeIfAbsent(meshes, m ->
{
float[][] v = new float[m.length][];
int[][] f = new int[m.length][];
for (int i = 0; i < m.length; i++)
{
v[i] = m[i].vertices;
f[i] = m[i].faces;
}
return split(v, f);
});
}
}
}
