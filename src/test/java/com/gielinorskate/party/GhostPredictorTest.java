package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BodyFlip;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

public class GhostPredictorTest
{
	private static final GhostPredictor.Ground FLAT = (x, y) -> 0f;

	private static GhostState state(float x, float vx, float h, float vh, SkaterState st, int seq)
	{
		return new GhostState(420, 0, x, 1000f, h, Angles.PI / 2, vx, 0f, vh, st, null, 0, null, 0f, seq);
	}

	private static GhostState events(GhostState s, int events)
	{
		return new GhostState(s.world, s.plane, s.x, s.y, s.h, s.heading, s.vx, s.vy, s.vh, s.state, s.hold, events,
			s.trick, s.flipTime, s.seq);
	}

	private static GhostState flip(Trick trick, float flipTime, int events, int seq)
	{
		return new GhostState(420, 0, 0f, 0f, 200f, 0f, 0f, 0f, 0f, SkaterState.AIRBORNE, null, events, trick,
			flipTime, seq);
	}

	@Test
	public void straightLineDeadReckoning()
	{
		GhostPredictor p = new GhostPredictor();
		assertTrue(p.accept(state(1000f, 500f, 0f, 0f, SkaterState.ROLLING, 1), 0f, FLAT));
		RenderPose pose = p.pose(0.5f, FLAT);
		assertEquals(1250f, pose.x, 0.01f);
		assertEquals(1000f, pose.y, 0.01f);
		assertEquals(0f, pose.h, 0.01f);
		assertEquals(Angles.PI / 2, pose.heading, 1e-5f);
		assertSame(SkaterState.ROLLING, pose.state);
	}

	@Test
	public void rollingFollowsTheReceiversGroundRelativeToTheLastState()
	{
		GhostPredictor p = new GhostPredictor();
		// a hill rising 1 unit per 10 along x; the sender's own ground was 5 units higher than ours
		GhostPredictor.Ground hill = (x, y) -> x / 10f;
		p.accept(state(1000f, 500f, 105f, 0f, SkaterState.ROLLING, 1), 0f, hill);
		assertEquals(105f + 25f, p.pose(0.5f, hill).h, 0.01f);
	}

	@Test
	public void extrapolationStopsAfterAWhileWithoutMessages()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 1000f, 0f, 0f, SkaterState.ROLLING, 1), 0f, FLAT);
		assertEquals(GhostPredictor.MAX_EXTRAPOLATION * 1000f, p.pose(5f, FLAT).x, 0.01f);
	}

	@Test
	public void airArcWithGravityNeverBelowGround()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 0f, 0f, 820f, SkaterState.AIRBORNE, 1), 0f, FLAT);
		float g = GhostPredictor.GRAVITY;
		assertEquals(820f * 0.2f - g / 2 * 0.04f, p.pose(0.2f, FLAT).h, 0.01f);
		assertEquals(820f * 0.4f - g / 2 * 0.16f, p.pose(0.4f, FLAT).h, 0.01f);
		// 820 * 1 - 1000 = -180 would be under the ground
		assertEquals(0f, p.pose(1f, FLAT).h, 0.01f);
		// unknown ground: no clamp
		assertEquals(-180f, p.pose(1f, null).h, 0.01f);
	}

	@Test
	public void correctionBlendsWithoutAJump()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 1000f, 0f, 0f, SkaterState.ROLLING, 1), 0f, FLAT);
		assertEquals(500f, p.pose(0.5f, FLAT).x, 0.01f);
		// the authoritative state is 60 units further on
		p.accept(state(560f, 1000f, 0f, 0f, SkaterState.ROLLING, 2), 0.5f, FLAT);
		assertEquals("no jump at the correction", 500f, p.pose(0.5f, FLAT).x, 0.01f);
		float mid = p.pose(0.575f, FLAT).x;
		assertTrue(mid > 575f && mid < 635f);
		assertEquals("blended out after 150 ms", 710f, p.pose(0.65f, FLAT).x, 0.01f);
		assertEquals(810f, p.pose(0.75f, FLAT).x, 0.01f);
	}

	@Test
	public void headingCorrectionBlendsTheShortWay()
	{
		GhostPredictor p = new GhostPredictor();
		GhostState a = new GhostState(420, 0, 0f, 0f, 0f, Angles.PI - 0.1f, 0f, 0f, 0f, SkaterState.ROLLING, null, 0,
			null, 0f, 1);
		GhostState b = new GhostState(420, 0, 0f, 0f, 0f, -Angles.PI + 0.1f, 0f, 0f, 0f, SkaterState.ROLLING, null,
			0, null, 0f, 2);
		p.accept(a, 0f, FLAT);
		p.accept(b, 1f, FLAT);
		assertEquals(Angles.PI - 0.1f, p.pose(1f, FLAT).heading, 1e-4f);
		assertEquals(Angles.PI, Math.abs(p.pose(1f + GhostPredictor.BLEND_TIME / 2, FLAT).heading), 1e-4f);
		assertEquals(-Angles.PI + 0.1f, p.pose(2f, FLAT).heading, 1e-4f);
	}

	@Test
	public void largeErrorSnaps()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 1000f, 0f, 0f, SkaterState.ROLLING, 1), 0f, FLAT);
		// 3 tiles is the limit: 400 units off snaps
		p.accept(state(900f, 1000f, 0f, 0f, SkaterState.ROLLING, 2), 0.5f, FLAT);
		assertEquals(900f, p.pose(0.5f, FLAT).x, 0.01f);
	}

	@Test
	public void flipAnimatesFromStartToCompletionWithTheLocalEaseOut()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(flip(Trick.KICKFLIP, 0f, GhostCodec.EV_POP | GhostCodec.EV_TRICK, 1), 0f, null);
		float turn = 2 * Angles.PI;
		assertEquals(0f, p.pose(0f, null).boardRoll, 1e-4f);
		// ease-out 1 - (1 - u)^2 at u = 0.5 is 0.75
		assertEquals(0.75f * turn, p.pose(Trick.KICKFLIP.duration / 2, null).boardRoll, 1e-3f);
		assertEquals(turn, p.pose(Trick.KICKFLIP.duration, null).boardRoll, 1e-3f);
		// held at the end until the next state clears it
		assertEquals(turn, p.pose(0.6f, null).boardRoll, 1e-3f);
		assertEquals(0f, p.pose(0.6f, null).boardYaw, 1e-4f);
	}

	@Test
	public void flipPicksUpMidwayFromTheSentFlipTime()
	{
		GhostPredictor p = new GhostPredictor();
		// a 360 shove-it half way through when the update was sent
		p.accept(flip(Trick.SHOVE_IT_360, Trick.SHOVE_IT_360.duration / 2, 0, 1), 3f, null);
		assertEquals(0.75f * 2 * Angles.PI, p.pose(3f, null).boardYaw, 1e-3f);
		assertEquals(0f, p.pose(3f, null).boardRoll, 1e-4f);
	}

	@Test
	public void nonFlipTricksDoNotTurnTheBoardButAreLabelled()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(flip(Trick.OLLIE, 0f, GhostCodec.EV_POP | GhostCodec.EV_TRICK, 1), 2f, null);
		assertEquals(0f, p.pose(2.1f, null).boardRoll, 0f);
		assertSame(Trick.OLLIE, p.labelTrick());
		assertEquals(0.5f, p.labelAge(2.5f), 1e-5f);
		assertEquals(0.25f, p.sincePop(2.25f), 1e-5f);
	}

	@Test
	public void noLabelBeforeAnyTrick()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 0f, 0f, 0f, SkaterState.ROLLING, 1), 0f, FLAT);
		assertNull(p.labelTrick());
		assertEquals(Float.MAX_VALUE, p.sincePop(1f), 0f);
	}

	@Test
	public void manualPitchComesFromTheHold()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.MANUAL, Trick.NOSE_MANUAL, 0, null,
			0f, 1), 0f, FLAT);
		assertTrue(p.pose(0f, FLAT).boardPitch < 0f);
		assertSame(Trick.NOSE_MANUAL, p.pose(0f, FLAT).hold);
	}

	@Test
	public void expiresAfterFifteenSecondsOfSilence()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 0f, 0f, 0f, SkaterState.ROLLING, 1), 0f, FLAT);
		assertFalse(p.expired(14.9f));
		assertTrue(p.expired(15.1f));
		p.accept(state(0f, 0f, 0f, 0f, SkaterState.ROLLING, 2), 10f, FLAT);
		assertFalse(p.expired(15.1f));
	}

	@Test
	public void staleSequenceIsIgnored()
	{
		GhostPredictor p = new GhostPredictor();
		assertTrue(p.accept(state(100f, 0f, 0f, 0f, SkaterState.ROLLING, 5), 0f, FLAT));
		assertFalse(p.accept(state(900f, 0f, 0f, 0f, SkaterState.ROLLING, 4), 0.1f, FLAT));
		assertFalse(p.accept(state(900f, 0f, 0f, 0f, SkaterState.ROLLING, 5), 0.2f, FLAT));
		assertEquals(100f, p.pose(0.3f, FLAT).x, 0.01f);
		assertEquals(5, p.latest().seq);
	}

	@Test
	public void aRestartedSenderIsPickedUpAfterSilence()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(100f, 0f, 0f, 0f, SkaterState.ROLLING, 500), 0f, FLAT);
		assertTrue(p.accept(state(300f, 0f, 0f, 0f, SkaterState.ROLLING, 1), GhostPredictor.RESYNC_SILENCE, FLAT));
	}

	@Test
	public void worldOrPlaneChangeSnaps()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 0f, 0f, 0f, SkaterState.ROLLING, 1), 0f, FLAT);
		p.accept(new GhostState(420, 1, 50f, 1000f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, 0, null, 0f, 2),
			1f, FLAT);
		assertEquals(50f, p.pose(1f, FLAT).x, 0.01f);
		assertEquals(1, p.latest().plane);
	}

	private static GhostState bodyFlip(SkaterState st, float angle, float rate, int seq)
	{
		return new GhostState(420, 0, 0f, 0f, 300f, 0f, 0f, 0f, 0f, st, null, 0, null, 0f, seq, 0f, angle, rate);
	}

	/** SkatePhysics' body flip rate: a whole flip in 0.55 s. */
	private static final float HELD = Angles.TWO_PI / 0.55f;

	@Test
	public void aHeldBodyFlipKeepsTurningBetweenUpdates()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(bodyFlip(SkaterState.AIRBORNE, 1f, HELD, 1), 0f, null);
		assertEquals(1f, p.bodyFlip(0f), 1e-4f);
		assertEquals(1f + HELD * 0.3f, p.bodyFlip(0.3f), 1e-3f);
		// the next update agrees: no correction
		p.accept(bodyFlip(SkaterState.AIRBORNE, 1f + HELD * 0.3f, HELD, 2), 0.3f, null);
		assertEquals(1f + HELD * 0.4f, p.bodyFlip(0.4f), 1e-3f);
	}

	@Test
	public void aReleasedBodyFlipSettlesOntoTheWholeTurnLikeTheAssist()
	{
		GhostPredictor p = new GhostPredictor();
		// 0.3 rad short of a full frontflip, easing at 3 rad/s
		float start = Angles.TWO_PI - 0.3f;
		p.accept(bodyFlip(SkaterState.AIRBORNE, start, 3f, 1), 0f, null);
		assertEquals(start + 0.15f, p.bodyFlip(0.05f), 1e-4f);
		assertEquals(Angles.TWO_PI, p.bodyFlip(0.5f), 1e-4f);
		assertEquals(Angles.TWO_PI, p.bodyFlip(1f), 1e-4f);
	}

	@Test
	public void landingABodyFlipDoesNotSpinItBack()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(bodyFlip(SkaterState.AIRBORNE, -Angles.TWO_PI + 0.05f, -3f, 1), 0f, null);
		// the landing resets the angle to 0: a whole turn from where the ghost is, which looks the same
		p.accept(bodyFlip(SkaterState.ROLLING, 0f, 0f, 2), 0.1f, null);
		float atLanding = p.bodyFlip(0.1f);
		assertEquals(0f, BodyFlip.residual(atLanding), 1e-3f);
		assertEquals(0f, p.bodyFlip(0.1f + GhostPredictor.BLEND_TIME), 1e-4f);
	}

	@Test
	public void aBodyFlipCorrectionBlendsTheShortWay()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(bodyFlip(SkaterState.AIRBORNE, 0f, HELD, 1), 0f, null);
		float predicted = p.bodyFlip(0.6f);
		// the flip was released 0.1 s in: the authoritative angle is behind
		p.accept(bodyFlip(SkaterState.AIRBORNE, HELD * 0.1f, 0f, 2), 0.6f, null);
		assertEquals("no jump at the correction", 0f, BodyFlip.residual(p.bodyFlip(0.6f) - predicted), 1e-3f);
		assertEquals(HELD * 0.1f, p.bodyFlip(0.6f + GhostPredictor.BLEND_TIME), 1e-4f);
	}

	@Test
	public void impossiblePitchesTheBoardEndOverEnd()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(flip(Trick.IMPOSSIBLE, 0f, GhostCodec.EV_POP | GhostCodec.EV_TRICK, 1), 0f, null);
		assertEquals(0f, p.pose(0f, null).boardPitch, 1e-4f);
		assertEquals(0.75f * Angles.TWO_PI, p.pose(Trick.IMPOSSIBLE.duration / 2, null).boardPitch, 1e-3f);
		assertEquals(0f, p.pose(Trick.IMPOSSIBLE.duration / 2, null).boardRoll, 1e-4f);
	}

	@Test
	public void bigspinTurnsTheBodyHalfAndTheBoardTheRestRelativeToIt()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(flip(Trick.BIGSPIN, 0f, GhostCodec.EV_TRICK, 1), 0f, null);
		RenderPose mid = p.pose(Trick.BIGSPIN.duration / 2, null);
		assertEquals(0.75f * Angles.PI, mid.heading, 1e-3f);
		// the board turns 360 in the world: 0.75 of it less the body's 0.75 of 180
		assertEquals(0.75f * Angles.TWO_PI - 0.75f * Angles.PI, mid.boardYaw, 1e-3f);
		RenderPose end = p.pose(Trick.BIGSPIN.duration, null);
		assertEquals(Angles.PI, Math.abs(end.heading), 1e-3f);
		assertEquals(Angles.PI, end.boardYaw, 1e-3f);
	}

	@Test
	public void aConstantRateCarveIsPredictedSmoothlyBetweenUpdates()
	{
		// a 1200 u/s carve at 2 rad/s (radius 600) sent every 0.6 s, received through the wire's rounding
		float v = 1200f;
		float w = 2f;
		float x0 = 400_000f;
		float y0 = 400_000f;
		float h0 = 0.3f;
		GhostPredictor p = new GhostPredictor();
		float frame = 1f / 60f;
		float lastX = Float.NaN;
		float lastY = Float.NaN;
		int seq = 0;
		for (int i = 0; i <= 180; i++)
		{
			float t = i * frame;
			float heading = h0 + w * t;
			float x = x0 + v / w * (float) (Math.cos(h0) - Math.cos(heading));
			float y = y0 + v / w * (float) (Math.sin(heading) - Math.sin(h0));
			if (i % 36 == 0)
			{
				float[] vel = GhostCodec.velocity(v, heading);
				GhostFrame f = new GhostFrame(420, 0, x, y, 0f, heading, vel[0], vel[1], 0f, SkaterState.ROLLING,
					null, null, 0f, w);
				SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
				m.seq = ++seq;
				assertTrue(p.accept(GhostCodec.decode(m), t, FLAT));
			}
			RenderPose pose = p.pose(t, FLAT);
			assertEquals("x at " + t, x, pose.x, 3f);
			assertEquals("y at " + t, y, pose.y, 3f);
			assertEquals("heading at " + t, 0f, Angles.wrap(pose.heading - heading), 0.005f);
			if (!Float.isNaN(lastX))
			{
				// one frame moves 1200 / 60 = 20 units: no stutter at the updates
				double step = Math.hypot(pose.x - lastX, pose.y - lastY);
				assertEquals("step at " + t, 20.0, step, 1.5);
			}
			lastX = pose.x;
			lastY = pose.y;
		}
	}

	@Test
	public void turningStopsWithTheExtrapolation()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, 0, null, 0f, 1, 1f),
			0f, FLAT);
		assertEquals(0.5f, p.pose(0.5f, FLAT).heading, 1e-4f);
		assertEquals(GhostPredictor.MAX_EXTRAPOLATION, p.pose(5f, FLAT).heading, 1e-4f);
		assertEquals(1f, p.turnRate(0.5f), 0f);
		assertEquals(0f, p.turnRate(5f), 0f);
	}

	@Test
	public void airSpinTurnsTheHeadingButNotTheTravel()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(new GhostState(420, 0, 0f, 0f, 100f, 0f, 0f, 1000f, 0f, SkaterState.AIRBORNE, null, 0, null, 0f, 1,
			3f), 0f, null);
		RenderPose pose = p.pose(0.2f, null);
		assertEquals(0.6f, pose.heading, 1e-4f);
		assertEquals(0f, pose.x, 1e-3f);
		assertEquals(200f, pose.y, 1e-3f);
	}

	@Test
	public void eventsAreCountedForTheBodyRig()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(flip(Trick.KICKFLIP, 0f, GhostCodec.EV_POP | GhostCodec.EV_TRICK, 1), 0f, null);
		p.accept(events(state(0f, 0f, 0f, 0f, SkaterState.ROLLING, 2), GhostCodec.EV_LAND), 0.5f, FLAT);
		p.accept(events(state(0f, 0f, 0f, 0f, SkaterState.BAILED, 3), GhostCodec.EV_BAIL | GhostCodec.EV_POP), 0.6f,
			FLAT);
		assertEquals(2, p.popCount());
		assertEquals(1, p.landCount());
		assertEquals(1, p.bailCount());
	}

	@Test
	public void flippingLastsTheTricksDuration()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(flip(Trick.KICKFLIP, 0.1f, GhostCodec.EV_TRICK, 1), 0f, null);
		assertTrue(p.flipping(Trick.KICKFLIP.duration - 0.11f));
		assertFalse(p.flipping(Trick.KICKFLIP.duration - 0.09f));
		p.accept(flip(Trick.OLLIE, 0f, GhostCodec.EV_TRICK, 2), 1f, null);
		assertFalse(p.flipping(1f));
	}

	@Test
	public void verticalSpeedFallsUnderGravityInTheAir()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(state(0f, 600f, 100f, 820f, SkaterState.AIRBORNE, 1), 0f, null);
		assertEquals(820f - GhostPredictor.GRAVITY * 0.25f, p.verticalSpeed(0.25f), 0.01f);
		assertEquals(600f, p.speed(), 0.01f);
	}
}
