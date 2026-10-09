package com.gielinorskate.physics;

import lombok.Getter;

/**
* What a bail throws off on its own: the skater's body ({@link TumbleBody}) and the board ({@link BoardBounce}).
* Falls under gravity, skids to a stop on the ground and moves in short substeps through the same
* {@link CollisionWorld} as the board, stepping over what the board steps over, falling off bigger drops and
* bouncing off whatever blocks (a wall, a step up, the loaded area's edge). Pure: no client dependency.
*/
abstract class ThrownBody
{
/** Speeds are clamped to this, whatever comes in. */
static final float MAX_SPEED = 4000f;
/** Longest substep of a move, so thin walls are never crossed. */
private static final float SUBSTEP = 4f;
/** Steps over this (as the board does) and falls off drops bigger than STEP_DOWN. */
private static final float STEP_UP = 24f;
private static final float STEP_DOWN = 48f;
/** Slower than this (u/s) the skid stops. */
private static final float STOP_SPEED = 5f;

final CollisionWorld world;
final float gravity;
private final float radius;
/** Skid deceleration on the ground (u/s^2), and the share of the speed into a wall a bounce keeps. */
private final float friction, restitution;

@Getter
float x, y, h;
float vx, vy, vh;
boolean grounded;

ThrownBody(CollisionWorld world, float gravity, float radius, float friction, float restitution)
{
this.world = world;
this.gravity = gravity;
this.radius = radius;
this.friction = friction;
this.restitution = restitution;
}

/** Skids (on the ground) or falls, moves, then lands, falls off a drop or follows the ground. */
void stepMotion(float dt)
{
if (grounded)
{
float s = (float) Math.hypot(vx, vy);
float ns = s - friction * dt;
if (ns <= STOP_SPEED)
{
vx = 0f;
vy = 0f;
}
else
{
vx *= ns / s;
vy *= ns / s;
}
}
else
vh -= gravity * dt;
move(vx * dt, vy * dt);

float ground = world.groundHeight(x, y);
if (!grounded)
{
h += vh * dt;
if (h <= ground)
{
h = ground;
land();
}
}
else if (ground < h - STEP_DOWN)
{
// slid off a drop: falls again
grounded = false;
vh = 0f;
}
else
h = ground;
}

/** Came down on the ground (h is already on it); vh is still the speed it came down at. */
abstract void land();

/** Blocked both ways in a substep: bounced straight back. */
void bouncedBack()
{
}

/** Moves by (dx, dy) in substeps, bouncing off whatever blocks. */
private void move(float dx, float dy)
{
float dist = (float) Math.hypot(dx, dy);
if (dist <= 0f)
return;
int n = Math.max(1, (int) Math.ceil(dist / SUBSTEP));
float sx = dx / n;
float sy = dy / n;
for (int i = 0; i < n; i++)
{
float nx = x + sx;
float ny = y + sy;
if (!blocked(nx, ny))
{
x = nx;
y = ny;
continue;
}
boolean xFree = sx != 0f && !blocked(x + sx, y);
boolean yFree = sy != 0f && !blocked(x, y + sy);
if (xFree && !yFree)
{
x += sx;
vy = -vy * restitution;
sy = 0f;
}
else if (yFree && !xFree)
{
y += sy;
vx = -vx * restitution;
sx = 0f;
}
else
{
vx = -vx * restitution;
vy = -vy * restitution;
bouncedBack();
return;
}
}
}

private boolean blocked(float nx, float ny)
{
return world.blockerTop(x, y, nx, ny, radius) > h + STEP_UP || world.groundHeight(nx, ny) - h > STEP_UP;
}

static float finite(float v)
{
return Float.isFinite(v) ? v : 0f;
}

/** Up-positive vertical speed (0 on the ground). */
public float getVerticalSpeed()
{
return vh;
}
}
