package com.gielinorskate.render;

/**
 * Finds one arm of a player mesh by its shape: OSRS models carry no bone or vertex-group data through the API,
 * so the arm is "what sticks out sideways past the chest, from the shoulder down". Puppet model space: y down
 * with the soles at 0, the body facing -z, left hand +x, right hand -x (see {@link MeshDeformer}).
 *
 * <p>The shoulder sits {@link #SHOULDER_FRACTION} of the model's height up, a little inside the widest point of
 * the chest band there. A vertex belongs to the arm by a smooth weight (never a hard cut, so moving the arm can
 * never tear the mesh): 0 inside {@link #INNER} of the shoulder half-width, 1 past {@link #OUTER}, faded out above
 * the shoulder (the head) and well below the hips (the legs). The hand is the end of the arm: the arm vertices
 * furthest from the shoulder. Pure, allocation-free; one instance per user (client thread only).
 */
final class ArmLocator
{
	/** Shoulder height as a fraction of the model's height. */
	static final float SHOULDER_FRACTION = 0.75f;
	/** Half-height (fraction of the model's height) of the chest band the shoulder width is measured in. */
	static final float CHEST_BAND = 0.06f;
	/** The shoulder half-width is clamped to these fractions of the model's height. */
	static final float MIN_HALF_WIDTH = 0.07f;
	static final float MAX_HALF_WIDTH = 0.16f;
	/** The shoulder joint, as a fraction of the shoulder half-width out from the centre line. */
	static final float JOINT = 0.8f;
	/** Arm weight ramps from 0 to 1 between these fractions of the shoulder half-width out from the centre line. */
	static final float INNER = 0.66f;
	static final float OUTER = 0.78f;
	/** Fraction of the model's height above the shoulder over which the weight fades out (the neck and head). */
	static final float HEAD_FADE = 0.06f;
	/** Fraction of the model's height below the hip line over which the weight fades out (the legs). */
	static final float LEG_FADE = 0.12f;
	/** The arm's end: vertices at least this fraction of the furthest arm vertex's distance from the shoulder. */
	static final float HAND_FRACTION = 0.92f;
	/** The centre line is measured on vertices lower than this fraction of the hip height (below the hands). */
	static final float LOWER_LEGS = 0.6f;
	/** Models shorter than this (units) are not a body. */
	static final float MIN_HEIGHT = 60f;

	/** Results of the latest {@link #locate}: the shoulder joint and the hand, model space. */
	final float[] shoulder = new float[3];
	final float[] hand = new float[3];
	/** Distance from the shoulder to the furthest arm vertex. */
	float armLength;
	/** The arm vertex furthest from the shoulder. */
	int handIndex;

	private float side;
	private float cx;
	private float halfWidth;
	private float height;
	private float hip;

	/**
	 * Finds the arm on {@code side} (+1 the model's +x side, the left hand; -1 the right) among the first {@code n}
	 * vertices. False when the mesh does not look like a body with an arm there.
	 */
	boolean locate(float[] xs, float[] ys, float[] zs, int n, float side)
	{
		this.side = side < 0f ? -1f : 1f;
		if (n <= 0 || xs == null || ys == null || zs == null || xs.length < n || ys.length < n || zs.length < n)
		{
			return false;
		}
		float top = 0f;
		for (int i = 0; i < n; i++)
		{
			top = Math.min(top, ys[i]);
		}
		height = -top;
		if (!(height >= MIN_HEIGHT) || Float.isInfinite(height))
		{
			return false;
		}
		hip = MeshDeformer.hipHeight(top);
		// the centre line from the lower legs (the arms can swing anywhere, the hands hang down to the hips; the
		// legs stand either side of it)
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			if (ys[i] > -LOWER_LEGS * hip)
			{
				minX = Math.min(minX, xs[i]);
				maxX = Math.max(maxX, xs[i]);
			}
		}
		cx = minX <= maxX ? (minX + maxX) / 2f : 0f;
		float shoulderY = -SHOULDER_FRACTION * height;
		float band = CHEST_BAND * height;
		float widest = 0f;
		float sumZ = 0f;
		int count = 0;
		for (int i = 0; i < n; i++)
		{
			if (Math.abs(ys[i] - shoulderY) <= band)
			{
				widest = Math.max(widest, (xs[i] - cx) * this.side);
				sumZ += zs[i];
				count++;
			}
		}
		if (count == 0)
		{
			return false;
		}
		halfWidth = Math.max(MIN_HALF_WIDTH * height, Math.min(MAX_HALF_WIDTH * height, widest));
		shoulder[0] = cx + this.side * JOINT * halfWidth;
		shoulder[1] = shoulderY;
		shoulder[2] = sumZ / count;

		float far = 0f;
		int farIndex = -1;
		for (int i = 0; i < n; i++)
		{
			if (weight(xs[i], ys[i]) >= 0.5f)
			{
				float d = distance(xs[i], ys[i], zs[i]);
				if (d > far)
				{
					far = d;
					farIndex = i;
				}
			}
		}
		if (farIndex < 0 || far < 0.1f * height || Float.isNaN(far) || Float.isInfinite(far))
		{
			return false;
		}
		float hx = 0f;
		float hy = 0f;
		float hz = 0f;
		int hn = 0;
		for (int i = 0; i < n; i++)
		{
			if (weight(xs[i], ys[i]) >= 0.5f && distance(xs[i], ys[i], zs[i]) >= HAND_FRACTION * far)
			{
				hx += xs[i];
				hy += ys[i];
				hz += zs[i];
				hn++;
			}
		}
		hand[0] = hx / hn;
		hand[1] = hy / hn;
		hand[2] = hz / hn;
		armLength = far;
		handIndex = farIndex;
		return true;
	}

	/** 0..1: how much the vertex at (x, y) is the located arm (valid after a successful {@link #locate}). */
	float weight(float x, float y)
	{
		float out = PushCycle.smoothstep(INNER * halfWidth, OUTER * halfWidth, (x - cx) * side);
		if (out <= 0f)
		{
			return 0f;
		}
		float shoulderY = shoulder[1];
		// fades out above the shoulder (y smaller) and below the hips (y larger)
		float below = PushCycle.smoothstep(shoulderY - HEAD_FADE * height, shoulderY, y);
		float above = 1f - PushCycle.smoothstep(-hip, -hip + LEG_FADE * height, y);
		return out * below * above;
	}

	private float distance(float x, float y, float z)
	{
		float dx = x - shoulder[0];
		float dy = y - shoulder[1];
		float dz = z - shoulder[2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
