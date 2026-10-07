package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

/**
 * Every grab, end to end on the 200-unit test body: the rig's pose held in the air, the board drawn as the renderer
 * draws it (the grab's tweak, the lift), then the mesh deformed. The hand ends on its spot of the board, the feet on
 * the lifted board, the knees up in front of the chest and the free arm out.
 */
public class GrabBodyTest
{
	private static final float DT = 1 / 60f;
	private static final Trick[] GRABS = {Trick.INDY, Trick.MELON, Trick.MUTE, Trick.STALEFISH, Trick.NOSEGRAB,
		Trick.TAILGRAB, Trick.CRAIL, Trick.TWEAKED_INDY, Trick.METHOD, Trick.JAPAN, Trick.TWEAKED_STALEFISH,
		Trick.NOSEBONE, Trick.TAILBONE, Trick.CRAIL_TWEAK};
	/** The grab keys: Q (left), E (right), and not known (a party ghost). */
	private static final int[] KEYS = {1, -1, 0};
	/** How close (units) the hand must end to its spot on the board. */
	private static final float HAND_TOLERANCE = 6f;

	/** The pose the rig holds {@code grab} in after half a second in the air, with the board as drawn. */
	private static BodyPose held(Trick grab, int key)
	{
		SkaterPoseRig rig = new SkaterPoseRig();
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		s.state = SkaterState.AIRBORNE;
		s.popped = true;
		rig.update(s, DT);
		s.popped = false;
		s.hold = grab;
		s.grabHand = key;
		for (int i = 0; i < 30; i++)
		{
			rig.update(s, DT);
		}
		BodyPose p = new BodyPose();
		rig.writeTo(p);
		drawBoard(p, grab);
		return p;
	}

	/** What the renderer writes: the board's drawn roll, pitch, deck lift and the grab's lift. */
	private static void drawBoard(BodyPose p, Trick grab)
	{
		p.boardRoll = GrabPose.boardRoll(grab);
		p.boardPitch = GrabPose.boardPitch(grab);
		p.deckLift = Math.round(BoardPlacement.deckLift(p.boardPitch));
		p.boardLift = BoardPlacement.grabLift(p.feetLift, false);
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

	private static float distance(Humanoid b, int i, float[] t)
	{
		float dx = b.xs[i] - t[0];
		float dy = b.ys[i] - t[1];
		float dz = b.zs[i] - t[2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static Humanoid deformed(BodyPose p)
	{
		Humanoid b = new Humanoid();
		MeshDeformer.deform(b.xs, b.ys, b.zs, b.n, p);
		return b;
	}

	@Test
	public void everyGrabsHandEndsOnItsSpotOfTheBoard()
	{
		for (Trick grab : GRABS)
		{
			for (int key : KEYS)
			{
				BodyPose p = held(grab, key);
				assertEquals(grab + " arm", 1f, p.armWeight, 0.01f);
				float d = distance(deformed(p), hand(p.armSide), target(p));
				assertTrue(grab + " key " + key + ": hand " + d + " from its spot", d < HAND_TOLERANCE);
			}
		}
	}

	@Test
	public void eachGrabIsHeldWithItsOwnHand()
	{
		// front (left, +x) hand: melon, mute, nosegrab; back (right): indy, stalefish, crail; tailgrab: the key's
		Trick[] front = {Trick.MELON, Trick.METHOD, Trick.MUTE, Trick.JAPAN, Trick.NOSEGRAB, Trick.NOSEBONE};
		Trick[] back = {Trick.INDY, Trick.TWEAKED_INDY, Trick.STALEFISH, Trick.TWEAKED_STALEFISH, Trick.CRAIL,
			Trick.CRAIL_TWEAK};
		for (int key : KEYS)
		{
			for (Trick g : front)
			{
				assertEquals(g + " key " + key, 1f, held(g, key).armSide, 0f);
			}
			for (Trick g : back)
			{
				assertEquals(g + " key " + key, -1f, held(g, key).armSide, 0f);
			}
		}
		assertEquals(1f, held(Trick.TAILGRAB, 1).armSide, 0f);
		assertEquals(-1f, held(Trick.TAILGRAB, -1).armSide, 0f);
		assertEquals(-1f, held(Trick.TAILGRAB, 0).armSide, 0f);
	}

	@Test
	public void anEdgeGrabsHandStaysOnItsOwnSideOfTheBody()
	{
		// the hand never crosses the centre line to the middle of the board, in front of the crotch (a Q Indy
		// held with the front hand ended 2.3 units past it)
		for (Trick grab : new Trick[]{Trick.INDY, Trick.MELON, Trick.MUTE, Trick.STALEFISH, Trick.TWEAKED_INDY,
			Trick.METHOD})
		{
			for (int key : KEYS)
			{
				BodyPose p = held(grab, key);
				Humanoid b = deformed(p);
				float x = b.xs[hand(p.armSide)] * p.armSide;
				assertTrue(grab + " key " + key + ": hand " + x + " from the centre line", x > 4f);
			}
		}
	}

	@Test
	public void theFeetStayOnTheLiftedBoard()
	{
		for (Trick grab : GRABS)
		{
			BodyPose p = held(grab, 0);
			assertEquals(grab + " lift", SkaterPoseRig.GRAB_LIFT, p.boardLift, 1f);
			Humanoid rest = new Humanoid();
			Humanoid b = deformed(p);
			// the middle of the deck top as the board is drawn (its tweak, the lift), in the puppet's space: board
			// (x, y, z) = (puppet z, puppet y - 20 - deckLift + lift, -puppet x)
			float[] deck = BoardPlacement.pose(new float[]{0f, -BoardGeometry.BOARD_TOP, 0f}, p.boardRoll,
				p.boardPitch);
			float deckY = deck[1] + BoardGeometry.BOARD_TOP + BoardPlacement.FOOT_CLEARANCE + p.deckLift - p.boardLift;
			float sum = 0f;
			int count = 0;
			for (int i = 0; i < rest.n; i++)
			{
				if (rest.ys[i] == 0f)
				{
					sum += b.ys[i];
					count++;
					// over the deck, not off its toe or heel edge
					assertTrue(grab + " sole " + i + " z " + b.zs[i], Math.abs(b.zs[i]) < 6f);
				}
			}
			// the soles stand on the middle of the deck (the tweak's tilt under each foot is as it always was)
			assertEquals(grab + " soles above the deck", BoardPlacement.FOOT_CLEARANCE, deckY - sum / count, 1.5f);
		}
	}

	@Test
	public void theFeetFollowTheLiftAsItEasesInAndOut()
	{
		for (float lift = 0f; lift <= 30f; lift += 2.5f)
		{
			for (float fold = 0f; fold <= 1f; fold += 0.25f)
			{
				BodyPose p = new BodyPose();
				p.legScale = 0.55f;
				p.torsoBend = 0.6f;
				p.kneeFold = fold;
				p.armWeight = fold;
				p.boardLift = lift;
				Humanoid rest = new Humanoid();
				Humanoid b = deformed(p);
				for (int i = 0; i < rest.n; i++)
				{
					if (rest.ys[i] == 0f)
					{
						assertEquals("fold " + fold + " lift " + lift, -lift, b.ys[i], 0.5f);
					}
				}
			}
		}
	}

	@Test
	public void theKneesComeUpInFrontOfTheChest()
	{
		for (Trick grab : GRABS)
		{
			BodyPose p = held(grab, 0);
			Humanoid rest = new Humanoid();
			Humanoid b = deformed(p);
			float hipY = -MeshDeformer.hipHeight(-200f) * p.legScale;
			float frontmost = Float.MAX_VALUE;
			float kneeY = 0f;
			for (int i = 0; i < rest.n; i++)
			{
				// the legs, halfway down
				if (Math.abs(rest.ys[i] + 50f) < 0.1f && Math.abs(rest.xs[i]) <= 12f && b.zs[i] < frontmost)
				{
					frontmost = b.zs[i];
					kneeY = b.ys[i];
				}
			}
			assertTrue(grab + " knee z " + frontmost, frontmost < -25f);
			// up near the hips' height, above the lifted feet (a toe-side roll tips the knees down a little)
			assertTrue(grab + " knee y " + kneeY + " hips " + hipY, kneeY < -p.boardLift - 4f && kneeY > hipY - 15f);
		}
	}

	@Test
	public void theFreeArmSwingsOutForBalance()
	{
		for (Trick grab : GRABS)
		{
			BodyPose p = held(grab, 0);
			float side = -p.armSide;
			ArmLocator a = new ArmLocator();
			Humanoid rest = new Humanoid();
			assertTrue(a.locate(rest.xs, rest.ys, rest.zs, rest.n, side));
			Humanoid b = deformed(p);
			int h = a.handIndex;
			// out along the board well past where it hung on its own side, and not hanging below the hips
			float hipY = -MeshDeformer.hipHeight(-200f) * p.legScale;
			assertTrue(grab + " free hand x " + b.xs[h], b.xs[h] * side > Humanoid.ARM_OUTER + 15f);
			assertTrue(grab + " free hand y " + b.ys[h] + " hips " + hipY, b.ys[h] < hipY - 20f);
		}
	}


	@Test
	public void outsideAGrabTheLegsAndHandsSquashAsBefore()
	{
		// no knee fold, no lift, no reach: every point below the hips is the plain squash, the hands included
		BodyPose p = new BodyPose();
		p.legScale = 0.62f;
		p.torsoBend = 0.34f;
		Humanoid rest = new Humanoid();
		Humanoid b = deformed(p);
		float hip = MeshDeformer.hipHeight(-200f);
		for (int i = 0; i < rest.n; i++)
		{
			if (rest.ys[i] > -hip)
			{
				assertEquals(rest.ys[i] * 0.62f, b.ys[i], 0f);
				assertEquals(rest.xs[i], b.xs[i], 0f);
				assertEquals(rest.zs[i], b.zs[i], 0f);
			}
		}
		assertEquals(0, BoardPlacement.grabLift(0f, false));
	}

	@Test
	public void brokenGrabInputsNeverExplodeTheMesh()
	{
		float[] bad = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -50f, 1e6f};
		for (float v : bad)
		{
			BodyPose p = held(Trick.INDY, 0);
			p.kneeFold = v;
			p.boardLift = v;
			Humanoid b = deformed(p);
			for (int i = 0; i < b.n; i++)
			{
				for (float c : new float[]{b.xs[i], b.ys[i], b.zs[i]})
				{
					assertTrue(v + ": vertex " + i + " " + c, !Float.isNaN(c) && Math.abs(c) < 400f);
				}
			}
			int lift = BoardPlacement.grabLift(v, false);
			assertTrue(v + ": lift " + lift, lift >= 0 && lift <= BoardPlacement.MAX_GRAB_LIFT);
		}
	}
}
