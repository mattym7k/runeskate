package com.gielinorskate.session;

import com.gielinorskate.GielinorSkateConfig.AfterBail;

/** What a bail does. Pure. */
final class BailRules
{
	private BailRules()
	{
	}

	/**
	 * A bail knocks the skater off the board ({@link Knockdown}) with "Get knocked off", the default; otherwise ("Hop
	 * straight back on") it is the quick fall and straight back on.
	 */
	static boolean knocksOff(AfterBail setting)
	{
		return setting != AfterBail.HOP_BACK_ON;
	}
}
