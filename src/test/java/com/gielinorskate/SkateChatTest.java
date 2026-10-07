package com.gielinorskate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkateChatTest
{
	@Test
	public void everyLineIsTaggedAsRuneSkateInTheHighlightColour()
	{
		String sent = SkateChat.format("Nothing interesting happens.");
		assertTrue(sent.startsWith("<colHIGHLIGHT>[RuneSkate] "));
		assertEquals("<colHIGHLIGHT>[RuneSkate] <colNORMAL>Nothing interesting happens.", sent);
	}
}
