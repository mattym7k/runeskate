package com.gielinorskate.session;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** What a setting changed while skating does: applied now, applied next session, or nothing to do. */
final class SettingsApply
{
	enum Kind
	{
		/** Re-applied to the running session. */
		LIVE,
		/** Read only when skating starts: the player is told it applies next time. */
		NEXT_SESSION,
		/** Read every frame (or not ours to handle): nothing to do or say. */
		ALREADY_LIVE
	}

	private static final Set<String> LIVE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"cameraZoom", "flickSensitivity", "manualKey", "trickControls", "flickButton", "mirrorFlicks",
		"controllerMode", "brakeKey", "leanForwardKey", "leanBackKey", "boardKey", "controllerPreset",
		"customControllerLayout")));
	private static final Set<String> NEXT_SESSION = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"speedScale", "popScale", "forgivingCollisions", "passThroughVegetation")));

	private SettingsApply()
	{
	}

	static Kind classify(String key)
	{
		if (LIVE.contains(key))
		{
			return Kind.LIVE;
		}
		return NEXT_SESSION.contains(key) ? Kind.NEXT_SESSION : Kind.ALREADY_LIVE;
	}
}
