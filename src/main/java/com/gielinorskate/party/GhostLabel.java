package com.gielinorskate.party;

/** Text drawn above one ghost this frame: local position (RuneLite z, down-negative) and opacity. Immutable. */
public final class GhostLabel
{
	/** The party member the ghost is. */
	public final long memberId;
	public final int x;
	public final int y;
	public final int z;
	public final String name;
	/** Trick name, or null when none is showing. */
	public final String trick;
	public final float trickAlpha;

	GhostLabel(long memberId, int x, int y, int z, String name, String trick, float trickAlpha)
	{
		this.memberId = memberId;
		this.x = x;
		this.y = y;
		this.z = z;
		this.name = name;
		this.trick = trick;
		this.trickAlpha = trickAlpha;
	}
}
