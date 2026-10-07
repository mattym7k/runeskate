package com.gielinorskate.render;

/**
 * Applies a {@link BodyPose} to a player mesh's vertex arrays in place. Pure and allocation-free.
 *
 * <p>Puppet model space: x along the board (the puppet is drawn turned 90 degrees from the board), y down
 * with the soles at y = 0, z out of the back (actors face -z). How it maps onto the board's own model
 * space follows from RuneLite's {@code Perspective.modelToCanvas} (model x -> local x, model z -> local y,
 * {@code x' = x cos + z sin}, {@code z' = z cos - x sin}) and the puppet being drawn 512 orientation units
 * past the board: board x = puppet z, board z = -puppet x. So the same physical roll as the board's
 * {@code BoardGeometry.rotate} roll is the (z, y) rotation below, and the same pitch as
 * {@code BoardPlacement.pose} is the (x, y) rotation below.
 *
 * <p>Order per vertex: knees bend (legs squash below the hips, the upper body drops rigidly; in a grab the knees
 * fold up toward the chest instead and the feet rise to the lifted board), the torso folds forward at the hips,
 * then the whole body pitches about the contact truck and rolls about the board's long axis through the feet; in
 * a grab the hand then reaches for the board and the free arm swings out; last, a front or back flip turns the
 * whole body about the centre of mass ({@link BodyPose#flip}), the same rotation {@link BoardPlacement#pose} gives
 * the board.
 */
public final class MeshDeformer
{
	/** Hip height as a fraction of the model's height. */
	static final float HIP_FRACTION = 0.45f;
	static final float MIN_HIP = 50f;
	static final float MAX_HIP = 120f;
	/** Model height used when the mesh has no vertex above the soles. */
	static final float DEFAULT_HEIGHT = 200f;
	/** Units above the hips over which the torso fold blends in. */
	static final float BEND_BAND = 30f;

	private MeshDeformer()
	{
	}

	/** Hip height (positive units above the soles) of a mesh whose highest vertex is at {@code topY}. */
	static float hipHeight(float topY)
	{
		float height = topY < 0f ? -topY : DEFAULT_HEIGHT;
		return Math.max(MIN_HIP, Math.min(MAX_HIP, HIP_FRACTION * height));
	}

	/** Deforms the first {@code n} vertices of {@code xs, ys, zs} by {@code pose}. */
	public static void deform(float[] xs, float[] ys, float[] zs, int n, BodyPose pose)
	{
		float top = 0f;
		for (int i = 0; i < n; i++)
		{
			top = Math.min(top, ys[i]);
		}
		float hip = hipHeight(top);
		Body body = Body.set(pose, hip, pose.legWeight > 1e-4f ? Leg.solve(xs, ys, zs, n, hip, pose.legScale, pose) : null);
		if (body.folding)
		{
			body.hz = legCentreZ(ys, zs, n, hip);
		}
		float cf = (float) Math.cos(pose.flip);
		float sf = (float) Math.sin(pose.flip);
		boolean flipping = Math.abs(pose.flip) > 1e-6f;
		float fy = pose.flipPivotY;
		// a grab: both arms' weights and shoulders from the mesh as animated, before anything moves
		boolean grabbing = pose.armWeight > 1e-4f || pose.kneeFold > 1e-4f;
		// a board held in both hands (the tantrum): each hand to its side of the hold point; a grab wins over it
		boolean lifting = !grabbing && pose.liftWeight > 1e-4f;
		Arm arm = grabbing ? Arm.find(Arm.GRAB, xs, ys, zs, n, pose.armSide)
			: lifting ? Arm.find(Arm.GRAB, xs, ys, zs, n, -1f) : null;
		Arm free = arm != null ? Arm.find(Arm.FREE, xs, ys, zs, n, grabbing ? -pose.armSide : 1f) : null;
		// in a grab the arms go with the upper body, never squashed down with the legs: a hand hanging below the hip
		// line stayed at the crotch while the chest folded forward
		float hold = arm == null ? 0f : lifting ? clamp01(pose.liftWeight)
			: Math.max(clamp01(pose.armWeight), clamp01(pose.kneeFold));
		for (int i = 0; i < n; i++)
		{
			float rigid = 0f;
			if (arm != null)
			{
				rigid = hold * (free == null ? arm.weights[i] : Math.max(arm.weights[i], free.weights[i]));
			}
			body.apply(xs[i], ys[i], zs[i], rigid);
			float x = body.x;
			float y = body.y;
			float z = body.z;
			if (flipping && arm == null)
			{
				// the whole body turns about the centre of mass with the board (BoardPlacement.pose's flip)
				float ry = y - fy;
				float fx = x * cf - ry * sf;
				y = x * sf + ry * cf + fy;
				x = fx;
			}
			xs[i] = x;
			ys[i] = y;
			zs[i] = z;
		}
		if (arm == null)
		{
			return;
		}
		if (lifting)
		{
			float w = clamp01(pose.liftWeight);
			arm.reachTo(xs, ys, zs, n, body, pose.liftX + arm.side * pose.liftSpread, pose.liftY, pose.liftZ, w);
			if (free != null)
			{
				free.reachTo(xs, ys, zs, n, body, pose.liftX + free.side * pose.liftSpread, pose.liftY, pose.liftZ, w);
			}
		}
		// the hand reaches for the board where it is drawn, the other arm swings out for balance (before the flip,
		// which turns body and board together)
		if (pose.armWeight > 1e-4f)
		{
			arm.reach(xs, ys, zs, n, body, pose);
			if (free != null)
			{
				free.balance(xs, ys, zs, n, body, clamp01(pose.armWeight));
			}
		}
		if (flipping)
		{
			for (int i = 0; i < n; i++)
			{
				float ry = ys[i] - fy;
				float fx = xs[i] * cf - ry * sf;
				ys[i] = xs[i] * sf + ry * cf + fy;
				xs[i] = fx;
			}
		}
	}

	/** {@code v} clamped to 0..1; NaN is 0. */
	private static float clamp01(float v)
	{
		return v > 0f ? Math.min(1f, v) : 0f;
	}

	/** Mean z of the lower legs (below the hanging hands): the knees fold about it. */
	private static float legCentreZ(float[] ys, float[] zs, int n, float hip)
	{
		float sum = 0f;
		int count = 0;
		for (int i = 0; i < n; i++)
		{
			if (ys[i] > -ArmLocator.LOWER_LEGS * hip)
			{
				sum += zs[i];
				count++;
			}
		}
		return count == 0 ? 0f : sum / count;
	}

	/** The knees never fold tighter than this (cos of the thigh's angle from straight down); the legs shorten instead. */
	static final float MIN_FOLD_COS = 0.15f;
	/** Foot height (ankle above the sole) as a fraction of the hip height: below it the foot stays flat on the board. */
	static final float FOOT_FRACTION = 0.1f;
	/** Units either side of the ankle over which the foot's counter-turn blends in. */
	static final float ANKLE_BAND = 4f;

	/**
	 * The whole-body part of the pose for one point, flip excluded: the knees (or the push leg), the torso fold and
	 * lean above the hips, then the pitch about the contact truck and the roll about the board's long axis. One
	 * reused instance (client thread only).
	 */
	private static final class Body
	{
		private static final Body SCRATCH = new Body();

		float hip;
		float s;
		float hipY;
		float cb;
		float sb;
		float cl;
		float sl;
		float cp;
		float sp;
		float cr;
		float sr;
		float px;
		Leg leg;
		/** A grab's knee fold (or a lifted board): the legs reach from the hips to the feet at -lift. */
		boolean folding;
		float fold;
		/** Hip-to-sole distance over the hip height. */
		float k;
		/** The leg's length factor, when the fold alone cannot reach (k above 1 or below MIN_FOLD_COS). */
		float len;
		/** The thigh's angle from straight down (the shin turns back by the same), with its cos and sin. */
		float knee;
		float ck;
		float sk;
		/** Model z the legs fold about. */
		float hz;
		/** Units the soles are lifted (with the board): the pitch and roll turn about them there. */
		float lift;
		/** Output of {@link #apply}. */
		float x;
		float y;
		float z;

		static Body set(BodyPose pose, float hip, Leg leg)
		{
			Body b = SCRATCH;
			b.hip = hip;
			b.s = pose.legScale;
			b.hipY = -hip * pose.legScale;
			// the torso folds toward the chest (-z): the roll rotation by -bend
			b.cb = (float) Math.cos(-pose.torsoBend);
			b.sb = (float) Math.sin(-pose.torsoBend);
			b.cl = (float) Math.cos(pose.torsoLean);
			b.sl = (float) Math.sin(pose.torsoLean);
			b.cp = (float) Math.cos(pose.pitch);
			b.sp = (float) Math.sin(pose.pitch);
			b.cr = (float) Math.cos(pose.roll);
			b.sr = (float) Math.sin(pose.roll);
			b.px = pose.pivotX;
			b.leg = leg;
			float lift = Float.isNaN(pose.boardLift) ? 0f : Math.max(0f, Math.min(hip, pose.boardLift));
			b.lift = lift;
			b.fold = clamp01(pose.kneeFold);
			b.folding = b.fold > 1e-4f || lift > 1e-4f;
			b.hz = 0f;
			if (b.folding)
			{
				// the hips to the soles: the folded thigh and shin (to the ankle) plus the flat foot below it
				b.k = (hip * pose.legScale - lift) / hip;
				float c;
				if (b.k >= 1f)
				{
					c = 1f;
					b.len = b.k;
				}
				else
				{
					c = (b.k - FOOT_FRACTION) / (1f - FOOT_FRACTION);
					b.len = 1f;
					if (!(c >= MIN_FOLD_COS))
					{
						c = MIN_FOLD_COS;
						b.len = Math.max(0f, b.k) / ((1f - FOOT_FRACTION) * MIN_FOLD_COS + FOOT_FRACTION);
					}
				}
				b.knee = (float) Math.acos(c);
				b.ck = c;
				b.sk = (float) Math.sin(b.knee);
			}
			return b;
		}

		/**
		 * Poses one point; {@code rigid} (0..1) moves it with the upper body even below the hip line (a grab's arms,
		 * whose hands hang lower than the hips).
		 */
		void apply(float x, float y, float z, float rigid)
		{
			if (y > -hip)
			{
				float w = leg == null ? 0f : leg.weight(x, y);
				float lx = x;
				float ly;
				float lz = z;
				if (w > 0f)
				{
					leg.move(x, y, z, w);
					lx = leg.x;
					ly = leg.y;
					lz = leg.z;
				}
				else if (folding)
				{
					foldLeg(z, y + hip);
					ly = this.y;
					lz = this.z;
				}
				else
				{
					ly = y * s;
				}
				if (rigid > 0f)
				{
					torso(x, y + hip, z, 1f);
					x = lx + (this.x - lx) * rigid;
					y = ly + (this.y - ly) * rigid;
					z = lz + (this.z - lz) * rigid;
				}
				else
				{
					x = lx;
					y = ly;
					z = lz;
				}
			}
			else
			{
				// height above the hips (negative), the same before and after the knees bend
				float dy = y + hip;
				// the fold and lean fade in over BEND_BAND above the hips, so there is no crease at the hip line
				float w = Math.min(1f, -dy / BEND_BAND);
				torso(x, dy, z, w + (1f - w) * rigid);
				x = this.x;
				y = this.y;
				z = this.z;
			}
			// about the soles: lifted with the board in a grab, so the feet stay on it
			y += lift;
			float dx = x - px;
			x = dx * cp - y * sp + px;
			y = dx * sp + y * cp;
			float z2 = z * cr - y * sr;
			y = z * sr + y * cr;
			this.x = x;
			this.y = y - lift;
			this.z = z2;
		}

		/**
		 * The upper body's fold and roll (not its lean along the board, nor the pitch) of the direction (dx, dy, dz),
		 * into x, y, z.
		 */
		void turnDirection(float dx, float dy, float dz)
		{
			float bz = dz * cb - dy * sb;
			float by = dz * sb + dy * cb;
			this.x = dx;
			this.y = bz * sr + by * cr;
			this.z = bz * cr - by * sr;
		}

		/** The torso's fold and lean about the hips by weight {@code w}; {@code dy} is the height above the hips (y down). */
		private void torso(float x, float dy, float z, float w)
		{
			float bz = z * cb - dy * sb;
			float by = z * sb + dy * cb;
			z += (bz - z) * w;
			dy += (by - dy) * w;
			float lx = x * cl - dy * sl;
			float ly = x * sl + dy * cl;
			x += (lx - x) * w;
			dy += (ly - dy) * w;
			this.x = x;
			this.y = dy + hipY;
			this.z = z;
		}

		/**
		 * A grab's legs for a point {@code rest} below the hips: squashed straight down to the feet at hip * k below
		 * the hips, and by {@link #fold} with the knees folded up toward the chest instead: the thigh swings forward by
		 * the knee angle, the shin back by the same, the foot stays flat; the sole lands on the same spot either way.
		 * The fold fades in below the hips (no crease at the hip line). Writes y and z.
		 */
		private void foldLeg(float z, float rest)
		{
			float squash = hipY + rest * k;
			float a = fold * PushCycle.smoothstep(0f, LEG_BLEND * hip, rest);
			if (a <= 0f)
			{
				this.y = squash;
				this.z = z;
				return;
			}
			float uz = z - hz;
			float uy = rest * len;
			// the knee halfway from the hip to the ankle, so the shin brings the foot back under the hip
			float ankleY = (1f - FOOT_FRACTION) * hip * len;
			float kneeY = 0.5f * ankleY;
			// the foot turns forward about the ankle (flat again once the shin has turned back)
			float t = knee * PushCycle.smoothstep(ankleY - ANKLE_BAND, ankleY + ANKLE_BAND, uy);
			float c = (float) Math.cos(t);
			float sn = (float) Math.sin(t);
			float ry = uy - ankleY;
			float nz = uz * c - ry * sn;
			uy = uz * sn + ry * c + ankleY;
			uz = nz;
			// the shin turns back about the knee by twice the knee angle...
			t = -2f * knee * PushCycle.smoothstep(kneeY - KNEE_BAND, kneeY + KNEE_BAND, uy);
			c = (float) Math.cos(t);
			sn = (float) Math.sin(t);
			ry = uy - kneeY;
			nz = uz * c - ry * sn;
			uy = uz * sn + ry * c + kneeY;
			uz = nz;
			// ...and the whole leg forward about the hip
			nz = uz * ck - uy * sk;
			uy = uz * sk + uy * ck;
			uz = nz;
			this.y = squash + (hipY + uy - squash) * a;
			this.z = z + (hz + uz - z) * a;
		}
	}

	/** The grabbing arm stretches at most this much to reach the board (blocky models hide a lot). */
	static final float MAX_ARM_STRETCH = 1.6f;
	/** ...and shortens at most to this when the board is nearer than the hand. */
	static final float MIN_ARM_STRETCH = 0.7f;
	/**
	 * The free arm's direction for balance in a grab, in the upper body's own frame (it folds and leans with it):
	 * out along the board on its own side, a little up and back (against the fold, so it ends a little above level).
	 */
	static final float BALANCE_OUT = 0.8f;
	static final float BALANCE_UP = 0.1f;
	static final float BALANCE_BACK = 0.35f;

	/**
	 * A grab's arms ({@link ArmLocator}): the grabbing one turns about the shoulder so the hand points at the grab
	 * point on the board ({@link GrabReach}), stretching (capped) to reach it; the free one swings out for balance.
	 * Each vertex follows by its arm weight times the pose's arm weight, so the arm eases in and out and never tears
	 * from the shoulder. At arm weight 0 it is exactly the plain body. Two reused instances (client thread only).
	 */
	private static final class Arm
	{
		static final Arm GRAB = new Arm();
		static final Arm FREE = new Arm();

		private final ArmLocator locator = new ArmLocator();
		private final float[] target = new float[3];
		/** Each vertex's arm weight, from the mesh before it moved; grown, never shrunk. */
		private float[] weights = new float[0];
		private float side;
		private float sx;
		private float sy;
		private float sz;
		private int handIndex;

		/** The arm on {@code side}, found into {@code a}, or null when the mesh has no arm there. */
		static Arm find(Arm a, float[] xs, float[] ys, float[] zs, int n, float side)
		{
			if (!a.locator.locate(xs, ys, zs, n, side))
			{
				return null;
			}
			if (a.weights.length < n)
			{
				a.weights = new float[n];
			}
			for (int i = 0; i < n; i++)
			{
				a.weights[i] = a.locator.weight(xs[i], ys[i]);
			}
			a.side = side < 0f ? -1f : 1f;
			a.sx = a.locator.shoulder[0];
			a.sy = a.locator.shoulder[1];
			a.sz = a.locator.shoulder[2];
			a.handIndex = a.locator.handIndex;
			return a;
		}

		/** Turns the (already posed) arm toward the board. */
		void reach(float[] xs, float[] ys, float[] zs, int n, Body body, BodyPose pose)
		{
			// the shoulder goes where the body put the arm's root
			body.apply(sx, sy, sz, 1f);
			float ox = body.x;
			float oy = body.y;
			float oz = body.z;
			// a lifted board is that much nearer the hips: the opposite of the deck lift
			GrabReach.target(pose.grabAlong, pose.grabAcross, pose.grabBoardY, pose.forwardX, pose.boardRoll,
				pose.boardPitch, pose.deckLift - pose.boardLift, target);
			turn(xs, ys, zs, n, ox, oy, oz, target[0], target[1], target[2], MIN_ARM_STRETCH, MAX_ARM_STRETCH,
				clamp01(pose.armWeight));
		}

		/** Turns the (already posed) arm so the hand points at (tx, ty, tz), by {@code weight}. */
		void reachTo(float[] xs, float[] ys, float[] zs, int n, Body body, float tx, float ty, float tz, float weight)
		{
			body.apply(sx, sy, sz, 1f);
			turn(xs, ys, zs, n, body.x, body.y, body.z, tx, ty, tz, MIN_ARM_STRETCH, MAX_ARM_STRETCH, weight);
		}

		/** Swings the (already posed) free arm out for balance, by {@code weight}. */
		void balance(float[] xs, float[] ys, float[] zs, int n, Body body, float weight)
		{
			body.apply(sx, sy, sz, 1f);
			float ox = body.x;
			float oy = body.y;
			float oz = body.z;
			// out an arm's length in the upper body's frame, folded and rolled with it (not tipped with a nose or
			// tail grab's lean, which would point it at the ground)
			float l = locator.armLength;
			body.turnDirection(side * BALANCE_OUT * l, -BALANCE_UP * l, BALANCE_BACK * l);
			turn(xs, ys, zs, n, ox, oy, oz, ox + body.x, oy + body.y, oz + body.z, 1f, 1f, weight);
		}

		/**
		 * Turns this arm's vertices about the shoulder (ox, oy, oz) so the hand points at (tx, ty, tz), stretching it
		 * by the distance ratio within [minStretch, maxStretch], each vertex by its arm weight times {@code reach}.
		 */
		private void turn(float[] xs, float[] ys, float[] zs, int n, float ox, float oy, float oz, float tx, float ty,
			float tz, float minStretch, float maxStretch, float reach)
		{
			float ux = xs[handIndex] - ox;
			float uy = ys[handIndex] - oy;
			float uz = zs[handIndex] - oz;
			tx -= ox;
			ty -= oy;
			tz -= oz;
			float lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
			float lt = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (!(lu > 1f) || !(lt > 1f) || Float.isInfinite(lu) || Float.isInfinite(lt))
			{
				return;
			}
			// axis u x t, angle between them
			float kx = uy * tz - uz * ty;
			float ky = uz * tx - ux * tz;
			float kz = ux * ty - uy * tx;
			float kl = (float) Math.sqrt(kx * kx + ky * ky + kz * kz);
			float dot = ux * tx + uy * ty + uz * tz;
			float angle = (float) Math.atan2(kl, dot);
			if (kl < 1e-4f * lu * lt)
			{
				if (dot > 0f)
				{
					angle = 0f;
					kx = 0f;
					ky = 0f;
					kz = 1f;
				}
				else
				{
					// straight back: turn about any axis across the arm
					kx = 0f;
					ky = 0f;
					kz = 1f;
					if (Math.abs(uz) > 0.9f * lu)
					{
						kx = 1f;
						kz = 0f;
					}
				}
			}
			else
			{
				kx /= kl;
				ky /= kl;
				kz /= kl;
			}
			float stretch = Math.max(minStretch, Math.min(maxStretch, lt / lu));
			if (Float.isNaN(angle) || Float.isNaN(stretch))
			{
				return;
			}
			for (int i = 0; i < n; i++)
			{
				float a = weights[i] * reach;
				if (a <= 0f)
				{
					continue;
				}
				float vx = xs[i] - ox;
				float vy = ys[i] - oy;
				float vz = zs[i] - oz;
				// Rodrigues: v cos + (k x v) sin + k (k . v)(1 - cos), by this vertex's share of the turn
				float c = (float) Math.cos(angle * a);
				float sn = (float) Math.sin(angle * a);
				float kd = (kx * vx + ky * vy + kz * vz) * (1f - c);
				float rx = vx * c + (ky * vz - kz * vy) * sn + kx * kd;
				float ry = vy * c + (kz * vx - kx * vz) * sn + ky * kd;
				float rz = vz * c + (kx * vy - ky * vx) * sn + kz * kd;
				float scale = 1f + (stretch - 1f) * a;
				xs[i] = ox + rx * scale;
				ys[i] = oy + ry * scale;
				zs[i] = oz + rz * scale;
			}
		}
	}

	/** Units below the hip line over which the push leg blends in, as a fraction of the hip height. */
	static final float LEG_BLEND = 0.25f;
	/** Units past the body's centre plane over which the push leg blends in (the crotch). */
	static final float SIDE_BAND = 4f;
	/** Units either side of the knee over which the shin's extra bend blends in. */
	static final float KNEE_BAND = 8f;
	/** The leg stretches at most this much to reach a far foot target (blocky models hide 30%). */
	static final float MAX_STRETCH = 1.3f;

	/**
	 * The push leg: every vertex below the hips on the {@link BodyPose#legSide} side of the body's centre
	 * plane. Two-bone IK with the knee halfway down: the knee bends forward (toward the chest, -z) when the
	 * target is nearer than the leg is long, the leg stretches (capped) when it is further; the leg then
	 * swings about the hip in the board plane to point at the target, and shears sideways toward it. Each
	 * vertex follows by its weight, which fades in below the hip line and past the centre plane, so the
	 * leg never tears from the body; at weight 0 it is exactly the plain crouch.
	 */
	private static final class Leg
	{
		float hip;
		float s;
		float side;
		float cx;
		float hx;
		float hz;
		float weight;
		float stretch;
		float knee;
		float swing;
		float footZ;
		/** Output of {@link #move}. */
		float x;
		float y;
		float z;

		private static final Leg SCRATCH = new Leg();

		/** The solved leg, or null if no vertex is on the push side. Reuses one instance (client thread only). */
		static Leg solve(float[] xs, float[] ys, float[] zs, int n, float hip, float s, BodyPose pose)
		{
			float minX = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			for (int i = 0; i < n; i++)
			{
				if (ys[i] > -hip)
				{
					minX = Math.min(minX, xs[i]);
					maxX = Math.max(maxX, xs[i]);
				}
			}
			if (minX > maxX)
			{
				return null;
			}
			float side = pose.legSide < 0f ? -1f : 1f;
			float cx = (minX + maxX) / 2f;
			float sx = 0f;
			float sz = 0f;
			int count = 0;
			for (int i = 0; i < n; i++)
			{
				if (ys[i] > -hip && (xs[i] - cx) * side > 0f)
				{
					sx += xs[i];
					sz += zs[i];
					count++;
				}
			}
			if (count == 0)
			{
				return null;
			}
			Leg l = SCRATCH;
			l.hip = hip;
			l.s = s;
			l.side = side;
			l.cx = cx;
			l.hx = sx / count;
			l.hz = sz / count;
			l.weight = Math.min(1f, pose.legWeight);
			l.footZ = pose.footZ;
			// from the (crouched) hip to the target, y down; the leg is hip long
			float dx = pose.footX;
			float dy = pose.footY + hip * s;
			float d = (float) Math.hypot(dx, dy);
			if (d >= hip)
			{
				l.stretch = Math.min(MAX_STRETCH, d / hip);
				l.knee = 0f;
			}
			else
			{
				l.stretch = 1f;
				l.knee = (float) Math.acos(Math.max(0f, d / hip));
			}
			l.swing = (float) Math.atan2(dx, dy);
			return l;
		}

		float weight(float vx, float vy)
		{
			float below = PushCycle.smoothstep(0f, LEG_BLEND * hip, vy + hip);
			float across = PushCycle.smoothstep(0f, SIDE_BAND, (vx - cx) * side);
			return weight * below * across;
		}

		void move(float vx, float vy, float vz, float w)
		{
			float len = s + (stretch - s) * w;
			float b = knee * w;
			float a = swing * w;
			float rest = vy + hip;
			float ux = vx - hx;
			float uz = vz - hz;
			float uy = rest * len;
			// the shin turns back about the knee by 2b, then the whole leg forward about the hip by b
			float kneeY = 0.5f * hip * len;
			float th = -2f * b * PushCycle.smoothstep(kneeY - KNEE_BAND, kneeY + KNEE_BAND, uy);
			float ry = uy - kneeY;
			float c = (float) Math.cos(th);
			float sn = (float) Math.sin(th);
			float nz = uz * c - ry * sn;
			uy = uz * sn + ry * c + kneeY;
			uz = nz;
			c = (float) Math.cos(b);
			sn = (float) Math.sin(b);
			nz = uz * c - uy * sn;
			uy = uz * sn + uy * c;
			uz = nz + footZ * w * rest / hip;
			// swing in the board plane about the hip
			c = (float) Math.cos(a);
			sn = (float) Math.sin(a);
			x = hx + ux * c + uy * sn;
			y = -hip * s - ux * sn + uy * c;
			z = hz + uz;
		}
	}
}
