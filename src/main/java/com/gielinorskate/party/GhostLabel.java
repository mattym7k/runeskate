package com.gielinorskate.party;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/** Text drawn above one ghost this frame: local position (RuneLite z, down-negative) and opacity. Immutable. */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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

}
