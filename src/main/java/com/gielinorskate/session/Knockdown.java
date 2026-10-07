package com.gielinorskate.session;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BoardBounce;
import com.gielinorskate.physics.CollisionWorld;
import com.gielinorskate.physics.TumbleBody;
import java.util.Random;

/**
 * A bail that knocks the skater off the board: the body is thrown and tumbles ({@link TumbleBody}) while the board
 * flies off on its own ({@link BoardBounce}); after the impact the skater lies still for {@link #LIE_SECONDS}, then
 * gets up over {@link #GET_UP_SECONDS} and is on foot. Short by design: never more than {@link #MAX_TOTAL} seconds
 * from the bail to standing, however far the fall. Pure: the session steps it, feeds it the keys and acts on its
 * {@link Outcome}s. Client thread only.
 * <p>
 * Keys: R skips the rest and puts the skater straight back on the board; during the lie-down a new move key or a
 * jump skips to the get-up, and the board key ends it at once to get the board back. In a Skate Duel none of that
 * works (the whole sequence plays; R stays off) so it is the same for both sides.
 */
public final class Knockdown
{
	/** The flight is cut short (the lie-down starts) after this, even mid-fall. */
	public static final float MAX_TUMBLE = 0.8f;
	/** Lying still after the impact. */
	public static final float LIE_SECONDS = 0.5f;
	/** Getting back up. */
	public static final float GET_UP_SECONDS = 0.4f;
	/** The longest the whole sequence can take (plus a physics step per phase). */
	public static final float MAX_TOTAL = MAX_TUMBLE + LIE_SECONDS + GET_UP_SECONDS;

	private static final float TWO_PI = (float) (2 * Math.PI);

	public enum Phase
	{
		/** Thrown off, in the air. */
		TUMBLE,
		/** Down after the impact (still skidding to a stop at first). */
		LIE,
		/** Getting up where the body lies. */
		GET_UP,
		/** Over: standing (or skipped). */
		DONE
	}

	/** What a key did. */
	public enum Outcome
	{
		NONE,
		/** R: back on the board where the body lies. */
		BACK_ON_BOARD,
		/** The board key while down: stand now and get the board back (step on it nearby, else call it). */
		RECLAIM
	}

	private final TumbleBody body;
	private final BoardBounce board;
	private Phase phase = Phase.DONE;
	private float phaseTime;
	private float total;
	private boolean dueling;
	private boolean impactPending;
	/** A move key was held at the last input (a key held through the bail must be pressed again to skip). */
	private boolean moveWasHeld = true;
	/** The lying angle the get-up starts from. */
	private float getUpFrom;

	public Knockdown(CollisionWorld world, float gravity, float radius)
	{
		body = new TumbleBody(world, gravity, radius);
		board = new BoardBounce(world, gravity);
	}

	/**
	 * Knocks the skater at (x, y, h), moving at (vx, vy) and facing {@code heading}, off the board.
	 *
	 * @param wall a wall bail, the wall's outward normal (nx, ny)
	 * @param dueling in a Skate Duel: no skipping
	 */
	public void start(float x, float y, float h, float vx, float vy, float heading, boolean wall, float nx, float ny,
		boolean dueling, Random random)
	{
		body.start(x, y, h, vx, vy, heading, wall, nx, ny, random.nextFloat() * 2f - 1f);
		board.start(x, y, h, vx, vy, heading, random);
		this.dueling = dueling;
		phase = Phase.TUMBLE;
		phaseTime = 0f;
		total = 0f;
		impactPending = false;
		moveWasHeld = true;
	}

	/** One fixed step of the body, the board and the phase timing. */
	public void step(float dt)
	{
		if (phase == Phase.DONE || !(dt > 0f))
		{
			return;
		}
		body.step(dt);
		board.step(dt);
		if (body.takeImpact())
		{
			impactPending = true;
		}
		phaseTime += dt;
		total += dt;
		switch (phase)
		{
			case TUMBLE:
				if (body.hasLanded() || phaseTime >= MAX_TUMBLE)
				{
					enter(Phase.LIE);
				}
				break;
			case LIE:
				if (phaseTime >= LIE_SECONDS)
				{
					startGetUp();
				}
				break;
			case GET_UP:
				if (phaseTime >= GET_UP_SECONDS)
				{
					finish();
				}
				break;
			default:
				break;
		}
	}

	/**
	 * This frame's keys.
	 *
	 * @param reset R went down
	 * @param moveHeld a move key (W, A, S, D) is held now: a new press skips the lie-down
	 * @param jumpPressed Space went down
	 * @param boardPressed the board key went down
	 */
	public Outcome input(boolean reset, boolean moveHeld, boolean jumpPressed, boolean boardPressed)
	{
		boolean movePressed = moveHeld && !moveWasHeld;
		moveWasHeld = moveHeld;
		if (phase == Phase.DONE || dueling)
		{
			return Outcome.NONE;
		}
		if (reset)
		{
			finish();
			return Outcome.BACK_ON_BOARD;
		}
		if (phase == Phase.LIE || phase == Phase.GET_UP)
		{
			if (boardPressed)
			{
				finish();
				return Outcome.RECLAIM;
			}
			if (phase == Phase.LIE && (movePressed || jumpPressed))
			{
				startGetUp();
			}
		}
		return Outcome.NONE;
	}

	private void enter(Phase p)
	{
		phase = p;
		phaseTime = 0f;
	}

	private void startGetUp()
	{
		body.stop();
		getUpFrom = body.getAngle();
		enter(Phase.GET_UP);
	}

	private void finish()
	{
		body.stop();
		if (!board.isAtRest())
		{
			board.restNow();
		}
		enter(Phase.DONE);
	}

	/** True once, at the body's first touchdown (sounds, smoke, the camera kick). */
	public boolean takeImpact()
	{
		boolean i = impactPending;
		impactPending = false;
		return i;
	}

	public boolean isDone()
	{
		return phase == Phase.DONE;
	}

	public Phase getPhase()
	{
		return phase;
	}

	/** 0..1 through the current lie-down or get-up (0 in the air, 1 when done). */
	public float getPhaseProgress()
	{
		switch (phase)
		{
			case LIE:
				return Math.min(1f, phaseTime / LIE_SECONDS);
			case GET_UP:
				return Math.min(1f, phaseTime / GET_UP_SECONDS);
			case DONE:
				return 1f;
			default:
				return 0f;
		}
	}

	/** Seconds since the bail. */
	public float getTotalTime()
	{
		return total;
	}

	public boolean isDueling()
	{
		return dueling;
	}

	public TumbleBody getBody()
	{
		return body;
	}

	public BoardBounce getBoard()
	{
		return board;
	}

	/**
	 * The body's tumble angle as drawn: the body sim's in the air and lying, easing (smoothstep) from the lying
	 * angle to the nearest whole turn, upright, through the get-up.
	 */
	public float bodyAngle()
	{
		if (phase == Phase.GET_UP || phase == Phase.DONE)
		{
			float from = phase == Phase.GET_UP ? getUpFrom : body.getAngle();
			float upright = Math.round(from / TWO_PI) * TWO_PI;
			if (phase == Phase.DONE)
			{
				return upright;
			}
			float u = getPhaseProgress();
			float s = u * u * (3f - 2f * u);
			return from + (upright - from) * s;
		}
		return body.getAngle();
	}

	/** Where the skater faces once up: toward the board (the body's facing when the board is right there). */
	public float standHeading()
	{
		float dx = board.getX() - body.getX();
		float dy = board.getY() - body.getY();
		if (Math.hypot(dx, dy) < 1f)
		{
			return body.getFacing();
		}
		return Angles.wrap((float) Math.atan2(dx, dy));
	}
}
