package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;

/** Goals that last the UTC day per account, so stopping and starting skating no longer rerolls them. */
public class DailyGoalsTest
{
	private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

	private ProgressionTest.FakeStore store;
	private DailyGoals daily;
	private Random random;

	private static ComboScorer.Summary combo(int value, Trick... tricks)
	{
		return new ComboScorer.Summary(Arrays.asList(tricks), 0f, 0f, 0, value, tricks.length, false, 0, false);
	}

	private static List<SessionGoals.Type> types(DailyGoals d)
	{
		List<SessionGoals.Type> out = new ArrayList<>();
		for (SessionGoals.Goal g : d.goals().goals())
		{
			out.add(g.type);
		}
		return out;
	}

	/** Puts these goals in the account's profile for {@code day}. */
	private void saved(String profile, LocalDate day, SessionGoals.Type... types)
	{
		SessionGoals g = new SessionGoals();
		g.start(Arrays.asList(types));
		store.values.put(profile + "." + DailyGoals.DAY_KEY, day.toString());
		store.values.put(profile + "." + DailyGoals.STATE_KEY, DailyGoals.encode(g.goals()));
	}

	@Before
	public void setUp()
	{
		store = new ProgressionTest.FakeStore();
		store.current = "rsprofile.a";
		daily = new DailyGoals(store);
		random = new Random(3);
	}

	@Test
	public void firstLoadRollsThreeGoalsAndSavesThemForTheDay()
	{
		daily.load(DAY, random);
		assertEquals(3, daily.goals().goals().size());
		assertEquals("2026-10-05", store.values.get("rsprofile.a." + DailyGoals.DAY_KEY));
		assertEquals(DailyGoals.encode(daily.goals().goals()), store.values.get("rsprofile.a." + DailyGoals.STATE_KEY));
	}

	@Test
	public void startingAgainTheSameDayKeepsTheGoalsAndTheirProgress()
	{
		saved("rsprofile.a", DAY, SessionGoals.Type.BODY_FLIP, SessionGoals.Type.DIFFERENT_FLIPS,
			SessionGoals.Type.TOTAL_POINTS);
		daily.load(DAY, random);
		assertEquals(1, daily.onLanded(combo(300, Trick.BACKFLIP, Trick.KICKFLIP), DAY, random).size());

		// stop and start skating (and even reload the account) the same day: nothing rerolls or pays twice
		DailyGoals again = new DailyGoals(store);
		again.load(DAY, new Random(99));
		again.startSession(DAY, new Random(99));
		assertEquals(Arrays.asList(SessionGoals.Type.BODY_FLIP, SessionGoals.Type.DIFFERENT_FLIPS,
			SessionGoals.Type.TOTAL_POINTS), types(again));
		assertTrue(again.goals().goals().get(0).isDone());
		assertTrue(again.onLanded(combo(300, Trick.BACKFLIP), DAY, random).isEmpty());
		// the "different" goals remember which tricks they have seen
		assertEquals("1/5", again.goals().goals().get(1).progressText());
		again.onLanded(combo(300, Trick.KICKFLIP), DAY, random);
		assertEquals("1/5", again.goals().goals().get(1).progressText());
		assertEquals("900/50,000", again.goals().goals().get(2).progressText());
	}

	@Test
	public void aNewUtcDayRollsNewGoals()
	{
		saved("rsprofile.a", DAY, SessionGoals.Type.BODY_FLIP, SessionGoals.Type.DIFFERENT_FLIPS,
			SessionGoals.Type.TOTAL_POINTS);
		daily.load(DAY, random);
		daily.onLanded(combo(300, Trick.BACKFLIP), DAY, random);
		LocalDate next = DAY.plusDays(1);
		daily.startSession(next, random);
		for (SessionGoals.Goal g : daily.goals().goals())
		{
			assertFalse(g.isDone());
		}
		assertEquals("2026-10-06", store.values.get("rsprofile.a." + DailyGoals.DAY_KEY));
	}

	@Test
	public void theDayCanTurnMidSession()
	{
		saved("rsprofile.a", DAY, SessionGoals.Type.BODY_FLIP, SessionGoals.Type.DIFFERENT_FLIPS,
			SessionGoals.Type.TOTAL_POINTS);
		daily.load(DAY, random);
		daily.onLanded(combo(300, Trick.BACKFLIP), DAY, random);
		daily.onLanded(combo(300, Trick.OLLIE), DAY.plusDays(1), random);
		assertEquals("2026-10-06", store.values.get("rsprofile.a." + DailyGoals.DAY_KEY));
	}

	@Test
	public void eachAccountHasItsOwnGoals()
	{
		saved("rsprofile.a", DAY, SessionGoals.Type.BODY_FLIP, SessionGoals.Type.DIFFERENT_FLIPS,
			SessionGoals.Type.TOTAL_POINTS);
		saved("rsprofile.b", DAY, SessionGoals.Type.GRABS, SessionGoals.Type.SPIN_360, SessionGoals.Type.MULTIPLIER);
		daily.load(DAY, random);
		daily.onLanded(combo(300, Trick.BACKFLIP), DAY, random);

		store.current = "rsprofile.b";
		daily.load(DAY, random);
		assertEquals(Arrays.asList(SessionGoals.Type.GRABS, SessionGoals.Type.SPIN_360, SessionGoals.Type.MULTIPLIER),
			types(daily));
		daily.onLanded(combo(300, Trick.OLLIE), DAY, random);
		// b's landing is saved to b, not a
		assertTrue(store.values.get("rsprofile.a." + DailyGoals.STATE_KEY).contains("BODY_FLIP"));
		assertTrue(store.values.get("rsprofile.b." + DailyGoals.STATE_KEY).contains("GRABS"));

		store.current = "rsprofile.a";
		daily.load(DAY, random);
		assertTrue(daily.goals().goals().get(0).isDone());
	}

	@Test
	public void loggedOutGoalsLiveInMemoryOnly()
	{
		store.current = null;
		daily.load(DAY, random);
		assertEquals(3, daily.goals().goals().size());
		List<SessionGoals.Type> first = types(daily);
		daily.startSession(DAY, random);
		assertEquals(first, types(daily));
		assertTrue(store.writes.isEmpty());
	}

	@Test
	public void unreadableSavedGoalsAreRerolled()
	{
		store.values.put("rsprofile.a." + DailyGoals.DAY_KEY, DAY.toString());
		store.values.put("rsprofile.a." + DailyGoals.STATE_KEY, "garbage");
		daily.load(DAY, random);
		assertEquals(3, daily.goals().goals().size());
		assertNotEquals("garbage", store.values.get("rsprofile.a." + DailyGoals.STATE_KEY));
	}

	@Test
	public void decodeRejectsBadData()
	{
		assertNull(DailyGoals.decode(null));
		assertNull(DailyGoals.decode(""));
		assertNull(DailyGoals.decode("1|BODY_FLIP,0,0,"));
		assertNull(DailyGoals.decode("1|NOPE,0,0,|GRABS,0,0,|SPIN_360,0,0,"));
		assertNull(DailyGoals.decode("1|GRABS,NaN,0,|BODY_FLIP,0,0,|SPIN_360,0,0,"));
		assertNull(DailyGoals.decode("1|GRABS,-1,0,|BODY_FLIP,0,0,|SPIN_360,0,0,"));
		assertNull(DailyGoals.decode("1|GRABS,0,0,|GRABS,0,0,|SPIN_360,0,0,"));
		// unknown trick names (a trick removed in an update) are dropped, not fatal
		List<SessionGoals.Goal> ok = DailyGoals.decode("1|DIFFERENT_FLIPS,2,0,KICKFLIP+GONE|GRABS,3,1,|SPIN_360,0,0,");
		assertEquals(3, ok.size());
		assertEquals("2/5", ok.get(0).progressText());
		assertTrue(ok.get(1).isDone());
	}

	@Test
	public void roundTrips()
	{
		SessionGoals g = new SessionGoals();
		g.start(Arrays.asList(SessionGoals.Type.DIFFERENT_GRINDS, SessionGoals.Type.GRIND_SECONDS,
			SessionGoals.Type.CLEAN_LANDINGS));
		g.onLanded(new ComboScorer.Summary(Arrays.asList(Trick.FIFTY_FIFTY), 4.25f, 0f, 0, 900, 1, true, 0, false));
		String s = DailyGoals.encode(g.goals());
		assertEquals(s, DailyGoals.encode(DailyGoals.decode(s)));
	}

	@Test
	public void savedGoalsAreProgressNotSettings()
	{
		assertTrue(ProgressionService.isProgressKey(DailyGoals.DAY_KEY));
		assertTrue(ProgressionService.isProgressKey(DailyGoals.STATE_KEY));
	}
}
