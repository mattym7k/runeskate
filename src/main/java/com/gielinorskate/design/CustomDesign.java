package com.gielinorskate.design;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.DesignPart;

/**
 * A player's own design as saved: its id (CUSTOM_ and 8 hex digits), name, part, when it was made, the size of the
 * image the player picked, and where the kept copy of that image (at most
 * {@link CustomDesignRules#STORED_MAX_SIDE} on its long side) lies in the part's layout. Pure, immutable.
 */
public final class CustomDesign
{
	public final String id;
	public final String name;
	public final DesignPart part;
	/** Epoch milliseconds. */
	public final long created;
	/** The picked image's size before it was downscaled. */
	public final int sourceWidth;
	public final int sourceHeight;
	/** The kept image's placement (its size is the kept image's). */
	public final ImagePlacement placement;

	public CustomDesign(String id, String name, DesignPart part, long created, int sourceWidth, int sourceHeight,
		ImagePlacement placement)
	{
		this.id = id;
		this.name = name;
		this.part = part;
		this.created = created;
		this.sourceWidth = sourceWidth;
		this.sourceHeight = sourceHeight;
		this.placement = placement;
	}

	public CustomDesign named(String newName)
	{
		return new CustomDesign(id, newName, part, created, sourceWidth, sourceHeight, placement);
	}

	public CustomDesign placed(ImagePlacement p)
	{
		return new CustomDesign(id, name, part, created, sourceWidth, sourceHeight, p);
	}

	/** The catalogue entry, at {@code revision}. */
	public BoardDesign asBoardDesign(long revision)
	{
		return BoardDesign.custom(id, name, part, revision);
	}
}
