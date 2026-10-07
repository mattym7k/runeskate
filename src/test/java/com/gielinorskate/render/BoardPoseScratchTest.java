package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class BoardPoseScratchTest
{
	private static final float[][] POSES = {
		{0f, 0f, 0f, 0f},
		{0.3f, 0f, 0f, 0f},
		{0f, 0.25f, 0f, 0f},
		{-0.4f, -0.2f, 0f, 0f},
		{0.1f, 0.3f, 1.7f, -61f},
		{-2.9f, -0.05f, -3.1f, -40f},
		{0f, 0f, 0f, 0f},
		{1e-6f, -1e-6f, 0f, 0f},
	};

	/** The allocating pose as it was before posing wrote into a scratch array (reference copy). */
	private static float[] oldPose(float[] tris, float roll, float pitch, float flip, float pivotY)
	{
		float cr = (float) Math.cos(roll);
		float sr = (float) Math.sin(roll);
		float cp0 = (float) Math.cos(0f);
		float sp0 = (float) Math.sin(0f);
		float[] out = new float[tris.length];
		for (int i = 0; i < tris.length; i += 3)
		{
			float x = tris[i];
			float y = tris[i + 1];
			float z = tris[i + 2];
			float x1 = x * cr - y * sr;
			float y1 = x * sr + y * cr;
			out[i] = x1;
			out[i + 1] = y1 * cp0 - z * sp0;
			out[i + 2] = y1 * sp0 + z * cp0;
		}
		if (pitch != 0f)
		{
			float pz = BoardPlacement.pivotZ(pitch);
			float cp = (float) Math.cos(pitch);
			float sp = (float) Math.sin(pitch);
			for (int i = 0; i < out.length; i += 3)
			{
				float y = out[i + 1] - BoardPlacement.AXLE_Y;
				float z = out[i + 2] - pz;
				out[i + 1] = y * cp - z * sp + BoardPlacement.AXLE_Y;
				out[i + 2] = y * sp + z * cp + pz;
			}
		}
		if (flip != 0f)
		{
			float c = (float) Math.cos(flip);
			float s = (float) Math.sin(flip);
			for (int i = 0; i < out.length; i += 3)
			{
				float y = out[i + 1] - pivotY;
				float z = out[i + 2];
				out[i + 1] = y * c - z * s + pivotY;
				out[i + 2] = y * s + z * c;
			}
		}
		return out;
	}

	@Test
	public void posingIntoAReusedArrayMatchesAFreshPoseBitForBit()
	{
		float[] base = BoardGeometry.defaultBoard().tris;
		float[] scratch = new float[base.length];
		for (float[] p : POSES)
		{
			float[] fresh = oldPose(base, p[0], p[1], p[2], p[3]);
			float[] reused = BoardPlacement.poseInto(base, p[0], p[1], p[2], p[3], scratch);
			assertSame(scratch, reused);
			for (int i = 0; i < base.length; i++)
			{
				assertEquals("pose " + p[0] + "," + p[1] + "," + p[2] + " float " + i,
					Float.floatToRawIntBits(fresh[i]), Float.floatToRawIntBits(reused[i]));
			}
		}
	}

	@Test
	public void rotateIntoMatchesRotate()
	{
		float[] base = BoardGeometry.defaultBoard().tris;
		float[] scratch = new float[base.length];
		float[] fresh = BoardGeometry.rotate(base, 0.7f, -0.3f);
		BoardGeometry.rotateInto(base, 0.7f, -0.3f, scratch);
		for (int i = 0; i < base.length; i++)
		{
			assertEquals(Float.floatToRawIntBits(fresh[i]), Float.floatToRawIntBits(scratch[i]));
		}
	}
}
