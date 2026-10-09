package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import lombok.RequiredArgsConstructor;
import lombok.With;

/**
* A player's own design as saved: its id (CUSTOM_ and 8 hex digits), name, part, when it was made, the size of the
* image the player picked, and where the kept copy of that image (at most
* {@link CustomDesignRules#STORED_MAX_SIDE} on its long side) lies in the part's layout. Pure, immutable.
*/
@RequiredArgsConstructor
public final class CustomDesign
{
public final String id;
@With
public final String name;
public final DesignPart part;
/** Epoch milliseconds. */
public final long created;
/** The picked image's size before it was downscaled. */
public final int sourceWidth;
public final int sourceHeight;
/** The kept image's placement (its size is the kept image's). */
@With
public final ImagePlacement placement;
}
