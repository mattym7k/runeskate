package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The idle pose given back on exit follows what the game set while skating. */
public class IdlePoseTrackerTest
{
	private static final int UNARMED = 808;
	private static final int TWO_HANDED = 2561;
	private static final int STANCE = 1000;
	private static final int CROUCH = 1001;

	@Test
	public void givesBackThePoseFromBeforeSkating()
	{
		IdlePoseTracker t = new IdlePoseTracker();
		t.start(UNARMED);
		t.wrote(STANCE);
		t.observe(STANCE);
		t.wrote(CROUCH);
		t.observe(CROUCH);
		assertEquals(UNARMED, t.restorePose());
	}

	@Test
	public void aWeaponWieldedWhileSkatingChangesThePoseGivenBack()
	{
		IdlePoseTracker t = new IdlePoseTracker();
		t.start(UNARMED);
		t.wrote(STANCE);
		// the server's appearance update replaced our stance with the two-handed idle
		t.observe(TWO_HANDED);
		assertEquals(TWO_HANDED, t.restorePose());
		t.wrote(STANCE);
		t.observe(STANCE);
		assertEquals(TWO_HANDED, t.restorePose());
	}

	@Test
	public void nothingWrittenYetMeansNothingToCompare()
	{
		IdlePoseTracker t = new IdlePoseTracker();
		t.start(UNARMED);
		t.observe(UNARMED);
		assertEquals(UNARMED, t.restorePose());
		t.observe(TWO_HANDED);
		assertEquals(TWO_HANDED, t.restorePose());
	}
}
