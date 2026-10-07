package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.FootBody;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.session.Knockdown;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

/** What a ghost's body plays and derives from its updates: pushes, crouch, knockdown, OSRS layer, detail. */
public class GhostAnimationTest
{
	private static final GhostPredictor.Ground FLAT = (x, y) -> 0f;
	private static final float HALF_PI = (float) (Math.PI / 2);

	private static GhostState rolling(float speed, int seq)
	{
		return new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, speed, 0f, SkaterState.ROLLING, null, 0, null, 0f, seq);
	}

	@Test
	public void speedGainedWhileRollingIsAPush()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(rolling(300f, 1), 0f, FLAT);
		assertEquals(0, p.pushCount());
		p.accept(rolling(300f + GhostPredictor.PUSH_GAIN + 1f, 2), 0.6f, FLAT);
		assertEquals(1, p.pushCount());
		// coasting (slowing) or a tiny gain is no push
		p.accept(rolling(320f, 3), 1.2f, FLAT);
		p.accept(rolling(325f, 4), 1.8f, FLAT);
		assertEquals(1, p.pushCount());
	}

	@Test
	public void speedGainedInTheAirOrOnFootIsNoPush()
	{
		GhostPredictor p = new GhostPredictor();
		p.accept(rolling(300f, 1), 0f, FLAT);
		p.accept(new GhostState(420, 0, 0f, 0f, 50f, 0f, 0f, 900f, 300f, SkaterState.AIRBORNE, null, 0, null, 0f, 2),
			0.6f, FLAT);
		assertEquals(0, p.pushCount());
		GhostState walk = new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, 100f, 0f, SkaterState.ROLLING, null, 0, null, 0f,
			3, 0f, 0f, 0f, BoardState.CARRIED, 0f, 0f, 0f, 0f);
		GhostState run = new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, 700f, 0f, SkaterState.ROLLING, null, 0, null, 0f,
			4, 0f, 0f, 0f, BoardState.CARRIED, 0f, 0f, 0f, 0f);
		p.accept(walk, 1.2f, FLAT);
		p.accept(run, 1.8f, FLAT);
		assertEquals(0, p.pushCount());
		// a stale update is not one either
		p.accept(rolling(0f, 5), 2.4f, FLAT);
		p.accept(rolling(900f, 5), 2.5f, FLAT);
		assertEquals(0, p.pushCount());
	}

	@Test
	public void theChargeIsTheLatestUpdatesOnTheGroundOnly()
	{
		GhostPredictor p = new GhostPredictor();
		assertEquals(0f, p.charge(), 0f);
		p.accept(rolling(300f, 1).withBody(0.5f, null, 0f), 0f, FLAT);
		assertEquals(0.5f, p.charge(), 0f);
		p.accept(rolling(300f, 2), 0.6f, FLAT);
		assertEquals(0f, p.charge(), 0f);
	}

	private static GhostState knocked(KnockdownPose.Stage stage, int seq)
	{
		return new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.BAILED, null, 0, null, 0f, seq, 0f, 0f,
			0f, BoardState.DROPPED, 50f, 0f, 0f, 0f).withBody(0f, stage, 3 * HALF_PI);
	}

	@Test
	public void eachKnockdownStageIsTimedFromWhenItWasFirstSeen()
	{
		GhostPredictor p = new GhostPredictor();
		assertNull(p.knockStage());
		p.accept(knocked(KnockdownPose.Stage.AIR, 1), 1f, FLAT);
		p.accept(knocked(KnockdownPose.Stage.AIR, 2), 1.5f, FLAT);
		assertSame(KnockdownPose.Stage.AIR, p.knockStage());
		assertEquals(0.6f, p.knockStageAge(1.6f), 1e-6f);
		p.accept(knocked(KnockdownPose.Stage.LIE, 3), 2f, FLAT);
		assertEquals(0.1f, p.knockStageAge(2.1f), 1e-6f);
		p.accept(rolling(0f, 4), 3f, FLAT);
		assertNull(p.knockStage());
	}

	@Test
	public void theKnockdownIsTimedAsTheSendersIs()
	{
		assertEquals(Knockdown.LIE_SECONDS, GhostKnockdown.LIE_SECONDS, 0f);
		assertEquals(Knockdown.GET_UP_SECONDS, GhostKnockdown.GET_UP_SECONDS, 0f);
	}

	@Test
	public void theTumbleSpinsOntoItsLyingAngleThenGetsUpToAWholeTurn()
	{
		float lie = 3 * HALF_PI;
		assertEquals(0f, GhostKnockdown.angle(KnockdownPose.Stage.AIR, lie, 0f), 0f);
		float mid = GhostKnockdown.angle(KnockdownPose.Stage.AIR, lie, GhostKnockdown.SPIN_SECONDS / 2);
		assertEquals(lie / 2, mid, 1e-5f);
		assertEquals(lie, GhostKnockdown.angle(KnockdownPose.Stage.AIR, lie, 5f), 0f);
		assertEquals(lie, GhostKnockdown.angle(KnockdownPose.Stage.LIE, lie, 0.2f), 0f);
		assertEquals(lie, GhostKnockdown.angle(KnockdownPose.Stage.GET_UP, lie, 0f), 1e-6f);
		// 3/4 turn rounds to the whole turn on its way up
		assertEquals(2 * Math.PI, GhostKnockdown.angle(KnockdownPose.Stage.GET_UP, lie, 1f), 1e-5f);
		assertEquals(0f, GhostKnockdown.angle(KnockdownPose.Stage.GET_UP, HALF_PI, 1f), 1e-6f);
		assertEquals(0.5f, GhostKnockdown.progress(KnockdownPose.Stage.LIE, GhostKnockdown.LIE_SECONDS / 2), 1e-6f);
		assertEquals(1f, GhostKnockdown.progress(KnockdownPose.Stage.GET_UP, 3f), 0f);
		assertEquals(0f, GhostKnockdown.progress(KnockdownPose.Stage.AIR, 3f), 0f);
	}

	@Test
	public void theLayerPlaysTheLocalSkatersAnimations()
	{
		assertSame(GhostAnim.STANCE, GhostAnim.pick(false, FootBody.Gait.IDLE, null));
		assertSame(GhostAnim.STANCE, GhostAnim.pick(false, FootBody.Gait.RUN, null));
		assertSame(GhostAnim.NONE, GhostAnim.pick(true, FootBody.Gait.IDLE, null));
		assertSame(GhostAnim.WALK, GhostAnim.pick(true, FootBody.Gait.WALK, null));
		assertSame(GhostAnim.RUN, GhostAnim.pick(true, FootBody.Gait.RUN, null));
		// the tumble is procedural on the stance; down, the OSRS lie-down and get-up
		assertSame(GhostAnim.STANCE, GhostAnim.pick(false, FootBody.Gait.IDLE, KnockdownPose.Stage.AIR));
		assertSame(GhostAnim.LIE, GhostAnim.pick(true, FootBody.Gait.WALK, KnockdownPose.Stage.LIE));
		assertSame(GhostAnim.GET_UP, GhostAnim.pick(false, FootBody.Gait.IDLE, KnockdownPose.Stage.GET_UP));
		assertEquals(AnimationID.HUMAN_SKI_IDLE, GhostAnim.STANCE.id(-1, -1));
		assertEquals(AnimationID.HUMAN_KNOCKDOWN_LOOP_NODELAY, GhostAnim.LIE.id(-1, -1));
		assertEquals(AnimationID.HUMAN_GETUP, GhostAnim.GET_UP.id(-1, -1));
		// the member's own walk and run (their weapon's), the game's when unknown
		assertEquals(1234, GhostAnim.WALK.id(1234, 5678));
		assertEquals(5678, GhostAnim.RUN.id(1234, 5678));
		assertEquals(AnimationID.HUMAN_WALK_F, GhostAnim.WALK.id(-1, -1));
		assertEquals(AnimationID.HUMAN_RUNNING, GhostAnim.RUN.id(1234, -1));
		assertEquals(-1, GhostAnim.NONE.id(1, 2));
		assertTrue(GhostAnim.STANCE.loops());
		assertFalse(GhostAnim.GET_UP.loops());
	}

	@Test
	public void theAppearanceKeyFollowsGearColoursAndIdle()
	{
		int[] gear = {256 + 1, 512 + 4151, 0, 0, 256 + 18, 512 + 1127, 256 + 26, 512 + 1079, 256 + 33, 256 + 42,
			256 + 48, 256 + 10};
		int[] colours = {1, 2, 3, 4, 5};
		int k = GhostAppearance.key(gear, colours, 0, -1, AnimationID.HUMAN_READY);
		assertEquals(k, GhostAppearance.key(gear.clone(), colours.clone(), 0, -1, AnimationID.HUMAN_READY));
		int[] other = gear.clone();
		other[1] = 512 + 4587;
		assertNotEquals(k, GhostAppearance.key(other, colours, 0, -1, AnimationID.HUMAN_READY));
		assertNotEquals(k, GhostAppearance.key(gear, new int[]{1, 2, 3, 4, 6}, 0, -1, AnimationID.HUMAN_READY));
		assertNotEquals(k, GhostAppearance.key(gear, colours, 1, -1, AnimationID.HUMAN_READY));
		assertNotEquals(k, GhostAppearance.key(gear, colours, 0, 2, AnimationID.HUMAN_READY));
		assertNotEquals(k, GhostAppearance.key(gear, colours, 0, -1, AnimationID.HUMAN_DS_READY));
		assertEquals(GhostAppearance.key(null, null, 0, -1, 808), GhostAppearance.key(null, null, 0, -1, 808));
	}

	@Test
	public void aSnapshotIsTakenOnlyOfThePlainStandingCharacter()
	{
		int ready = AnimationID.HUMAN_READY;
		assertTrue(GhostAppearance.mayTake(-1, ready, ready, -1));
		// mid action, walking or turning, or as an NPC (a transformation): not a standing body
		assertFalse(GhostAppearance.mayTake(AnimationID.HUMAN_GETUP, ready, ready, -1));
		assertFalse(GhostAppearance.mayTake(-1, AnimationID.HUMAN_WALK_F, ready, -1));
		assertFalse(GhostAppearance.mayTake(-1, ready, ready, 2));
		// OSRS animations go on top only of the game's own standing frame: a weapon's stance would double up
		assertTrue(GhostAppearance.layerable(ready));
		assertFalse(GhostAppearance.layerable(AnimationID.HUMAN_DS_READY));
	}

	@Test
	public void theNearestGhostsGetTheFullBody()
	{
		float near = 5 * 128f;
		float far = GhostDetail.FULL_RANGE + 1f;
		assertTrue(GhostDetail.full(0, near));
		assertTrue(GhostDetail.full(GhostDetail.MAX_FULL - 1, near));
		assertFalse(GhostDetail.full(GhostDetail.MAX_FULL, near));
		assertFalse(GhostDetail.full(0, far));
		// ranks by distance, nearest first; ties keep their order
		float[] d = {900f, 100f, 500f, 100f};
		int[] rank = new int[4];
		GhostDetail.rank(d, 4, rank);
		assertEquals(3, rank[0]);
		assertEquals(0, rank[1]);
		assertEquals(2, rank[2]);
		assertEquals(1, rank[3]);
	}
}
