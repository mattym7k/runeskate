package com.gielinorskate.party;

import java.util.Locale;

/** Pure gating and placement rules for drawing party ghosts. */
public final class GhostVisibility
{
	/** The trick label is fully shown this long... */
	static final float LABEL_HOLD = 1f;
	/** ...then fades out, gone 1.5 s after the trick. */
	static final float LABEL_FADE = 0.5f;

	private GhostVisibility()
	{
	}

	/**
	 * Ghosts are drawn only while this client is skating with "Show party skaters" on, outside PvP areas (the
	 * real character or the skater) and outside instances (two instances' coordinates can coincide).
	 */
	public static boolean showGhosts(boolean localSkating, boolean showSetting, boolean localInPvpArea,
		boolean localInstanced)
	{
		return localSkating && showSetting && !localInPvpArea && !localInstanced;
	}

	/** The ghost is on this client's world and plane. */
	public static boolean sameSpace(int localWorld, int localPlane, GhostState s)
	{
		return s != null && s.world == localWorld && s.plane == localPlane;
	}

	/** A local position lies inside a loaded scene of {@code sizeX} x {@code sizeY} tiles. */
	public static boolean inScene(float localX, float localY, int sizeX, int sizeY)
	{
		return localX >= 0f && localY >= 0f && localX < sizeX * 128f && localY < sizeY * 128f;
	}

	/** Opacity of a trick label {@code age} seconds after the trick. */
	public static float labelAlpha(float age)
	{
		if (age < LABEL_HOLD)
		{
			return 1f;
		}
		return Math.max(0f, 1f - (age - LABEL_HOLD) / LABEL_FADE);
	}

	/** A scene player's name and a party member's display name are the same player. */
	public static boolean sameName(String playerName, String memberName)
	{
		return playerName != null && memberName != null && normalise(playerName).equals(normalise(memberName));
	}

	private static String normalise(String name)
	{
		return name.replace(' ', ' ').replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
