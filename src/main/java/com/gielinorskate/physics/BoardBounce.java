package com.gielinorskate.physics;

import java.util.Random;
import lombok.Getter;

/**
* The board flying off on its own after a bail: thrown with (most of) the skater's velocity, a random spin and
* flip, bouncing off walls and the ground as a {@link ThrownBody}, then sliding to rest flat, wheels down. Always
* at rest within {@link #REST_TIMEOUT} seconds. Units: local units, seconds, radians; yaw 0 = north, clockwise.
*/
public final class BoardBounce extends ThrownBody
{
/** Landing faster than this (u/s down) bounces, keeping BOUNCE of it. */
static final float BOUNCE_MIN = 150f;
static final float BOUNCE = 0.4f;
/** A bounce keeps this share of the speed along the ground and of the spins. */
static final float BOUNCE_KEEP = 0.7f;
/** However it bounced, it lies still this many seconds after the throw. */
public static final float REST_TIMEOUT = 1.6f;


/** Where the nose points (0 = north, clockwise). */
@Getter
private float yaw;
private float yawRate;
/** Flip about the long axis, raw (0 at rest). */
@Getter
private float roll;
private float rollRate;
/** End over end, raw (0 at rest). */
@Getter
private float pitch;
private float pitchRate;
private boolean resting;
private float age;

public BoardBounce(CollisionWorld world, float gravity)
{
// radius 10; slides at 2500 u/s^2 on the ground; a bounce off a wall keeps half the speed into it
super(world, gravity, 10f, 2500f, 0.5f);
}

/** Throws the board from (x, y, h) with the skater's velocity (vx, vy), nose along {@code heading}. */
public void start(float x, float y, float h, float vx, float vy, float heading, Random random)
{
this.x = finite(x);
this.y = finite(y);
this.h = finite(h);
float sx = finite(vx);
float sy = finite(vy);
float s = (float) Math.hypot(sx, sy);
float scale = s > MAX_SPEED ? MAX_SPEED / s : 1f;
// a little sideways, so the board and the body part ways; it flies off with 0.7 of the skater's velocity
float side = (random.nextFloat() - 0.5f) * 0.3f;
this.vx = (sx + sy * side) * scale * 0.7f;
this.vy = (sy - sx * side) * scale * 0.7f;
// pops 280 off the ground plus up to 200; spins 3 rad/s about the vertical plus up to 6, flips 10 about the
// long axis plus up to 10 and tumbles end over end 1 plus up to 3 (small), each either way
vh = 280f + 200f * random.nextFloat();
yaw = Angles.wrap(finite(heading));
yawRate = sign(random) * (3f + 6f * random.nextFloat());
roll = 0f;
rollRate = sign(random) * (10f + 10f * random.nextFloat());
pitch = 0f;
pitchRate = sign(random) * (1f + 3f * random.nextFloat());
grounded = false;
resting = false;
age = 0f;
}

private static float sign(Random random)
{
return random.nextBoolean() ? 1f : -1f;
}

public void step(float dt)
{
if (resting || !(dt > 0f))
return;
age += dt;
if (age >= REST_TIMEOUT)
{
restNow();
return;
}
stepMotion(dt);

yaw = Angles.wrap(yaw + yawRate * dt);
if (!grounded)
{
roll += rollRate * dt;
pitch += pitchRate * dt;
return;
}
// flat on the ground, wheels down: the nearest whole turn of the flip; settles at 15/s
float k = 1f - (float) Math.exp(-15f * dt);
yawRate -= yawRate * k;
float flat = Math.round(roll / Angles.TWO_PI) * Angles.TWO_PI;
roll += (flat - roll) * k;
float level = Math.round(pitch / Angles.TWO_PI) * Angles.TWO_PI;
pitch += (level - pitch) * k;
if (vx == 0f && vy == 0f && Math.abs(flat - roll) < 0.01f && Math.abs(level - pitch) < 0.01f)
restNow();
}

@Override
void land()
{
if (vh < -BOUNCE_MIN)
{
vh = -vh * BOUNCE;
vx *= BOUNCE_KEEP;
vy *= BOUNCE_KEEP;
yawRate *= BOUNCE_KEEP;
rollRate *= 0.5f;
pitchRate *= 0.5f;
}
else
{
vh = 0f;
grounded = true;
}
}

@Override
void bouncedBack()
{
yawRate = -yawRate;
}

/** Puts the board down flat where it is, at once (the skater is standing up and needs to know where it lies). */
public void restNow()
{
h = finite(world.groundHeight(x, y));
vx = 0f;
vy = 0f;
vh = 0f;
roll = 0f;
pitch = 0f;
yawRate = 0f;
rollRate = 0f;
pitchRate = 0f;
grounded = true;
resting = true;
}

public boolean isAtRest()
{
return resting;
}
}
