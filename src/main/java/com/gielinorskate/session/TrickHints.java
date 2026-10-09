package com.gielinorskate.session;

import com.gielinorskate.input.NearMiss;

/**
* Throttles the near-miss flick hints ("Flick faster", ...): one shows for {@link #SHOW_SECONDS} (fading over the
* last {@link #FADE_SECONDS}), a new one only {@link #MIN_GAP_SECONDS} after the last, and a landed trick clears
* the one showing. Pure; times are seconds on one clock.
*/
public final class TrickHints
{
static final float SHOW_SECONDS = 2.5f;
static final float FADE_SECONDS = 0.5f;
static final float MIN_GAP_SECONDS = 4f;

private NearMiss current;
private float shownAt = Float.NEGATIVE_INFINITY;

/** Offers a near miss at {@code now}; returns true when it is shown (false while throttled or null). */
public boolean offer(NearMiss miss, float now)
{
if (miss == null || now - shownAt < MIN_GAP_SECONDS)
return false;
current = miss;
shownAt = now;
return true;
}

/** A flick made a trick: the hint showing is no longer needed (the throttle still runs). */
public void onTrick()
{
current = null;
}

public void reset()
{
current = null;
shownAt = Float.NEGATIVE_INFINITY;
}

/** The hint text showing at {@code now}, in controller mode's wording when {@code controller}; or null. */
public String text(float now, boolean controller)
{
return alpha(now) <= 0f ? null : controller ? current.controllerHint : current.hint;
}

/** Opacity of the hint at {@code now}: 1, fading to 0 over the last FADE_SECONDS; 0 when none. */
public float alpha(float now)
{
float age = now - shownAt;
return current == null || age < 0f || age >= SHOW_SECONDS ? 0f
: age <= SHOW_SECONDS - FADE_SECONDS ? 1f : (SHOW_SECONDS - age) / FADE_SECONDS;
}
}
