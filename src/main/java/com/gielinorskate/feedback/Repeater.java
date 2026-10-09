package com.gielinorskate.feedback;

/** Fires every {@link #interval} seconds while a condition holds (grind tinks, grind sparks). Pure. */
final class Repeater
{
private final float interval;
private final boolean fireOnStart;
private float elapsed;
private boolean running;

/** @param fireOnStart fire on the first active frame too, not only after the first interval */
Repeater(float interval, boolean fireOnStart)
{
this.interval = interval;
this.fireOnStart = fireOnStart;
}

/** True on the frames it fires: at most once a frame, so a long frame never bursts. */
boolean update(boolean active, float dt)
{
if (!active)
{
running = false;
return false;
}
if (!running)
{
running = true;
elapsed = 0f;
return fireOnStart;
}
elapsed += dt;
if (elapsed >= interval)
{
// keep the remainder so the beat stays even, but never owe more than one
elapsed = Math.min(elapsed - interval, interval);
return true;
}
return false;
}
}
