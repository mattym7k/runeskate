package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MeshDeformerTest
{
	/** A stick figure: soles (0), knee (-50), hip line (-90), chest (-150, 10 forward), head top (-200). */
	private static float[][] figure()
	{
		return new float[][]{
			{0f, 0f, 0f, 0f, 20f},
			{0f, -50f, -90f, -150f, -200f},
			{0f, 0f, 0f, -10f, 0f}};
	}

	private static float[][] deformed(BodyPose p)
	{
		float[][] f = figure();
		MeshDeformer.deform(f[0], f[1], f[2], f[0].length, p);
		return f;
	}

	@Test
	public void neutralPoseChangesNothing()
	{
		float[][] f = deformed(new BodyPose());
		float[][] g = figure();
		for (int a = 0; a < 3; a++)
		{
			for (int i = 0; i < g[a].length; i++)
			{
				assertEquals(g[a][i], f[a][i], 1e-4f);
			}
		}
	}

	@Test
	public void hipHeightIsAFractionOfTheModelClamped()
	{
		// 0.45 * 200 = 90
		assertEquals(90f, MeshDeformer.hipHeight(-200f), 1e-4f);
		assertEquals(Tuning.MIN_HIP, MeshDeformer.hipHeight(-20f), 0f);
		assertEquals(Tuning.MAX_HIP, MeshDeformer.hipHeight(-1000f), 0f);
		assertEquals(90f, MeshDeformer.hipHeight(0f), 1e-4f);
	}

	@Test
	public void crouchBendsTheLegsAndDropsTheUpperBodyRigidly()
	{
		BodyPose p = new BodyPose();
		p.legScale = 0.8f;
		float[][] f = deformed(p);
		// hip 90: the soles stay, the knee squashes to -50 * 0.8, everything above drops 90 * 0.2 = 18
		assertEquals(0f, f[1][0], 1e-4f);
		assertEquals(-40f, f[1][1], 1e-4f);
		assertEquals(-72f, f[1][2], 1e-4f);
		assertEquals(-182f, f[1][4], 1e-4f);
		assertEquals(20f, f[0][4], 1e-4f);
	}

	@Test
	public void squashIsContinuousAcrossTheHipLine()
	{
		BodyPose p = new BodyPose();
		p.legScale = 0.7f;
		p.torsoBend = 0.3f;
		float[] xs = {0f, 0f};
		float[] ys = {-89.99f, -90.01f};
		float[] zs = {5f, 5f};
		MeshDeformer.deform(xs, ys, zs, 2, p);
		assertEquals(ys[0], ys[1], 0.05f);
		assertEquals(zs[0], zs[1], 0.05f);
	}

	@Test
	public void torsoBendFoldsTheHeadTowardTheChest()
	{
		BodyPose p = new BodyPose();
		p.torsoBend = 0.4f;
		float[][] f = deformed(p);
		// the body faces -z: the head goes forward (-z) and down (y grows)
		assertTrue(f[2][4] < -10f);
		assertTrue(f[1][4] > -200f);
		// legs untouched
		assertEquals(-50f, f[1][1], 1e-4f);
		assertEquals(0f, f[2][1], 1e-4f);
	}

	@Test
	public void negativeRollLeansTheHeadTowardTheChestAboutTheFeet()
	{
		BodyPose p = new BodyPose();
		p.roll = -0.3f;
		float[][] f = deformed(p);
		assertEquals(0f, f[1][0], 1e-4f);
		assertEquals(0f, f[2][0], 1e-4f);
		// head (0, -200, 0) -> z = 200 sin(-0.3) = -59.1
		assertEquals(200f * (float) Math.sin(-0.3), f[2][4], 1e-2f);
		assertEquals(-200f * (float) Math.cos(0.3), f[1][4], 1e-2f);
	}

	@Test
	public void pitchTurnsAboutThePivotLikeTheBoard()
	{
		BodyPose p = new BodyPose();
		p.pitch = 0.25f;
		p.pivotX = 30f;
		float[] xs = {30f, 30f};
		float[] ys = {0f, -100f};
		float[] zs = {0f, 0f};
		MeshDeformer.deform(xs, ys, zs, 2, p);
		assertEquals(30f, xs[0], 1e-4f);
		assertEquals(0f, ys[0], 1e-4f);
		// a point above the pivot tips toward +x for a positive pitch
		assertEquals(30f + 100f * (float) Math.sin(0.25), xs[1], 1e-3f);
	}

	/**
	 * The body turns with the board: the board's model space is (board x, y, board z) = (puppet z, y,
	 * -puppet x) (see MeshDeformer), so rolling and pitching a point by the board's own rotation and mapping
	 * it back must match the deformer.
	 */
	@Test
	public void rollAndPitchMatchTheBoardRotationSense()
	{
		float px = 12f;
		float py = -80f;
		float pz = 7f;
		float roll = 0.35f;
		float pitch = -0.2f;

		float[] board = BoardGeometry.rotate(new float[]{pz, py, -px}, roll, 0f);
		BodyPose r = new BodyPose();
		r.roll = roll;
		float[] xs = {px};
		float[] ys = {py};
		float[] zs = {pz};
		MeshDeformer.deform(xs, ys, zs, 1, r);
		assertEquals(board[0], zs[0], 1e-3f);
		assertEquals(board[1], ys[0], 1e-3f);
		assertEquals(board[2], -xs[0], 1e-3f);

		board = BoardGeometry.rotate(new float[]{pz, py, -px}, 0f, pitch);
		BodyPose q = new BodyPose();
		q.pitch = pitch;
		xs[0] = px;
		ys[0] = py;
		zs[0] = pz;
		MeshDeformer.deform(xs, ys, zs, 1, q);
		assertEquals(board[0], zs[0], 1e-3f);
		assertEquals(board[1], ys[0], 1e-3f);
		assertEquals(board[2], -xs[0], 1e-3f);
	}
	/**
	 * Two legs side by side along x (the board axis) at x = -12 (rear, for a regular rider going +x) and
	 * x = +12 (front), a vertex every 10 units from the soles up to the hip line at -90, then a torso up to
	 * the head top at -200 (model height 200, so hip 90).
	 */
	private static float[][] twoLegs()
	{
		int legVerts = 10;
		int torso = 11;
		float[] xs = new float[2 * legVerts + torso];
		float[] ys = new float[xs.length];
		float[] zs = new float[xs.length];
		int i = 0;
		for (float side : new float[]{-12f, 12f})
		{
			for (int k = 0; k < legVerts; k++)
			{
				xs[i] = side;
				ys[i] = -10f * k;
				i++;
			}
		}
		for (int k = 0; k < torso; k++)
		{
			xs[i] = (k % 3 - 1) * 12f;
			ys[i] = -100f - 10f * k;
			i++;
		}
		return new float[][]{xs, ys, zs};
	}

	private static BodyPose push(float footX, float footY, float footZ)
	{
		BodyPose p = new BodyPose();
		p.legWeight = 1f;
		p.legSide = -1f;
		p.footX = footX;
		p.footY = footY;
		p.footZ = footZ;
		return p;
	}

	@Test
	public void pushMovesOnlyTheRearLegAndPutsTheFootOnTheTarget()
	{
		float[][] f = twoLegs();
		float[][] g = twoLegs();
		// 30 back, 20 below the soles (the ground), 15 toward the chest
		MeshDeformer.deform(f[0], f[1], f[2], f[0].length, push(-30f, 20f, -15f));
		// rear sole: (-12, 0, 0) -> (-42, 20, -15); reach sqrt(30^2 + 110^2) = 114 of a 90 leg (stretch 1.27)
		assertEquals(-42f, f[0][0], 0.05f);
		assertEquals(20f, f[1][0], 0.05f);
		assertEquals(-15f, f[2][0], 0.05f);
		// the front leg and the torso are untouched (no crouch in this pose)
		for (int i = 10; i < g[0].length; i++)
		{
			assertEquals(g[0][i], f[0][i], 1e-4f);
			assertEquals(g[1][i], f[1][i], 1e-4f);
			assertEquals(g[2][i], f[2][i], 1e-4f);
		}
		// the rear foot really moved, the rear hip vertex did not
		assertTrue(Math.abs(f[0][0] - g[0][0]) > 20f);
		assertEquals(g[0][9], f[0][9], 1e-3f);
		assertEquals(g[1][9], f[1][9], 1e-3f);
	}

	@Test
	public void pushedLegHasNoTearAtTheHip()
	{
		BodyPose p = push(-45f, 20f, -15f);
		p.legScale = 0.88f;
		// plus a front-leg vertex and the head top (model height 200: hip 90)
		float[] xs = {-12f, -12f, -12f, 12f, 0f};
		float[] ys = {-89.99f, -90.01f, -85f, -85f, -200f};
		float[] zs = {0f, 0f, 0f, 0f, 0f};
		MeshDeformer.deform(xs, ys, zs, 5, p);
		assertEquals(xs[0], xs[1], 0.05f);
		assertEquals(ys[0], ys[1], 0.05f);
		assertEquals(zs[0], zs[1], 0.05f);
		// 5 below the hip barely moves: the leg fades in below the hip line
		assertEquals(-12f, xs[2], 1.5f);
		assertEquals(-85f * 0.88f, ys[2], 1.5f);
	}

	@Test
	public void pushedLegFadesInAtTheBodyCentrePlane()
	{
		// a vertex right on the centre plane between the legs stays with the plain crouch
		float[][] f = twoLegs();
		float[] xs = new float[f[0].length + 1];
		float[] ys = new float[xs.length];
		float[] zs = new float[xs.length];
		System.arraycopy(f[0], 0, xs, 0, f[0].length);
		System.arraycopy(f[1], 0, ys, 0, f[1].length);
		xs[xs.length - 1] = 0f;
		ys[ys.length - 1] = -60f;
		MeshDeformer.deform(xs, ys, zs, xs.length, push(-30f, 20f, -15f));
		assertEquals(0f, xs[xs.length - 1], 1e-4f);
		assertEquals(-60f, ys[ys.length - 1], 1e-4f);
	}

	@Test
	public void liftedFootBendsTheKneeForward()
	{
		float[][] f = twoLegs();
		// foot 20 above the soles straight under the hip: 70 of a 90 leg, the knee comes forward (-z)
		MeshDeformer.deform(f[0], f[1], f[2], f[0].length, push(0f, -20f, 0f));
		assertEquals(-12f, f[0][0], 0.05f);
		assertEquals(-20f, f[1][0], 0.05f);
		assertEquals(0f, f[2][0], 0.05f);
		// knee vertex (rest y -40/-50 around the 45 knee) is forward of the leg line
		assertTrue(f[2][5] < -15f);
	}

	@Test
	public void zeroLegWeightIsThePlainCrouch()
	{
		float[][] f = twoLegs();
		BodyPose p = push(-30f, 20f, -15f);
		p.legWeight = 0f;
		p.legScale = 0.9f;
		MeshDeformer.deform(f[0], f[1], f[2], f[0].length, p);
		assertEquals(-12f, f[0][0], 1e-4f);
		assertEquals(0f, f[1][0], 1e-4f);
		assertEquals(-50f * 0.9f, f[1][5], 1e-3f);
	}

	@Test
	public void torsoLeanTipsTheHeadTowardPositiveXAboveTheHips()
	{
		float[][] f = twoLegs();
		BodyPose p = new BodyPose();
		p.torsoLean = 0.2f;
		MeshDeformer.deform(f[0], f[1], f[2], f[0].length, p);
		int head = f[0].length - 1;
		// head (0, -200): 110 above the hip line, tips toward +x by about 110 sin 0.2 = 22
		assertTrue(f[0][head] > 15f);
		assertEquals(0f, f[1][0], 1e-4f);
		assertEquals(-12f, f[0][0], 1e-4f);
	}

	@Test
	public void flipRotatesTheWholeMeshRigidlyAboutTheCentreOfMass()
	{
		float[][] f = twoLegs();
		float[][] g = twoLegs();
		BodyPose p = new BodyPose();
		p.flip = 2.1f;
		p.flipPivotY = -41f;
		int n = f[0].length;
		MeshDeformer.deform(f[0], f[1], f[2], n, p);
		// relative geometry kept: every pairwise distance is unchanged
		for (int i = 0; i < n; i++)
		{
			for (int j = i + 1; j < n; j++)
			{
				assertEquals(dist(g, i, j), dist(f, i, j), 1e-2f);
			}
			// and every vertex keeps its distance from the pivot (0, -41, z)
			assertEquals((float) Math.hypot(g[0][i], g[1][i] + 41f), (float) Math.hypot(f[0][i], f[1][i] + 41f), 1e-2f);
			assertEquals(g[2][i], f[2][i], 1e-4f);
		}
		// the pivot itself stays put; a point above it turns the board's pitch way (toward +x)
		float[] xs = {0f, 0f};
		float[] ys = {-41f, -91f};
		float[] zs = {0f, 0f};
		MeshDeformer.deform(xs, ys, zs, 2, p);
		assertEquals(0f, xs[0], 1e-3f);
		assertEquals(-41f, ys[0], 1e-3f);
		assertEquals(50f * (float) Math.sin(2.1), xs[1], 1e-2f);
		assertEquals(-41f - 50f * (float) Math.cos(2.1), ys[1], 1e-2f);
	}

	@Test
	public void flipIsContinuousPastAFullTurn()
	{
		BodyPose a = new BodyPose();
		a.flip = (float) (2 * Math.PI) - 0.001f;
		a.flipPivotY = -41f;
		BodyPose b = new BodyPose();
		b.flip = (float) (2 * Math.PI) + 0.001f;
		b.flipPivotY = -41f;
		float[][] f = twoLegs();
		float[][] g = twoLegs();
		MeshDeformer.deform(f[0], f[1], f[2], f[0].length, a);
		MeshDeformer.deform(g[0], g[1], g[2], g[0].length, b);
		for (int i = 0; i < f[0].length; i++)
		{
			assertEquals(f[0][i], g[0][i], 0.5f);
			assertEquals(f[1][i], g[1][i], 0.5f);
		}
	}

	private static float dist(float[][] m, int i, int j)
	{
		float dx = m[0][i] - m[0][j];
		float dy = m[1][i] - m[1][j];
		float dz = m[2][i] - m[2][j];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	// ---- grabs: the arm reaches for the board

	private static BodyPose grab(float weight, float side)
	{
		BodyPose p = new BodyPose();
		p.legScale = 0.5f;
		p.torsoBend = 0.7f;
		p.armWeight = weight;
		p.armSide = side;
		p.grabAlong = -6f;
		p.grabAcross = -13f;
		p.grabBoardY = -14f;
		return p;
	}

	private static float[] targetOf(BodyPose p)
	{
		float[] t = new float[3];
		BoardPlacement.grabTarget(p.grabAlong, p.grabAcross, p.grabBoardY, p.boardRoll, p.boardPitch, p.deckLift,
			t);
		return t;
	}

	private static Humanoid deformed(BodyPose p, Humanoid b)
	{
		MeshDeformer.deform(b.xs, b.ys, b.zs, b.n, p);
		return b;
	}

	private static int handIndex(Humanoid b, float side)
	{
		ArmLocator a = new ArmLocator();
		assertTrue(a.locate(b.xs, b.ys, b.zs, b.n, side));
		return a.handIndex;
	}

	private static float distance(Humanoid b, int i, float[] t)
	{
		return (float) Math.sqrt(Math.pow(b.xs[i] - t[0], 2) + Math.pow(b.ys[i] - t[1], 2) + Math.pow(b.zs[i] - t[2], 2));
	}

	@Test
	public void aGrabPutsTheHandOnTheBoardWhenItCanReach()
	{
		BodyPose p = grab(1f, -1f);
		Humanoid b = new Humanoid();
		int hand = handIndex(b, -1f);
		Humanoid plain = deformed(grab(0f, -1f), new Humanoid());
		deformed(p, b);
		float[] t = targetOf(p);
		float before = distance(plain, hand, t);
		float after = distance(b, hand, t);
		assertTrue("hand " + after + " from the board, was " + before, after < 0.25f * before);
		assertTrue("hand " + after + " from the board", after < 15f);
	}

	@Test
	public void onlyTheArmsMove()
	{
		Humanoid rest = new Humanoid();
		Humanoid plain = deformed(grab(0f, -1f), new Humanoid());
		Humanoid b = deformed(grab(1f, -1f), new Humanoid());
		for (int i = 0; i < b.n; i++)
		{
			if (!rest.isArm(i, -1f) && !rest.isArm(i, 1f))
			{
				assertEquals("vertex " + i, plain.xs[i], b.xs[i], 1.5f);
				assertEquals("vertex " + i, plain.ys[i], b.ys[i], 1.5f);
				assertEquals("vertex " + i, plain.zs[i], b.zs[i], 1.5f);
			}
		}
	}

	@Test
	public void theLeftHandGrabsWithTheLeftArmAndTheRightSwingsOut()
	{
		BodyPose p = grab(1f, 1f);
		Humanoid b = deformed(p, new Humanoid());
		float[] t = targetOf(p);
		int left = handIndex(new Humanoid(), 1f);
		int right = handIndex(new Humanoid(), -1f);
		assertTrue("left hand " + distance(b, left, t), distance(b, left, t) < 15f);
		// the free (right) hand goes out along the board on its own side, well away from the board
		assertTrue("right hand x " + b.xs[right], b.xs[right] < -Humanoid.ARM_OUTER - 20f);
		assertTrue("right hand " + distance(b, right, t), distance(b, right, t) > 60f);
	}

	@Test
	public void theReachEasesInWithTheArmWeight()
	{
		int hand = handIndex(new Humanoid(), -1f);
		float[] t = targetOf(grab(1f, -1f));
		float none = distance(deformed(grab(0f, -1f), new Humanoid()), hand, t);
		float half = distance(deformed(grab(0.5f, -1f), new Humanoid()), hand, t);
		float full = distance(deformed(grab(1f, -1f), new Humanoid()), hand, t);
		assertTrue(none > half && half > full);
	}

	@Test
	public void theHandFollowsTheBoardsTweak()
	{
		BodyPose p = grab(1f, -1f);
		p.boardRoll = 0.4f;
		Humanoid b = deformed(p, new Humanoid());
		int hand = handIndex(new Humanoid(), -1f);
		assertTrue(distance(b, hand, targetOf(p)) < 15f);
	}

	@Test
	public void aFlipTurnsTheReachingBodyLikeTheBoard()
	{
		BodyPose p = grab(1f, -1f);
		p.flip = 1.1f;
		p.flipPivotY = -41f;
		Humanoid flipped = deformed(p, new Humanoid());
		BodyPose q = grab(1f, -1f);
		Humanoid upright = deformed(q, new Humanoid());
		float c = (float) Math.cos(1.1);
		float s = (float) Math.sin(1.1);
		for (int i = 0; i < upright.n; i++)
		{
			float ry = upright.ys[i] + 41f;
			assertEquals(upright.xs[i] * c - ry * s, flipped.xs[i], 1e-2f);
			assertEquals(upright.xs[i] * s + ry * c - 41f, flipped.ys[i], 1e-2f);
			assertEquals(upright.zs[i], flipped.zs[i], 1e-2f);
		}
	}

	@Test
	public void noGrabPoseEverExplodesTheMesh()
	{
		java.util.Random r = new java.util.Random(7);
		for (int k = 0; k < 300; k++)
		{
			BodyPose p = new BodyPose();
			p.legScale = 0.5f + r.nextFloat() * 0.7f;
			p.torsoBend = r.nextFloat();
			p.torsoLean = r.nextFloat() - 0.5f;
			p.roll = r.nextFloat() - 0.5f;
			p.flip = (r.nextFloat() - 0.5f) * 12f;
			p.flipPivotY = -41f;
			p.armWeight = r.nextFloat();
			p.armSide = r.nextBoolean() ? 1f : -1f;
			p.grabAlong = (r.nextFloat() - 0.5f) * 90f;
			p.grabAcross = (r.nextFloat() - 0.5f) * 26f;
			p.grabBoardY = -14f;
			p.boardRoll = (r.nextFloat() - 0.5f) * 7f;
			p.boardPitch = (r.nextFloat() - 0.5f) * 1.2f;
			// a grab's knees-up: any fold, any lift (even past what the legs can fold to)
			p.kneeFold = r.nextFloat() * 1.2f;
			p.boardLift = r.nextFloat() * 60f;
			Humanoid b = deformed(p, new Humanoid());
			for (int i = 0; i < b.n; i++)
			{
				for (float v : new float[]{b.xs[i], b.ys[i], b.zs[i]})
				{
					assertTrue("pose " + k + " vertex " + i + ": " + v, !Float.isNaN(v) && Math.abs(v) < 400f);
				}
			}
		}
	}
}
