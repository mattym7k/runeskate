package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class GhostCodecTest
{
	static GhostFrame frame(float x, float y, float h, float heading, SkaterState state)
	{
		return new GhostFrame(420, 1, x, y, h, heading, 0f, 0f, 0f, state, null, null, 0f);
	}

	@Test
	public void roundTripStaysWithinRounding()
	{
		GhostFrame f = new GhostFrame(330, 2, 409_664.4f, 411_200.6f, -37.3f, 2.71828f, 812.4f, -1201.6f, 455.5f,
			SkaterState.AIRBORNE, Trick.INDY, Trick.KICKFLIP, 0.4f);
		SkateGhostUpdate m = GhostCodec.encode(f, GhostCodec.EV_POP | GhostCodec.EV_TRICK, Trick.KICKFLIP);
		m.seq = 77;
		GhostState s = GhostCodec.decode(m);
		assertEquals(330, s.world);
		assertEquals(2, s.plane);
		assertEquals(409_664.4f, s.x, 0.5f);
		assertEquals(411_200.6f, s.y, 0.5f);
		assertEquals(-37.3f, s.h, 0.5f);
		assertEquals(2.71828f, s.heading, 0.0005f);
		assertEquals(812.4f, s.vx, 0.5f);
		assertEquals(-1201.6f, s.vy, 0.5f);
		assertEquals(455.5f, s.vh, 0.5f);
		assertSame(SkaterState.AIRBORNE, s.state);
		assertSame(Trick.INDY, s.hold);
		assertSame(Trick.KICKFLIP, s.trick);
		// progress 0.4 of a 0.35 s kickflip, sent as nominal seconds
		assertEquals(0.4f * Trick.KICKFLIP.duration, s.flipTime, 0.0005f);
		assertEquals(GhostCodec.EV_POP | GhostCodec.EV_TRICK, s.events);
		assertEquals(77, s.seq);
	}

	@Test
	public void directionalAndTweakedGrabsRoundTripByName()
	{
		for (Trick grab : new Trick[]{Trick.MUTE, Trick.CRAIL, Trick.METHOD, Trick.CRAIL_TWEAK})
		{
			GhostFrame f = new GhostFrame(330, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.AIRBORNE, grab, null, 0f);
			SkateGhostUpdate m = GhostCodec.encode(f, GhostCodec.EV_TRICK, grab);
			assertEquals(grab.name(), m.hold);
			GhostState s = GhostCodec.decode(m);
			assertSame(grab, s.hold);
			assertSame(grab, s.trick);
		}
		// an older plugin that does not know a grab's name just shows no grab
		SkateGhostUpdate m = GhostCodec.encode(frame(0f, 0f, 0f, 0f, SkaterState.AIRBORNE), 0, null);
		m.hold = "SOME_FUTURE_GRAB";
		m.tr = "SOME_FUTURE_GRAB";
		GhostState s = GhostCodec.decode(m);
		assertNull(s.hold);
		assertNull(s.trick);
	}

	@Test
	public void noFlipAndNoHoldEncodeAsNull()
	{
		SkateGhostUpdate m = GhostCodec.encode(frame(0f, 0f, 0f, 0f, SkaterState.ROLLING), 0, null);
		assertNull(m.hold);
		assertNull(m.tr);
		assertEquals(0, m.ft);
		GhostState s = GhostCodec.decode(m);
		assertNull(s.hold);
		assertNull(s.trick);
	}

	@Test
	public void aTrickEventWithoutAFlipStillCarriesItsTrickForTheLabel()
	{
		SkateGhostUpdate m = GhostCodec.encode(frame(0f, 0f, 0f, 0f, SkaterState.AIRBORNE), GhostCodec.EV_TRICK,
			Trick.OLLIE);
		assertEquals("OLLIE", m.tr);
		assertEquals(0, m.ft);
	}

	@Test
	public void aFinishedFlipEventIsNotSentAsANewFlip()
	{
		// the kickflip's event was held back by the rate limit and the flip is over: animating it now would be wrong
		SkateGhostUpdate m = GhostCodec.encode(frame(0f, 0f, 0f, 0f, SkaterState.ROLLING), GhostCodec.EV_TRICK,
			Trick.KICKFLIP);
		assertNull(m.tr);
	}

	@Test
	public void tricksAndStatesGoByName()
	{
		GhostFrame f = new GhostFrame(330, 2, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.GRINDING, Trick.FEEBLE,
			Trick.TRIPLE_KICKFLIP, 0.5f);
		SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
		assertEquals("GRINDING", m.st);
		assertEquals("FEEBLE", m.hold);
		assertEquals("TRIPLE_KICKFLIP", m.tr);
		GhostState s = GhostCodec.decode(m);
		assertSame(SkaterState.GRINDING, s.state);
		assertSame(Trick.FEEBLE, s.hold);
		assertSame(Trick.TRIPLE_KICKFLIP, s.trick);
	}

	@Test
	public void unknownNamesDecodeSafely()
	{
		// a newer plugin's trick or state, an older plugin's ordinal read as a string, or nothing at all
		SkateGhostUpdate m = new SkateGhostUpdate();
		m.st = "WALL_RIDE";
		m.hold = "1234";
		m.tr = "kickflip";
		m.ft = 200;
		GhostState s = GhostCodec.decode(m);
		assertSame(SkaterState.ROLLING, s.state);
		assertNull(s.hold);
		assertNull(s.trick);
		m.st = null;
		m.tr = "";
		s = GhostCodec.decode(m);
		assertSame(SkaterState.ROLLING, s.state);
		assertNull(s.trick);
	}

	@Test
	public void turnRateRoundTripsInMilliradiansPerSecond()
	{
		GhostFrame f = new GhostFrame(420, 1, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, null, 0f,
			-2.3456f);
		SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
		assertEquals(-2346, m.tw);
		assertEquals(-2.346f, GhostCodec.decode(m).turnRate, 1e-6f);
	}

	@Test
	public void aTurnRateOutOfRangeIsBoundedOnReceipt()
	{
		// a buggy or hostile sender: no ghost spins faster than any real turn
		SkateGhostUpdate m = GhostCodec.encode(frame(0f, 0f, 0f, 0f, SkaterState.ROLLING), 0, null);
		m.tw = Integer.MAX_VALUE;
		assertEquals(TurnRateMeter.MAX_RATE, GhostCodec.decode(m).turnRate, 0f);
		m.tw = Integer.MIN_VALUE;
		assertEquals(-TurnRateMeter.MAX_RATE, GhostCodec.decode(m).turnRate, 0f);
		// off the board too
		m.ob = "CARRIED";
		m.tw = 99_999;
		assertEquals(TurnRateMeter.MAX_RATE, GhostCodec.decode(m).turnRate, 0f);
		// in range is untouched
		m.tw = -9_999;
		assertEquals(-9.999f, GhostCodec.decode(m).turnRate, 1e-6f);
	}

	@Test
	public void bodyFlipRoundTripsInMilliradians()
	{
		GhostFrame f = new GhostFrame(420, 1, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.AIRBORNE, null, null, 0f, 0f,
			7.5f, -11.424f);
		SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
		assertEquals(7500, m.bf);
		assertEquals(-11424, m.bw);
		GhostState s = GhostCodec.decode(m);
		assertEquals(7.5f, s.bodyFlip, 1e-6f);
		assertEquals(-11.424f, s.bodyFlipRate, 1e-6f);
	}

	@Test
	public void aLandedBodyFlipIsSentForTheLabel()
	{
		// body flips have no board rotation of their own: nothing to animate twice, so the label gets them
		SkateGhostUpdate m = GhostCodec.encode(frame(0f, 0f, 0f, 0f, SkaterState.ROLLING), GhostCodec.EV_TRICK,
			Trick.DOUBLE_BACKFLIP);
		assertEquals("DOUBLE_BACKFLIP", m.tr);
	}

	@Test
	public void headingIsWrapped()
	{
		SkateGhostUpdate m = GhostCodec.encode(frame(0f, 0f, 0f, 3 * Angles.PI / 2, SkaterState.ROLLING), 0, null);
		assertEquals(-Angles.PI / 2, GhostCodec.decode(m).heading, 0.001f);
	}

	@Test
	public void velocityFollowsTravelHeadingAndSpeedMagnitude()
	{
		// heading 0 = north (+y), PI/2 = east (+x); a negative (fakie) speed travels along the travel heading too
		float[] v = GhostCodec.velocity(-500f, Angles.PI / 2);
		assertEquals(500f, v[0], 0.01f);
		assertEquals(0f, v[1], 0.01f);
	}

	@Test
	public void sameStateIgnoresSeqAndEvents()
	{
		SkateGhostUpdate a = GhostCodec.encode(frame(100f, 200f, 0f, 1f, SkaterState.ROLLING), 0, null);
		SkateGhostUpdate b = GhostCodec.encode(frame(100.2f, 200f, 0f, 1f, SkaterState.ROLLING), GhostCodec.EV_POP,
			null);
		b.seq = 9;
		assertTrue(a.sameState(b));
		SkateGhostUpdate c = GhostCodec.encode(frame(102f, 200f, 0f, 1f, SkaterState.ROLLING), 0, null);
		assertFalse(a.sameState(c));
	}

	@Test
	public void eventBitsFromPhysicsEventsAndGrindTransitions()
	{
		int bits = GhostCodec.eventBits(Arrays.asList(SkateEvent.POP, SkateEvent.PUSH),
			Collections.singletonList(TrickEvent.trick(Trick.KICKFLIP)), SkaterState.ROLLING, SkaterState.AIRBORNE);
		// a push goes as a passive bit (it rides with the next update, never sends one of its own)
		assertEquals(GhostCodec.EV_POP | GhostCodec.EV_TRICK | GhostCodec.EV_PUSH, bits);
		assertEquals(GhostCodec.EV_GRIND_LOCK, GhostCodec.eventBits(Collections.emptyList(), Collections.emptyList(),
			SkaterState.AIRBORNE, SkaterState.GRINDING));
		assertEquals(GhostCodec.EV_GRIND_EXIT | GhostCodec.EV_POP, GhostCodec.eventBits(
			Collections.singletonList(SkateEvent.POP), Collections.emptyList(), SkaterState.GRINDING,
			SkaterState.AIRBORNE));
		assertEquals(GhostCodec.EV_LAND | GhostCodec.EV_BAIL | GhostCodec.EV_RESET, GhostCodec.eventBits(
			Arrays.asList(SkateEvent.LAND, SkateEvent.BAIL, SkateEvent.RESET), Collections.emptyList(),
			SkaterState.ROLLING, SkaterState.ROLLING));
		// a grab starting is a trick too
		assertEquals(GhostCodec.EV_TRICK, GhostCodec.eventBits(Collections.emptyList(),
			Collections.singletonList(TrickEvent.holdStart(Trick.INDY)), SkaterState.AIRBORNE, SkaterState.AIRBORNE));
	}

	@Test
	public void eventTrickIsTheLatestTrickOrHoldStart()
	{
		assertSame(Trick.MELON, GhostCodec.eventTrick(Arrays.asList(TrickEvent.trick(Trick.OLLIE),
			TrickEvent.holdStart(Trick.MELON))));
		assertNull(GhostCodec.eventTrick(Collections.emptyList()));
	}

	@Test
	public void absoluteCoordinatesUseTheSceneBase()
	{
		assertEquals(3200 * 128 + 6464f, GhostCodec.toAbsolute(6464f, 3200), 0f);
		assertEquals(6464f, GhostCodec.toLocal(3200 * 128 + 6464f, 3200), 0f);
	}
}
