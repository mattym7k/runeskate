package com.gielinorskate.party;

import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.BodyPose;
import com.gielinorskate.render.FootBody;
import com.gielinorskate.render.KnockdownBody;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.render.SkaterPoseRig;

/**
 * A light version of the local skater's procedural body for a party ghost: the same {@link SkaterPoseRig}, fed
 * from what the ghost's updates carry. Lean from the turn rate, the pop charge's crouch, a push kick when an update
 * shows the ghost sped up rolling (GhostPredictor.pushCount: the wire has no push), the air tuck after a pop, the
 * landing squash and the bail fall from the update's events, grabs, manual and grind pitch, and the front / back
 * flip; knocked down, the knockdown's body (KnockdownBody). Pure; one per ghost.
 */
final class GhostRig
{
	/** Seconds: a pop this recent when the ghost is first drawn still counts (the rig extends for 0.12 s). */
	private static final float JUST_POPPED = 0.1f;
	/** Seconds: a pop this recent when an on-foot ghost leaves the ground makes it a jump, not a fall. */
	private static final float JUMP_WINDOW = 0.5f;

	private final SkaterPoseRig rig = new SkaterPoseRig();
	private final SkaterPoseRig.Signals signals = new SkaterPoseRig.Signals();
	private final FootBody foot = new FootBody();
	private int pops;
	private int lands;
	private int bails;
	private int pushes;
	/** The last frame was a knockdown: the next one starts the rig from standing. */
	private boolean knocked;
	private SkaterState lastState;
	/** Vertical speed (up > 0) last seen in the air, for the landing squash. */
	private float airSpeed;

	/** One render frame: steps the rig from {@code ghost} as drawn ({@code pose}) and writes it to {@code out}. */
	void update(GhostPredictor ghost, RenderPose pose, float now, float dt, BodyPose out)
	{
		SkaterPoseRig.Signals s = signals;
		startAfresh();
		boolean first = lastState == null;
		// a ghost first drawn just after a pop still extends and tucks
		boolean popped = first ? ghost.sincePop(now) <= JUST_POPPED : ghost.popCount() != pops;
		boolean landed = !first && ghost.landCount() != lands;
		boolean bailed = !first && ghost.bailCount() != bails;
		boolean pushed = !first && ghost.pushCount() != pushes;
		pops = ghost.popCount();
		lands = ghost.landCount();
		bails = ghost.bailCount();
		pushes = ghost.pushCount();

		s.state = pose.state;
		s.speed = ghost.speed();
		s.carveRate = ghost.turnRate(now);
		s.charge = pose.state == SkaterState.AIRBORNE ? 0f : ghost.charge();
		s.verticalSpeed = ghost.verticalSpeed(now);
		// the hold's pitch only: an impossible's pitch is the board's, not the body's
		s.boardPitch = SkatePhysics.boardPitchFor(pose.state, pose.hold);
		s.hold = pose.hold;
		s.flipping = ghost.flipping(now);
		s.popped = popped;
		s.rolledOff = !first && !popped && pose.state == SkaterState.AIRBORNE && lastState != SkaterState.AIRBORNE;
		s.landed = landed;
		s.bailed = bailed;
		s.landingSpeed = Math.abs(airSpeed);
		s.proceduralBail = true;
		s.pushed = pushed;
		s.proceduralPush = true;
		s.goofy = false;
		s.bodyFlipAngle = ghost.bodyFlip(now);
		s.onFoot = false;
		rig.update(s, dt);
		rig.writeTo(out);
		// the next step off starts from standing
		foot.reset();

		if (pose.state == SkaterState.AIRBORNE)
		{
			airSpeed = s.verticalSpeed;
		}
		lastState = pose.state;
	}

	/**
	 * One render frame of a ghost on foot: upright (the walk or run is the OSRS animation under it), a light tuck
	 * in a jump (a pop on the wire) and the landing squash, as the local walker's; the gait follows the speed.
	 */
	void updateOnFoot(GhostPredictor ghost, RenderPose pose, float now, float dt, BodyPose out)
	{
		startAfresh();
		boolean airborne = pose.state == SkaterState.AIRBORNE;
		boolean jumping = ghost.popCount() != pops || ghost.sincePop(now) <= JUMP_WINDOW;
		pops = ghost.popCount();
		lands = ghost.landCount();
		bails = ghost.bailCount();
		pushes = ghost.pushCount();
		foot.fill(signals, ghost.speed(), airborne, jumping, ghost.verticalSpeed(now));
		rig.update(signals, dt);
		rig.writeTo(out);
		if (airborne)
		{
			airSpeed = signals.verticalSpeed;
		}
		lastState = pose.state;
	}

	/** The walker's gait (from its speed), for the walk or run animation. */
	FootBody.Gait gait()
	{
		return foot.gait();
	}

	/**
	 * One frame knocked down: the knockdown's body (the tumble, or with an OSRS lie-down or get-up playing, the body
	 * as animated); the next skate or walk frame starts the rig from standing, as the local skater's does.
	 */
	void knockdown(KnockdownPose k, boolean animated, BodyPose out)
	{
		KnockdownBody.write(k, animated, out);
		knocked = true;
	}

	private void startAfresh()
	{
		if (knocked)
		{
			knocked = false;
			rig.reset();
			foot.reset();
			lastState = null;
		}
	}
}
