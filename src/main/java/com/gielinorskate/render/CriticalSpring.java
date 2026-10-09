package com.gielinorskate.render;

/**
* A critically damped spring (x'' = -2w x' - w^2 (x - target)), stepped with its exact solution so the
* motion does not depend on the frame rate and never overshoots a fixed target. Kicks (velocity impulses)
* give a dip that springs back. Pure.
*/
final class CriticalSpring
{
float value;
float velocity;

/** Moves toward {@code target}; {@code omega} (1/s) sets the speed: about 4 / omega seconds to settle. */
void step(float target, float omega, float dt)
{
if (dt <= 0f)
return;
float y = value - target;
float e = (float) Math.exp(-omega * dt);
// exact: y(t) = (y0 + (v0 + w y0) t) e^-wt, v(t) = (v0 - w (v0 + w y0) t) e^-wt
float tmp = (velocity + omega * y) * dt;
value = target + (y + tmp) * e;
velocity = (velocity - omega * tmp) * e;
}

/** Adds a velocity impulse. */
void kick(float dv)
{
velocity += dv;
}

void reset(float v)
{
value = v;
velocity = 0f;
}

/**
* The kick that makes a spring at rest on its target peak {@code peak} away from it: the response
* v0 t e^-wt peaks at t = 1/w with v0 / (w e).
*/
static float kickForPeak(float peak, float omega)
{
return peak * omega * (float) Math.E;
}
}
