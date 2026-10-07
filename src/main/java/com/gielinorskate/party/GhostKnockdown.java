package com.gielinorskate.party;

import com.gielinorskate.render.KnockdownPose;

/**
 * A knocked-off ghost's tumble, timed on this side from when each stage was first seen (updates carry only the
 * stage and the lying angle): a spin from upright onto the lying angle, lying there, then the body easing round to
 * the nearest whole turn (upright) through the get-up, as the sender's knockdown draws it. Pure.
 */
final class GhostKnockdown
{
	/** The sender's lie-down and get-up (session.Knockdown), seconds. */
	static final float LIE_SECONDS = 0.5f;
	static final float GET_UP_SECONDS = 0.4f;
	/** The tumble reaches its lying angle this long after being thrown off (a typical flight, MIN_SPIN_TIME up). */
	static final float SPIN_SECONDS = 0.35f;

	private static final float TWO_PI = (float) (2 * Math.PI);

	private GhostKnockdown()
	{
	}

	/** 0..1 through the lie-down or get-up {@code age} seconds into it; 0 tumbling. */
	static float progress(KnockdownPose.Stage stage, float age)
	{
		float span;
		switch (stage)
		{
			case LIE:
				span = LIE_SECONDS;
				break;
			case GET_UP:
				span = GET_UP_SECONDS;
				break;
			default:
				return 0f;
		}
		return Math.max(0f, Math.min(1f, age / span));
	}

	/** The body's tumble angle {@code age} seconds into {@code stage}, lying at {@code lie}. */
	static float angle(KnockdownPose.Stage stage, float lie, float age)
	{
		switch (stage)
		{
			case AIR:
				return lie * Math.max(0f, Math.min(1f, age / SPIN_SECONDS));
			case LIE:
				return lie;
			default:
				float u = progress(stage, age);
				float s = u * u * (3f - 2f * u);
				float upright = Math.round(lie / TWO_PI) * TWO_PI;
				return lie + (upright - lie) * s;
		}
	}
}
