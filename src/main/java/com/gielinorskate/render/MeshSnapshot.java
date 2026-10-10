package com.gielinorskate.render;

/**
 * Our own copy of a party member's character mesh (vertex positions only), taken once from a frame the client
 * rebuilt for us and drawn from afterwards, so a ghost's body never follows what the member's real character is
 * doing on this client. Valid for the vertex and face count and appearance key it was taken with. Pure; arrays grow
 * only when a bigger mesh is taken (no allocation per frame).
 */
public final class MeshSnapshot
{
	float[] xs = new float[0];
	private float[] ys = new float[0];
	private float[] zs = new float[0];
	/** Vertices taken; -1 when none (not valid). */
	int count = -1;
	private int faces;
	private int key;

	/** Whether this snapshot was taken from a mesh of {@code count} vertices and {@code faces} faces under {@code key}. */
	public boolean matches(int count, int faces, int key)
	{
		return this.count >= 0 && this.count == count && this.faces == faces && this.key == key;
	}

	/** Copies the first {@code count} vertices. */
	public void take(float[] x, float[] y, float[] z, int count, int faces, int key)
	{
		if (xs.length < count)
		{
			xs = new float[count];
			ys = new float[count];
			zs = new float[count];
		}
		System.arraycopy(x, 0, xs, 0, count);
		System.arraycopy(y, 0, ys, 0, count);
		System.arraycopy(z, 0, zs, 0, count);
		this.count = count;
		this.faces = faces;
		this.key = key;
	}

	/** Writes the snapshot's vertices over the first {@link #count} of these arrays. */
	public void copyInto(float[] x, float[] y, float[] z)
	{
		System.arraycopy(xs, 0, x, 0, count);
		System.arraycopy(ys, 0, y, 0, count);
		System.arraycopy(zs, 0, z, 0, count);
	}

	public void clear()
	{
		count = -1;
	}
}
