package com.gielinorskate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Checks the config interface's source text (no reflection): saved keys never change, every visible item has a
 * section and hidden ones don't.
 */
public class GielinorSkateConfigTest
{
	/** Keys saved before the Q2 settings pass: renaming any of them would reset players' settings. */
	private static final Set<String> EXISTING_KEYS = new HashSet<>(Arrays.asList(
		"toggleKey", "cameraZoom", "speedScale", "popScale", "cameraHeight", "manualKey", "flickSensitivity",
		"smoothMotion", "passThroughVegetation", "forgivingCollisions", "allowPvpAreas", "shareWithParty",
		"showPartySkaters", "soundEffects", "soundVolume", "particleEffects", "funnyBailMessages"));

	/** The annotation body runs up to the ')' that ends its line and is followed by the method declaration. */
	private static final Pattern ITEM = Pattern.compile(
		"@ConfigItem\\s*\\((.*?)\\)\\s*\\n\\s*(?:default\\s|[A-Za-z<>\\[\\].]+\\s+\\w+\\s*\\()", Pattern.DOTALL);
	private static final Pattern KEY = Pattern.compile("keyName\\s*=\\s*\"([^\"]+)\"");

	/** Each @ConfigItem's annotation body, from the source file. */
	private static List<String> items() throws IOException
	{
		String src = new String(Files.readAllBytes(
			Paths.get("src/main/java/com/gielinorskate/GielinorSkateConfig.java")), StandardCharsets.UTF_8);
		List<String> bodies = new ArrayList<>();
		Matcher m = ITEM.matcher(src);
		while (m.find())
		{
			bodies.add(m.group(1));
		}
		// every @ConfigItem must be parsed, or a key could slip past the checks unnoticed
		int declared = src.split("@ConfigItem", -1).length - 1;
		assertEquals("parsed config items", declared, bodies.size());
		return bodies;
	}

	private static String key(String body)
	{
		Matcher m = KEY.matcher(body);
		return m.find() ? m.group(1) : "";
	}

	@Test
	public void everyExistingKeyIsKept() throws IOException
	{
		Set<String> keys = new TreeSet<>();
		for (String body : items())
		{
			keys.add(key(body));
		}
		for (String key : EXISTING_KEYS)
		{
			assertTrue("missing key " + key, keys.contains(key));
		}
	}

	@Test
	public void everyVisibleItemIsInASectionAndHiddenOnesAreNot() throws IOException
	{
		List<String> bodies = items();
		assertFalse(bodies.isEmpty());
		for (String body : bodies)
		{
			boolean hidden = body.replaceAll("\\s", "").contains("hidden=true");
			boolean sectioned = body.contains("section");
			if (hidden)
			{
				assertFalse(key(body) + " is hidden but in a section", sectioned);
			}
			else
			{
				assertTrue(key(body) + " has no section", sectioned);
			}
		}
	}

	@Test
	public void newDefaults()
	{
		GielinorSkateConfig defaults = new GielinorSkateConfig()
		{
		};
		assertTrue(defaults.showTrickHints());
		assertTrue(defaults.showControlsCard());
		assertFalse(defaults.forgivingCollisions());
		assertFalse(defaults.seenIntro());
		assertFalse(defaults.mirrorFlicks());
		assertEquals(GielinorSkateConfig.TrickControls.MOUSE, defaults.trickControls());
		assertEquals(GielinorSkateConfig.FlickButton.RIGHT, defaults.flickButton());
		assertEquals(java.awt.event.KeyEvent.VK_UP, defaults.leanForwardKey().getKeyCode());
		assertEquals(java.awt.event.KeyEvent.VK_DOWN, defaults.leanBackKey().getKeyCode());
		assertTrue(defaults.showLeaderboardOverlay());
		assertFalse(defaults.leaderboardOverlayCollapsed());
		assertEquals("combo", defaults.leaderboardOverlayBoard());
		assertEquals("week", defaults.leaderboardOverlayPeriod());
		assertTrue(defaults.shareCustomDesigns());
		assertFalse(defaults.showPartyCustomDesigns());
		assertTrue(defaults.duelEndings());
		assertEquals(GielinorSkateConfig.CameraHeight.HIGH, defaults.cameraHeight());
	}

	@Test
	public void theCustomDesignSettingsAreInThePartySection() throws IOException
	{
		int found = 0;
		for (String body : items())
		{
			String key = key(body);
			if (key.equals("shareCustomDesigns") || key.equals("showPartyCustomDesigns"))
			{
				assertTrue(key, body.contains("section = PARTY"));
				found++;
			}
		}
		assertEquals(2, found);
	}

	/** The settings players look for first sit in the open sections at the top; the rest wait in Advanced. */
	@Test
	public void theCommonSettingsAreInTheTopSectionsAndTheRestInAdvanced() throws IOException
	{
		java.util.Map<String, String> expected = new java.util.HashMap<>();
		for (String k : Arrays.asList("toggleKey", "controllerMode", "controllerPreset", "submitScores",
			"showLeaderboardOverlay"))
		{
			expected.put(k, "GENERAL");
		}
		for (String k : Arrays.asList("shareWithParty", "showPartySkaters", "shareCustomDesigns",
			"showPartyCustomDesigns", "allowDuelChallenges", "duelEndings"))
		{
			expected.put(k, "PARTY");
		}
		for (String k : Arrays.asList("trickControls", "forgivingCollisions", "afterBail"))
		{
			expected.put(k, "GAMEPLAY");
		}
		for (String k : Arrays.asList("cameraZoom", "cameraHeight", "soundEffects", "particleEffects"))
		{
			expected.put(k, "CAMERA");
		}
		for (String body : items())
		{
			if (body.replaceAll("\\s", "").contains("hidden=true"))
			{
				continue;
			}
			String key = key(body);
			String section = expected.getOrDefault(key, "ADVANCED");
			assertTrue(key + " should be in " + section, body.contains("section = " + section));
		}
	}

	/** The custom controller layout is hidden state, and the preset defaults to Skate 3. */
	@Test
	public void theCustomLayoutIsHiddenAndThePresetDefaultsToSkate3() throws IOException
	{
		Set<String> hidden = new HashSet<>();
		for (String body : items())
		{
			if (body.replaceAll("\\s", "").contains("hidden=true"))
			{
				hidden.add(key(body));
			}
		}
		assertTrue(hidden.contains("customControllerLayout"));
		GielinorSkateConfig defaults = new GielinorSkateConfig()
		{
		};
		assertEquals(GielinorSkateConfig.ControllerPreset.SKATE_3, defaults.controllerPreset());
		assertEquals("", defaults.customControllerLayout());
	}

	@Test
	public void thirdPartyWarningUsesThePluginHubWordingVerbatim()
	{
		// plugin/AGENTS.md: the exact sentence the Plugin Hub requires for a third-party server feature
		String required = "This feature submits your IP address to a 3rd-party server not controlled or verified by "
			+ "RuneLite developers";
		assertEquals(required, GielinorSkateConfig.THIRD_PARTY_WARNING);
		assertTrue(GielinorSkateConfig.SUBMIT_SCORES_DESCRIPTION.contains(required));
		assertTrue(GielinorSkateConfig.SUBMIT_SCORES_DESCRIPTION.contains("RuneScape name"));
	}
}
