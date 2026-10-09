package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.progression.ProfileStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class DuelRecordTest
{
	private static final class MapStore implements ProfileStore
	{
		String profile = "acc1";
		final Map<String, String> values = new HashMap<>();

		@Override
		public String profile()
		{
			return profile;
		}

		@Override
		public String get(String profile, String key)
		{
			return values.get(profile + "/" + key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			values.put(profile + "/" + key, value);
		}
	}

	private final MapStore store = new MapStore();
	private final DuelRecord record = new DuelRecord(store);

	@Test
	public void winsAndLossesAreSavedPerAccount()
	{
		record.load();
		record.add(DuelStateMachine.Outcome.WIN, 1);
		record.add(DuelStateMachine.Outcome.WIN, 1);
		record.add(DuelStateMachine.Outcome.LOSS, 1);
		assertEquals(2, record.getWins());
		assertEquals(1, record.getLosses());
		assertEquals("2", store.values.get("acc1/" + DuelRecord.WINS_KEY));
		assertEquals("1", store.values.get("acc1/" + DuelRecord.LOSSES_KEY));

		store.profile = "acc2";
		record.load();
		assertEquals(0, record.getWins());
		record.add(DuelStateMachine.Outcome.LOSS, 1);
		store.profile = "acc1";
		record.load();
		assertEquals(2, record.getWins());
		assertEquals(1, record.getLosses());
	}

	@Test
	public void drawsAndCancelsAreNotCounted()
	{
		record.load();
		record.add(DuelStateMachine.Outcome.DRAW, 1);
		record.add(DuelStateMachine.Outcome.CANCELLED, 1);
		assertEquals(0, record.getWins());
		assertEquals(0, record.getLosses());
		assertTrue(store.values.isEmpty());
	}

	@Test
	public void aResultAfterTheAccountChangedGoesToTheNewAccount()
	{
		record.load();
		store.profile = "acc2";
		record.add(DuelStateMachine.Outcome.WIN, 1);
		assertEquals("1", store.values.get("acc2/" + DuelRecord.WINS_KEY));
		assertEquals(null, store.values.get("acc1/" + DuelRecord.WINS_KEY));
	}

	@Test
	public void loggedOutNothingIsSavedAndBadValuesReadAsZero()
	{
		store.values.put("acc1/" + DuelRecord.WINS_KEY, "lots");
		record.load();
		assertEquals(0, record.getWins());
		store.profile = null;
		record.load();
		record.add(DuelStateMachine.Outcome.WIN, 1);
		assertFalse(store.values.containsKey("null/" + DuelRecord.WINS_KEY));
	}

	@Test
	public void theKeysAreKnown()
	{
		assertTrue(DuelRecord.isKey("duelWins"));
		assertTrue(DuelRecord.isKey("duelLosses"));
		assertFalse(DuelRecord.isKey("shareWithParty"));
	}

	@Test
	public void aVoidedLossIsTakenBack()
	{
		record.load();
		record.add(DuelStateMachine.Outcome.LOSS, 1);
		record.add(DuelStateMachine.Outcome.LOSS, 1);
		record.add(DuelStateMachine.Outcome.LOSS, -1);
		assertEquals(1, record.getLosses());
		assertEquals("1", store.values.get("acc1/" + DuelRecord.LOSSES_KEY));
		record.add(DuelStateMachine.Outcome.LOSS, -1);
		record.add(DuelStateMachine.Outcome.LOSS, -1);
		assertEquals("never below 0", 0, record.getLosses());
	}
}
