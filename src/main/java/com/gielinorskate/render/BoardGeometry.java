package com.gielinorskate.render;

/** Board model space: long axis = z, y negative = up, wheels rest on y = 0. Pure. */
public final class BoardGeometry
{
/** Height of the deck top the feet stand on. */
public static final float BOARD_TOP = 16f;

private BoardGeometry()
{
}

/** Rotates by roll (around z) then pitch (around x). Returns a new array. */
public static float[] rotate(float[] tris, float roll, float pitch)
{
return rotateInto(tris, roll, pitch, new float[tris.length]);
}

/** As {@link #rotate}, writing into {@code out} (at least {@code tris.length} long; may not be {@code tris}). Returns {@code out}. */
static float[] rotateInto(float[] tris, float roll, float pitch, float[] out)
{
float cr = (float) Math.cos(roll);
float sr = (float) Math.sin(roll);
float cp = (float) Math.cos(pitch);
float sp = (float) Math.sin(pitch);
for (int i = 0; i < tris.length; i += 3)
{
float x = tris[i];
float y = tris[i + 1];
float z = tris[i + 2];
float y1 = x * sr + y * cr;
out[i] = x * cr - y * sr;
out[i + 1] = y1 * cp - z * sp;
out[i + 2] = y1 * sp + z * cp;
}
return out;
}
}
