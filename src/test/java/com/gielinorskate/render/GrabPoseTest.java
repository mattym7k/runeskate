package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class GrabPoseTest
{
	@Test
	public void noseAndTailGrabsReachAndPitchTheirWay()
	{
		assertTrue(GrabPose.torsoLean(Trick.NOSEGRAB) > 0.3f);
		assertTrue(Math.abs(GrabPose.boardPitch(Trick.NOSEGRAB)) > 0.2f);
		assertTrue(GrabPose.torsoLean(Trick.TAILGRAB) < -0.3f);
		assertTrue(Math.abs(GrabPose.boardPitch(Trick.TAILGRAB)) > 0.2f);
		// the two pitch the board opposite ways: each lifts its own end (see below)
		assertTrue(GrabPose.boardPitch(Trick.NOSEGRAB) * GrabPose.boardPitch(Trick.TAILGRAB) < 0f);
		// a crail is the back hand reaching all the way to the nose
		assertTrue(GrabPose.torsoLean(Trick.CRAIL) > 0.3f);
	}

	@Test
	public void toeSideGrabsLeanToTheChestAndHeelSideOnesAway()
	{
		// BodyPose.roll: negative leans the head toward the chest (the toe side)
		for (Trick toe : new Trick[]{Trick.INDY, Trick.MUTE})
		{
			assertTrue(toe.toString(), GrabPose.bodyRoll(toe) < 0f);
			assertTrue(toe.toString(), GrabPose.boardRoll(toe) > 0f);
		}
		for (Trick heel : new Trick[]{Trick.MELON, Trick.STALEFISH})
		{
			assertTrue(heel.toString(), GrabPose.bodyRoll(heel) > 0f);
			assertTrue(heel.toString(), GrabPose.boardRoll(heel) < 0f);
		}
	}

	@Test
	public void tweaksGoFurtherTheSameWay()
	{
		for (Trick t : Trick.values())
		{
			Trick tw = Grabs.tweaked(t);
			if (tw == null)
			{
				continue;
			}
			assertEquals(GrabPose.TWEAK * GrabPose.torsoLean(t), GrabPose.torsoLean(tw), 1e-6f);
			assertEquals(GrabPose.TWEAK * GrabPose.bodyRoll(t), GrabPose.bodyRoll(tw), 1e-6f);
			assertEquals(GrabPose.TWEAK * GrabPose.boardPitch(t), GrabPose.boardPitch(tw), 1e-6f);
			assertEquals(GrabPose.TWEAK * GrabPose.boardRoll(t), GrabPose.boardRoll(tw), 1e-6f);
		}
	}

	@Test
	public void everyGrabHasItsOwnPoseAndNothingElseHasOne()
	{
		Set<String> poses = new HashSet<>();
		for (Trick t : Trick.values())
		{
			String pose = GrabPose.torsoLean(t) + "/" + GrabPose.bodyRoll(t) + "/" + GrabPose.boardPitch(t) + "/"
				+ GrabPose.boardRoll(t);
			if (t.kind == TrickKind.GRAB)
			{
				assertTrue("same pose as another grab: " + t, poses.add(pose));
			}
			else
			{
				assertEquals(t.toString(), "0.0/0.0/0.0/0.0", pose);
			}
		}
		assertEquals("0.0/0.0/0.0/0.0", GrabPose.torsoLean(null) + "/" + GrabPose.bodyRoll(null) + "/"
			+ GrabPose.boardPitch(null) + "/" + GrabPose.boardRoll(null));
	}

	@Test
	public void eachGrabHoldsItsPartOfTheBoard()
	{
		assertTrue(GrabPose.grabAlong(Trick.NOSEGRAB) > 35f);
		assertTrue(GrabPose.grabAlong(Trick.TAILGRAB) < -35f);
		assertTrue(GrabPose.grabAlong(Trick.CRAIL) > 35f);
		for (Trick toe : new Trick[]{Trick.INDY, Trick.MUTE, Trick.JAPAN, Trick.TWEAKED_INDY})
		{
			assertEquals(toe.toString(), -GrabPose.EDGE, GrabPose.grabAcross(toe), 0f);
		}
		for (Trick heel : new Trick[]{Trick.MELON, Trick.METHOD, Trick.STALEFISH})
		{
			assertEquals(heel.toString(), GrabPose.EDGE, GrabPose.grabAcross(heel), 0f);
		}
		// a tweak holds the same spot as its grab
		assertEquals(GrabPose.grabAlong(Trick.TAILGRAB), GrabPose.grabAlong(Trick.TAILBONE), 0f);
		assertEquals(GrabPose.grabBoardY(Trick.NOSEGRAB), GrabPose.grabBoardY(Trick.NOSEBONE), 0f);
	}

	@Test
	public void onlyGrabsReach()
	{
		Set<Trick> reaching = new HashSet<>();
		for (Trick t : Trick.values())
		{
			if (GrabPose.reaches(t))
			{
				reaching.add(t);
				assertEquals(t.toString(), TrickKind.GRAB, t.kind);
			}
		}
		assertTrue(reaching.contains(Trick.INDY));
		assertTrue(reaching.contains(Trick.CRAIL_TWEAK));
		assertTrue(!GrabPose.reaches(null));
	}

	@Test
	public void aGhostsGrabUsesTheUsualHand()
	{
		assertTrue(GrabPose.usualLeftHand(Trick.MELON));
		assertTrue(GrabPose.usualLeftHand(Trick.METHOD));
		assertTrue(GrabPose.usualLeftHand(Trick.NOSEGRAB));
		assertTrue(!GrabPose.usualLeftHand(Trick.INDY));
		assertTrue(!GrabPose.usualLeftHand(Trick.CRAIL));
	}

	@Test
	public void everyGrabKeyAndAimIsHeldWithTheGrabsOwnHand()
	{
		// the keys' unaimed grabs (Q an Indy, E a Melon) are not crossed over to the key's hand
		assertTrue(!GrabPose.leftHand(Grabs.pick(true, null), 1));
		assertTrue(GrabPose.leftHand(Grabs.pick(false, null), -1));
		for (int key : new int[]{1, -1, 0})
		{
			assertTrue(GrabPose.leftHand(Trick.MUTE, key));
			assertTrue(GrabPose.leftHand(Trick.JAPAN, key));
			assertTrue(GrabPose.leftHand(Trick.NOSEBONE, key));
			assertTrue(!GrabPose.leftHand(Trick.TWEAKED_STALEFISH, key));
			assertTrue(!GrabPose.leftHand(Trick.CRAIL_TWEAK, key));
		}
		// a tailgrab is either hand's: the key's, the back hand when not known
		assertTrue(GrabPose.leftHand(Trick.TAILGRAB, 1));
		assertTrue(!GrabPose.leftHand(Trick.TAILBONE, -1));
		assertTrue(!GrabPose.leftHand(Trick.TAILGRAB, 0));
		// every aimed pick already matches its hand
		for (Grabs.Aim aim : new Grabs.Aim[]{Grabs.Aim.NOSE, Grabs.Aim.TOE, Grabs.Aim.HEEL, Grabs.Aim.TAIL})
		{
			assertEquals(aim + " left", true, GrabPose.leftHand(Grabs.pick(true, aim), 1));
			assertEquals(aim + " right", false, GrabPose.leftHand(Grabs.pick(false, aim), -1));
		}
	}

	/**
	 * The tweak brings the grabbed part of the board up to the hand: the spot, as drawn with the tweak's pitch (and
	 * the deck lift that pitch gives the body), is higher relative to the soles than on a flat board. The drawn
	 * pitch turns the board's +z end up, and a spot along the board toward the nose sits at board -z for regular
	 * stance (GrabReach), so a nose-end grab pitches negative.
	 */
	@Test
	public void theTweakLiftsTheGrabbedEndOfTheBoardTowardTheHand()
	{
		for (Trick t : Trick.values())
		{
			float pitch = GrabPose.boardPitch(t);
			if (!GrabPose.reaches(t) || pitch == 0f)
			{
				continue;
			}
			float[] flat = new float[3];
			float[] tweaked = new float[3];
			GrabReach.target(GrabPose.grabAlong(t), GrabPose.grabAcross(t), GrabPose.grabBoardY(t), 1f, 0f, 0f, 0f,
				flat);
			GrabReach.target(GrabPose.grabAlong(t), GrabPose.grabAcross(t), GrabPose.grabBoardY(t), 1f, 0f, pitch,
				BoardPlacement.deckLift(pitch), tweaked);
			// y down: the grabbed spot comes up toward the body
			assertTrue(t + ": spot y " + tweaked[1] + " vs flat " + flat[1], tweaked[1] < flat[1] - 1f);
		}
	}
}
