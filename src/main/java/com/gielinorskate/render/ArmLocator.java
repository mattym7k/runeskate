package com.gielinorskate.render;

/**
* Finds one arm of a player mesh by its shape: OSRS models carry no bone or vertex-group data through the API,
* so the arm is "what sticks out sideways past the chest, from the shoulder down". Puppet model space: y down
* with the soles at 0, the body facing -z, left hand +x, right hand -x (see {@link MeshDeformer}).
*
* <p>The shoulder sits 0.75 of the model's height up, a little inside (0.8 of) the widest point of the chest band
* there (0.06 of the height either side), that half-width kept within 0.07 and 0.16 of the height. A vertex belongs
* to the arm by a smooth weight (never a hard cut, so moving the arm can never tear the mesh): 0 inside 0.66 of the
* shoulder half-width, 1 past 0.78, faded out over 0.06 of the height above the shoulder (the head) and 0.12 below the
* hips (the legs). The hand is the end of the arm: the arm vertices at least 0.92 of the furthest arm vertex's
* distance from the shoulder. Models shorter than 60 units are not a body. Pure, allocation-free; one instance per
* user (client thread only).
*/
final class ArmLocator
{
/** The centre line is measured on vertices lower than this fraction of the hip height (below the hands). */
static final float LOWER_LEGS = 0.6f;

/** Results of the latest {@link #locate}: the shoulder joint and the hand, model space. */
final float[] shoulder = new float[3];
final float[] hand = new float[3];
/** Distance from the shoulder to the furthest arm vertex. */
float armLength;
/** The arm vertex furthest from the shoulder. */
int handIndex;
/** The located arm's side: +1 the model's +x side (the left hand), -1 the right. */
float side;
private float cx;
private float halfWidth;
private float height;
private float hip;

/**
* Finds the arm on {@code side} (+1 the model's +x side, the left hand; -1 the right) among the first {@code n}
* vertices. False when the mesh does not look like a body with an arm there.
*/
boolean locate(float[] xs, float[] ys, float[] zs, int n, float side)
{
this.side = side < 0f ? -1f : 1f;
if (n <= 0 || xs == null || ys == null || zs == null || xs.length < n || ys.length < n || zs.length < n)
return false;
float top = 0f;
for (int i = 0; i < n; i++)
top = Math.min(top, ys[i]);
height = -top;
if (!(height >= 60f) || Float.isInfinite(height))
return false;
hip = MeshDeformer.hipHeight(top);
// the centre line from the lower legs (the arms can swing anywhere, the hands hang down to the hips; the
// legs stand either side of it)
float minX = Float.MAX_VALUE;
float maxX = -Float.MAX_VALUE;
for (int i = 0; i < n; i++)
{
if (ys[i] > -LOWER_LEGS * hip)
{
minX = Math.min(minX, xs[i]);
maxX = Math.max(maxX, xs[i]);
}
}
cx = minX <= maxX ? (minX + maxX) / 2f : 0f;
float shoulderY = -0.75f * height;
float widest = 0f;
float sumZ = 0f;
int count = 0;
for (int i = 0; i < n; i++)
{
if (Math.abs(ys[i] - shoulderY) <= 0.06f * height)
{
widest = Math.max(widest, (xs[i] - cx) * this.side);
sumZ += zs[i];
count++;
}
}
if (count == 0)
return false;
halfWidth = Math.max(0.07f * height, Math.min(0.16f * height, widest));
shoulder[0] = cx + this.side * 0.8f * halfWidth;
shoulder[1] = shoulderY;
shoulder[2] = sumZ / count;

float far = 0f;
int farIndex = -1;
for (int i = 0; i < n; i++)
{
float d = weight(xs[i], ys[i]) >= 0.5f ? distance(xs[i], ys[i], zs[i]) : 0f;
if (d > far)
{
far = d;
farIndex = i;
}
}
if (farIndex < 0 || far < 0.1f * height || Float.isInfinite(far))
return false;
int hn = 0;
hand[0] = 0f;
hand[1] = 0f;
hand[2] = 0f;
for (int i = 0; i < n; i++)
{
if (weight(xs[i], ys[i]) >= 0.5f && distance(xs[i], ys[i], zs[i]) >= 0.92f * far)
{
hand[0] += xs[i];
hand[1] += ys[i];
hand[2] += zs[i];
hn++;
}
}
for (int k = 0; k < 3; k++)
hand[k] /= hn;
armLength = far;
handIndex = farIndex;
return true;
}

/** 0..1: how much the vertex at (x, y) is the located arm (valid after a successful {@link #locate}). */
float weight(float x, float y)
{
float out = PushCycle.smoothstep(0.66f * halfWidth, 0.78f * halfWidth, (x - cx) * side);
// fades out above the shoulder (y smaller) and below the hips (y larger)
return out <= 0f ? 0f : out * PushCycle.smoothstep(shoulder[1] - 0.06f * height, shoulder[1], y)
* (1f - PushCycle.smoothstep(-hip, -hip + 0.12f * height, y));
}

private float distance(float x, float y, float z)
{
float dx = x - shoulder[0];
float dy = y - shoulder[1];
float dz = z - shoulder[2];
return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
}
}
