package com.gielinorskate.physics;

/**
 * The skater's body knocked off the board by a bail: a short hop along the travel direction with a forward tumble
 * about the body's side axis (a wall bail rebounds off the wall and falls backward instead), then the impact and a
 * high-friction skid until it lies still, flat on its back or front. Collides with the same {@link CollisionWorld}
 * as the board (walls, steps, the loaded area's edge); never passes through a wall. Pure: no client dependency.
 * Units: local units, seconds, radians.
 * <p>
 * {@link #getAngle()} is the tumble about the side axis, raw (not wrapped), positive tipping the head toward the
 * facing direction: 0 standing, PI / 2 lying face down with the head forward, 3 PI / 2 (or -PI / 2) lying on the
 * back. {@link #getH()} is the height of the body's lowest point (the ground under it once it lies).
 */
public final class TumbleBody
{
	/** At this speed (u/s) and over, the hop is highest and the tumble longest. */
	static final float FAST = 1500f;
	/** Hop take-off speed (u/s up) at {@link #FAST} and at a standstill: a quarter-second flight at speed. */
	static final float HOP_FAST = 260f;
	static final float HOP_SLOW = 150f;
	/** Share of the skater's speed the body carries off the board (the rest is lost in the knock). */
	static final float CARRY = 0.6f;
	/** A wall bail bounces off the wall at this speed (u/s) along its normal, with this hop. */
	static final float WALL_REBOUND = 180f;
	static final float WALL_HOP = 200f;
	/** Skid deceleration on the ground (u/s^2): from {@link #FAST} carried, about a quarter second. */
	static final float SKID_FRICTION = 3500f;
	/** A bounce off a wall keeps this share of the speed into it. */
	static final float RESTITUTION = 0.3f;
	/** Slower than this (u/s) the skid stops. */
	static final float STOP_SPEED = 5f;
	/** Longest substep of a move, so thin walls are never crossed. */
	static final float SUBSTEP = 4f;
	/** The body steps over this (as the board does) and falls off drops bigger than {@link #STEP_DOWN}. */
	static final float STEP_UP = 24f;
	static final float STEP_DOWN = 48f;
	/** How fast the body settles flat (1/s) once it hits the ground, and the roll in the air. */
	static final float SETTLE_RATE = 25f;
	static final float ROLL_RATE = 10f;
	/** Most sideways roll (radians) in the tumble. */
	static final float MAX_ROLL = 0.4f;
	/** Shortest flight the tumble is timed over, so a tiny hop never spins wildly. */
	static final float MIN_SPIN_TIME = 0.12f;
	/** Speeds and heights are clamped to these, whatever comes in. */
	static final float MAX_SPEED = 4000f;
	/** The puppet's shape for the ground offset: centre of mass above the feet, head above it, half thickness. */
	static final float COM_HEIGHT = 41f;
	static final float HEAD_ABOVE_COM = 49f;
	static final float HALF_THICKNESS = 10f;

	private static final float HALF_PI = (float) (Math.PI / 2);
	private static final float PI = (float) Math.PI;

	private final CollisionWorld world;
	private final float gravity;
	private final float radius;

	private float x;
	private float y;
	private float h;
	private float vx;
	private float vy;
	private float vh;
	private float facing;
	private float angle;
	private float angVel;
	private float lieAngle;
	private float roll;
	private float rollTarget;
	private boolean grounded;
	private boolean landedOnce;
	private boolean impactPending;
	private float airTime;
	private float lastAirTime;

	public TumbleBody(CollisionWorld world, float gravity, float radius)
	{
		this.world = world;
		this.gravity = gravity;
		this.radius = radius;
	}

	/**
	 * Knocks the body off the board at (x, y), feet at height h, moving at (vx, vy).
	 *
	 * @param facing where the skater faced (the board heading): the tumble's facing for a wall bail or a bail
	 *     standing still; otherwise the body faces along its travel
	 * @param wall the bail was into a wall with outward normal (wallNx, wallNy): it rebounds off it, backward
	 * @param rollSeed -1..1, the side the body rolls to as it tumbles
	 */
	public void start(float x, float y, float h, float vx, float vy, float facing, boolean wall, float wallNx,
		float wallNy, float rollSeed)
	{
		this.x = finite(x);
		this.y = finite(y);
		this.h = finite(h);
		float speed = (float) Math.hypot(finite(vx), finite(vy));
		float frac = Math.max(0f, Math.min(1f, speed / FAST));
		float target;
		float nLen = (float) Math.hypot(finite(wallNx), finite(wallNy));
		if (wall && nLen > 1e-3f)
		{
			this.vx = wallNx / nLen * WALL_REBOUND;
			this.vy = wallNy / nLen * WALL_REBOUND;
			vh = WALL_HOP;
			this.facing = Angles.wrap(finite(facing));
			// knocked back off the wall: onto the back
			target = -HALF_PI;
		}
		else
		{
			float carried = Math.min(speed, MAX_SPEED) * CARRY;
			this.vx = speed > 0f ? finite(vx) / speed * carried : 0f;
			this.vy = speed > 0f ? finite(vy) / speed * carried : 0f;
			vh = HOP_SLOW + (HOP_FAST - HOP_SLOW) * frac;
			this.facing = speed > 50f ? (float) Math.atan2(vx, vy) : Angles.wrap(finite(facing));
			// slow: a fall onto the face; faster: over onto the back; fast: a turn and a quarter, a face-plant slide
			target = frac < 0.35f ? HALF_PI : frac < 0.7f ? 3 * HALF_PI : 5 * HALF_PI;
		}
		float drop = Math.max(0f, this.h - world.groundHeight(this.x, this.y));
		float flight = (vh + (float) Math.sqrt(vh * vh + 2 * gravity * drop)) / gravity;
		angle = 0f;
		angVel = target / Math.max(MIN_SPIN_TIME, flight);
		lieAngle = target;
		roll = 0f;
		rollTarget = Math.max(-1f, Math.min(1f, finite(rollSeed))) * MAX_ROLL;
		grounded = false;
		landedOnce = false;
		impactPending = false;
		airTime = 0f;
		lastAirTime = 0f;
	}

	public void step(float dt)
	{
		if (!(dt > 0f))
		{
			return;
		}
		if (grounded)
		{
			float s = (float) Math.hypot(vx, vy);
			float ns = s - SKID_FRICTION * dt;
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
			airTime += dt;
		}
		move(vx * dt, vy * dt);

		float ground = world.groundHeight(x, y);
		if (!grounded)
		{
			h += vh * dt;
			if (h <= ground)
			{
				land(ground);
			}
		}
		else if (ground < h - STEP_DOWN)
		{
			// slid off a drop: falls again
			grounded = false;
			vh = 0f;
			airTime = 0f;
		}
		else
		{
			h = ground;
		}

		if (!landedOnce)
		{
			angle += angVel * dt;
			roll += (rollTarget - roll) * (1f - (float) Math.exp(-ROLL_RATE * dt));
		}
		else
		{
			float k = 1f - (float) Math.exp(-SETTLE_RATE * dt);
			angle += (lieAngle - angle) * k;
			roll -= roll * k;
			if (Math.abs(lieAngle - angle) < 0.005f)
			{
				angle = lieAngle;
			}
			if (Math.abs(roll) < 0.005f)
			{
				roll = 0f;
			}
		}
	}

	private void land(float ground)
	{
		h = ground;
		vh = 0f;
		grounded = true;
		lastAirTime = airTime;
		airTime = 0f;
		if (!landedOnce)
		{
			landedOnce = true;
			impactPending = true;
			// flat on whichever side it came down nearer to
			lieAngle = (float) (Math.round((angle - HALF_PI) / PI) * PI + HALF_PI);
		}
	}

	/** Moves by (dx, dy) in substeps, bouncing off whatever blocks (a wall, a step up, the loaded area's edge). */
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
				return;
			}
		}
	}

	private boolean blocked(float nx, float ny)
	{
		if (world.blockerTop(x, y, nx, ny, radius) > h + STEP_UP)
		{
			return true;
		}
		return world.groundHeight(nx, ny) - h > STEP_UP;
	}

	/** Ends the skid at once (the get-up starts where the body lies). */
	public void stop()
	{
		vx = 0f;
		vy = 0f;
	}

	/** True once, at the first touchdown after the knock-off (the impact). */
	public boolean takeImpact()
	{
		boolean i = impactPending;
		impactPending = false;
		return i;
	}

	/** On the ground, not moving, lying flat. */
	public boolean isAtRest()
	{
		return grounded && vx == 0f && vy == 0f && angle == lieAngle && roll == 0f;
	}

	public boolean isAirborne()
	{
		return !grounded;
	}

	/** Touched down at least once since the knock-off. */
	public boolean hasLanded()
	{
		return landedOnce;
	}

	/**
	 * The puppet's height above {@link #getH()} that keeps its lowest point on it when tumbled by {@code angle}
	 * about the centre of mass: 0 standing, about -31 lying (the body comes down flat), a little up when upside
	 * down (the head is below the feet).
	 */
	public static float groundOffset(float angle)
	{
		float c = (float) Math.cos(angle);
		float s = Math.abs((float) Math.sin(angle));
		float feet = COM_HEIGHT - COM_HEIGHT * c;
		float head = COM_HEIGHT + HEAD_ABOVE_COM * c;
		return -(Math.min(feet, head) - HALF_THICKNESS * s);
	}

	private static float finite(float v)
	{
		return Float.isNaN(v) || Float.isInfinite(v) ? 0f : v;
	}

	public float getX()
	{
		return x;
	}

	public float getY()
	{
		return y;
	}

	public float getH()
	{
		return h;
	}

	public float getVelocityX()
	{
		return vx;
	}

	public float getVelocityY()
	{
		return vy;
	}

	/** Up-positive vertical speed (0 on the ground). */
	public float getVerticalSpeed()
	{
		return vh;
	}

	public float getSpeed()
	{
		return (float) Math.hypot(vx, vy);
	}

	/** Which way the body faces: the tumble turns it about the side axis of this. */
	public float getFacing()
	{
		return facing;
	}

	public float getAngle()
	{
		return angle;
	}

	/** The lying angle the tumble settles at (the planned one until the impact). */
	public float getLieAngle()
	{
		return lieAngle;
	}

	/** Sideways roll, radians. */
	public float getRoll()
	{
		return roll;
	}

	/** Seconds of the latest flight that ended on the ground (the impact's, for the camera kick). */
	public float getLastAirTime()
	{
		return lastAirTime;
	}
}
