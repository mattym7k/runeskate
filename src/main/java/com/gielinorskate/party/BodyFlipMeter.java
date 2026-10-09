package com.gielinorskate.party;

/**
* The local skater's front / back flip rate for the party, measured over physics time, and whether the flip
* started, stopped or reversed this frame. That change goes out at once instead of waiting for the next
* snapshot, so a ghost does not keep flipping for up to 0.6 s after the skater let go. Pure.
*/
final class BodyFlipMeter
{
/**
* Rates at least this fast (rad/s) are a held flip: SkatePhysics turns a held flip at 2 pi / 0.55 = 11.4
* rad/s and eases a released one onto the whole turn at 3 rad/s, so 6 is safely between the two.
*/
static final float HELD_RATE = 6f;

private float last = Float.NaN;
private float rate;
private int direction;
private boolean changed;

/**
* One render frame that ran {@code dt} seconds of physics steps (0 for none).
*
* @param angle the body flip angle (SkatePhysics.getBodyFlipAngle)
*/
void update(float angle, boolean airborne, float dt)
{
if (airborne && !Float.isNaN(last) && dt <= 0f)
{
// no physics step: nothing moved, keep the rate
changed = false;
return;
}
int before = direction;
if (!airborne)
// on the ground (or a rail) the angle is reset to 0, which is not a rotation
rate = 0f;
else if (!Float.isNaN(last))
rate = (angle - last) / dt;
last = angle;
direction = directionOf(rate);
changed = direction != before;
}

/** Rad/s, + = frontflip. */
float rate()
{
return rate;
}

/** 1 while a frontflip is held, -1 a backflip, else 0. */
int direction()
{
return direction;
}

/** {@link #direction} changed on the latest update. */
boolean directionChanged()
{
return changed;
}

void reset()
{
last = Float.NaN;
rate = 0f;
direction = 0;
changed = false;
}

static int directionOf(float rate)
{
return rate >= HELD_RATE ? 1 : rate <= -HELD_RATE ? -1 : 0;
}
}
