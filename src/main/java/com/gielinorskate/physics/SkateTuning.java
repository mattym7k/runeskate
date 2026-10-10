package com.gielinorskate.physics;

import com.google.gson.Gson;
import java.io.Reader;

/** Every feel constant. Units: local units (128 per tile, about 1 m), seconds, degrees where named. */
public final class SkateTuning implements Cloneable
{
	/** Push strength at low speed; tapers as pushImpulse * (1 - speed/maxPushSpeed)^0.5, never below pushMinImpulse. */
	public float pushImpulse = 240f;
	/** The first push from a standstill (or out of a slow roll back) gives this much. */
	public float pushFromRest = 360f;
	public float pushMinImpulse = 60f;
	public float pushCooldown = 0.40f;
	public float maxPushSpeed = 1500f;
	public float maxSpeed = 2600f;
	public float rollingFriction = 45f;
	/** Air drag while rolling: drag * speed^2 u/s^2 (halved from the review value so flat pushing still reaches ~1500; hills still slow before the 2600 cap). */
	public float drag = 0.00006f;
	public float powerslideDecel = 900f;
	public float carveRate = 2.6f;
	public float carveSpeedFalloff = 2200f;
	/** Shift + steer: a tight carve at this multiple of the carve rate, bleeding tightCarveBleed + tightCarveBleedFraction * speed u/s^2. */
	public float tightCarveMult = 2.2f;
	public float tightCarveBleed = 200f;
	public float tightCarveBleedFraction = 0.3f;
	/** Below pivotSpeed the carve rate blends toward pivotRate rad/s (a kick-turn at a standstill). */
	public float pivotSpeed = 250f;
	public float pivotRate = 4.5f;
	public float airSpinRate = 7.0f;
	public float gravity = 2000f;
	/** Fraction of gravity * slope that pulls the skater along a hill (full gravity made hills unskateable). */
	public float slopeGravityScale = 0.4f;
	/** Hill slopes (rise per unit) are clamped to +-this before scaling. */
	public float maxSlope = 0.6f;
	public float ollieImpulse = 820f;
	/**
	 * Pop strength with no charge. 0.85 made quick ollies (crouch starts only after the 18 px pull, so
	 * they rarely charge) 121 high against 168 for a paused one: (0.85)^2 = 72% of full height. At 0.92
	 * an uncharged pop is (0.92)^2 = 85% of full (142 vs 168), so heights no longer feel random.
	 */
	public float minPopFraction = 0.92f;
	/** Crouch time for a full pop. 0.35 s needed a deliberate pause; 0.20 s is a normal wind-up. */
	public float crouchChargeTime = 0.20f;
	public float landingToleranceDeg = 55f;
	public float maxStepUp = 24f;
	public float rollOffDrop = 24f;
	/** A wall hit bails when speed * cos(incidence) exceeds this AND the incidence is within wallBailAngleDeg of head-on. */
	public float wallBailSpeed = 1100f;
	public float wallBailAngleDeg = 30f;
	/** A softer hit (speed * cos(incidence) above this) stumbles: steering is locked for stumbleTime, the combo goes on. */
	public float wallStumbleSpeed = 600f;
	public float stumbleTime = 0.3f;
	/** Fraction of the speed lost on first contact with a wall, times cos(incidence). */
	public float wallContactLoss = 0.25f;
	/** Speed bled while pressed against a wall, u/s^2, times cos(incidence). */
	public float wallScrape = 400f;
	/** Max rad/s the board eases along a wall it scrapes, only while not steering. */
	public float wallAlignRate = 1.5f;
	/** Radians/sec the heading eases toward the travel direction (or its opposite) while airborne with no steer input. */
	public float spinAssistRate = 6.0f;
	public float bailDuration = 1.2f;
	public float bailFriction = 1500f;
	/** Seconds after a bail finishes before the skater gets back on automatically. */
	public float bailAutoReset = 0.5f;
	/**
	 * Skater radius for wall blocking and contacts ({@link CollisionWorld#blockerTop(float, float, float, float, float)}
	 * and {@link CollisionWorld#contact}), local units, at most 12: the object boxes' tile index is grown by
	 * BlockerSet.SKATER_R = 12, so larger radii are capped there. The forgiving-collisions option lowers it to 8.
	 */
	public float skaterRadius = 12f;
	/**
	 * Ramp launch: rolling uphill at rampLaunchSpeedFraction * maxPushSpeed or faster, a terrain slope that drops
	 * by rampLaunchSlopeDrop or more just ahead (from an approach of at least rampLaunchMinSlope) leaves the
	 * ground with vh = speed * approach slope (at most maxSlope).
	 */
	public float rampLaunchSpeedFraction = 0.6f;
	public float rampLaunchSlopeDrop = 0.35f;
	public float rampLaunchMinSlope = 0.2f;
	/**
	 * Distance (local units) rolled up an approach of at least rampLaunchMinSlope before a crest launches: two
	 * tiles of run-up, so a lone steep tile on rough ground is a bump, not a ramp.
	 */
	public float rampLaunchMinRun = 256f;
	/**
	 * Momentum climb: a terrain rise too steep to roll up (over maxStepUp in one ground substep) is climbed at
	 * momentumClimbSpeedPerRise * rise speed or faster, losing speed as gravity * momentumClimbGravityScale on it.
	 */
	public float momentumClimbSpeedPerRise = 40f;
	public float momentumClimbGravityScale = 1f;
	/** Drop-in: a terrain slope down (not a ledge) no steeper than this is followed instead of rolled off. */
	public float dropInMaxGradient = 4f;

	public static SkateTuning load(Gson gson, Reader json)
	{
		SkateTuning t = gson.fromJson(json, SkateTuning.class);
		return t == null ? new SkateTuning() : t;
	}

	public SkateTuning scaled(float speedScale, float popScale)
	{
		SkateTuning t;
		try
		{
			// a field-by-field copy: every field is a float
			t = (SkateTuning) clone();
		}
		catch (CloneNotSupportedException e)
		{
			throw new AssertionError(e);
		}
		t.pushImpulse *= speedScale;
		t.pushFromRest *= speedScale;
		t.pushMinImpulse *= speedScale;
		t.maxPushSpeed *= speedScale;
		// hits scale with the speeds pushing reaches: at 150 % an unscaled 1100 bailed on most head-ons
		t.wallBailSpeed *= speedScale;
		t.ollieImpulse *= popScale;
		return t;
	}

	/**
	 * A push speed or pop height setting (percent) as a factor, clamped to the 75-150 % range: 50 % pop made doubles
	 * nearly impossible, 200 % push made turns ~17 tiles wide (polish review).
	 */
	public static float scaleFromPercent(int percent)
	{
		return Math.max(75, Math.min(150, percent)) / 100f;
	}
}
