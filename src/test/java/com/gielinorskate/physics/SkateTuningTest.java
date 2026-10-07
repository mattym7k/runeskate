package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class SkateTuningTest
{
	@Test
	public void missingFieldsKeepDefaults()
	{
		SkateTuning t = SkateTuning.load(new Gson(), new StringReader("{\"gravity\": 1000}"));
		assertEquals(1000f, t.gravity, 0f);
		assertEquals(new SkateTuning().ollieImpulse, t.ollieImpulse, 0f);
	}

	@Test
	public void bundledJsonLoads() throws Exception
	{
		try (Reader r = new InputStreamReader(
			SkateTuning.class.getResourceAsStream("/com/gielinorskate/skate-tuning.json"), StandardCharsets.UTF_8))
		{
			SkateTuning t = SkateTuning.load(new Gson(), r);
			assertEquals(new SkateTuning().maxPushSpeed, t.maxPushSpeed, 0f);
		}
	}

	/** The session loads the bundled JSON, so it must carry every retuned default, not the old values. */
	@Test
	public void bundledJsonMatchesEveryDefault() throws Exception
	{
		SkateTuning d = new SkateTuning();
		try (Reader r = new InputStreamReader(
			SkateTuning.class.getResourceAsStream("/com/gielinorskate/skate-tuning.json"), StandardCharsets.UTF_8))
		{
			SkateTuning j = SkateTuning.load(new Gson(), r);
			float[][] pairs = {
				{d.pushImpulse, j.pushImpulse}, {d.pushFromRest, j.pushFromRest}, {d.pushMinImpulse, j.pushMinImpulse},
				{d.pushCooldown, j.pushCooldown}, {d.maxPushSpeed, j.maxPushSpeed}, {d.maxSpeed, j.maxSpeed},
				{d.rollingFriction, j.rollingFriction}, {d.drag, j.drag}, {d.powerslideDecel, j.powerslideDecel},
				{d.carveRate, j.carveRate}, {d.carveSpeedFalloff, j.carveSpeedFalloff},
				{d.tightCarveMult, j.tightCarveMult}, {d.tightCarveBleed, j.tightCarveBleed},
				{d.tightCarveBleedFraction, j.tightCarveBleedFraction}, {d.pivotSpeed, j.pivotSpeed},
				{d.pivotRate, j.pivotRate}, {d.airSpinRate, j.airSpinRate}, {d.gravity, j.gravity},
				{d.slopeGravityScale, j.slopeGravityScale}, {d.maxSlope, j.maxSlope}, {d.ollieImpulse, j.ollieImpulse},
				{d.minPopFraction, j.minPopFraction}, {d.crouchChargeTime, j.crouchChargeTime},
				{d.landingToleranceDeg, j.landingToleranceDeg}, {d.maxStepUp, j.maxStepUp}, {d.rollOffDrop, j.rollOffDrop},
				{d.wallBailSpeed, j.wallBailSpeed}, {d.wallBailAngleDeg, j.wallBailAngleDeg},
				{d.wallStumbleSpeed, j.wallStumbleSpeed}, {d.stumbleTime, j.stumbleTime},
				{d.wallContactLoss, j.wallContactLoss}, {d.wallScrape, j.wallScrape}, {d.wallAlignRate, j.wallAlignRate},
				{d.spinAssistRate, j.spinAssistRate}, {d.bailDuration, j.bailDuration}, {d.bailFriction, j.bailFriction},
				{d.bailAutoReset, j.bailAutoReset}, {d.skaterRadius, j.skaterRadius},
				{d.rampLaunchSpeedFraction, j.rampLaunchSpeedFraction}, {d.rampLaunchSlopeDrop, j.rampLaunchSlopeDrop},
				{d.rampLaunchMinSlope, j.rampLaunchMinSlope}, {d.rampLaunchMinRun, j.rampLaunchMinRun},
{d.momentumClimbSpeedPerRise, j.momentumClimbSpeedPerRise},
				{d.momentumClimbGravityScale, j.momentumClimbGravityScale}, {d.dropInMaxGradient, j.dropInMaxGradient},
			};
			for (int i = 0; i < pairs.length; i++)
			{
				assertEquals("field #" + i, pairs[i][0], pairs[i][1], 0f);
			}
		}
	}

	@Test
	public void scaledAdjustsPushAndPop()
	{
		SkateTuning base = new SkateTuning();
		SkateTuning s = base.scaled(1.5f, 0.5f);
		assertEquals(base.pushImpulse * 1.5f, s.pushImpulse, 1e-3f);
		assertEquals(base.maxPushSpeed * 1.5f, s.maxPushSpeed, 1e-3f);
		assertEquals(base.ollieImpulse * 0.5f, s.ollieImpulse, 1e-3f);
		assertEquals(base.gravity, s.gravity, 0f);
	}

	@Test
	public void wallBailSpeedScalesWithPushSpeed()
	{
		// faster pushing must not turn ordinary head-ons into bails: 1100 * 1.5 = 1650
		SkateTuning base = new SkateTuning();
		assertEquals(base.wallBailSpeed * 1.5f, base.scaled(1.5f, 1f).wallBailSpeed, 1e-3f);
		assertEquals(base.wallBailSpeed * 0.75f, base.scaled(0.75f, 1f).wallBailSpeed, 1e-3f);
	}

	@Test
	public void scalePercentIsClampedTo75To150()
	{
		// a value saved before the 75-150 range (50 or 200) is clamped
		assertEquals(0.75f, SkateTuning.scaleFromPercent(50), 0f);
		assertEquals(0.75f, SkateTuning.scaleFromPercent(75), 0f);
		assertEquals(1f, SkateTuning.scaleFromPercent(100), 0f);
		assertEquals(1.5f, SkateTuning.scaleFromPercent(150), 0f);
		assertEquals(1.5f, SkateTuning.scaleFromPercent(200), 0f);
	}
}
