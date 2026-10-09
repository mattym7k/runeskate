package com.gielinorskate.physics;

/** How the latest landing went, for the HUD and the scorer's clean-landing bonus. */
public enum LandingQuality
{
/** The board landed within 15 degrees of the travel (either way round) with its flip finished. */
CLEAN,
/** Landed, but turned further off the travel or still finishing a flip. */
SLOPPY
}
