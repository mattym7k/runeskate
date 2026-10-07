package com.gielinorskate.physics;

import java.util.Random;

/**
 * The board flying off on its own after a bail: thrown with (most of) the skater's velocity, a random spin and
 * flip, bouncing off walls and the ground of the same {@link CollisionWorld}, then sliding to rest flat, wheels
 * down. Always at rest within {@link #REST_TIMEOUT} seconds. Pure: no client dependency. Units: local units,
 * seconds, radians; yaw 0 = north, clockwise.
 */
public final class BoardBounce
{
	/** Share of the skater's velocity the board flies off with. */
	static final float CARRY = 0.7f;
	/** Pop off the ground (u/s up): this plus up to {@link #POP_RANDOM}. */
	static final float POP = 280f;
	static final float POP_RANDOM = 200f;
	/** Spin (rad/s) about the vertical: this plus up to {@link #SPIN_RANDOM}, either way. */
	static final float SPIN = 3f;
	static final float SPIN_RANDOM = 6f;
	/** Flip (rad/s) about the long axis, either way. */
	static final float FLIP = 10f;
	static final float FLIP_RANDOM = 10f;
	/** Tumble (rad/s) end over end, either way: small. */
	static final float PITCH_SPIN = 1f;
	static final float PITCH_RANDOM = 3f;
	/** Landing faster than this (u/s down) bounces, keeping {@link #BOUNCE} of it. */
	static final float BOUNCE_MIN = 150f;
	static final float BOUNCE = 0.4f;
	/** A bounce keeps this share of the speed along the ground and of the spins. */
	static final float BOUNCE_KEEP = 0.7f;
	/** Sliding deceleration on the ground (u/s^2). */
	static final float FRICTION = 2500f;
	static final float RESTITUTION = 0.5f;
	static final float STOP_SPEED = 5f;
	/** How fast (1/s) it settles flat on the ground. */
	static final float SETTLE_RATE = 15f;
	/** However it bounced, it lies still this many seconds after the throw. */
	public static final float REST_TIMEOUT = 1.6f;
	static final float SUBSTEP = 4f;
	static final float STEP_UP = 24f;
	static final float STEP_DOWN = 48f;
	static final float RADIUS = 10f;
	static final float MAX_SPEED = 4000f;

	private static final float TWO_PI = (float) (2 * Math.PI);

	private final CollisionWorld world;
	private final float gravity;

	private float x;
	private float y;
	private float h;
	private float vx;
	private float vy;
	private float vh;
	private float yaw;
	private float yawRate;
	private float roll;
	private float rollRate;
	private float pitch;
	private float pitchRate;
	private boolean onGround;
	private boolean resting;
	private float age;

	public BoardBounce(CollisionWorld world, float gravity)
	{
		this.world = world;
		this.gravity = gravity;
	}

	/** Throws the board from (x, y, h) with the skater's velocity (vx, vy), nose along {@code heading}. */
	public void start(float x, float y, float h, float vx, float vy, float heading, Random random)
	{
		this.x = finite(x);
		this.y = finite(y);
		this.h = finite(h);
		float sx = finite(vx);
		float sy = finite(vy);
		float s = (float) Math.hypot(sx, sy);
		float scale = s > MAX_SPEED ? MAX_SPEED / s : 1f;
		// a little sideways, so the board and the body part ways
		float side = (random.nextFloat() - 0.5f) * 0.3f;
		this.vx = (sx + sy * side) * scale * CARRY;
		this.vy = (sy - sx * side) * scale * CARRY;
		vh = POP + POP_RANDOM * random.nextFloat();
		yaw = Angles.wrap(finite(heading));
		yawRate = sign(random) * (SPIN + SPIN_RANDOM * random.nextFloat());
		roll = 0f;
		rollRate = sign(random) * (FLIP + FLIP_RANDOM * random.nextFloat());
		pitch = 0f;
		pitchRate = sign(random) * (PITCH_SPIN + PITCH_RANDOM * random.nextFloat());
		onGround = false;
		resting = false;
		age = 0f;
	}

	private static float sign(Random random)
	{
		return random.nextBoolean() ? 1f : -1f;
	}

	public void step(float dt)
	{
		if (resting || !(dt > 0f))
		{
			return;
		}
		age += dt;
		if (age >= REST_TIMEOUT)
		{
			restNow();
			return;
		}
		if (onGround)
		{
			float s = (float) Math.hypot(vx, vy);
			float ns = s - FRICTION * dt;
			if (ns <= STOP_SPEED)
			{
				vx = 0f;
				vy = 0f;
			}
			else
			{
				vx *= ns / s;
				vy *= ns / s;
			}
		}
		else
		{
			vh -= gravity * dt;
		}
		move(vx * dt, vy * dt);

		float ground = world.groundHeight(x, y);
		if (!onGround)
		{
			h += vh * dt;
			if (h <= ground)
			{
				h = ground;
				if (vh < -BOUNCE_MIN)
				{
					vh = -vh * BOUNCE;
					vx *= BOUNCE_KEEP;
					vy *= BOUNCE_KEEP;
					yawRate *= BOUNCE_KEEP;
					rollRate *= 0.5f;
					pitchRate *= 0.5f;
				}
				else
				{
					vh = 0f;
					onGround = true;
				}
			}
		}
		else if (ground < h - STEP_DOWN)
		{
			onGround = false;
			vh = 0f;
		}
		else
		{
			h = ground;
		}

		yaw = Angles.wrap(yaw + yawRate * dt);
		if (!onGround)
		{
			roll += rollRate * dt;
			pitch += pitchRate * dt;
			return;
		}
		// flat on the ground, wheels down: the nearest whole turn of the flip
		float k = 1f - (float) Math.exp(-SETTLE_RATE * dt);
		yawRate -= yawRate * k;
		float flat = Math.round(roll / TWO_PI) * TWO_PI;
		roll += (flat - roll) * k;
		float level = Math.round(pitch / TWO_PI) * TWO_PI;
		pitch += (level - pitch) * k;
		if (vx == 0f && vy == 0f && Math.abs(flat - roll) < 0.01f && Math.abs(level - pitch) < 0.01f)
		{
			restNow();
		}
	}

	/** Puts the board down flat where it is, at once (the skater is standing up and needs to know where it lies). */
	public void restNow()
	{
		h = world.groundHeight(x, y);
		if (Float.isNaN(h) || Float.isInfinite(h))
		{
			h = 0f;
		}
		vx = 0f;
		vy = 0f;
		vh = 0f;
		roll = 0f;
		pitch = 0f;
		yawRate = 0f;
		rollRate = 0f;
		pitchRate = 0f;
		onGround = true;
		resting = true;
	}

	private void move(float dx, float dy)
	{
		float dist = (float) Math.hypot(dx, dy);
		if (dist <= 0f)
		{
			return;
		}
		int n = Math.max(1, (int) Math.ceil(dist / SUBSTEP));
		float sx = dx / n;
		float sy = dy / n;
		for (int i = 0; i < n; i++)
		{
			float nx = x + sx;
			float ny = y + sy;
			if (!blocked(nx, ny))
			{
				x = nx;
				y = ny;
				continue;
			}
			boolean xFree = sx != 0f && !blocked(x + sx, y);
			boolean yFree = sy != 0f && !blocked(x, y + sy);
			if (xFree && !yFree)
			{
				x += sx;
				vy = -vy * RESTITUTION;
				sy = 0f;
			}
			else if (yFree && !xFree)
			{
				y += sy;
				vx = -vx * RESTITUTION;
				sx = 0f;
			}
			else
			{
				vx = -vx * RESTITUTION;
				vy = -vy * RESTITUTION;
				yawRate = -yawRate;
				return;
			}
		}
	}

	private boolean blocked(float nx, float ny)
	{
		if (world.blockerTop(x, y, nx, ny, RADIUS) > h + STEP_UP)
		{
			return true;
		}
		return world.groundHeight(nx, ny) - h > STEP_UP;
	}

	private static float finite(float v)
	{
		return Float.isNaN(v) || Float.isInfinite(v) ? 0f : v;
	}

	public boolean isAtRest()
	{
		return resting;
	}

	public float getX()
	{
		return x;
	}

	public float getY()
	{
		return y;
	}

	/** Height of the board's wheels' contact (where a dropped board's height is measured). */
	public float getH()
	{
		return h;
	}

	public float getVerticalSpeed()
	{
		return vh;
	}

	/** Where the nose points (0 = north, clockwise). */
	public float getYaw()
	{
		return yaw;
	}

	/** Flip about the long axis, raw (0 at rest). */
	public float getRoll()
	{
		return roll;
	}

	/** End over end, raw (0 at rest). */
	public float getPitch()
	{
		return pitch;
	}
}
