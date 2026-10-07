package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

/**
 * The rolling grab on the 200-unit test body: rolling with a grab held (the physics' ground grab as the rig's hold) the
 * skater crouches low and the hand reaches down to its spot of the board, which stays on the ground under planted
 * feet. Landing out of an air grab with the key held settles into it with no jump.
 */
public class RollingGrabBodyTest
{
	private static final float DT = 1 / 60f;
	private static final Trick[] GRABS = {Trick.INDY, Trick.MELON, Trick.MUTE, Trick.STALEFISH, Trick.NOSEGRAB,
		Trick.TAILGRAB, Trick.CRAIL, Trick.TWEAKED_INDY, Trick.METHOD, Trick.JAPAN, Trick.TWEAKED_STALEFISH,
		Trick.NOSEBONE, Trick.TAILBONE, Trick.CRAIL_TWEAK};
	private static final int[] KEYS = {1, -1, 0};
	private static final float HAND_TOLERANCE = 6f;
	private static final float CRAIL_TOLERANCE = 8f;

	private static SkaterPoseRig.Signals rollingSignals(Trick grab, int key)
	{
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		s.state = SkaterState.ROLLING;
		s.speed = 400f;
		s.hold = grab;
		s.grabHand = key;
		return s;
	}

	/** The pose after a second rolling with {@code grab} held, the board drawn flat on the ground. */
	private static BodyPose held(Trick grab, int key)
	{
		SkaterPoseRig rig = new SkaterPoseRig();
		SkaterPoseRig.Signals s = rollingSignals(grab, key);
		for (int i = 0; i < 60; i++)
		{
			rig.update(s, DT);
		}
		return drawn(rig);
	}

	/** What the renderer writes on the ground: no tweak (the board is not a hold), the rig's lift. */
	private static BodyPose drawn(SkaterPoseRig rig)
	{
		BodyPose p = new BodyPose();
		rig.writeTo(p);
		p.boardRoll = 0f;
		p.boardPitch = 0f;
		p.deckLift = 0f;
		p.boardLift = BoardPlacement.grabLift(p.feetLift, false);
		return p;
	}

	private static float[] target(BodyPose p)
	{
		float[] t = new float[3];
		GrabReach.target(p.grabAlong, p.grabAcross, p.grabBoardY, p.forwardX, p.boardRoll, p.boardPitch,
			p.deckLift - p.boardLift, t);
		return t;
	}

	private static int hand(float side)
	{
		Humanoid rest = new Humanoid();
		ArmLocator a = new ArmLocator();
		assertTrue(a.locate(rest.xs, rest.ys, rest.zs, rest.n, side));
		return a.handIndex;
	}

	private static Humanoid deformed(BodyPose p)
	{
		Humanoid b = new Humanoid();
		MeshDeformer.deform(b.xs, b.ys, b.zs, b.n, p);
		return b;
	}

	private static float distance(Humanoid b, int i, float[] t)
	{
		float dx = b.xs[i] - t[0];
		float dy = b.ys[i] - t[1];
		float dz = b.zs[i] - t[2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	@Test
	public void theHandReachesDownToItsSpotOfTheBoardOnTheGround()
	{
		for (Trick grab : GRABS)
		{
			for (int key : KEYS)
			{
				BodyPose p = held(grab, key);
				assertEquals(grab + " arm", 1f, p.armWeight, 0.01f);
				assertEquals(grab + " lift", 0f, p.boardLift, 0f);
				float d = distance(deformed(p), hand(p.armSide), target(p));
				// a crail is the back hand all the way to the nose on the ground: the longest reach
				float tolerance = grab == Trick.CRAIL || grab == Trick.CRAIL_TWEAK ? CRAIL_TOLERANCE : HAND_TOLERANCE;
				assertTrue(grab + " key " + key + ": hand " + d + " from its spot", d < tolerance);
			}
		}
	}

	@Test
	public void aRollingTailgrabIsTheBackHandsWhicheverKey()
	{
		for (int key : KEYS)
		{
			assertEquals(-1f, held(Trick.TAILGRAB, key).armSide, 0f);
			assertEquals(-1f, held(Trick.TAILBONE, key).armSide, 0f);
		}
		assertEquals(1f, held(Trick.NOSEGRAB, -1).armSide, 0f);
	}

	@Test
	public void theFeetStayPlantedOnTheBoardOnTheGround()
	{
		for (Trick grab : GRABS)
		{
			BodyPose p = held(grab, 0);
			assertEquals(0f, p.feetLift, 1e-3f);
			Humanoid rest = new Humanoid();
			Humanoid b = deformed(p);
			for (int i = 0; i < rest.n; i++)
			{
				if (rest.ys[i] == 0f)
				{
					// a grab's body roll tips the soles a little about the board's long axis, never off the deck
					assertEquals(grab + " sole " + i, 0f, b.ys[i], 3f);
					assertTrue(grab + " sole " + i + " z " + b.zs[i], Math.abs(b.zs[i]) < 6f);
				}
			}
		}
	}

	@Test
	public void itIsALowCrouch()
	{
		for (Trick grab : GRABS)
		{
			BodyPose p = held(grab, 0);
			assertTrue(grab + " legScale " + p.legScale, p.legScale < 0.6f);
			assertTrue(grab + " fold " + p.kneeFold, p.kneeFold > 0.99f);
			Humanoid b = deformed(p);
			float top = 0f;
			for (int i = 0; i < b.n; i++)
			{
				top = Math.min(top, b.ys[i]);
			}
			assertTrue("head at " + top, top > -150f);
		}
	}

	@Test
	public void theFreeArmGoesOutForBalance()
	{
		for (Trick grab : GRABS)
		{
			BodyPose p = held(grab, 0);
			float side = -p.armSide;
			ArmLocator a = new ArmLocator();
			Humanoid rest = new Humanoid();
			assertTrue(a.locate(rest.xs, rest.ys, rest.zs, rest.n, side));
			Humanoid b = deformed(p);
			assertTrue(grab + " free hand x " + b.xs[a.handIndex], b.xs[a.handIndex] * side > Humanoid.ARM_OUTER + 15f);
		}
	}

	@Test
	public void withoutAGrabRollingIsAsBefore()
	{
		SkaterPoseRig rig = new SkaterPoseRig();
		SkaterPoseRig.Signals s = rollingSignals(null, 1);
		for (int i = 0; i < 60; i++)
		{
			rig.update(s, DT);
		}
		BodyPose p = new BodyPose();
		rig.writeTo(p);
		assertEquals(0f, p.armWeight, 0f);
		assertEquals(0f, p.kneeFold, 0f);
		assertEquals(0f, p.feetLift, 0f);
		assertEquals(1f, p.legScale, 1e-4f);
	}

	@Test
	public void landingFromAnAirGrabSettlesIntoTheRollingGrabSmoothly()
	{
		SkaterPoseRig rig = new SkaterPoseRig();
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		s.state = SkaterState.AIRBORNE;
		s.popped = true;
		rig.update(s, DT);
		s.popped = false;
		s.hold = Trick.INDY;
		s.grabHand = 1;
		for (int i = 0; i < 30; i++)
		{
			rig.update(s, DT);
		}
		BodyPose last = drawn(rig);
		assertEquals(SkaterPoseRig.GRAB_LIFT, last.feetLift, 0.5f);
		// touchdown: the physics' air grab is released and the same grab carries on as the rolling grab
		s.state = SkaterState.ROLLING;
		s.landed = true;
		s.landingSpeed = 800f;
		for (int i = 0; i < 40; i++)
		{
			rig.update(s, DT);
			s.landed = false;
			BodyPose p = drawn(rig);
			assertEquals("arm stays on the board", 1f, p.armWeight, 0.02f);
			assertTrue("lift step " + (last.feetLift - p.feetLift), Math.abs(last.feetLift - p.feetLift) < 3f);
			assertTrue("crouch step", Math.abs(last.legScale - p.legScale) < 0.08f);
			last = p;
		}
		assertEquals(0f, last.feetLift, 0.5f);
	}
}
