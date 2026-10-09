package com.gielinorskate.party;

import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.tricks.Trick;

/**
 * Test driver for one ghost: a member whose updates reach a {@link GhostPredictor} through its timeline as a real
 * sender's do (each carries its send time, no latency). The latest state keeps going out every {@link #SEND_DT},
 * moved on by its velocity and turn rate (falling in the air), so the playback always has the next update. The
 * ghost is drawn {@link #DELAY} behind once {@link #warmUp} has let the clock sync settle.
 */
final class GhostFeed
{
	static final float FRAME = 1f / 60f;
	static final float SEND_DT = 0.1f;
	/** The playback delay with updates every SEND_DT and no jitter (GhostClockSync.MIN_DELAY). */
	static final float DELAY = GhostClockSync.MIN_DELAY;
	static final GhostPredictor.Ground FLAT = (x, y) -> 0f;

	/** One drawn frame at {@code now}. */
	interface Frame
	{
		void draw(GhostPredictor predictor, float now);
	}

	final GhostPredictor predictor = new GhostPredictor();
	float now;
	private int seq;
	private GhostState last;
	private float nextSend;

	/** Sends {@code s} now (its seq replaced by the next one). */
	void send(GhostState s)
	{
		last = copy(s, s.x, s.y, s.h, s.heading, s.vh, s.events, ++seq, s.flipTime, s.bodyFlip);
		accept(last);
		nextSend = now + SEND_DT;
	}

	/** Sends a standing skater for a while (not drawn), so the playback has eased onto {@link #DELAY} behind. */
	void warmUp()
	{
		send(state(420, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, 0, null, 0f, 0));
		run(20f, (p, t) -> p.pose(t, FLAT));
	}

	/** Draws frames for {@code seconds}, the latest state going out again every {@link #SEND_DT}. */
	void run(float seconds, Frame frame)
	{
		int frames = Math.round(seconds / FRAME);
		for (int i = 0; i < frames; i++)
		{
			now += FRAME;
			if (last != null && now >= nextSend)
			{
				float dt = now - (nextSend - SEND_DT);
				boolean air = last.state == SkaterState.AIRBORNE;
				float vh = air ? last.vh - GhostPredictor.GRAVITY * dt : last.vh;
				float h = air ? Math.max(0f, last.h + last.vh * dt - GhostPredictor.GRAVITY / 2f * dt * dt) : last.h;
				last = copy(last, last.x + last.vx * dt, last.y + last.vy * dt, h, last.heading + last.turnRate * dt,
					vh, 0, ++seq, last.flipTime + dt, last.bodyFlip + last.bodyFlipRate * dt);
				accept(last);
				nextSend = now + SEND_DT;
			}
			frame.draw(predictor, now);
		}
	}

	private void accept(GhostState s)
	{
		int x = Math.round(s.x);
		int y = Math.round(s.y);
		int h = Math.round(s.h);
		int hd = Math.round(s.heading * 1000f);
		String tj = GhostFeed.wire(GhostTrajectory.timeMs(now), s.events,
			new float[GhostTrajectory.EVENT_BITS], 0, new float[0], new float[0], new float[0], new float[0],
			new float[0], new SkaterState[0], x, y, h, hd);
		predictor.accept(s, GhostTrajectory.decode(tj, s.events, x, y, h, hd), now, FLAT);
	}

	private static GhostState copy(GhostState s, float x, float y, float h, float heading, float vh, int events,
		int seq, float flipTime, float bodyFlip)
	{
		return new GhostState(s.world, s.plane, x, y, h, heading, s.vx, s.vy, vh, s.state, s.hold, events, s.trick,
			flipTime, seq, s.turnRate, bodyFlip, s.bodyFlipRate, s.offBoard, s.boardX, s.boardY, s.boardH,
			s.boardHeading, s.charge, s.knockStage, s.knockLie);
	}

	// ---- received states and local frames, as built before GhostState took every field

	static GhostState state(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq)
	{
		return state(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq, 0f);
	}

	static GhostState state(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq, float turnRate)
	{
		return state(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq, turnRate,
			0f, 0f);
	}

	static GhostState state(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq, float turnRate,
		float bodyFlip, float bodyFlipRate)
	{
		return state(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq, turnRate,
			bodyFlip, bodyFlipRate, null, 0f, 0f, 0f, 0f);
	}

	static GhostState state(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq, float turnRate,
		float bodyFlip, float bodyFlipRate, BoardState offBoard, float boardX, float boardY, float boardH,
		float boardHeading)
	{
		return new GhostState(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq,
			turnRate, bodyFlip, bodyFlipRate, offBoard, boardX, boardY, boardH, boardHeading, 0f, null, 0f);
	}

	/** {@code s} with the body fields: the pop charge, and the knockdown's stage (null: none) and lying angle. */
	static GhostState withBody(GhostState s, float charge, KnockdownPose.Stage knockStage, float knockLie)
	{
		return new GhostState(s.world, s.plane, s.x, s.y, s.h, s.heading, s.vx, s.vy, s.vh, s.state, s.hold,
			s.events, s.trick, s.flipTime, s.seq, s.turnRate, s.bodyFlip, s.bodyFlipRate, s.offBoard, s.boardX,
			s.boardY, s.boardH, s.boardHeading, charge, knockStage, knockLie);
	}

	/** A local frame with the flip {@code flipTrick} at {@code flipProgress} (0..1). */
	static GhostState frame(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, SkaterState state, Trick hold, Trick flipTrick, float flipProgress)
	{
		return frame(world, plane, x, y, h, heading, vx, vy, vh, state, hold, flipTrick, flipProgress, 0f);
	}

	static GhostState frame(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, SkaterState state, Trick hold, Trick flipTrick, float flipProgress, float turnRate)
	{
		return frame(world, plane, x, y, h, heading, vx, vy, vh, state, hold, flipTrick, flipProgress, turnRate, 0f,
			0f);
	}

	static GhostState frame(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, SkaterState state, Trick hold, Trick flipTrick, float flipProgress, float turnRate, float bodyFlip,
		float bodyFlipRate)
	{
		return state(world, plane, x, y, h, heading, vx, vy, vh, state, hold, 0, flipTrick,
			flipTrick == null ? 0f : flipProgress * flipTrick.duration, 0, turnRate, bodyFlip, bodyFlipRate);
	}

	/** A walker's frame, its board CARRIED (null too) or DROPPED at the board position. */
	static GhostState onFoot(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, boolean airborne, float turnRate, BoardState board, float boardX, float boardY, float boardH,
		float boardHeading)
	{
		return state(world, plane, x, y, h, heading, vx, vy, vh, airborne ? SkaterState.AIRBORNE : SkaterState.ROLLING,
			null, 0, null, 0f, 0, turnRate, 0f, 0f, board == null ? BoardState.CARRIED : board, boardX, boardY, boardH,
			boardHeading);
	}

	/** A knocked-off frame in {@code stage}: the body, to lie at {@code lieAngle}, and the board where it is. */
	static GhostState knockdown(int world, int plane, float x, float y, float h, float facing, float vx, float vy,
		boolean airborne, KnockdownPose.Stage stage, float lieAngle, float boardX, float boardY, float boardH,
		float boardHeading)
	{
		return new GhostState(world, plane, x, y, h, facing, vx, vy, 0f,
			airborne ? SkaterState.AIRBORNE : SkaterState.BAILED, null, 0, null, 0f, 0, 0f, 0f, 0f, BoardState.DROPPED,
			boardX, boardY, boardH, boardHeading, 0f, stage, lieAngle);
	}

	/** The timeline text of the first {@code count} samples (see GhostTrajectory#encode). */
	static String wire(int timeMs, int ev, float[] eventAgo, int count, float[] ago, float[] xs, float[] ys, float[] hs,
		float[] headings, SkaterState[] states, int refX, int refY, int refH, int refHd)
	{
		GhostTrajectory t = new GhostTrajectory(ago.length);
		t.timeMs = timeMs;
		t.eventAgo = eventAgo;
		t.count = Math.min(count, ago.length);
		System.arraycopy(ago, 0, t.ago, 0, ago.length);
		System.arraycopy(xs, 0, t.x, 0, ago.length);
		System.arraycopy(ys, 0, t.y, 0, ago.length);
		System.arraycopy(hs, 0, t.h, 0, ago.length);
		System.arraycopy(headings, 0, t.heading, 0, ago.length);
		System.arraycopy(states, 0, t.state, 0, ago.length);
		return t.encode(ev, refX, refY, refH, refHd);
	}
}
