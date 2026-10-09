package com.gielinorskate.physics;

import lombok.Getter;

/**
* The skater's body knocked off the board by a bail: a short hop along the travel direction with a forward tumble
* about the body's side axis (a wall bail rebounds off the wall and falls backward instead), then the impact and a
* high-friction skid until it lies still, flat on its back or front. Collides as a {@link ThrownBody}; never passes
* through a wall. Units: local units, seconds, radians.
* <p>
* {@link #getAngle()} is the tumble about the side axis, raw (not wrapped), positive tipping the head toward the
* facing direction: 0 standing, PI / 2 lying face down with the head forward, 3 PI / 2 (or -PI / 2) lying on the
* back. {@link #getH()} is the height of the body's lowest point (the ground under it once it lies).
*/
public final class TumbleBody extends ThrownBody
{
/** At this speed (u/s) and over, the hop is highest and the tumble longest. */
static final float FAST = 1500f;
/** The puppet's shape for the ground offset: centre of mass above the feet, head above it, half thickness. */
static final float COM_HEIGHT = 41f;
static final float HEAD_ABOVE_COM = 49f;
static final float HALF_THICKNESS = 10f;

private static final float HALF_PI = (float) (Math.PI / 2);

/** Which way the body faces: the tumble turns it about the side axis of this. */
@Getter
private float facing;
@Getter
private float angle;
private float angVel;
/** The lying angle the tumble settles at (the planned one until the impact). */
@Getter
private float lieAngle;
/** Sideways roll, radians. */
@Getter
private float roll;
private float rollTarget;
private boolean landedOnce;
private boolean impactPending;
private float airTime;
/** Seconds of the latest flight that ended on the ground (the impact's, for the camera kick). */
@Getter
private float lastAirTime;

public TumbleBody(CollisionWorld world, float gravity, float radius)
{
// skids at 3500 u/s^2 (from FAST carried, about a quarter second); a bounce off a wall keeps 0.3
super(world, gravity, radius, 3500f, 0.3f);
}

/**
* Knocks the body off the board at (x, y), feet at height h, moving at (vx, vy).
*
* @param facing where the skater faced (the board heading): the tumble's facing for a wall bail or a bail
*     standing still; otherwise the body faces along its travel
* @param wall the bail was into a wall with outward normal (wallNx, wallNy): it rebounds off it, backward
* @param rollSeed -1..1, the side the body rolls to as it tumbles
*/
public void start(float x, float y, float h, float vx, float vy, float facing, boolean wall, float wallNx,
float wallNy, float rollSeed)
{
this.x = finite(x);
this.y = finite(y);
this.h = finite(h);
float speed = (float) Math.hypot(finite(vx), finite(vy));
float frac = Math.max(0f, Math.min(1f, speed / FAST));
float target;
float nLen = (float) Math.hypot(finite(wallNx), finite(wallNy));
if (wall && nLen > 1e-3f)
{
// bounces off the wall at 180 u/s along its normal, with a 200 hop
this.vx = wallNx / nLen * 180f;
this.vy = wallNy / nLen * 180f;
vh = 200f;
this.facing = Angles.wrap(finite(facing));
// knocked back off the wall: onto the back
target = -HALF_PI;
}
else
{
// carries 0.6 of the skater's speed off the board (the rest is lost in the knock)
float carried = Math.min(speed, MAX_SPEED) * 0.6f;
this.vx = speed > 0f ? finite(vx) / speed * carried : 0f;
this.vy = speed > 0f ? finite(vy) / speed * carried : 0f;
// hop take-off speed (u/s up): 150 at a standstill to 260 at FAST, a quarter-second flight at speed
vh = 150f + (260f - 150f) * frac;
this.facing = speed > 50f ? (float) Math.atan2(vx, vy) : Angles.wrap(finite(facing));
// slow: a fall onto the face; faster: over onto the back; fast: a turn and a quarter, a face-plant slide
target = frac < 0.35f ? HALF_PI : frac < 0.7f ? 3 * HALF_PI : 5 * HALF_PI;
}
float drop = Math.max(0f, this.h - world.groundHeight(this.x, this.y));
float flight = (vh + (float) Math.sqrt(vh * vh + 2 * gravity * drop)) / gravity;
angle = 0f;
// timed over at least 0.12 s, so a tiny hop never spins wildly
angVel = target / Math.max(0.12f, flight);
lieAngle = target;
roll = 0f;
// rolls at most 0.4 sideways in the tumble
rollTarget = Math.max(-1f, Math.min(1f, finite(rollSeed))) * 0.4f;
grounded = false;
landedOnce = false;
impactPending = false;
airTime = 0f;
lastAirTime = 0f;
}

public void step(float dt)
{
if (!(dt > 0f))
return;
if (!grounded)
airTime += dt;
stepMotion(dt);

if (!landedOnce)
{
// the roll in the air eases in at 10/s
angle += angVel * dt;
roll += (rollTarget - roll) * (1f - (float) Math.exp(-10f * dt));
}
else
{
// settles flat at 25/s once it hits the ground
float k = 1f - (float) Math.exp(-25f * dt);
angle += (lieAngle - angle) * k;
roll -= roll * k;
if (Math.abs(lieAngle - angle) < 0.005f)
angle = lieAngle;
if (Math.abs(roll) < 0.005f)
roll = 0f;
}
}

@Override
void land()
{
vh = 0f;
grounded = true;
lastAirTime = airTime;
airTime = 0f;
if (!landedOnce)
{
landedOnce = true;
impactPending = true;
// flat on whichever side it came down nearer to
lieAngle = (float) (Math.round((angle - HALF_PI) / Angles.PI) * Angles.PI + HALF_PI);
}
}

/** Ends the skid at once (the get-up starts where the body lies). */
public void stop()
{
vx = 0f;
vy = 0f;
}

/** True once, at the first touchdown after the knock-off (the impact). */
public boolean takeImpact()
{
boolean i = impactPending;
impactPending = false;
return i;
}

/** On the ground, not moving, lying flat. */
public boolean isAtRest()
{
return grounded && vx == 0f && vy == 0f && angle == lieAngle && roll == 0f;
}

public boolean isAirborne()
{
return !grounded;
}

/** Touched down at least once since the knock-off. */
public boolean hasLanded()
{
return landedOnce;
}

/**
* The puppet's height above {@link #getH()} that keeps its lowest point on it when tumbled by {@code angle}
* about the centre of mass: 0 standing, about -31 lying (the body comes down flat), a little up when upside
* down (the head is below the feet).
*/
public static float groundOffset(float angle)
{
float c = (float) Math.cos(angle);
float s = Math.abs((float) Math.sin(angle));
float feet = COM_HEIGHT - COM_HEIGHT * c;
float head = COM_HEIGHT + HEAD_ABOVE_COM * c;
return -(Math.min(feet, head) - HALF_THICKNESS * s);
}

public float getVelocityX()
{
return vx;
}

public float getVelocityY()
{
return vy;
}

public float getSpeed()
{
return (float) Math.hypot(vx, vy);
}
}
