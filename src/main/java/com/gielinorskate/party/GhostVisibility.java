package com.gielinorskate.party;

import java.util.Locale;

/** Pure gating and placement rules for drawing party ghosts. */
final class GhostVisibility
{
/** The trick label is fully shown this long... */
static final float LABEL_HOLD = 1f;

private GhostVisibility()
{
}

/**
* Ghosts are drawn only while this client is skating with "Show party skaters" on, outside PvP areas (the
* real character or the skater) and outside instances (two instances' coordinates can coincide).
*/
static boolean showGhosts(boolean localSkating, boolean showSetting, boolean localInPvpArea,
boolean localInstanced)
{
return localSkating && showSetting && !localInPvpArea && !localInstanced;
}

/** The ghost is on this client's world and plane. */
static boolean sameSpace(int localWorld, int localPlane, GhostState s)
{
return s != null && s.world == localWorld && s.plane == localPlane;
}

/** A local position lies inside a loaded scene of {@code sizeX} x {@code sizeY} tiles. */
static boolean inScene(float localX, float localY, int sizeX, int sizeY)
{
return localX >= 0f && localY >= 0f && localX < sizeX * 128f && localY < sizeY * 128f;
}

/** Opacity of a trick label {@code age} seconds after the trick. */
static float labelAlpha(float age)
{
if (age < LABEL_HOLD)
return 1f;
// ...then fades out, gone 1.5 s after the trick.
return Math.max(0f, 1f - (age - LABEL_HOLD) / 0.5f);
}

/** A scene player's name and a party member's display name are the same player. */
static boolean sameName(String playerName, String memberName)
{
return playerName != null && memberName != null && normalise(playerName).equals(normalise(memberName));
}

private static String normalise(String name)
{
return name.replace(' ', ' ').replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
}

// ---- the per-ghost body budget: only the nearest MAX_FULL ghosts within FULL_RANGE get the full body (the
// procedural pose on top of the OSRS animation, about 0.3 ms a frame for a 5,000-vertex character); the rest
// play the animation only

static final int MAX_FULL = 4;
/** Local units: 32 tiles, about where a body's lean and crouch stop being readable. */
static final float FULL_RANGE = 32 * 128f;

/**
* A ghost's ground distance from the camera's focal point. The client's focal point keeps the scene's y in its
* Z ({@code getCameraFocalPointZ}); its Y is the height, so measuring against Y put every ghost ~50 tiles away.
*/
static float focusDistance(float x, float y, float focalX, float focalZ)
{
return (float) Math.hypot(x - focalX, y - focalZ);
}

/** The ghost {@code rank}-th nearest (0 the nearest) at {@code distance} gets the full body. */
static boolean full(int rank, float distance)
{
return rank < MAX_FULL && distance <= FULL_RANGE;
}
}
