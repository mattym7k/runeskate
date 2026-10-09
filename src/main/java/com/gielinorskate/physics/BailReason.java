package com.gielinorskate.physics;

import com.gielinorskate.Text;

/** Why the skater bailed, shown under "Bailed" on the HUD (the text bundled under bail.NAME). */
public enum BailReason
{
/** Landed with the board too far across the direction of travel. */
SIDEWAYS,
/** Touched down before a board flip was far enough along to catch. */
FLIP_NOT_CAUGHT,
/** Touched down more than 40 degrees off a whole front or back flip. */
BODY_FLIP,
/** A hard, head-on hit. */
WALL;

public final String text = Text.get("bail." + name());

/**
* The reason a landing bails, or null when it does not: sideways first (the landing angle is checked
* before the flips), then an uncaught board flip, then an unfinished body flip.
*/
public static BailReason forLanding(boolean sideways, boolean flipUncaught, boolean bodyFlipOff)
{
return sideways ? SIDEWAYS : flipUncaught ? FLIP_NOT_CAUGHT : bodyFlipOff ? BODY_FLIP : null;
}
}
