package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BoardBounce;
import com.gielinorskate.physics.CollisionWorld;
import java.util.Random;
import net.runelite.api.Client;

/**
 * A snapped board's two halves ({@link HalfBoard}) flying apart from where it broke, each bounced, spun and settled
 * by its own {@link BoardBounce} against the collision world, then lying still until {@link #LIE_SECONDS} after the
 * snap, then sinking out of sight over {@link #SINK_SECONDS} and removed. Cosmetic objects only (RuneLiteObjects of our
 * own). Used for the local skater's tantrum and for party ghosts'. Client thread.
 */
public final class SnappedBoard
{
	/** The halves lie this long after the snap (flight included), then sink away. */
	public static final float LIE_SECONDS = 3f;
	public static final float SINK_SECONDS = 0.5f;
	/** How deep (units) a half has sunk when it is removed: well below a board's height. */
	static final float SINK_DEPTH = 40f;
	/** Speed (u/s) each half is thrown along the board, away from the break, before BoardBounce's carry share. */
	static final float THROW_SPEED = 650f;
	/** Most parts a half is drawn as (the baked board's). */
	private static final int MAX_PARTS = 4;
	/** Physics step for the halves. */
	private static final float STEP = 0.02f;

	private final Client client;
	private final HalfBoard[] halves = new HalfBoard[2];
	private final BoardBounce[] sims = new BoardBounce[2];
	private final BoardController[][] objects = new BoardController[2][MAX_PARTS];
	private final int[] counts = new int[2];
	private boolean registered;
	private float age;
	private float accumulator;
	private boolean flying;

	public SnappedBoard(Client client)
	{
		this.client = client;
	}

	/**
	 * Copies the board's two halves now (before the snap, so the snap itself costs nothing). False when the board
	 * can't snap (the halves can't be made): nothing will be drawn.
	 */
	public boolean prepare(BoardPoser board)
	{
		despawn();
		HalfBoard nose = board == null ? null : board.half(true);
		HalfBoard tail = nose == null ? null : board.half(false);
		halves[0] = nose;
		halves[1] = tail;
		return nose != null && tail != null;
	}

	/** The halves are made and waiting for the snap. */
	public boolean isPrepared()
	{
		return halves[0] != null && halves[1] != null && !flying;
	}

	/**
	 * The board breaks at local ({@code x}, {@code y}), its wheels' contact at up-positive {@code h}, its nose along
	 * {@code yaw} (0 north, clockwise): the halves fly apart along it. {@code world} null: a flat ground at {@code h}.
	 */
	public void snap(CollisionWorld world, float gravity, float x, float y, float h, float yaw, int worldViewId,
		int plane, Random random)
	{
		if (halves[0] == null || halves[1] == null)
		{
			return;
		}
		CollisionWorld w = world != null ? world : flat(h);
		float sx = (float) Math.sin(yaw);
		float sy = (float) Math.cos(yaw);
		for (int k = 0; k < 2; k++)
		{
			HalfBoard half = halves[k];
			// the nose half goes the way the nose points, the tail half the other way
			float dir = k == 0 ? 1f : -1f;
			BoardBounce b = new BoardBounce(w, gravity);
			b.start(x + sx * dir * 20f, y + sy * dir * 20f, h, sx * dir * THROW_SPEED, sy * dir * THROW_SPEED,
				yaw, random);
			sims[k] = b;
			counts[k] = Math.max(1, Math.min(MAX_PARTS, half.partCount()));
			for (int i = 0; i < counts[k]; i++)
			{
				BoardController c = new BoardController(i);
				c.setBoard(half, null);
				c.setWorldView(worldViewId);
				c.setLevel(plane);
				objects[k][i] = c;
			}
		}
		age = 0f;
		accumulator = 0f;
		flying = true;
		place();
		for (int k = 0; k < 2; k++)
		{
			for (int i = 0; i < counts[k]; i++)
			{
				client.registerRuneLiteObject(objects[k][i]);
			}
		}
		registered = true;
	}

	/** The halves are out (flying, lying or sinking). */
	public boolean isActive()
	{
		return flying;
	}

	/** On by {@code dt}: the halves fly, lie and sink; removed at the end. */
	public void update(float dt)
	{
		if (!flying)
		{
			return;
		}
		age += Math.max(0f, dt);
		if (age >= LIE_SECONDS + SINK_SECONDS)
		{
			despawn();
			return;
		}
		accumulator += Math.max(0f, Math.min(0.1f, dt));
		while (accumulator >= STEP)
		{
			sims[0].step(STEP);
			sims[1].step(STEP);
			accumulator -= STEP;
		}
		place();
	}

	private void place()
	{
		float sink = sinkDepth(age);
		for (int k = 0; k < 2; k++)
		{
			BoardBounce b = sims[k];
			HalfBoard half = halves[k];
			int jau = Angles.toJau(b.getYaw());
			// the half's centre is its model origin: that far above where it rests (RuneLite z grows downward)
			int z = -Math.round(b.getH() + half.bottom() - sink);
			for (int i = 0; i < counts[k]; i++)
			{
				BoardController c = objects[k][i];
				c.setX(Math.round(b.getX()));
				c.setY(Math.round(b.getY()));
				c.setZ(z);
				c.setOrientation(jau);
				c.setPose(b.getRoll(), b.getPitch(), 0f, false);
			}
		}
	}

	/** Removes the halves (and forgets them): nothing of this board is drawn any more. */
	public void despawn()
	{
		if (registered)
		{
			for (int k = 0; k < 2; k++)
			{
				for (int i = 0; i < counts[k]; i++)
				{
					if (objects[k][i] != null)
					{
						client.removeRuneLiteObject(objects[k][i]);
					}
				}
			}
		}
		registered = false;
		flying = false;
		for (int k = 0; k < 2; k++)
		{
			halves[k] = null;
			sims[k] = null;
			counts[k] = 0;
			for (int i = 0; i < MAX_PARTS; i++)
			{
				objects[k][i] = null;
			}
		}
	}

	/** How far (units) the halves have sunk {@code age} seconds after the snap: none until they have lain. Pure. */
	public static float sinkDepth(float age)
	{
		if (!(age > LIE_SECONDS))
		{
			return 0f;
		}
		float u = Math.min(1f, (age - LIE_SECONDS) / SINK_SECONDS);
		return SINK_DEPTH * u * u;
	}

	/** Flat ground at {@code h}, nothing in the way (a party ghost's halves outside the local collision world). */
	static CollisionWorld flat(float h)
	{
		return new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return h;
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return h;
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return Float.NEGATIVE_INFINITY;
			}
		};
	}
}
