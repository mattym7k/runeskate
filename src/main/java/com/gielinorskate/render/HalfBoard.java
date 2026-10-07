package com.gielinorskate.render;

import net.runelite.api.Model;
import net.runelite.api.ModelData;

/**
 * One half of a snapped board ({@link BoardSplit}), drawn like the whole board (one model per part, each by its own
 * {@link BoardController} placed the same) but turned about the half's own centre: its parts are copies of the
 * board's model data (the merged templates are shared, so nothing is merged again) with the other half's faces
 * hidden after lighting. The baked board's halves keep the designs' corner colours the board had when it snapped;
 * the classic board's keep its deck colours. Made when the tantrum starts, well before the snap. Client thread.
 */
public final class HalfBoard implements BoardPoser
{
	private final ModelData[] data;
	private final float[][] base;
	private final float[][] posed;
	private final boolean[][] in;
	/** Per part: the baked board's corner colours, or null (the classic board, lit by the client). */
	private final short[][] cornerHsl;
	private final int ambient;
	private final int contrast;
	private final Model[] lit;
	private final float[] litRoll;
	private final float[] litPitch;
	private final float bottom;

	HalfBoard(ModelData[] data, BoardSplit.Half half, short[][] cornerHsl, int ambient, int contrast)
	{
		this.data = data;
		this.base = half.vertices;
		this.in = half.faceIn;
		this.cornerHsl = cornerHsl;
		this.ambient = ambient;
		this.contrast = contrast;
		this.bottom = half.bottom();
		posed = new float[data.length][];
		for (int i = 0; i < data.length; i++)
		{
			posed[i] = new float[base[i].length];
		}
		lit = new Model[data.length];
		litRoll = new float[data.length];
		litPitch = new float[data.length];
	}

	/** Units from the half's centre (its model origin) down to its lowest point. */
	public float bottom()
	{
		return bottom;
	}

	@Override
	public int partCount()
	{
		return data.length;
	}

	@Override
	public Model pose(float roll, float pitch, float flip, float pivotY)
	{
		return pose(0, roll, pitch, flip, pivotY);
	}

	/** Part {@code part} of the half rolled and pitched about the half's centre (no flip: it flies on its own). */
	@Override
	public Model pose(int part, float roll, float pitch, float flip, float pivotY)
	{
		if (part < 0 || part >= data.length)
		{
			return null;
		}
		if (lit[part] != null && !BakedBoardModel.needsRelight(litRoll[part], litPitch[part], 0f, 0f, roll, pitch, 0f,
			0f))
		{
			return lit[part];
		}
		ModelData d = data[part];
		float[] v = BoardGeometry.rotateInto(base[part], roll, pitch, posed[part]);
		float[] vx = d.getVerticesX();
		float[] vy = d.getVerticesY();
		float[] vz = d.getVerticesZ();
		int n = Math.min(v.length / 3, d.getVerticesCount());
		for (int i = 0; i < n; i++)
		{
			vx[i] = v[i * 3];
			vy[i] = v[i * 3 + 1];
			vz[i] = v[i * 3 + 2];
		}
		// any vertex the mesh does not have (the classic template's leftovers) sits at the centre
		for (int i = n; i < d.getVerticesCount(); i++)
		{
			vx[i] = 0;
			vy[i] = 0;
			vz[i] = 0;
		}
		Model m = d.light(ambient, contrast, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
		if (m != null)
		{
			BoardSplit.shadeHalf(m.getFaceColors1(), m.getFaceColors2(), m.getFaceColors3(), cornerHsl[part],
				in[part]);
			lit[part] = m;
			litRoll[part] = roll;
			litPitch[part] = pitch;
		}
		return m;
	}
}
