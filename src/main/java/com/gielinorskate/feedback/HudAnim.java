package com.gielinorskate.feedback;

/**
* Pure animation curves for the score HUD. Every function takes the seconds since its trigger (score-clock
* time); a negative or very large age gives the resting value.
*/
public final class HudAnim
{
/** The level banner every ten levels stays up for this long, fading over the last 0.8 s. */
public static final float BANNER_SECONDS = 4f;

private HudAnim()
{
}

/** Scale of the newest trick line {@code age} seconds after it appeared: 1.35 to 1 over 0.12 s, easing out. */
public static float popScale(float age)
{
return popScale(age, 0.12f);
}

/** Scale of a callout word: pops in like a trick line, but a little slower so the bigger word reads as a punch. */
public static float calloutScale(float age)
{
return popScale(age, 0.18f);
}

private static float popScale(float age, float seconds)
{
return age < 0f || age >= seconds ? 1f : 1.35f + (1f - 1.35f) * easeOutBack(age / seconds);
}

/** Ring stroke multiplier {@code age} seconds after the multiplier rose: 1.4 down to 1. */
public static float ringPulse(float age)
{
return 1f + 0.4f * ringFlash(age);
}

/** White flash strength on the ring, 1 down to 0 over 0.15 s, {@code age} seconds after the multiplier rose. */
public static float ringFlash(float age)
{
return age < 0f || age >= 0.15f ? 0f : 1f - age / 0.15f;
}

/** The part of {@code value} shown {@code age} seconds after a landing (ease-out cubic over 0.3 s). */
public static int countUp(int value, float age)
{
if (age >= 0.3f)
return value;
if (age <= 0f)
return 0;
float u = 1f - age / 0.3f;
return Math.round(value * (1f - u * u * u));
}

/** Horizontal HUD offset (px) {@code age} seconds after a bail. */
public static int shakeX(float age)
{
return shake(age, 25f, 0f);
}

/** Vertical HUD offset (px) {@code age} seconds after a bail. */
public static int shakeY(float age)
{
return shake(age, 19f, (float) (Math.PI / 2));
}

/**
* A bail shakes the HUD cluster by up to 3 px for 0.2 s, at a different frequency (Hz) on each axis so the
* motion is a jitter rather than a line.
*/
private static int shake(float age, float hz, float phase)
{
return age < 0f || age >= 0.2f ? 0
: Math.round(3 * (1f - age / 0.2f) * (float) Math.sin(2 * Math.PI * hz * age + phase));
}

/** How far (0..1 of its path) an XP drop has risen {@code age} seconds after the landing (ease-out over 1.2 s). */
public static float xpDropRise(float age)
{
if (age <= 0f)
return 0f;
if (age >= 1.2f)
return 1f;
float u = 1f - age / 1.2f;
return 1f - u * u;
}

/** Opacity 0..1 of an XP drop (up 1.2 s, fading over the last 0.4 s); 0 once it is done. */
public static float xpDropAlpha(float age)
{
return fade(age, 1.2f, 0.4f);
}

/** Opacity 0..1 of a callout (up 1.6 s, fading over the last 0.4 s); 0 once it is done. */
public static float calloutAlpha(float age)
{
return fade(age, 1.6f, 0.4f);
}

/** Opacity of the level banner ("Skating level 50!") {@code age} seconds after the level-up. */
public static float bannerAlpha(float age)
{
return fade(age, BANNER_SECONDS, 0.8f);
}

/** Opacity of the "Goal complete" line (named on the HUD for 3 s) {@code age} seconds after a goal was done. */
public static float goalFlashAlpha(float age)
{
return fade(age, 3f, 0.4f);
}

private static float fade(float age, float total, float fadeSeconds)
{
return age < 0f || age >= total ? 0f : age <= total - fadeSeconds ? 1f : (total - age) / fadeSeconds;
}

/** 0 at u = 0, 1 at u = 1, overshooting 1 a little in between (the usual 1.70158: about 10% overshoot). */
static float easeOutBack(float u)
{
float t = u - 1f;
return 1f + (1.70158f + 1f) * t * t * t + 1.70158f * t * t;
}
}
