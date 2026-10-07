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
		record.record(DuelStateMachine.Outcome.WIN);
		record.record(DuelStateMachine.Outcome.WIN);
		record.record(DuelStateMachine.Outcome.LOSS);
		assertEquals(2, record.wins());
		assertEquals(1, record.losses());
		assertEquals("2", store.values.get("acc1/" + DuelRecord.WINS_KEY));
		assertEquals("1", store.values.get("acc1/" + DuelRecord.LOSSES_KEY));

		store.profile = "acc2";
		record.load();
		assertEquals(0, record.wins());
		record.record(DuelStateMachine.Outcome.LOSS);
		store.profile = "acc1";
		record.load();
		assertEquals(2, record.wins());
		assertEquals(1, record.losses());
	}

	@Test
	public void drawsAndCancelsAreNotCounted()
	{
		record.load();
		record.record(DuelStateMachine.Outcome.DRAW);
		record.record(DuelStateMachine.Outcome.CANCELLED);
		assertEquals(0, record.wins());
		assertEquals(0, record.losses());
		assertTrue(store.values.isEmpty());
	}

	@Test
	public void aResultAfterTheAccountChangedGoesToTheNewAccount()
	{
		record.load();
		store.profile = "acc2";
		record.record(DuelStateMachine.Outcome.WIN);
		assertEquals("1", store.values.get("acc2/" + DuelRecord.WINS_KEY));
		assertEquals(null, store.values.get("acc1/" + DuelRecord.WINS_KEY));
	}

	@Test
	public void loggedOutNothingIsSavedAndBadValuesReadAsZero()
	{
		store.values.put("acc1/" + DuelRecord.WINS_KEY, "lots");
		record.load();
		assertEquals(0, record.wins());
		store.profile = null;
		record.load();
		record.record(DuelStateMachine.Outcome.WIN);
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
		record.record(DuelStateMachine.Outcome.LOSS);
		record.record(DuelStateMachine.Outcome.LOSS);
		record.unrecord(DuelStateMachine.Outcome.LOSS);
		assertEquals(1, record.losses());
		assertEquals("1", store.values.get("acc1/" + DuelRecord.LOSSES_KEY));
		record.unrecord(DuelStateMachine.Outcome.LOSS);
		record.unrecord(DuelStateMachine.Outcome.LOSS);
		assertEquals("never below 0", 0, record.losses());
	}
}
