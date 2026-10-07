package com.gielinorskate.feedback;

/**
 * Pure animation curves for the score HUD. Every function takes the seconds since its trigger (score-clock
 * time); a negative or very large age gives the resting value.
 */
public final class HudAnim
{
	/** A new trick line scales from {@link #POP_START_SCALE} to 1 over this, easing out with a slight overshoot. */
	public static final float POP_SECONDS = 0.12f;
	public static final float POP_START_SCALE = 1.35f;
	/** The ring's stroke is {@link #PULSE_EXTRA} thicker (and flashes white) when the multiplier rises, for this long. */
	public static final float PULSE_SECONDS = 0.15f;
	public static final float PULSE_EXTRA = 0.4f;
	/** A landed combo's score counts up from 0 over this. */
	public static final float COUNT_UP_SECONDS = 0.3f;
	/** A bail shakes the HUD cluster by up to {@link #SHAKE_PIXELS} for this long. */
	public static final float SHAKE_SECONDS = 0.2f;
	public static final int SHAKE_PIXELS = 3;
	/** An XP drop floats up for this long, fading over the last {@link #XP_DROP_FADE_SECONDS}. */
	public static final float XP_DROP_SECONDS = 1.2f;
	public static final float XP_DROP_FADE_SECONDS = 0.4f;
	/** A callout word stays up for this long, fading over the last {@link #CALLOUT_FADE_SECONDS}. */
	public static final float CALLOUT_SECONDS = 1.6f;
	public static final float CALLOUT_FADE_SECONDS = 0.4f;
	/** A callout pops in like a trick line, but a little slower so the bigger word reads as a punch. */
	public static final float CALLOUT_POP_SECONDS = 0.18f;

	/** The level banner every ten levels stays up for this long, fading over the last {@link #BANNER_FADE_SECONDS}. */
	public static final float BANNER_SECONDS = 4f;
	public static final float BANNER_FADE_SECONDS = 0.8f;
	/** A completed session goal is named on the HUD for this long. */
	public static final float GOAL_FLASH_SECONDS = 3f;

	/** easeOutBack overshoot constant (the usual 1.70158: about 10% overshoot). */
	private static final float BACK = 1.70158f;
	/** Shake frequencies (Hz), different on each axis so the motion is a jitter rather than a line. */
	private static final float SHAKE_HZ_X = 25f;
	private static final float SHAKE_HZ_Y = 19f;

	private HudAnim()
	{
	}

	/** Scale of the newest trick line {@code age} seconds after it appeared. */
	public static float popScale(float age)
	{
		return popScale(age, POP_SECONDS);
	}

	/** Scale of a callout word {@code age} seconds after it appeared. */
	public static float calloutScale(float age)
	{
		return popScale(age, CALLOUT_POP_SECONDS);
	}

	private static float popScale(float age, float seconds)
	{
		if (age < 0f || age >= seconds)
		{
			return 1f;
		}
		return POP_START_SCALE + (1f - POP_START_SCALE) * easeOutBack(age / seconds);
	}

	/** Ring stroke multiplier {@code age} seconds after the multiplier rose: 1.4 down to 1. */
	public static float ringPulse(float age)
	{
		return 1f + PULSE_EXTRA * ringFlash(age);
	}

	/** White flash strength on the ring, 1 down to 0, {@code age} seconds after the multiplier rose. */
	public static float ringFlash(float age)
	{
		if (age < 0f || age >= PULSE_SECONDS)
		{
			return 0f;
		}
		return 1f - age / PULSE_SECONDS;
	}

	/** The part of {@code value} shown {@code age} seconds after a landing (ease-out cubic). */
	public static int countUp(int value, float age)
	{
		if (age >= COUNT_UP_SECONDS)
		{
			return value;
		}
		if (age <= 0f)
		{
			return 0;
		}
		float u = 1f - age / COUNT_UP_SECONDS;
		return Math.round(value * (1f - u * u * u));
	}

	/** Horizontal HUD offset (px) {@code age} seconds after a bail. */
	public static int shakeX(float age)
	{
		return shake(age, SHAKE_HZ_X, 0f);
	}

	/** Vertical HUD offset (px) {@code age} seconds after a bail. */
	public static int shakeY(float age)
	{
		return shake(age, SHAKE_HZ_Y, (float) (Math.PI / 2));
	}

	private static int shake(float age, float hz, float phase)
	{
		if (age < 0f || age >= SHAKE_SECONDS)
		{
			return 0;
		}
		float amplitude = SHAKE_PIXELS * (1f - age / SHAKE_SECONDS);
		return Math.round(amplitude * (float) Math.sin(2 * Math.PI * hz * age + phase));
	}

	/** How far (0..1 of its path) an XP drop has risen {@code age} seconds after the landing (ease-out). */
	public static float xpDropRise(float age)
	{
		if (age <= 0f)
		{
			return 0f;
		}
		if (age >= XP_DROP_SECONDS)
		{
			return 1f;
		}
		float u = 1f - age / XP_DROP_SECONDS;
		return 1f - u * u;
	}

	/** Opacity 0..1 of an XP drop; 0 once it is done. */
	public static float xpDropAlpha(float age)
	{
		return fade(age, XP_DROP_SECONDS, XP_DROP_FADE_SECONDS);
	}

	/** Opacity 0..1 of a callout; 0 once it is done. */
	public static float calloutAlpha(float age)
	{
		return fade(age, CALLOUT_SECONDS, CALLOUT_FADE_SECONDS);
	}

	/** Opacity of the level banner ("Skating level 50!") {@code age} seconds after the level-up. */
	public static float bannerAlpha(float age)
	{
		return fade(age, BANNER_SECONDS, BANNER_FADE_SECONDS);
	}

	/** Opacity of the "Goal complete" line {@code age} seconds after a goal was done. */
	public static float goalFlashAlpha(float age)
	{
		return fade(age, GOAL_FLASH_SECONDS, CALLOUT_FADE_SECONDS);
	}

	private static float fade(float age, float total, float fadeSeconds)
	{
		if (age < 0f || age >= total)
		{
			return 0f;
		}
		float fadeStart = total - fadeSeconds;
		return age <= fadeStart ? 1f : (total - age) / fadeSeconds;
	}

	/** 0 at u = 0, 1 at u = 1, overshooting 1 a little in between. */
	static float easeOutBack(float u)
	{
		float t = u - 1f;
		return 1f + (BACK + 1f) * t * t * t + BACK * t * t;
	}
}
