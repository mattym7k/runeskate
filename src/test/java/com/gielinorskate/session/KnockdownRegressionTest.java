package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.GielinorSkateConfig.AfterBail;
import com.gielinorskate.physics.BailReason;
import com.gielinorskate.physics.CollisionWorld;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkateTuning;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * The knockdown changes only what a bail looks like and what follows it: the bail itself (its cause, its events),
 * the lost combo are what they were, whichever "After a bail" is chosen.
 */
public class KnockdownRegressionTest
{
	private static final float DT = 0.02f;
	private static final SkateTuning T = new SkateTuning();

	/** Flat ground with a tall wall across at y = wallY. */
	private static CollisionWorld wallAt(float wallY)
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
				return (y0 < wallY) != (y1 < wallY) ? 300f : Float.NEGATIVE_INFINITY;
			}
		};
	}

	@Test
	public void aHeadOnWallHitStillBailsAsAWallBail()
	{
		SkatePhysics p = new SkatePhysics(T, wallAt(300f), 0f, 0f, 0f);
		p.setRollingSpeed(1500f);
		SkateInput in = new SkateInput();
		List<SkateEvent> events = new ArrayList<>();
		List<TrickEvent> tricks = new ArrayList<>();
		for (int i = 0; i < 50 && p.getState() != SkaterState.BAILED; i++)
		{
			p.step(DT, in);
			events.addAll(p.drainEvents());
			tricks.addAll(p.drainTrickEvents());
		}
		assertEquals(SkaterState.BAILED, p.getState());
		assertEquals(BailReason.WALL, p.getLastBailReason());
		assertTrue(events.contains(SkateEvent.BAIL));
		assertTrue(tricks.stream().anyMatch(e -> e.type == TrickEvent.Type.BAILED));
		// the knockdown reads the wall: knocked back off it, on the near side
		assertTrue(p.isBailWall());
		assertEquals(-1f, p.getBailWallNy(), 1e-3f);
		Knockdown k = new Knockdown(wallAt(300f), T.gravity, T.skaterRadius);
		k.start(p.getX(), p.getY(), p.getH(), p.getVelocityX(), p.getVelocityY(), p.getHeading(), p.isBailWall(),
			p.getBailWallNx(), p.getBailWallNy(), new Random(4));
		while (!k.isDone())
		{
			k.step(DT);
			assertTrue(k.getBody().getY() < 300f);
			assertTrue(k.getBoard().getY() < 300f);
		}
		assertTrue(k.getBody().getY() < p.getY());
	}

	@Test
	public void theBailStillLosesTheCombo()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f);
		scorer.accept(TrickEvent.bailed(), 0.2f);
		assertEquals(0, scorer.sessionScore());
		assertTrue(scorer.lastResult().isBailed());
		assertEquals(0, scorer.comboPoints());
	}

	@Test
	public void bothSettingsKeepTheBail()
	{
		// the choice only decides what follows the bail; it is never a reason not to bail
		assertTrue(BailRules.knocksOff(AfterBail.KNOCKED_OFF));
		assertFalse(BailRules.knocksOff(AfterBail.HOP_BACK_ON));
		assertEquals(AfterBail.KNOCKED_OFF, new com.gielinorskate.GielinorSkateConfig()
		{
		}.afterBail());
	}

	@Test
	public void hopBackOnStillGetsUpByItself()
	{
		// the old path, unchanged: the bailed skater is back on after bailDuration + bailAutoReset
		SkatePhysics p = new SkatePhysics(T, wallAt(300f), 0f, 0f, 0f);
		p.setRollingSpeed(1500f);
		SkateInput in = new SkateInput();
		float t = 0f;
		boolean bailed = false;
		while (t < 5f)
		{
			p.step(DT, in);
			t += DT;
			bailed |= p.getState() == SkaterState.BAILED;
			if (bailed && p.getState() == SkaterState.ROLLING)
			{
				break;
			}
		}
		assertTrue(bailed);
		assertEquals(SkaterState.ROLLING, p.getState());
	}
}
