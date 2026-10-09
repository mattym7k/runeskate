package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.render.TantrumSequence.Phase;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

/** The tantrum's and the celebration's timing, body and board, and the snapped halves' fade. */
public class DuelEndingSequenceTest
{
	@Test
	public void theTantrumTakesTwoToThreeSecondsAndSnapsNearItsEnd()
	{
		assertTrue(TantrumSequence.DURATION >= 2f && TantrumSequence.DURATION <= 3f);
		assertTrue(TantrumSequence.SNAP_AT > 1f && TantrumSequence.SNAP_AT < TantrumSequence.DURATION);
		Phase last = Phase.GRAB;
		for (float t = 0f; t < TantrumSequence.DURATION + 0.5f; t += 0.01f)
		{
			Phase p = TantrumSequence.phase(t);
			assertTrue("phases only go forward at " + t, p.ordinal() >= last.ordinal());
			last = p;
			float u = TantrumSequence.progress(t);
			assertTrue(u >= 0f && u <= 1f);
			assertEquals(t < TantrumSequence.SNAP_AT, TantrumSequence.holding(t));
		}
		assertEquals(Phase.DONE, last);
		assertEquals(Phase.SLAM, TantrumSequence.phase(TantrumSequence.SNAP_AT - 0.001f));
		assertEquals(Phase.HUFF, TantrumSequence.phase(TantrumSequence.SNAP_AT));
		assertEquals(Phase.GRAB, TantrumSequence.phase(Float.NaN));
	}

	@Test
	public void theWindUpStampsItsFeetAndNothingElsePlaysAnEmote()
	{
		assertEquals(AnimationID.EMOTE_STAMPFEET, TantrumSequence.STOMP_ANIMATION);
		for (float t = 0f; t < TantrumSequence.DURATION; t += 0.01f)
		{
			int want = TantrumSequence.phase(t) == Phase.STOMP ? AnimationID.EMOTE_STAMPFEET : -1;
			assertEquals("at " + t, want, TantrumSequence.animation(t));
		}
	}

	@Test
	public void theArmsReachInHoldAndLetGo()
	{
		BodyPose p = new BodyPose();
		TantrumSequence.writeBody(0f, p);
		assertEquals(0f, p.liftWeight, 1e-6f);
		for (float t = TantrumSequence.GRAB; t < TantrumSequence.SNAP_AT; t += 0.01f)
		{
			TantrumSequence.writeBody(t, p);
			assertEquals("held at " + t, 1f, p.liftWeight, 1e-6f);
		}
		TantrumSequence.writeBody(TantrumSequence.DURATION - 0.001f, p);
		assertEquals(0f, p.liftWeight, 1e-3f);
		assertEquals(0f, p.torsoBend, 0.02f);
		// nothing but the arms and the fold: no grab, no flip, no leg
		assertEquals(0f, p.armWeight, 0f);
		assertEquals(0f, p.flip, 0f);
		assertEquals(0f, p.legWeight, 0f);
	}

	@Test
	public void bothHandsGoOverTheHeadWithTheBoard()
	{
		float top = TantrumSequence.GRAB + TantrumSequence.STOMP + TantrumSequence.LIFT - 0.001f;
		BodyPose p = new BodyPose();
		TantrumSequence.writeBody(top, p);
		Humanoid b = new Humanoid();
		MeshDeformer.deform(b.xs, b.ys, b.zs, b.n, p);
		for (float side : new float[]{-1f, 1f})
		{
			ArmLocator a = new ArmLocator();
			Humanoid rest = new Humanoid();
			assertTrue(a.locate(rest.xs, rest.ys, rest.zs, rest.n, side));
			int hand = a.handIndex;
			// above the head (-200), on its own side of the board's middle
			assertTrue("hand " + side + " at " + b.ys[hand], b.ys[hand] < -200f);
			assertTrue(b.xs[hand] * side > 0f);
		}
		float[] board = new float[4];
		TantrumSequence.board(top, board);
		assertTrue(board[1] < -200f);
	}

	@Test
	public void theBoardIsSlammedOntoTheGroundInFrontWhereItSnaps()
	{
		float[] b = new float[4];
		TantrumSequence.board(TantrumSequence.SNAP_AT - 1e-4f, b);
		// its middle at the board's held height: the wheels on the ground
		assertEquals(CarryPose.HELD_Y, b[1], 0.5f);
		Placement at = new Placement();
		for (float heading : new float[]{0f, 1f, -2.5f, Angles.PI})
		{
			TantrumSequence.place(1000f, 2000f, 50f, heading, b, at);
			float[] snap = new float[2];
			TantrumSequence.snapPoint(1000f, 2000f, heading, snap);
			assertEquals(snap[0], at.x, 1f);
			assertEquals(snap[1], at.y, 1f);
			// in front of the body: along its heading
			float ahead = (snap[0] - 1000f) * (float) Math.sin(heading) + (snap[1] - 2000f) * (float) Math.cos(heading);
			assertEquals(-TantrumSequence.GROUND_Z, ahead, 1f);
			// on the ground (RuneLite z down: the board's origin, its wheels' bottoms, at h)
			assertEquals(-50f, at.z, 1f);
			// across the body: the nose a quarter turn to its left
			assertEquals(Angles.toJau(TantrumSequence.boardHeading(heading)), at.orientation());
		}
	}

	@Test
	public void theCelebrationIsShortAndPicksItsEmote()
	{
		assertTrue(CelebrationSequence.DURATION >= 1.5f && CelebrationSequence.DURATION <= 2f);
		assertEquals(AnimationID.EMOTE_CHEER, CelebrationSequence.animation(false));
		assertEquals(AnimationID.EMOTE_JUMP_WITH_JOY, CelebrationSequence.animation(true));
		assertTrue(CelebrationSequence.playing(0f));
		assertFalse(CelebrationSequence.playing(CelebrationSequence.DURATION));
		assertEquals(0.5f, CelebrationSequence.progress(CelebrationSequence.DURATION / 2), 1e-6f);
		assertEquals(1f, CelebrationSequence.progress(10f), 0f);
	}

	@Test
	public void theHalvesLieAboutThreeSecondsThenSinkAway()
	{
		assertEquals(3f, SnappedBoard.LIE_SECONDS, 0.5f);
		assertEquals(0f, SnappedBoard.sinkDepth(0f), 0f);
		assertEquals(0f, SnappedBoard.sinkDepth(SnappedBoard.LIE_SECONDS), 0f);
		float last = 0f;
		for (float t = SnappedBoard.LIE_SECONDS; t <= SnappedBoard.LIE_SECONDS + SnappedBoard.SINK_SECONDS; t += 0.01f)
		{
			float d = SnappedBoard.sinkDepth(t);
			assertTrue(d >= last);
			last = d;
		}
		assertEquals(Tuning.SINK_DEPTH, SnappedBoard.sinkDepth(100f), 1e-6f);
		assertEquals(0f, SnappedBoard.sinkDepth(Float.NaN), 0f);
	}
}
