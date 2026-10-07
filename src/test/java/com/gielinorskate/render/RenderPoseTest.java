package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

public class RenderPoseTest
{
	private static RenderPose pose(float x, float y, float h, float heading, float roll, float yaw, float pitch,
		SkaterState state)
	{
		return new RenderPose(x, y, h, heading, heading, roll, yaw, pitch, state, null);
	}

	@Test
	public void lerpsPositionLinearly()
	{
		RenderPose a = pose(0f, 100f, 10f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose b = pose(30f, 130f, 20f, 0f, 0f, 0f, 0.2f, SkaterState.ROLLING);
		RenderPose m = RenderPose.lerp(a, b, 0.25f);
		assertEquals(7.5f, m.x, 1e-4f);
		assertEquals(107.5f, m.y, 1e-4f);
		assertEquals(12.5f, m.h, 1e-4f);
		assertEquals(0.05f, m.boardPitch, 1e-5f);
	}

	@Test
	public void alphaEndsReturnTheSnapshots()
	{
		RenderPose a = pose(0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose b = pose(10f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		assertEquals(0f, RenderPose.lerp(a, b, 0f).x, 0f);
		assertEquals(10f, RenderPose.lerp(a, b, 1f).x, 0f);
		// out-of-range alpha is clamped, never extrapolated
		assertEquals(10f, RenderPose.lerp(a, b, 1.7f).x, 0f);
		assertEquals(0f, RenderPose.lerp(a, b, -0.5f).x, 0f);
	}

	@Test
	public void headingTakesTheShortWayAcrossPi()
	{
		RenderPose a = pose(0f, 0f, 0f, Angles.PI - 0.1f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose b = pose(0f, 0f, 0f, -Angles.PI + 0.1f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose m = RenderPose.lerp(a, b, 0.5f);
		assertEquals(Angles.PI, Math.abs(m.heading), 1e-4f);
		assertEquals(Angles.PI, Math.abs(m.cameraHeading), 1e-4f);
	}

	@Test
	public void flipRollIsLerpedUnwrappedBeyondOneTurn()
	{
		// mid double kickflip: 6.0 -> 6.8 rad must not wrap back through zero
		RenderPose a = pose(0f, 0f, 50f, 0f, 6.0f, 0f, 0f, SkaterState.AIRBORNE);
		RenderPose b = pose(0f, 0f, 60f, 0f, 6.8f, 0f, 0f, SkaterState.AIRBORNE);
		assertEquals(6.4f, RenderPose.lerp(a, b, 0.5f).boardRoll, 1e-4f);
	}

	@Test
	public void flipCompletionSnapToZeroRotatesForwardNotBack()
	{
		// a caught kickflip at 94% (5.9 rad) whose roll becomes 0 on landing: the short way is +0.38
		float caught = 0.94f * Angles.TWO_PI;
		RenderPose a = pose(0f, 0f, 5f, 0f, caught, 0f, 0f, SkaterState.AIRBORNE);
		RenderPose b = pose(0f, 30f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose m = RenderPose.lerp(a, b, 0.5f);
		float expected = caught + 0.5f * (Angles.TWO_PI - caught);
		assertEquals(0f, Angles.wrap(m.boardRoll - expected), 1e-4f);
		// a landing is continuous: position still lerps
		assertEquals(15f, m.y, 1e-4f);
	}

	@Test
	public void halfTurnYawSnapIsNotLerped()
	{
		// a shove-it at 180 degrees whose yaw becomes 0: either way round would be a fake spin
		RenderPose a = pose(0f, 0f, 0f, 0f, 0f, Angles.PI, 0f, SkaterState.AIRBORNE);
		RenderPose b = pose(0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		assertEquals(0f, RenderPose.lerp(a, b, 0.3f).boardYaw, 0f);
	}

	@Test
	public void grindLockIsNotLerped()
	{
		RenderPose a = pose(0f, 0f, 80f, 0f, 0f, 0f, 0f, SkaterState.AIRBORNE);
		RenderPose b = pose(40f, 0f, 60f, 0.5f, 0f, 0f, 0f, SkaterState.GRINDING);
		assertSame(b, RenderPose.lerp(a, b, 0.2f));
	}

	@Test
	public void leavingARailIsLerped()
	{
		RenderPose a = pose(0f, 0f, 60f, 0f, 0f, 0f, 0f, SkaterState.GRINDING);
		RenderPose b = pose(0f, 20f, 64f, 0f, 0f, 0f, 0f, SkaterState.AIRBORNE);
		assertEquals(10f, RenderPose.lerp(a, b, 0.5f).y, 1e-4f);
	}

	@Test
	public void bailAndResetAreNotLerped()
	{
		RenderPose rolling = pose(0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose bailed = pose(20f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.BAILED);
		assertSame(bailed, RenderPose.lerp(rolling, bailed, 0.5f));
		assertSame(rolling, RenderPose.lerp(bailed, rolling, 0.5f));
	}

	@Test
	public void teleportIsNotLerped()
	{
		RenderPose a = pose(0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose b = pose(500f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		assertSame(b, RenderPose.lerp(a, b, 0.5f));
	}

	@Test
	public void holdAndStateComeFromTheNewerSnapshot()
	{
		RenderPose a = new RenderPose(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null);
		RenderPose b = new RenderPose(0f, 4f, 0f, 0f, 0f, 0f, 0f, 0.25f, SkaterState.MANUAL, Trick.MANUAL);
		RenderPose m = RenderPose.lerp(a, b, 0.5f);
		assertEquals(SkaterState.MANUAL, m.state);
		assertEquals(Trick.MANUAL, m.hold);
	}

	@Test
	public void constantSpeedGivesConstantPerFrameDisplacementAt144Fps()
	{
		// 1500 u/s with 20 ms physics steps rendered at 144 fps: every frame moves 1500/144 units (+-1)
		float step = 0.02f;
		float frame = 1f / 144f;
		float speed = 1500f;
		float simTime = 0f;
		float accumulator = 0f;
		RenderPose prev = pose(0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
		RenderPose cur = prev;
		float lastY = Float.NaN;
		for (int i = 0; i < 300; i++)
		{
			accumulator += frame;
			while (accumulator >= step)
			{
				prev = cur;
				simTime += step;
				cur = pose(0f, speed * simTime, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING);
				accumulator -= step;
			}
			float y = RenderPose.lerp(prev, cur, accumulator / step).y;
			// from the frame after the first step (before it there is nothing to move between)
			if (!Float.isNaN(lastY))
			{
				assertEquals(speed * frame, y - lastY, 1f);
			}
			lastY = simTime > 0f ? y : Float.NaN;
		}
	}
}
