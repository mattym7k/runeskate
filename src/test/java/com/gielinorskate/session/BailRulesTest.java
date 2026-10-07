package com.gielinorskate.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.GielinorSkateConfig.AfterBail;
import org.junit.Test;

/** Whether a bail knocks the skater off, and R during a Skate Duel. */
public class BailRulesTest
{
	@Test
	public void theSettingChoosesOutsideADuel()
	{
		assertTrue(BailRules.knocksOff(AfterBail.KNOCKED_OFF, false));
		assertFalse(BailRules.knocksOff(AfterBail.HOP_BACK_ON, false));
		// a missing setting keeps the default
		assertTrue(BailRules.knocksOff(null, false));
	}

	@Test
	public void aDuelAlwaysKnocksOffWhateverTheSetting()
	{
		assertTrue(BailRules.knocksOff(AfterBail.HOP_BACK_ON, true));
		assertTrue(BailRules.knocksOff(AfterBail.KNOCKED_OFF, true));
	}

	@Test
	public void rWorksOutsideADuel()
	{
		BailRules.ResetGate g = new BailRules.ResetGate();
		assertTrue(g.allow(true, false));
		assertFalse(g.takeHint());
		assertFalse(g.allow(false, false));
	}

	@Test
	public void rIsIgnoredInADuelWithOneHintPerDuel()
	{
		BailRules.ResetGate g = new BailRules.ResetGate();
		assertFalse(g.allow(true, true));
		assertTrue(g.takeHint());
		assertFalse(g.takeHint());
		assertFalse(g.allow(true, true));
		assertFalse("once per duel", g.takeHint());
		// the duel ends: R works again
		assertTrue(g.allow(true, false));
		assertFalse(g.takeHint());
		// the next duel says it again
		assertFalse(g.allow(true, true));
		assertTrue(g.takeHint());
	}

	@Test
	public void noHintWithoutR()
	{
		BailRules.ResetGate g = new BailRules.ResetGate();
		assertFalse(g.allow(false, true));
		assertFalse(g.takeHint());
	}

	@Test
	public void theHintText()
	{
		assertTrue(BailRules.R_DISABLED_HINT.contains("R is disabled during a Skate Duel"));
	}
}
