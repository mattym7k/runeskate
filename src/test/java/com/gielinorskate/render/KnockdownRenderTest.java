package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The knocked-off skater as drawn: the get-up animation squeezed into its time, the body pose, the blend. */
public class KnockdownRenderTest
{
	private static KnockdownPose pose(KnockdownPose.Stage stage, float progress, float x, float angle)
	{
		return new KnockdownPose(stage, progress, x, 0f, 0f, 0f, angle, 0.2f, 100f, 0f, 0f, 0.5f, 3f, 0.1f);
	}

	@Test
	public void theGetUpAnimationIsScrubbedThroughItsFramesByProgress()
	{
		int[] lengths = {10, 10, 20, 10};
		assertEquals(0, AnimationScrub.frameAt(0f, lengths));
		assertEquals(1, AnimationScrub.frameAt(0.25f, lengths));
		assertEquals(2, AnimationScrub.frameAt(0.5f, lengths));
		assertEquals(2, AnimationScrub.frameAt(0.7f, lengths));
		assertEquals(3, AnimationScrub.frameAt(0.8f, lengths));
		// the end holds the last frame
		assertEquals(3, AnimationScrub.frameAt(1f, lengths));
		assertEquals(3, AnimationScrub.frameAt(5f, lengths));
		assertEquals(0, AnimationScrub.frameAt(-1f, lengths));
	}

	@Test
	public void anUnusableAnimationIsLeftAlone()
	{
		assertEquals(-1, AnimationScrub.frameAt(0.5f, null));
		assertEquals(-1, AnimationScrub.frameAt(0.5f, new int[0]));
		assertEquals(-1, AnimationScrub.frameAt(Float.NaN, new int[]{1, 2}));
		// zero-length frames count as one tick
		assertEquals(1, AnimationScrub.frameAt(0.6f, new int[]{0, 0}));
	}

	@Test
	public void inTheAirTheBodyTumblesProcedurally()
	{
		BodyPose out = new BodyPose();
		KnockdownBody.write(pose(KnockdownPose.Stage.AIR, 0f, 0f, 2f), false, out);
		assertEquals(2f, out.flip, 0f);
		assertEquals(0.2f, out.roll, 0f);
		assertTrue("tucked", out.legScale < 1f);
		assertEquals(BoardPlacement.puppetFlipPivotY(), out.flipPivotY, 0f);
		// the animation flag only matters once down
		KnockdownBody.write(pose(KnockdownPose.Stage.AIR, 0f, 0f, 2f), true, out);
		assertEquals(2f, out.flip, 0f);
	}

	@Test
	public void withAnAnimationPlayingTheBodyIsNotTurnedAsWell()
	{
		BodyPose out = new BodyPose();
		KnockdownBody.write(pose(KnockdownPose.Stage.LIE, 0.5f, 0f, 1.57f), true, out);
		assertTrue(out.isNeutral());
		KnockdownBody.write(pose(KnockdownPose.Stage.GET_UP, 0.5f, 0f, 1f), true, out);
		assertTrue(out.isNeutral());
	}

	@Test
	public void proceduralLyingAndGettingUpFollowTheAngle()
	{
		BodyPose out = new BodyPose();
		KnockdownBody.write(pose(KnockdownPose.Stage.LIE, 0.5f, 0f, 1.57f), false, out);
		assertEquals(1.57f, out.flip, 0f);
		assertEquals(0f, out.roll, 0f);
		KnockdownBody.write(pose(KnockdownPose.Stage.GET_UP, 0.5f, 0f, 0.8f), false, out);
		assertEquals(0.8f, out.flip, 0f);
		assertTrue("knees bent rising", out.legScale < 1f);
	}

	@Test
	public void posesBlendBetweenSteps()
	{
		KnockdownPose a = pose(KnockdownPose.Stage.AIR, 0f, 0f, 1f);
		KnockdownPose b = pose(KnockdownPose.Stage.AIR, 0f, 20f, 2f);
		KnockdownPose m = KnockdownPose.lerp(a, b, 0.5f);
		assertEquals(10f, m.bodyX, 1e-4f);
		assertEquals(1.5f, m.bodyAngle, 1e-4f);
		assertSame(b, KnockdownPose.lerp(null, b, 0.5f));
		// a jump (a skip) is not blended
		KnockdownPose far = pose(KnockdownPose.Stage.AIR, 0f, 500f, 2f);
		assertSame(far, KnockdownPose.lerp(a, far, 0.5f));
	}

	@Test
	public void theBoardIsRaisedWhenUpsideDown()
	{
		assertEquals(0f, KnockdownPose.boardLift(0f), 1e-4f);
		assertEquals(BoardGeometry.BOARD_TOP, KnockdownPose.boardLift((float) Math.PI), 1e-3f);
		assertEquals(0f, KnockdownPose.boardLift((float) (2 * Math.PI)), 1e-3f);
	}
}
