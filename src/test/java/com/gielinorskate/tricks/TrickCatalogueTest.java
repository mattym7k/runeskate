package com.gielinorskate.tricks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The bundled trick catalogue (trick.NAME in text/data.properties) loads every field as it was written in code. */
public class TrickCatalogueTest
{
	@Test
	public void everyTrickLoadsItsEntry()
	{
		check(Trick.OLLIE, "Ollie", TrickKind.POP, 100, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.NOLLIE, "Nollie", TrickKind.POP, 150, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.KICKFLIP, "Kickflip", TrickKind.FLIP, 300, 1f, 0f, 0.35f, 0f, 0f, 0f);
		check(Trick.HEELFLIP, "Heelflip", TrickKind.FLIP, 300, -1f, 0f, 0.35f, 0f, 0f, 0f);
		check(Trick.POP_SHOVE_IT, "Pop Shove-it", TrickKind.FLIP, 250, 0f, 0.5f, 0.30f, 0f, 0f, 0f);
		check(Trick.FS_POP_SHOVE_IT, "FS Pop Shove-it", TrickKind.FLIP, 250, 0f, -0.5f, 0.30f, 0f, 0f, 0f);
		check(Trick.SHOVE_IT_360, "360 Shove-it", TrickKind.FLIP, 500, 0f, 1f, 0.40f, 0f, 0f, 0f);
		check(Trick.FS_SHOVE_IT_360, "FS 360 Shove-it", TrickKind.FLIP, 500, 0f, -1f, 0.40f, 0f, 0f, 0f);
		check(Trick.VARIAL_KICKFLIP, "Varial Kickflip", TrickKind.FLIP, 600, 1f, 0.5f, 0.40f, 0f, 0f, 0f);
		check(Trick.VARIAL_HEELFLIP, "Varial Heelflip", TrickKind.FLIP, 600, -1f, -0.5f, 0.40f, 0f, 0f, 0f);
		check(Trick.TRE_FLIP, "360 Flip", TrickKind.FLIP, 900, 1f, 1f, 0.45f, 0f, 0f, 0f);
		check(Trick.LASER_FLIP, "Laser Flip", TrickKind.FLIP, 950, -1f, -1f, 0.45f, 0f, 0f, 0f);
		check(Trick.DOUBLE_KICKFLIP, "Double Kickflip", TrickKind.FLIP, 800, 2f, 0f, 0.50f, 0f, 0f, 0f);
		check(Trick.DOUBLE_HEELFLIP, "Double Heelflip", TrickKind.FLIP, 800, -2f, 0f, 0.50f, 0f, 0f, 0f);
		check(Trick.NOLLIE_KICKFLIP, "Nollie Kickflip", TrickKind.FLIP, 350, 1f, 0f, 0.35f, 0f, 0f, 0f);
		check(Trick.NOLLIE_HEELFLIP, "Nollie Heelflip", TrickKind.FLIP, 350, -1f, 0f, 0.35f, 0f, 0f, 0f);
		check(Trick.NOLLIE_SHOVE_IT, "Nollie Shove-it", TrickKind.FLIP, 300, 0f, 0.5f, 0.30f, 0f, 0f, 0f);
		check(Trick.NOLLIE_FS_SHOVE_IT, "Nollie FS Shove-it", TrickKind.FLIP, 300, 0f, -0.5f, 0.30f, 0f, 0f, 0f);
		check(Trick.NOLLIE_TRE_FLIP, "Nollie 360 Flip", TrickKind.FLIP, 1000, 1f, 1f, 0.45f, 0f, 0f, 0f);
		check(Trick.INDY, "Indy", TrickKind.GRAB, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.MELON, "Melon", TrickKind.GRAB, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.MUTE, "Mute", TrickKind.GRAB, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.STALEFISH, "Stalefish", TrickKind.GRAB, 350, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.NOSEGRAB, "Nosegrab", TrickKind.GRAB, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.TAILGRAB, "Tailgrab", TrickKind.GRAB, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.CRAIL, "Crail", TrickKind.GRAB, 350, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.METHOD, "Method", TrickKind.GRAB, 450, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.TWEAKED_INDY, "Tweaked Indy", TrickKind.GRAB, 450, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.JAPAN, "Japan", TrickKind.GRAB, 450, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.TWEAKED_STALEFISH, "Tweaked Stalefish", TrickKind.GRAB, 525, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.NOSEBONE, "Nosebone", TrickKind.GRAB, 450, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.TAILBONE, "Tailbone", TrickKind.GRAB, 450, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.CRAIL_TWEAK, "Crail Tweak", TrickKind.GRAB, 525, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.MANUAL, "Manual", TrickKind.MANUAL, 150, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.NOSE_MANUAL, "Nose Manual", TrickKind.MANUAL, 200, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.FIFTY_FIFTY, "50-50", TrickKind.GRIND, 200, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.FIVE_O, "5-0", TrickKind.GRIND, 250, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.NOSEGRIND, "Nosegrind", TrickKind.GRIND, 250, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.BOARDSLIDE, "Boardslide", TrickKind.GRIND, 250, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.CROOKED, "Crooked Grind", TrickKind.GRIND, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.NOSESLIDE, "Noseslide", TrickKind.GRIND, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.TAILSLIDE, "Tailslide", TrickKind.GRIND, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.SMITH, "Smith Grind", TrickKind.GRIND, 350, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.FEEBLE, "Feeble Grind", TrickKind.GRIND, 350, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.LIPSLIDE, "Lipslide", TrickKind.GRIND, 300, 0f, 0f, 0f, 0f, 0f, 0f);
		check(Trick.IMPOSSIBLE, "Impossible", TrickKind.FLIP, 450, 0f, 0f, 0.40f, 1f, 0f, 0f);
		check(Trick.HARDFLIP, "Hardflip", TrickKind.FLIP, 500, 1f, -0.5f, 0.40f, 0f, 0f, 0f);
		check(Trick.INWARD_HEELFLIP, "Inward Heelflip", TrickKind.FLIP, 500, -1f, 0.5f, 0.40f, 0f, 0f, 0f);
		check(Trick.BIGSPIN, "Bigspin", TrickKind.FLIP, 550, 0f, 1f, 0.45f, 0f, 0.5f, 0f);
		check(Trick.FS_BIGSPIN, "FS Bigspin", TrickKind.FLIP, 550, 0f, -1f, 0.45f, 0f, -0.5f, 0f);
		check(Trick.NOLLIE_HARDFLIP, "Nollie Hardflip", TrickKind.FLIP, 550, 1f, -0.5f, 0.40f, 0f, 0f, 0f);
		check(Trick.NOLLIE_INWARD_HEELFLIP, "Nollie Inward Heelflip", TrickKind.FLIP, 550, -1f, 0.5f, 0.40f, 0f, 0f, 0f);
		check(Trick.TRIPLE_KICKFLIP, "Triple Kickflip", TrickKind.FLIP, 1300, 3f, 0f, 0.65f, 0f, 0f, 0f);
		check(Trick.TRIPLE_HEELFLIP, "Triple Heelflip", TrickKind.FLIP, 1300, -3f, 0f, 0.65f, 0f, 0f, 0f);
		check(Trick.FRONTFLIP, "Frontflip", TrickKind.FLIP, 1000, 0f, 0f, 0f, 0f, 0f, 1f);
		check(Trick.BACKFLIP, "Backflip", TrickKind.FLIP, 1000, 0f, 0f, 0f, 0f, 0f, -1f);
		check(Trick.DOUBLE_FRONTFLIP, "Double Frontflip", TrickKind.FLIP, 2200, 0f, 0f, 0f, 0f, 0f, 2f);
		check(Trick.DOUBLE_BACKFLIP, "Double Backflip", TrickKind.FLIP, 2200, 0f, 0f, 0f, 0f, 0f, -2f);
		assertEquals(58, Trick.values().length);
	}

	private static void check(Trick t, String name, TrickKind kind, int points, float roll, float yaw, float duration,
		float pitch, float bodyYaw, float bodyFlip)
	{
		assertEquals(name, t.displayName);
		assertEquals(kind, t.kind);
		assertEquals(points, t.points);
		assertEquals(Float.floatToIntBits(roll), Float.floatToIntBits(t.rollTurns));
		assertEquals(Float.floatToIntBits(yaw), Float.floatToIntBits(t.yawTurns));
		assertEquals(Float.floatToIntBits(duration), Float.floatToIntBits(t.duration));
		assertEquals(Float.floatToIntBits(pitch), Float.floatToIntBits(t.pitchTurns));
		assertEquals(Float.floatToIntBits(bodyYaw), Float.floatToIntBits(t.bodyYawTurns));
		assertEquals(Float.floatToIntBits(bodyFlip), Float.floatToIntBits(t.bodyFlipTurns));
	}
}
