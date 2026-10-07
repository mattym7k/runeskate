package com.gielinorskate.render;

/**
 * Where one puppet object is drawn: local x / y, RuneLite z (grows downward), orientation in JAU (kept as a float
 * so it can be blended), and for a board its roll and pitch. Mutable and reused; {@link #valid} is false until
 * something was set.
 */
public final class Placement
{
	public float x;
	public float y;
	public float z;
	public float jau;
	public float roll;
	public float pitch;
	public boolean valid;

	public void set(float x, float y, float z, float jau, float roll, float pitch)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.jau = jau;
		this.roll = roll;
		this.pitch = pitch;
		this.valid = true;
	}

	public void copyFrom(Placement o)
	{
		set(o.x, o.y, o.z, o.jau, o.roll, o.pitch);
		valid = o.valid;
	}

	public void invalidate()
	{
		valid = false;
	}

	/** The orientation as RuneLite takes it: rounded and wrapped to 0..2047. */
	public int orientation()
	{
		return Math.round(jau) & 2047;
	}
}
