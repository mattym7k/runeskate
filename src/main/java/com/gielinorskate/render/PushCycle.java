package com.gielinorskate.render;

/**
 * One procedural foot push, as a function of its phase (0..1 over {@link #DURATION} seconds). Pure and
 * allocation-free. Values are in travel terms; {@link SkaterPoseRig} maps them onto the puppet's model space
 * for the current stance and riding direction.
 *
 * <ol>
 * <li>lift (0 .. {@link #LIFT_END}): the rear foot comes up off the deck and out to the toe side;</li>
 * <li>reach ({@link #LIFT_END} .. {@link #PLANT_START}): it swings down to the ground beside the board while
 * the front knee bends to lower the body;</li>
 * <li>plant ({@link #PLANT_START} .. {@link #PLANT_END}): on the ground, it sweeps backward along the
 * board's travel axis (the push itself);</li>
 * <li>recover ({@link #PLANT_END} .. {@link #RECOVER_END}): it lifts off the ground and swings forward;</li>
 * <li>return ({@link #RECOVER_END} .. {@link #SETTLED}): it sets back down on the deck where it started.</li>
 * </ol>
 * Every channel eases between keyframes with smoothstep, so nothing jumps and each segment is monotonic.
 */
final class PushCycle
{
	/** Seconds per push: physics pushes every ~0.4 s while W is held, so the cycles chain. */
	/** One unhurried kick; physics pushes faster (every ~0.4 s) but only every other push shows a kick. */
	static final float DURATION = 0.70f;

	static final float LIFT_END = 0.14f;
	static final float PLANT_START = 0.30f;
	static final float PLANT_END = 0.62f;
	static final float RECOVER_END = 0.80f;
	/** The foot is back at rest on the deck; the rear-leg override fades out after this. */
	static final float SETTLED = 0.94f;

	/** The ground below the soles (y down): the deck top is BOARD_TOP up, the soles FOOT_CLEARANCE above it. */
	static final float GROUND_DEPTH = BoardGeometry.BOARD_TOP + BoardPlacement.FOOT_CLEARANCE;
	/** Units the foot travels backward on the ground. */
	static final float SWEEP = 50f;
	/** Front-knee bend (1 - legScale) while the foot is down. */
	static final float PLANT_SQUASH = 0.12f;
	/** Radians the upper body leans toward the direction of travel while the foot is down. */
	static final float PLANT_LEAN = 0.14f;
	/** Units the foot moves out to the toe side: the deck is 13 wide each side of its centre line. */
	static final float PLANT_SIDE = 17f;

	private static final float[] KEYS = {0f, LIFT_END, PLANT_START, PLANT_END, RECOVER_END, SETTLED, 1f};
	private static final float[] BACK = {0f, -4f, -2f, SWEEP, 24f, 0f, 0f};
	private static final float[] FOOT_Y = {0f, -10f, GROUND_DEPTH, GROUND_DEPTH, -8f, 0f, 0f};
	private static final float[] SIDE = {0f, 14f, PLANT_SIDE, PLANT_SIDE, 12f, 0f, 0f};

	private static final float[] BODY_KEYS = {0f, 0.12f, PLANT_START, 0.60f, 0.84f, 1f};
	private static final float[] SQUASH = {0f, 0f, PLANT_SQUASH, PLANT_SQUASH, 0f, 0f};
	private static final float[] LEAN = {0f, 0f, PLANT_LEAN, PLANT_LEAN, 0f, 0f};

	/**
	 * Phase over which the rear-leg override fades in at the start, and from which it fades out once the
	 * foot is back at rest (so the fade itself moves nothing but the knee-bend versus squash difference).
	 */
	private static final float WEIGHT_IN = 0.08f;
	private static final float WEIGHT_OUT = SETTLED;

	/** One sample of the cycle. */
	static final class Sample
	{
		/** Units the rear foot is behind its resting spot along the travel axis (negative = ahead). */
		float back;
		/** Rear-foot height, y down: 0 on the deck, {@link #GROUND_DEPTH} on the ground, negative lifted. */
		float footY;
		/** Units the rear foot is out to the toe side (the chest side) of its resting spot. */
		float side;
		/** 0..1: how much the rear leg follows the foot target instead of the plain crouch. */
		float legWeight;
		/** Extra knee bend (1 - legScale) from the front leg. */
		float squash;
		/** Radians the upper body leans toward the direction of travel. */
		float lean;
	}

	private PushCycle()
	{
	}

	/** Samples the cycle at {@code phase}; outside 0..1 the skater stands normally (all zero). */
	static void sample(float phase, Sample out)
	{
		if (!(phase > 0f && phase < 1f))
		{
			out.back = 0f;
			out.footY = 0f;
			out.side = 0f;
			out.legWeight = 0f;
			out.squash = 0f;
			out.lean = 0f;
			return;
		}
		out.back = keyed(KEYS, BACK, phase);
		out.footY = keyed(KEYS, FOOT_Y, phase);
		out.side = keyed(KEYS, SIDE, phase);
		out.legWeight = smoothstep(0f, WEIGHT_IN, phase) * (1f - smoothstep(WEIGHT_OUT, 1f, phase));
		out.squash = keyed(BODY_KEYS, SQUASH, phase);
		out.lean = keyed(BODY_KEYS, LEAN, phase);
	}

	/** Smoothstep-eased interpolation between keyframes ({@code keys} ascending from 0 to 1). */
	static float keyed(float[] keys, float[] values, float phase)
	{
		for (int i = 1; i < keys.length; i++)
		{
			if (phase <= keys[i])
			{
				float u = smoothstep(keys[i - 1], keys[i], phase);
				return values[i - 1] + (values[i] - values[i - 1]) * u;
			}
		}
		return values[values.length - 1];
	}

	/** 0 at or below {@code a}, 1 at or above {@code b}, 3u^2 - 2u^3 between. */
	static float smoothstep(float a, float b, float x)
	{
		if (x <= a)
		{
			return 0f;
		}
		if (x >= b)
		{
			return 1f;
		}
		float u = (x - a) / (b - a);
		return u * u * (3f - 2f * u);
	}
}
