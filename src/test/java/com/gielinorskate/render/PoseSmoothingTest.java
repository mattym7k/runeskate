package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

public class PoseSmoothingTest
{
	private static final float PEAK = PoseSmoothing.POP_PITCH;

	@Test
	public void popIsFlatBeforeAndAfter()
	{
		assertEquals(0f, PoseSmoothing.popPitch(-0.01f, false), 0f);
		assertEquals(0f, PoseSmoothing.popPitch(0f, false), 1e-6f);
		assertEquals(0f, PoseSmoothing.popPitch(PoseSmoothing.POP_RISE + PoseSmoothing.POP_FALL, false), 1e-6f);
		assertEquals(0f, PoseSmoothing.popPitch(1f, false), 0f);
	}

	@Test
	public void popRisesOverFiftyMillisecondsWithoutAOneFrameJump()
	{
		assertEquals(0.05f, PoseSmoothing.POP_RISE, 0f);
		assertEquals(PEAK, PoseSmoothing.popPitch(0.05f, false), 1e-5f);
		// one 144 fps frame in: well below the peak
		float firstFrame = PoseSmoothing.popPitch(1f / 144f, false);
		assertTrue(firstFrame > 0f && firstFrame < 0.4f * PEAK);
		float last = 0f;
		for (float t = 0.005f; t <= 0.05f; t += 0.005f)
		{
			float v = PoseSmoothing.popPitch(t, false);
			assertTrue(v >= last);
			last = v;
		}
	}

	@Test
	public void popEasesBackOverTwoHundredMilliseconds()
	{
		assertEquals(0.2f, PoseSmoothing.POP_FALL, 0f);
		assertEquals(PEAK / 2f, PoseSmoothing.popPitch(0.05f + 0.1f, false), 1e-4f);
		float last = PEAK;
		for (float t = 0.06f; t < 0.25f; t += 0.01f)
		{
			float v = PoseSmoothing.popPitch(t, false);
			assertTrue(v <= last + 1e-6f);
			assertTrue(v > 0f);
			last = v;
		}
	}

	@Test
	public void nolliePopTipsTheNoseDown()
	{
		for (float t = 0.01f; t < 0.25f; t += 0.02f)
		{
			assertEquals(-PoseSmoothing.popPitch(t, false), PoseSmoothing.popPitch(t, true), 0f);
		}
	}

	@Test
	public void nollieTricksAreRecognised()
	{
		assertTrue(PoseSmoothing.isNollie(Trick.NOLLIE));
		assertTrue(PoseSmoothing.isNollie(Trick.NOLLIE_KICKFLIP));
		assertTrue(PoseSmoothing.isNollie(Trick.NOLLIE_HEELFLIP));
		assertFalse(PoseSmoothing.isNollie(Trick.OLLIE));
		assertFalse(PoseSmoothing.isNollie(Trick.KICKFLIP));
		assertFalse(PoseSmoothing.isNollie(null));
	}

	@Test
	public void approachIsExponentialWithTheGivenTimeConstant()
	{
		// after exactly one time constant 1 - 1/e of the gap is closed
		assertEquals(1f - (float) Math.exp(-1), PoseSmoothing.approach(0f, 1f, 0.08f, 0.08f), 1e-5f);
		// frame-rate independent: two half steps equal one whole step
		float half = PoseSmoothing.approach(PoseSmoothing.approach(0f, 0.25f, 0.01f, 0.08f), 0.25f, 0.01f, 0.08f);
		assertEquals(PoseSmoothing.approach(0f, 0.25f, 0.02f, 0.08f), half, 1e-6f);
		// never overshoots, even over a long hitch
		assertEquals(0.25f, PoseSmoothing.approach(0f, 0.25f, 5f, 0.08f), 1e-6f);
		assertEquals(0.4f, PoseSmoothing.approach(0.4f, 0.4f, 0.016f, 0.08f), 0f);
		// dt 0 leaves it where it was
		assertEquals(0.1f, PoseSmoothing.approach(0.1f, 1f, 0f, 0.08f), 0f);
	}

	@Test
	public void manualPitchSettlesWithinAFewTimeConstants()
	{
		float pitch = 0f;
		for (int i = 0; i < 24; i++)
		{
			pitch = PoseSmoothing.approach(pitch, 0.25f, 1f / 60f, PoseSmoothing.POSE_TAU);
		}
		// 0.4 s = 5 tau: within 1%
		assertEquals(0.25f, pitch, 0.0025f);
	}

	@Test
	public void stumbleWobbleShakesTheBoardThenSettles()
	{
		assertEquals(0f, PoseSmoothing.stumbleWobble(0f), 1e-6f);
		float peak = 0f;
		boolean bothWays = false;
		float last = 0f;
		for (float s = 0f; s < PoseSmoothing.STUMBLE_TIME; s += 0.005f)
		{
			float w = PoseSmoothing.stumbleWobble(s);
			peak = Math.max(peak, Math.abs(w));
			bothWays |= w * last < 0f;
			last = w;
		}
		assertTrue("visible: " + peak, peak > 0.1f);
		assertTrue("small: " + peak, peak <= PoseSmoothing.STUMBLE_ROLL);
		assertTrue(bothWays);
		assertEquals(0f, PoseSmoothing.stumbleWobble(PoseSmoothing.STUMBLE_TIME), 0f);
		assertEquals(0f, PoseSmoothing.stumbleWobble(Float.MAX_VALUE), 0f);
	}

	@Test
	public void aGrabsBoardTweakSnapsInWithinATenthOfASecond()
	{
		float target = 0.4f;
		float v = 0f;
		for (int i = 0; i < 6; i++)
		{
			v = PoseSmoothing.approach(v, target, 1f / 60f, PoseSmoothing.GRAB_TAU);
		}
		assertTrue("tweak " + v, v > 0.9f * target);
		// the first frame is not a jump all the way
		assertTrue(PoseSmoothing.approach(0f, target, 1f / 60f, PoseSmoothing.GRAB_TAU) < 0.6f * target);
	}
}
