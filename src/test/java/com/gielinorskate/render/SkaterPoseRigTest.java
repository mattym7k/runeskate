package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

public class SkaterPoseRigTest
{
	static final float DT = 1 / 60f;

	private final SkaterPoseRig rig = new SkaterPoseRig();
	private final SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
	private final BodyPose out = new BodyPose();

	/** Runs {@code seconds} of frames, clearing one-frame events after the first; fails on a visible jump. */
	private void run(float seconds)
	{
		int n = Math.round(seconds / DT);
		for (int i = 0; i < n; i++)
		{
			float roll = out.roll;
			float pitch = out.pitch;
			float legs = out.legScale;
			float footX = out.legWeight * out.footX;
			float footY = out.legWeight * out.footY;
			rig.update(s, DT);
			rig.writeTo(out);
			// continuity: nothing moves more than this in one 60 fps frame
			assertTrue("roll jump " + (out.roll - roll), Math.abs(out.roll - roll) < 0.08f);
			assertTrue("pitch jump", Math.abs(out.pitch - pitch) < 0.06f);
			assertTrue("legs jump " + (out.legScale - legs), Math.abs(out.legScale - legs) < 0.15f);
			// the push foot's weighted offset is what the mesh shows. Its fastest real move is the reach down
			// to the ground, 30 units in 0.16 of a 0.42 s cycle with smoothstep's 1.5x peak rate:
			// 30 * 1.5 / (0.16 * 0.42 * 60) = 11.2 units per frame
			assertTrue("push foot jump x", Math.abs(out.legWeight * out.footX - footX) < 12f);
			assertTrue("push foot jump y", Math.abs(out.legWeight * out.footY - footY) < 12f);
			s.popped = false;
			s.rolledOff = false;
			s.landed = false;
			s.bailed = false;
			s.pushed = false;
		}
	}

	@Test
	public void leanIntoTheTurnScalesWithSpeedAndTurnRateAndCaps()
	{
		// right (clockwise) carve rolls negative like the board's steer lean; 0.6 * atan(800 * 1 / 2000)
		assertEquals(-0.6f * (float) Math.atan(0.4), SkaterPoseRig.leanFor(800f, 1f), 1e-5f);
		assertTrue(SkaterPoseRig.leanFor(800f, -1f) > 0f);
		assertTrue(Math.abs(SkaterPoseRig.leanFor(1600f, 1f)) > Math.abs(SkaterPoseRig.leanFor(800f, 1f)));
		// riding fakie the centre of the turn is on the other side
		assertTrue(SkaterPoseRig.leanFor(-800f, 1f) > 0f);
		assertEquals(-SkaterPoseRig.MAX_LEAN, SkaterPoseRig.leanFor(2600f, 50f), 1e-6f);
		assertEquals(0f, SkaterPoseRig.leanFor(0f, 2f), 0f);
	}

	@Test
	public void tightCarveLeansFurtherAndTheLeanEasesIn()
	{
		s.speed = 900f;
		s.carveRate = 1.2f;
		run(1f);
		float normal = out.roll;
		assertEquals(SkaterPoseRig.leanFor(900f, 1.2f), normal, 1e-3f);
		// Shift + steer: the physics carve rate is tightCarveMult (2.2) times higher
		s.carveRate = 1.2f * 2.2f;
		run(1f);
		assertTrue(out.roll < normal - 0.05f);
		s.carveRate = 0f;
		run(1f);
		assertEquals(0f, out.roll, 1e-3f);
	}

	@Test
	public void crouchDepthFollowsTheCharge()
	{
		s.charge = 0.5f;
		run(0.5f);
		assertEquals(1f - 0.5f * SkaterPoseRig.CHARGE_CROUCH, out.legScale, 1e-3f);
		assertTrue(out.torsoBend > 0f);
		s.charge = 1f;
		run(0.5f);
		assertEquals(1f - SkaterPoseRig.CHARGE_CROUCH, out.legScale, 1e-3f);
	}

	@Test
	public void popExtendsThenTucksThenReachesForTheGround()
	{
		s.charge = 1f;
		run(0.4f);
		s.charge = 0f;
		s.state = SkaterState.AIRBORNE;
		s.popped = true;
		s.verticalSpeed = 800f;
		float minCrouch = Float.MAX_VALUE;
		for (int i = 0; i < 7; i++)
		{
			run(DT);
			minCrouch = Math.min(minCrouch, 1f - out.legScale);
		}
		// the legs straighten past standing within the extension window
		assertTrue("extension " + minCrouch, minCrouch < 0f);
		run(0.3f);
		assertEquals(SkaterPoseRig.AIR_TUCK, 1f - out.legScale, 0.02f);
		s.flipping = true;
		run(0.3f);
		assertEquals(SkaterPoseRig.TRICK_TUCK, 1f - out.legScale, 0.02f);
		s.flipping = false;
		s.verticalSpeed = -600f;
		run(0.3f);
		assertEquals(SkaterPoseRig.LAND_REACH, 1f - out.legScale, 0.02f);
	}

	@Test
	public void landingCompressesByLandingSpeedAndSpringsBack()
	{
		assertTrue(SkaterPoseRig.landingCompression(800f) > SkaterPoseRig.landingCompression(300f));
		assertEquals(SkaterPoseRig.MAX_LANDING_COMPRESSION, SkaterPoseRig.landingCompression(5000f), 0f);
		assertEquals(SkaterPoseRig.MIN_LANDING_COMPRESSION, SkaterPoseRig.landingCompression(0f), 0f);

		run(0.5f);
		s.landed = true;
		s.landingSpeed = 820f;
		float deepest = 0f;
		int frames = Math.round(0.25f / DT);
		for (int i = 0; i < frames; i++)
		{
			run(DT);
			deepest = Math.max(deepest, 1f - out.legScale);
		}
		assertEquals(SkaterPoseRig.LANDING_COMPRESSION, deepest, 0.01f);
		// most of the way back by 0.25 s, home by 0.5 s
		assertTrue(1f - out.legScale < 0.4f * deepest);
		run(0.25f);
		assertEquals(0f, 1f - out.legScale, 0.01f);
	}

	@Test
	public void manualTipsTheBodyWithTheBoardAboutTheContactTruck()
	{
		s.state = SkaterState.MANUAL;
		s.hold = Trick.MANUAL;
		s.boardPitch = 0.25f;
		run(1f);
		assertEquals(SkaterPoseRig.BODY_PITCH_FRACTION * 0.25f, out.pitch,
			SkaterPoseRig.MANUAL_SWAY + 1e-3f);
		// pitch > 0 turns about the board's -z truck, which is puppet +x
		assertEquals(BoardPlacement.TRUCK_Z, out.pivotX, 0f);
		s.hold = Trick.NOSE_MANUAL;
		s.boardPitch = -0.25f;
		run(1f);
		assertTrue(out.pitch < 0f);
		assertEquals(-BoardPlacement.TRUCK_Z, out.pivotX, 0f);
	}

	@Test
	public void grindCrouchesAndSwaysWithinBounds()
	{
		s.state = SkaterState.GRINDING;
		s.hold = Trick.FIFTY_FIFTY;
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		run(1f);
		for (int i = 0; i < 180; i++)
		{
			run(DT);
			min = Math.min(min, out.roll);
			max = Math.max(max, out.roll);
		}
		assertEquals(SkaterPoseRig.GRIND_CROUCH, 1f - out.legScale, 0.01f);
		assertTrue(max > 0.02f && min < -0.02f);
		assertTrue(max <= SkaterPoseRig.GRIND_SWAY + 1e-4f && min >= -SkaterPoseRig.GRIND_SWAY - 1e-4f);
	}

	@Test
	public void bailFallsSmoothlyOntoTheChestAndGetsUp()
	{
		s.speed = 800f;
		run(0.2f);
		s.state = SkaterState.BAILED;
		s.bailed = true;
		s.speed = 0f;
		run(0.15f);
		float early = -out.roll;
		assertTrue("falling " + early, early > 0.2f && early < SkaterPoseRig.BAIL_TUMBLE);
		run(1.2f);
		assertEquals(-SkaterPoseRig.BAIL_TUMBLE, out.roll, 0.05f);
		s.state = SkaterState.ROLLING;
		run(1f);
		assertEquals(0f, out.roll, 0.01f);
	}

	@Test
	public void bailWithABailAnimationDoesNotTumble()
	{
		s.state = SkaterState.BAILED;
		s.bailed = true;
		s.proceduralBail = false;
		run(1f);
		assertEquals(0f, out.roll, 1e-4f);
	}

	@Test
	public void resetIsNeutral()
	{
		s.charge = 1f;
		s.speed = 900f;
		s.carveRate = 2f;
		run(0.5f);
		rig.reset();
		rig.writeTo(out);
		assertTrue(out.isNeutral());
	}

	@Test
	public void swayStaysInRange()
	{
		for (float t = 0f; t < 20f; t += 0.01f)
		{
			assertTrue(Math.abs(SkaterPoseRig.sway(t)) <= 1f);
		}
	}
	@Test
	public void pushPlantsTheRearFootBehindOnTheGroundThenReturnsToTheBoard()
	{
		s.speed = 500f;
		s.pushed = true;
		// half a cycle: the foot is down and sweeping
		run(PushCycle.DURATION / 2f);
		assertEquals(1f, out.legWeight, 1e-3f);
		assertEquals(PushCycle.GROUND_DEPTH, out.footY, 0.5f);
		// regular stance riding forward (+x): the rear leg is the -x one and the foot goes behind (-x)
		assertEquals(-1f, out.legSide, 0f);
		assertTrue(out.footX < -5f);
		// toward the toe side, off the deck
		assertTrue(out.footZ < -13f);
		// the front knee bends and the body leans into the push
		assertTrue(out.legScale < 0.92f);
		assertTrue(out.torsoLean > 0.05f);
		run(PushCycle.DURATION);
		assertEquals(0f, out.legWeight, 1e-4f);
		assertEquals(1f, out.legScale, 1e-3f);
		assertEquals(0f, out.torsoLean, 1e-4f);
		assertTrue(out.isNeutral());
	}

	@Test
	public void ridingFakieThePushLegAndSweepMirror()
	{
		s.speed = -400f;
		s.pushed = true;
		run(PushCycle.DURATION / 2f);
		assertEquals(1f, out.legSide, 0f);
		assertTrue(out.footX > 5f);
		assertTrue(out.torsoLean < -0.05f);
	}

	@Test
	public void goofyMirrorsTheRegularPush()
	{
		s.goofy = true;
		s.speed = 500f;
		s.pushed = true;
		run(PushCycle.DURATION / 2f);
		assertEquals(1f, out.legSide, 0f);
		assertTrue(out.footX > 5f);
	}

	@Test
	public void noPushWhileAirborneGrindingManualOrBailed()
	{
		for (SkaterState st : new SkaterState[]{SkaterState.AIRBORNE, SkaterState.GRINDING, SkaterState.MANUAL,
			SkaterState.BAILED})
		{
			rig.reset();
			out.neutral();
			s.state = st;
			s.pushed = true;
			for (int i = 0; i < 20; i++)
			{
				rig.update(s, DT);
				rig.writeTo(out);
				assertEquals(st.toString(), 0f, out.legWeight, 0f);
				s.pushed = i % 5 == 0;
			}
		}
	}

	@Test
	public void noProceduralPushWhenAPushAnimationPlays()
	{
		s.proceduralPush = false;
		s.pushed = true;
		run(PushCycle.DURATION / 2f);
		assertEquals(0f, out.legWeight, 0f);
	}

	@Test
	public void popMidPushCancelsTheCycleSmoothly()
	{
		s.speed = 500f;
		s.pushed = true;
		run(PushCycle.DURATION / 2f);
		assertTrue(out.legWeight > 0.9f);
		s.state = SkaterState.AIRBORNE;
		s.popped = true;
		// run() fails on any visible jump
		run(0.1f);
		assertTrue(out.legWeight < 0.5f);
		run(0.3f);
		assertEquals(0f, out.legWeight, 1e-3f);
		// a landing does not resume the cancelled push
		s.state = SkaterState.ROLLING;
		run(0.3f);
		assertEquals(0f, out.legWeight, 0f);
	}

	@Test
	public void holdingPushKicksAboutOncePerTwoPushes()
	{
		// physics keeps its quick 0.4 s push rhythm; the animation plays one longer kick and ignores pushes that
		// land mid-kick, so holding W shows a calm stride instead of a kick on every push. run() checks continuity.
		s.speed = 500f;
		for (int k = 0; k < 10; k++)
		{
			s.pushed = true;
			run(0.4f);
		}
		// 10 pushes over 4 s at 0, 0.4, ... 3.6 with a 0.70 s kick: kicks start at 0, 0.8, 1.6, 2.4, 3.2 = 5
		assertEquals(5, rig.pushCyclesStarted());
	}

	@Test
	public void bodyFlipPassesThroughRawAboutTheCentreOfMass()
	{
		s.state = SkaterState.AIRBORNE;
		float a = 0f;
		// a front flip over 0.6 s, past a full turn: drawn raw, no wrap
		for (int i = 0; i < 40; i++)
		{
			a += 2.4f * (float) Math.PI * DT / 0.6f;
			s.bodyFlipAngle = a;
			rig.update(s, DT);
			rig.writeTo(out);
			assertEquals(SkaterPoseRig.REGULAR_FORWARD_X * a, out.flip, 1e-4f);
			assertEquals(BoardPlacement.puppetFlipPivotY(), out.flipPivotY, 0f);
		}
		assertTrue(out.flip > 2f * (float) Math.PI);
	}

	@Test
	public void goofyMirrorsTheFlip()
	{
		s.goofy = true;
		s.state = SkaterState.AIRBORNE;
		s.bodyFlipAngle = 0.3f;
		rig.update(s, DT);
		rig.writeTo(out);
		assertEquals(-0.3f, out.flip, 1e-5f);
	}

	@Test
	public void flipTucksTheKnees()
	{
		s.state = SkaterState.AIRBORNE;
		s.bodyFlipAngle = 1f;
		assertEquals(SkaterPoseRig.TRICK_TUCK, SkaterPoseRig.crouchTarget(s, 1f, true), 0f);
		s.bodyFlipAngle = 0f;
		assertEquals(SkaterPoseRig.AIR_TUCK, SkaterPoseRig.crouchTarget(s, 1f, true), 0f);
	}

	@Test
	public void wholeTurnsNeverShowButAResetMidFlipEasesOut()
	{
		s.state = SkaterState.AIRBORNE;
		s.bodyFlipAngle = 2f * (float) Math.PI;
		rig.update(s, DT);
		rig.writeTo(out);
		// landing a completed flip: the angle goes back to 0, which is the same pose
		s.state = SkaterState.ROLLING;
		s.landed = true;
		s.bodyFlipAngle = 0f;
		rig.update(s, DT);
		rig.writeTo(out);
		assertEquals(0f, out.flip, 1e-4f);
		s.landed = false;

		// a bail 1.3 rad into a flip resets the angle to 0: drawn as an ease, not a snap
		rig.reset();
		s.state = SkaterState.AIRBORNE;
		s.bodyFlipAngle = 1.3f;
		rig.update(s, DT);
		rig.writeTo(out);
		s.state = SkaterState.BAILED;
		s.bailed = true;
		s.bodyFlipAngle = 0f;
		float last = out.flip;
		for (int i = 0; i < 60; i++)
		{
			rig.update(s, DT);
			rig.writeTo(out);
			assertTrue("flip jump " + (out.flip - last), Math.abs(out.flip - last) < 0.25f);
			last = out.flip;
			s.bailed = false;
		}
		assertEquals(0f, out.flip, 1e-3f);
	}

	@Test
	public void fastRealFlipIsNotMistakenForAReset()
	{
		s.state = SkaterState.AIRBORNE;
		float a = 0f;
		for (int i = 0; i < 30; i++)
		{
			// a double flip at twice its mean rate, 50 rad/s
			a += 50f * DT;
			s.bodyFlipAngle = a;
			rig.update(s, DT);
			rig.writeTo(out);
			assertEquals(a, out.flip, 1e-4f);
		}
	}

	@Test
	public void aGrabReachesForItsPartOfTheBoardAndLetsGoOnLanding()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.NOSEGRAB;
		run(0.5f);
		assertEquals(GrabPose.torsoLean(Trick.NOSEGRAB), out.torsoLean, 0.02f);
		s.hold = Trick.TAILGRAB;
		run(0.5f);
		assertEquals(GrabPose.torsoLean(Trick.TAILGRAB), out.torsoLean, 0.02f);
		s.hold = Trick.INDY;
		run(0.5f);
		assertEquals(GrabPose.bodyRoll(Trick.INDY), out.roll, 0.02f);
		s.state = SkaterState.ROLLING;
		s.hold = null;
		s.landed = true;
		run(0.6f);
		assertEquals(0f, out.torsoLean, 0.01f);
		assertEquals(0f, out.roll, 0.01f);
	}

	@Test
	public void goofyReachesTheOtherWayAlongTheBoard()
	{
		s.state = SkaterState.AIRBORNE;
		s.goofy = true;
		s.hold = Trick.NOSEGRAB;
		run(0.5f);
		assertEquals(-GrabPose.torsoLean(Trick.NOSEGRAB), out.torsoLean, 0.02f);
	}

	@Test
	public void theGrabbingHandReachesForTheBoardInAboutATenthOfASecond()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.INDY;
		s.grabHand = -1;
		run(0.1f);
		assertTrue("arm " + out.armWeight, out.armWeight > 0.75f);
		run(0.2f);
		assertEquals(1f, out.armWeight, 0.02f);
		assertEquals(-1f, out.armSide, 0f);
		assertEquals(GrabPose.grabAlong(Trick.INDY), out.grabAlong, 0.5f);
		assertEquals(GrabPose.grabAcross(Trick.INDY), out.grabAcross, 0.5f);
		assertEquals(GrabPose.grabBoardY(Trick.INDY), out.grabBoardY, 0f);
	}

	@Test
	public void eachGrabUsesItsOwnHandWhicheverKeyPickedIt()
	{
		// Q (left) with no aim picks an Indy: still the back (right) hand, never across the body
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.INDY;
		s.grabHand = 1;
		run(0.3f);
		assertEquals(-1f, out.armSide, 0f);
		rig.reset();
		rig.writeTo(out);
		// E (right) with no aim picks a Melon: the front (left) hand
		s.hold = Trick.MELON;
		s.grabHand = -1;
		run(0.3f);
		assertEquals(1f, out.armSide, 0f);
	}

	@Test
	public void aTailgrabIsHeldWithTheKeysHand()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.TAILGRAB;
		s.grabHand = 1;
		run(0.3f);
		assertEquals(1f, out.armSide, 0f);
		rig.reset();
		rig.writeTo(out);
		s.grabHand = -1;
		run(0.3f);
		assertEquals(-1f, out.armSide, 0f);
	}

	@Test
	public void aGrabPullsTheKneesAndBoardUpWithinATenthOfASecondAndLetsThemDownAsFast()
	{
		airborneAfterPop();
		assertEquals(0f, out.kneeFold, 0f);
		assertEquals(0f, out.feetLift, 0f);
		s.hold = Trick.INDY;
		s.grabHand = -1;
		run(0.1f);
		assertTrue("fold " + out.kneeFold, out.kneeFold > 0.85f);
		run(0.3f);
		assertEquals(1f, out.kneeFold, 0.01f);
		assertEquals(SkaterPoseRig.GRAB_LIFT, out.feetLift, 0.2f);
		s.hold = null;
		run(0.15f);
		assertTrue("fold " + out.kneeFold, out.kneeFold < 0.1f);
		assertTrue("lift " + out.feetLift, out.feetLift < 0.1f * SkaterPoseRig.GRAB_LIFT);
	}

	@Test
	public void theKneesAndBoardComeDownOnLanding()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.MELON;
		run(0.3f);
		s.state = SkaterState.ROLLING;
		s.hold = null;
		s.landed = true;
		run(0.2f);
		assertTrue("fold " + out.kneeFold, out.kneeFold < 0.05f);
	}

	@Test
	public void noOtherTrickFoldsTheKneesOrLiftsTheBoard()
	{
		s.state = SkaterState.AIRBORNE;
		s.popped = true;
		s.flipping = true;
		s.hold = Trick.KICKFLIP;
		s.bodyFlipAngle = 2f;
		run(0.5f);
		assertEquals(0f, out.kneeFold, 0f);
		assertEquals(0f, out.feetLift, 0f);
		s.state = SkaterState.GRINDING;
		s.hold = Trick.FIFTY_FIFTY;
		run(0.5f);
		assertEquals(0f, out.kneeFold, 0f);
	}

	@Test
	public void withTheHandUnknownTheGrabsUsualHandReaches()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.MELON;
		s.grabHand = 0;
		run(0.3f);
		assertEquals(1f, out.armSide, 0f);
		assertEquals(1f, out.armWeight, 0.05f);
	}

	@Test
	public void theHandLetsGoOnLandingAndForOtherTricks()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.NOSEGRAB;
		s.grabHand = 1;
		run(0.3f);
		s.state = SkaterState.ROLLING;
		s.hold = null;
		s.landed = true;
		run(0.3f);
		assertEquals(0f, out.armWeight, 0.02f);
		s.state = SkaterState.MANUAL;
		s.hold = Trick.MANUAL;
		run(0.3f);
		assertEquals(0f, out.armWeight, 0.02f);
	}

	@Test
	public void switchingHandsLetsGoBeforeTheOtherArmReaches()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.INDY;
		s.grabHand = -1;
		run(0.3f);
		s.hold = Trick.MELON;
		s.grabHand = 1;
		float side = out.armSide;
		for (int i = 0; i < 60; i++)
		{
			float before = out.armWeight;
			run(DT);
			if (out.armSide != side)
			{
				// the side only changes once the old arm is (nearly) back
				assertTrue("weight before the switch " + before, before < SkaterPoseRig.ARM_LET_GO + 0.01f);
				side = out.armSide;
			}
		}
		assertEquals(1f, out.armSide, 0f);
		assertEquals(1f, out.armWeight, 0.05f);
	}

	@Test
	public void aGrabTucksDeepAndFoldsForward()
	{
		s.state = SkaterState.AIRBORNE;
		assertTrue(SkaterPoseRig.GRAB_TUCK >= SkaterPoseRig.TRICK_TUCK);
		s.hold = Trick.INDY;
		assertEquals(SkaterPoseRig.GRAB_TUCK, SkaterPoseRig.crouchTarget(s, 1f, true), 0f);
		run(0.5f);
		assertTrue("bend " + out.torsoBend,
			out.torsoBend > SkaterPoseRig.TORSO_BEND_PER_CROUCH * SkaterPoseRig.GRAB_TUCK + 0.8f * SkaterPoseRig.GRAB_BEND);
	}

	@Test
	public void goofyGrabsTheNoseOnTheOtherSide()
	{
		s.state = SkaterState.AIRBORNE;
		s.goofy = true;
		s.hold = Trick.NOSEGRAB;
		run(0.3f);
		assertEquals(-1f, out.forwardX, 0f);
	}

	/** In the air after a pop, the legs tucked and no trick going. */
	private void airborneAfterPop()
	{
		s.state = SkaterState.AIRBORNE;
		s.popped = true;
		run(0.4f);
	}

	@Test
	public void aGrabSnapsTheBodyAndHandToTheBoardWithinATenthOfASecond()
	{
		airborneAfterPop();
		float tuckBefore = 1f - out.legScale;
		s.hold = Trick.NOSEGRAB;
		s.grabHand = 1;
		run(0.1f);
		assertTrue("arm " + out.armWeight, out.armWeight > 0.9f);
		assertTrue("lean " + out.torsoLean, out.torsoLean > 0.9f * GrabPose.torsoLean(Trick.NOSEGRAB));
		float tuck = 1f - out.legScale;
		assertTrue("tuck " + tuck, tuck - tuckBefore > 0.85f * (SkaterPoseRig.GRAB_TUCK - tuckBefore));
		float bend = out.torsoBend - SkaterPoseRig.TORSO_BEND_PER_CROUCH * tuck;
		assertTrue("fold " + bend, bend > 0.9f * SkaterPoseRig.GRAB_BEND);
		s.hold = null;
		rig.reset();
		rig.writeTo(out);
		airborneAfterPop();
		s.hold = Trick.INDY;
		s.grabHand = -1;
		run(0.1f);
		assertTrue("roll " + out.roll, out.roll < 0.85f * GrabPose.bodyRoll(Trick.INDY));
	}

	@Test
	public void aGrabRightAfterThePopTucksAtOnce()
	{
		s.state = SkaterState.AIRBORNE;
		s.hold = Trick.INDY;
		assertEquals(SkaterPoseRig.GRAB_TUCK, SkaterPoseRig.crouchTarget(s, 0.01f, true), 0f);
		// the arm and body still get there quickly from the pop's extension
		s.hold = null;
		s.popped = true;
		run(DT);
		s.hold = Trick.INDY;
		s.grabHand = -1;
		run(0.1f);
		assertTrue("arm " + out.armWeight, out.armWeight > 0.9f);
		assertTrue("tuck " + (1f - out.legScale), 1f - out.legScale > 0.8f * SkaterPoseRig.GRAB_TUCK);
	}

	@Test
	public void lettingGoInTheAirReleasesWithinATenthOfASecond()
	{
		airborneAfterPop();
		s.hold = Trick.NOSEGRAB;
		s.grabHand = 1;
		run(0.4f);
		s.hold = null;
		run(0.1f);
		assertTrue("arm " + out.armWeight, out.armWeight < 0.1f);
		assertTrue("lean " + out.torsoLean, Math.abs(out.torsoLean) < 0.1f * GrabPose.torsoLean(Trick.NOSEGRAB));
		float tuck = 1f - out.legScale;
		assertTrue("tuck " + tuck, tuck < SkaterPoseRig.TRICK_TUCK + 0.03f);
		assertTrue("fold " + out.torsoBend,
			out.torsoBend - SkaterPoseRig.TORSO_BEND_PER_CROUCH * tuck < 0.1f * SkaterPoseRig.GRAB_BEND);
	}

	@Test
	public void anAimRenamingTheGrabSlidesTheHandWithoutAJump()
	{
		airborneAfterPop();
		s.hold = Trick.INDY;
		s.grabHand = -1;
		run(0.05f);
		// the aim turns the Indy into a Tailgrab (same hand): the hand slides along, across and up to the tip
		s.hold = Trick.TAILGRAB;
		for (int i = 0; i < 30; i++)
		{
			float y = out.grabBoardY;
			float along = out.grabAlong;
			run(DT);
			assertTrue("hand height jump " + (out.grabBoardY - y), Math.abs(out.grabBoardY - y) < 2f);
			assertTrue("hand along jump " + (out.grabAlong - along), Math.abs(out.grabAlong - along) < 12f);
		}
		assertEquals(GrabPose.grabBoardY(Trick.TAILGRAB), out.grabBoardY, 0.2f);
		assertEquals(GrabPose.grabAlong(Trick.TAILGRAB), out.grabAlong, 0.5f);
		assertEquals(1f, out.armWeight, 0.01f);
	}
}
