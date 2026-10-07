package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SettingsApplyTest
{
	@Test
	public void zoomFlickSensitivityAndManualKeyApplyLive()
	{
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("cameraZoom"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("flickSensitivity"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("manualKey"));
	}

	@Test
	public void tuningAndWorldSettingsApplyNextTime()
	{
		assertEquals(SettingsApply.Kind.NEXT_SESSION, SettingsApply.classify("speedScale"));
		assertEquals(SettingsApply.Kind.NEXT_SESSION, SettingsApply.classify("popScale"));
		assertEquals(SettingsApply.Kind.NEXT_SESSION, SettingsApply.classify("forgivingCollisions"));
		assertEquals(SettingsApply.Kind.NEXT_SESSION, SettingsApply.classify("passThroughVegetation"));
	}

	@Test
	public void settingsReadEveryFrameOrUnknownSayNothing()
	{
		assertEquals(SettingsApply.Kind.ALREADY_LIVE, SettingsApply.classify("cameraHeight"));
		assertEquals(SettingsApply.Kind.ALREADY_LIVE, SettingsApply.classify("smoothMotion"));
		assertEquals(SettingsApply.Kind.ALREADY_LIVE, SettingsApply.classify("toggleKey"));
		assertEquals(SettingsApply.Kind.ALREADY_LIVE, SettingsApply.classify("someNewEffectsSetting"));
		assertEquals(SettingsApply.Kind.ALREADY_LIVE, SettingsApply.classify("showTrickHints"));
		assertEquals(SettingsApply.Kind.ALREADY_LIVE, SettingsApply.classify("showGrindEdges"));
		assertEquals(SettingsApply.Kind.ALREADY_LIVE, SettingsApply.classify("seenIntro"));
	}

	@Test
	public void trickControlFlickButtonAndMirrorApplyLive()
	{
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("trickControls"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("flickButton"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("mirrorFlicks"));
	}

	@Test
	public void controllerModeAndItsKeysApplyLive()
	{
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("controllerMode"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("brakeKey"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("leanForwardKey"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("leanBackKey"));
	}

	@Test
	public void theControllerPresetAndCustomLayoutApplyLive()
	{
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("controllerPreset"));
		assertEquals(SettingsApply.Kind.LIVE, SettingsApply.classify("customControllerLayout"));
	}
}
