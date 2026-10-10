package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.CollisionWorld;
import com.gielinorskate.physics.SkateTuning;
import java.util.Random;
import org.junit.Test;

/** The knockdown sequence: knock-off, lie down, get up; its timing and the skips. */
public class KnockdownTest
{
	/** The longest the whole sequence can take (plus a physics step per phase). */
	private static final float MAX_TOTAL = Knockdown.MAX_TUMBLE + Knockdown.LIE_SECONDS + Knockdown.GET_UP_SECONDS;
	private static final float DT = 0.02f;
	private static final SkateTuning T = new SkateTuning();

	static CollisionWorld flat()
	{
		return new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return 0f;
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return 0f;
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return Float.NEGATIVE_INFINITY;
			}
		};
	}

	/** A cliff: the ground drops 5000 just ahead (a very long fall). */
	static CollisionWorld cliff()
	{
		return new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return y > 40f ? -5000f : 0f;
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return groundHeight(x, y);
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return Float.NEGATIVE_INFINITY;
			}
		};
	}

	private static Knockdown knocked(CollisionWorld w, float speed)
	{
		Knockdown k = new Knockdown(w, T.gravity, T.skaterRadius);
		k.start(0f, 0f, 0f, 0f, speed, 0f, false, 0f, 0f, new Random(1));
		return k;
	}

	/** Steps with no input until done; returns the seconds it took. */
	private static float runIdle(Knockdown k)
	{
		float t = 0f;
		while (!k.isDone() && t < 10f)
		{
			k.step(DT);
			assertEquals(Knockdown.Outcome.NONE, k.input(false, false, false, false));
			t += DT;
		}
		return t;
	}

	@Test
	public void theWholeSequenceIsShort()
	{
		// the longest it can ever take, by design
		assertTrue(MAX_TOTAL <= 2.0f);
		assertTrue(Knockdown.LIE_SECONDS <= 0.5f);
		assertTrue(Knockdown.GET_UP_SECONDS <= 0.4f);
		for (float speed : new float[]{0f, 300f, 900f, 1500f, 2600f})
		{
			float t = runIdle(knocked(flat(), speed));
			assertTrue("at " + speed + ": " + t, t <= 2.0f);
			// a typical crash: about a second and a half at most, at least a second
			assertTrue("at " + speed + ": " + t, t >= 0.9f && t <= 1.5f);
		}
	}

	@Test
	public void evenAVeryLongFallEndsWithinTwoSeconds()
	{
		Knockdown k = knocked(cliff(), 1500f);
		float t = runIdle(k);
		assertTrue("done: " + t, k.isDone());
		assertTrue(t <= MAX_TOTAL + 3 * DT + 1e-4f);
		assertTrue(t <= 2.0f);
	}

	@Test
	public void phasesRunInOrder()
	{
		Knockdown k = knocked(flat(), 1500f);
		assertEquals(Knockdown.Phase.TUMBLE, k.getPhase());
		boolean impact = false;
		while (k.getPhase() == Knockdown.Phase.TUMBLE)
		{
			k.step(DT);
			impact |= k.takeImpact();
		}
		assertTrue("the impact comes as the lie-down starts", impact);
		assertEquals(Knockdown.Phase.LIE, k.getPhase());
		float lie = 0f;
		while (k.getPhase() == Knockdown.Phase.LIE)
		{
			k.step(DT);
			lie += DT;
		}
		assertEquals(Knockdown.LIE_SECONDS, lie, DT + 1e-4f);
		assertEquals(Knockdown.Phase.GET_UP, k.getPhase());
		assertEquals(0f, k.getBody().getSpeed(), 0f);
		float up = 0f;
		while (!k.isDone())
		{
			k.step(DT);
			up += DT;
		}
		assertEquals(Knockdown.GET_UP_SECONDS, up, DT + 1e-4f);
		assertFalse(k.takeImpact());
	}

	@Test
	public void rSkipsTheRestAndPutsTheSkaterBackOnTheBoard()
	{
		Knockdown k = knocked(flat(), 1500f);
		k.step(DT);
		assertEquals(Knockdown.Outcome.BACK_ON_BOARD, k.input(true, false, false, false));
		assertTrue(k.isDone());
	}

	@Test
	public void anyNewInputDuringTheLieDownSkipsToTheGetUp()
	{
		for (int which = 0; which < 2; which++)
		{
			Knockdown k = knocked(flat(), 1500f);
			k.input(false, false, false, false);
			toLie(k);
			k.step(DT);
			assertEquals(Knockdown.Outcome.NONE, k.input(false, which == 0, which == 1, false));
			assertEquals(Knockdown.Phase.GET_UP, k.getPhase());
		}
	}

	@Test
	public void aMoveKeyHeldThroughTheBailMustBePressedAgain()
	{
		Knockdown k = knocked(flat(), 1500f);
		k.input(false, true, false, false);
		toLie(k);
		k.step(DT);
		k.input(false, true, false, false);
		assertEquals(Knockdown.Phase.LIE, k.getPhase());
		k.input(false, false, false, false);
		k.input(false, true, false, false);
		assertEquals(Knockdown.Phase.GET_UP, k.getPhase());
	}

	@Test
	public void theBoardKeyDuringTheLieDownGoesStraightToReclaiming()
	{
		Knockdown k = knocked(flat(), 1500f);
		toLie(k);
		assertEquals(Knockdown.Outcome.RECLAIM, k.input(false, false, false, true));
		assertTrue(k.isDone());
	}

	@Test
	public void inputInTheAirIsIgnored()
	{
		Knockdown k = knocked(flat(), 1500f);
		k.step(DT);
		assertEquals(Knockdown.Outcome.NONE, k.input(false, true, true, true));
		assertEquals(Knockdown.Phase.TUMBLE, k.getPhase());
	}

	@Test
	public void theBodyFacesTheBoardOnceUp()
	{
		Knockdown k = knocked(flat(), 1500f);
		runIdle(k);
		float[] board = {k.getBoard().getX(), k.getBoard().getY()};
		float want = (float) Math.atan2(board[0] - k.getBody().getX(), board[1] - k.getBody().getY());
		assertEquals(want, k.standHeading(), 1e-4f);
	}

	@Test
	public void theBoardIsAtRestOnceTheSkaterStands()
	{
		Knockdown k = knocked(flat(), 2600f);
		runIdle(k);
		assertTrue(k.getBoard().isAtRest());
	}

	@Test
	public void theGetUpTurnsTheBodyUprightSmoothly()
	{
		Knockdown k = knocked(flat(), 1500f);
		while (k.getPhase() != Knockdown.Phase.GET_UP)
		{
			k.step(DT);
		}
		float lie = k.getBody().getAngle();
		assertEquals(lie, k.bodyAngle(), 0.05f);
		float last = k.bodyAngle();
		while (!k.isDone())
		{
			k.step(DT);
			float a = k.bodyAngle();
			assertTrue(Math.abs(a - last) < 0.5f);
			last = a;
		}
		// upright: whole turns
		double turns = k.bodyAngle() / (2 * Math.PI);
		assertEquals(Math.round(turns), turns, 1e-4);
	}

	private static void toLie(Knockdown k)
	{
		while (k.getPhase() == Knockdown.Phase.TUMBLE)
		{
			k.step(DT);
		}
		assertEquals(Knockdown.Phase.LIE, k.getPhase());
	}
}
