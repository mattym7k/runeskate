package com.gielinorskate.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.GielinorSkateConfig.AfterBail;
import org.junit.Test;

/** Whether a bail knocks the skater off. */
public class BailRulesTest
{
	@Test
	public void theSettingChooses()
	{
		assertTrue(BailRules.knocksOff(AfterBail.KNOCKED_OFF));
		assertFalse(BailRules.knocksOff(AfterBail.HOP_BACK_ON));
		// a missing setting keeps the default
		assertTrue(BailRules.knocksOff(null));
	}
}
