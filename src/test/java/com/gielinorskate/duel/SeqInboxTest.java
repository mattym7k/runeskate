package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SeqInboxTest
{
	private final SeqInbox<String> in = new SeqInbox<>();

	@Test
	public void inOrderMessagesComeStraightThrough()
	{
		in.reset(1);
		assertEquals(Collections.singletonList("a"), in.offer(2, "a", 0f));
		assertEquals(Collections.singletonList("b"), in.offer(3, "b", 0f));
		assertEquals(3, in.last());
	}

	@Test
	public void staleAndRepeatedSeqsAreDropped()
	{
		in.reset(5);
		assertTrue(in.offer(5, "dup", 0f).isEmpty());
		assertTrue(in.offer(2, "old", 0f).isEmpty());
		assertEquals(Collections.singletonList("x"), in.offer(6, "x", 0f));
		assertTrue(in.offer(6, "again", 0f).isEmpty());
	}

	@Test
	public void anEarlyMessageWaitsForTheGapThenBothGoInOrder()
	{
		in.reset(1);
		assertTrue(in.offer(3, "c", 0f).isEmpty());
		assertTrue(in.offer(4, "d", 0f).isEmpty());
		assertEquals(Arrays.asList("b", "c", "d"), in.offer(2, "b", 0.1f));
		assertEquals(4, in.last());
	}

	@Test
	public void aGapThatNeverFillsIsSkippedAfterTheTimeout()
	{
		in.reset(1);
		assertTrue(in.offer(3, "c", 0f).isEmpty());
		assertTrue(in.drain(SeqInbox.GAP_TIMEOUT - 0.1f).isEmpty());
		assertEquals(Collections.singletonList("c"), in.drain(SeqInbox.GAP_TIMEOUT));
		// the missing one turning up later is stale
		assertTrue(in.offer(2, "b", 10f).isEmpty());
	}

	@Test
	public void itHoldsOnlyABoundedNumberOfEarlyMessages()
	{
		in.reset(0);
		for (int s = 2; s < 2 + SeqInbox.MAX_HELD + 50; s++)
		{
			in.offer(s, "m" + s, 0f);
		}
		assertEquals(SeqInbox.MAX_HELD, in.held());
	}
}
