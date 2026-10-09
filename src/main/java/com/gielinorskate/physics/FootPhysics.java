package com.gielinorskate.physics;

import java.util.*;
import lombok.Getter;

/**
* The skater on foot: camera-relative walking and sprinting, turning toward the move direction, jumping and
* falling, colliding with the same {@link CollisionWorld} as the board. Pure: no client dependency.
* <p>
* Never bails: a wall is slid along, and a fall of any height lands on the feet. A foot steps up
* {@link #STEP_UP} (the board only {@link SkateTuning#maxStepUp}); a jump reaches about 90-unit benches and low
* walls. Pass-through vegetation is whatever the world says (it is built with the same setting as skating),
* and the edge of the loaded area is never crossed or climbed.
* Units: local units, seconds, radians (heading 0 = north, clockwise).
*/
public final class FootPhysics
{
/** The game's walk: one tile (128) per 0.6 s tick, about 213 u/s. Its walk animation is made for this. */
public static final float GAME_WALK_SPEED = 128f / 0.6f;
/** The game's run: two tiles per tick, about 427 u/s. */
public static final float GAME_RUN_SPEED = 2 * GAME_WALK_SPEED;
/**
* Walking speed as a multiple of the game's walk. Skate 3 on foot is a brisk, purposeful walk (1.5x: 320 u/s,
* 2.5 tiles a second); the game's own walk felt like wading. The walk animation is played this much faster
* ({@code render.GaitPlayback}) so the feet keep up.
*/
public static final float WALK_SPEED_SCALE = 1.5f;
/**
* Sprinting speed as a multiple of the game's run. 2x (853 u/s, 6.7 tiles a second) is a real sprint, still well
* under the board's push top speed (1500), so the board stays the fast way round.
*/
public static final float SPRINT_SPEED_SCALE = 2f;
/** Walking speed, u/s. Not tied to game ticks. */
public static final float WALK_SPEED = GAME_WALK_SPEED * WALK_SPEED_SCALE;
/** Sprinting speed, u/s. */
public static final float SPRINT_SPEED = GAME_RUN_SPEED * SPRINT_SPEED_SCALE;
/** A foot steps up this much (a curb, a stair); the board steps 24. Ground dropping away by more is a fall. */
public static final float STEP_UP = 48f;
/** Jump take-off speed (u/s up): with gravity 2000 the feet rise about 68, enough with a step for 90-unit tops. */
public static final float JUMP_VH = 520f;
/**
* Take-off speed of a jump that lands on the board (u/s up): the feet rise about 109 and the hop lasts about
* 0.66 s (a plain jump 0.52 s), a big, readable hop onto the board in the Skate 3 way.
*/
public static final float MOUNT_JUMP_VH = 660f;
/** Longest substep of a move, so thin walls are never stepped over. */
private static final float SUBSTEP = 4f;
private static final float EPS = 1e-4f;

private final CollisionWorld world;
private final float gravity;
private final float radius;
private final Contact hit = new Contact();
private final List<FootEvent> events = new ArrayList<>();

/** Position, height of the feet (up-positive) and where the body faces (it turns toward the move direction). */
@Getter
private float x, y, h, heading;
private float vx, vy, vh;
@Getter
private boolean airborne;
/** Sprint held while moving, on the latest step. */
boolean sprinting;
private float airTime;
/** Where the latest jump took off, NaN for a fall (no jump) or before any. */
@Getter
private float jumpStartX = Float.NaN, jumpStartY = Float.NaN;
/** Sprinting as the current or latest jump took off. */
@Getter
private boolean jumpedSprinting;
/** The current or latest jump took off as a mount hop ({@link FootInput#mountJump}); a fall never is. */
@Getter
private boolean mountJump;
/** Seconds in the air of the latest landed jump or fall. */
@Getter
private float lastAirTime;
private boolean hitWall;

/**
* @param gravity the board's gravity ({@link SkateTuning#gravity})
* @param radius the skater's collision radius ({@link SkateTuning#skaterRadius})
*/
public FootPhysics(CollisionWorld world, float gravity, float radius, float x, float y, float h, float heading)
{
this.world = world;
this.gravity = gravity;
this.radius = radius;
this.x = x;
this.y = y;
this.heading = Angles.wrap(heading);
// stepping off inside something (a wedged board): out the shortest way first
if (world.contact(x, y, radius, h, STEP_UP, hit))
{
this.x += hit.nx * (hit.depth + 0.5f);
this.y += hit.ny * (hit.depth + 0.5f);
}
float g = world.groundHeight(this.x, this.y);
// stepped off mid-air: falls from there
airborne = h - g > STEP_UP;
this.h = airborne ? h : g;
}

/** Sets the horizontal velocity (u/s), e.g. momentum carried off the board. */
public void setVelocity(float vx, float vy)
{
this.vx = vx;
this.vy = vy;
}

public void step(float dt, FootInput in)
{
hitWall = false;
float mx = in.moveX;
float my = in.moveY;
float mag = (float) Math.hypot(mx, my);
if (mag > 1f)
{
mx /= mag;
my /= mag;
mag = 1f;
}
boolean moving = mag > 1e-3f;
sprinting = in.sprint && moving;
float target = sprinting ? SPRINT_SPEED : WALK_SPEED;
// reaches sprint speed from a standstill (and stops from it) in 0.1 s; 0.35 of that in the air
approachVelocity(mx * target, my * target, SPRINT_SPEED / 0.1f * (airborne ? 0.35f : 1f) * dt);
if (moving)
// the body turns toward the move direction at up to 720 degrees a second
heading = Angles.turnToward(heading, (float) Math.atan2(mx, my), 4f * Angles.PI * dt);

if (!airborne && in.jumpPressed)
{
airborne = true;
vh = in.mountJump ? MOUNT_JUMP_VH : JUMP_VH;
airTime = 0f;
jumpStartX = x;
jumpStartY = y;
jumpedSprinting = sprinting;
mountJump = in.mountJump;
events.add(FootEvent.JUMP);
}

move(dt);

float g = world.groundHeight(x, y);
if (airborne)
{
vh -= gravity * dt;
h += vh * dt;
airTime += dt;
if (h <= g)
{
h = g;
if (vh <= 0f)
{
airborne = false;
vh = 0f;
lastAirTime = airTime;
events.add(FootEvent.LAND);
}
}
}
else if (h - g > STEP_UP)
{
// walked off something tall: a fall, not a jump
airborne = true;
vh = 0f;
airTime = 0f;
jumpStartX = Float.NaN;
jumpStartY = Float.NaN;
jumpedSprinting = false;
mountJump = false;
events.add(FootEvent.FALL);
}
else
h = g;
in.clearEdges();
}

private void approachVelocity(float tx, float ty, float maxDelta)
{
float dx = tx - vx;
float dy = ty - vy;
float d = (float) Math.hypot(dx, dy);
if (d <= maxDelta || d < EPS)
{
vx = tx;
vy = ty;
return;
}
vx += dx / d * maxDelta;
vy += dy / d * maxDelta;
}

/** Moves by the velocity over {@code dt} in short substeps, sliding along whatever blocks. */
private void move(float dt)
{
float dist = (float) Math.hypot(vx, vy) * dt;
if (dist < EPS)
return;
int n = Math.max(1, (int) Math.ceil(dist / SUBSTEP));
float sdt = dt / n;
for (int k = 0; k < n; k++)
{
float dx = vx * sdt;
float dy = vy * sdt;
if (Math.abs(dx) < EPS && Math.abs(dy) < EPS)
return;
if (!blocked(x + dx, y + dy))
{
x += dx;
y += dy;
continue;
}
hitWall = true;
if (!slide(dx, dy))
{
vx = 0f;
vy = 0f;
return;
}
}
}

/**
* A blocked substep (dx, dy): moves along the wall instead and removes the velocity into it. Uses the
* world's shape normal where there is one, else whichever grid axis is free. False when stuck.
*/
private boolean slide(float dx, float dy)
{
if (world.contact(x + dx, y + dy, radius, h, STEP_UP, hit))
{
float nx = hit.nx;
float ny = hit.ny;
if (nx * dx + ny * dy > 0f)
{
nx = -nx;
ny = -ny;
}
float into = -(nx * dx + ny * dy);
if (into > EPS)
{
for (float scale = 1f; scale >= 0.25f; scale *= 0.5f)
{
float tx = (dx + into * nx) * scale;
float ty = (dy + into * ny) * scale;
if (Math.hypot(tx, ty) > EPS && !blocked(x + tx, y + ty))
{
x += tx;
y += ty;
// the velocity loses its part into the wall
float vn = vx * nx + vy * ny;
if (vn < 0f)
{
vx -= vn * nx;
vy -= vn * ny;
}
return true;
}
}
}
}
boolean freeX = Math.abs(dx) > EPS && !blocked(x + dx, y);
boolean freeY = Math.abs(dy) > EPS && !blocked(x, y + dy);
if (freeX && freeY)
{
freeX = Math.abs(dx) >= Math.abs(dy);
freeY = !freeX;
}
if (freeX)
{
x += dx;
vy = 0f;
}
else if (freeY)
{
y += dy;
vx = 0f;
}
return freeX || freeY;
}

/**
* True when a move from here to (nx, ny) is refused: further out past the edge of the loaded area, a blocker
* taller than a step above the feet (anything off the world reads as infinitely tall), or ground rising more
* than a step above the feet.
*/
private boolean blocked(float nx, float ny)
{
float edgeTo = world.edgeDistance(nx, ny);
return !(edgeTo > 0f) && edgeTo < world.edgeDistance(x, y)
|| world.blockerTop(x, y, nx, ny, radius) > h + STEP_UP
|| world.groundHeight(nx, ny) - h > STEP_UP;
}

/** Events since the last call (JUMP, FALL, LAND). */
public List<FootEvent> drainEvents()
{
if (events.isEmpty())
return Collections.emptyList();
List<FootEvent> out = new ArrayList<>(events);
events.clear();
return out;
}

public float getVelocityX()
{
return vx;
}

public float getVelocityY()
{
return vy;
}

/** Horizontal speed, u/s. */
public float getSpeed()
{
return (float) Math.hypot(vx, vy);
}

/** Direction of horizontal travel; the body heading when standing still. */
public float getTravelHeading()
{
return getSpeed() > 1f ? (float) Math.atan2(vx, vy) : heading;
}

/** Vertical speed, u/s (up > 0); 0 on the ground. */
public float getVerticalVelocity()
{
return vh;
}

/** True when the current (or latest) flight was a jump, not a fall. */
public boolean isJump()
{
return !Float.isNaN(jumpStartX);
}

/** Ran into something on the latest step (and slid along it or stopped). */
public boolean hitWall()
{
return hitWall;
}
}
