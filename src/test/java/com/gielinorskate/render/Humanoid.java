package com.gielinorskate.render;

/**
 * A blocky 200-unit test body in puppet model space (y down, soles at 0, facing -z, left hand +x): two legs up to
 * the hips at -90, a torso 32 wide up to -160, a head to -200, and two arms hanging at the sides (x 19..25) from
 * the shoulders (-158) down to the hands (-86), a vertex row every 6 units.
 */
final class Humanoid
{
	static final float ARM_INNER = 19f;
	static final float ARM_OUTER = 25f;
	static final float ARM_TOP = -158f;
	static final float HAND_Y = -86f;

	final float[] xs;
	final float[] ys;
	final float[] zs;
	final int n;

	Humanoid()
	{
		float[][] v = new float[3][400];
		int i = 0;
		for (float x : new float[]{-12f, -4f, 4f, 12f})
		{
			for (float y = 0f; y >= -90f; y -= 10f)
			{
				i = put(v, i, x, y, 0f);
			}
		}
		for (float x : new float[]{-16f, -8f, 0f, 8f, 16f})
		{
			for (float y = -90f; y >= -160f; y -= 10f)
			{
				i = put(v, i, x, y, -8f);
				i = put(v, i, x, y, 8f);
			}
		}
		for (float x : new float[]{-8f, 0f, 8f})
		{
			for (float y = -160f; y >= -200f; y -= 10f)
			{
				i = put(v, i, x, y, 0f);
			}
		}
		for (float side : new float[]{-1f, 1f})
		{
			for (float x : new float[]{ARM_INNER, ARM_OUTER})
			{
				for (float y = ARM_TOP; y <= HAND_Y + 0.01f; y += 6f)
				{
					i = put(v, i, side * x, y, -3f);
					i = put(v, i, side * x, y, 3f);
				}
			}
		}
		n = i;
		xs = v[0];
		ys = v[1];
		zs = v[2];
	}

	private static int put(float[][] v, int i, float x, float y, float z)
	{
		v[0][i] = x;
		v[1][i] = y;
		v[2][i] = z;
		return i + 1;
	}

	/** True for an arm vertex on {@code side}. */
	boolean isArm(int i, float side)
	{
		return xs[i] * side >= ARM_INNER - 0.01f && ys[i] >= ARM_TOP - 0.01f;
	}
}
