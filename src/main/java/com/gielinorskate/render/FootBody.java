package com.gielinorskate.render;

import com.gielinorskate.physics.FootPhysics;
import com.gielinorskate.physics.SkaterState;

/**
 * The walker's body: which OSRS pose animation fits its speed (idle, walk or run, with hysteresis so the
 * 0.1 s speed-up never flickers between them), and the {@link SkaterPoseRig} signals for an on-foot frame (a
 * jump's take-off, tuck and landing; a fall barely bends the knees). Pure; one per walker (local or ghost).
 */
public final class FootBody
{
	public enum Gait
	{
		IDLE,
		WALK,
		RUN
	}

	/** u/s: standing starts walking above this, walking stops below WALK_TO_IDLE. */
	static final float IDLE_TO_WALK = 40f;
	static final float WALK_TO_IDLE = 20f;
	/**
	 * u/s: walking breaks into a run above this (between the walk, 320, and the sprint, 853), a run slows below
	 * RUN_TO_WALK (still above the walk, so a steady walk never runs).
	 */
	static final float WALK_TO_RUN = 1.25f * FootPhysics.WALK_SPEED;
	static final float RUN_TO_WALK = 1.1f * FootPhysics.WALK_SPEED;

	private Gait gait = Gait.IDLE;
	private boolean wasAirborne;
	/** Vertical speed last seen in the air, for the landing squash. */
	private float airSpeed;

	/** The gait at {@code speed} u/s coming from {@code current}; in the air the body stands for the tuck. */
	public static Gait gait(Gait current, float speed, boolean airborne)
	{
		if (airborne)
		{
			return Gait.IDLE;
		}
		switch (current)
		{
			case RUN:
				return speed >= RUN_TO_WALK ? Gait.RUN : speed >= WALK_TO_IDLE ? Gait.WALK : Gait.IDLE;
			case WALK:
				return speed > WALK_TO_RUN ? Gait.RUN : speed >= WALK_TO_IDLE ? Gait.WALK : Gait.IDLE;
			default:
				return speed > WALK_TO_RUN ? Gait.RUN : speed > IDLE_TO_WALK ? Gait.WALK : Gait.IDLE;
		}
	}

	public Gait gait()
	{
		return gait;
	}

	/**
	 * One on-foot frame into {@code s} (every field set), and the gait updated.
	 *
	 * @param jumping the flight is a jump (not a fall)
	 * @param verticalSpeed up-positive u/s
	 */
	public void fill(SkaterPoseRig.Signals s, float speed, boolean airborne, boolean jumping, float verticalSpeed)
	{
		gait = gait(gait, speed, airborne);
		boolean tookOff = airborne && !wasAirborne;
		boolean landed = !airborne && wasAirborne;
		s.state = airborne ? SkaterState.AIRBORNE : SkaterState.ROLLING;
		s.onFoot = true;
		s.speed = 0f;
		s.carveRate = 0f;
		s.charge = 0f;
		s.verticalSpeed = airborne ? verticalSpeed : 0f;
		s.boardPitch = 0f;
		s.hold = null;
		s.grabHand = 0;
		s.flipping = false;
		s.popped = tookOff && jumping;
		s.rolledOff = tookOff && !jumping;
		s.landed = landed;
		s.bailed = false;
		s.landingSpeed = landed ? Math.abs(airSpeed) : 0f;
		s.proceduralBail = true;
		s.pushed = false;
		s.proceduralPush = false;
		s.goofy = false;
		s.bodyFlipAngle = 0f;
		if (airborne)
		{
			airSpeed = verticalSpeed;
		}
		wasAirborne = airborne;
	}

	public void reset()
	{
		gait = Gait.IDLE;
		wasAirborne = false;
		airSpeed = 0f;
	}
}
