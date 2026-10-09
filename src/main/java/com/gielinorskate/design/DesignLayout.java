package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
* Each part's design image layout: the size of the image a design is painted in (the part's UVs span it, v up), and
* the box-blur radius the offline bake gives an image of exactly that size at High and Normal detail. A player's
* image is placed in this layout ({@link ImagePlacement}). The values are those of design_layout.json (written by
* tools/blend_to_board_baked.py or tools/custom_designs.py): update them here when the tools write new ones. Pure.
*/
public final class DesignLayout
{
private static final DesignLayout BUNDLED = new DesignLayout();

/** One part's layout. */
@RequiredArgsConstructor
public static final class Part
{
/** The design image's size in pixels (UV 0..1 spans it). */
public final int width;
public final int height;
/** Blur radius (pixels of a layout-sized image) at High and Normal detail. */
private final int blurHigh;
private final int blurLow;

public int blur(boolean high)
{
return high ? blurHigh : blurLow;
}
}

private final Map<DesignPart, Part> parts = new EnumMap<>(DesignPart.class);

private DesignLayout()
{
parts.put(DesignPart.GRIP, new Part(361, 1059, 2, 5));
parts.put(DesignPart.DECK, new Part(300, 820, 2, 3));
parts.put(DesignPart.WHEELS, new Part(394, 389, 12, 12));
}

/** The plugin's layout. */
public static DesignLayout bundled()
{
return BUNDLED;
}

public Part of(DesignPart part)
{
return parts.get(part);
}
}
