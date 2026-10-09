package com.gielinorskate.physics;

/** Input for one FootPhysics step. Held values persist; jumpPressed is an edge, cleared after each step. */
public final class FootInput
{
/** Wanted move direction in world space (x east, y north), length 0 (stand) to 1; longer is normalised. */
public float moveX;
public float moveY;
/** Held: sprint (Shift). */
public boolean sprint;
/** Edge: jump (Space) pressed since the last step. */
public boolean jumpPressed;
/** Held: a jump now lands on the board (the session asks BoardSwap), so it is the bigger mount hop. */
public boolean mountJump;

/**
* Sets the move direction from camera-relative keys: forward is away from the camera (along
* {@code cameraYaw}, 0 = north, clockwise), right is 90 degrees clockwise of it. Opposite keys cancel; a
* diagonal is normalised, so all eight directions walk at the same speed.
*/
public void setCameraRelative(boolean forward, boolean back, boolean left, boolean right, float cameraYaw)
{
float f = (forward ? 1f : 0f) - (back ? 1f : 0f);
float r = (right ? 1f : 0f) - (left ? 1f : 0f);
if (f == 0f && r == 0f)
{
moveX = 0f;
moveY = 0f;
return;
}
float sin = (float) Math.sin(cameraYaw);
float cos = (float) Math.cos(cameraYaw);
// forward (sin, cos); right is forward turned 90 degrees clockwise: (cos, -sin)
float x = f * sin + r * cos;
float y = f * cos - r * sin;
float len = (float) Math.hypot(x, y);
moveX = x / len;
moveY = y / len;
}

void clearEdges()
{
jumpPressed = false;
}
}
