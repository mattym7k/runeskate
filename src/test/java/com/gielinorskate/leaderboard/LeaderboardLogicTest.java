package com.gielinorskate.leaderboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.ProfileStore;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.RuneScapeProfileType;
import org.junit.Test;

/** Week boundary, retry policy, queue and backoff, fetch throttle, response parsing, per-account storage. */
public class LeaderboardLogicTest
{
	// ---- week boundary: Monday 00:00 UTC

	@Test
	public void weekStartsMondayMidnightUtc()
	{
		assertEquals("2026-10-05", WeekStart.key(Instant.parse("2026-10-05T00:00:00Z").toEpochMilli()));
		assertEquals("2026-09-28", WeekStart.key(Instant.parse("2026-10-04T23:59:59.999Z").toEpochMilli()));
		assertEquals("2026-10-05", WeekStart.key(Instant.parse("2026-10-11T23:59:59Z").toEpochMilli()));
		assertEquals("2026-10-12", WeekStart.key(Instant.parse("2026-10-12T00:00:00Z").toEpochMilli()));
		// across a year end
		assertEquals("2026-12-28", WeekStart.key(Instant.parse("2027-01-01T12:00:00Z").toEpochMilli()));
	}

	// ---- response policy (API.md "Errors and retries")

	@Test
	public void retryRules()
	{
		assertEquals(ResponsePolicy.Action.OK, ResponsePolicy.classify(200, null));
		assertEquals(ResponsePolicy.Action.OK, ResponsePolicy.classify(201, null));
		assertEquals(ResponsePolicy.Action.CLAIM, ResponsePolicy.classify(404, "not_claimed"));
		assertEquals(ResponsePolicy.Action.DROP, ResponsePolicy.classify(404, "not_found"));
		for (int status : new int[]{400, 403, 405, 409, 413, 422})
		{
			assertEquals("status " + status, ResponsePolicy.Action.DROP, ResponsePolicy.classify(status, "x"));
		}
		assertEquals(ResponsePolicy.Action.RETRY, ResponsePolicy.classify(429, "rate_limited"));
		assertEquals(ResponsePolicy.Action.RETRY, ResponsePolicy.classify(500, "internal"));
		assertEquals(ResponsePolicy.Action.RETRY, ResponsePolicy.classify(503, null));
		assertEquals(ResponsePolicy.Action.RETRY, ResponsePolicy.classify(0, null));
		assertEquals(Long.valueOf(42_000), ResponsePolicy.retryAfterMs("42"));
		assertEquals(Long.valueOf(1_000), ResponsePolicy.retryAfterMs("0"));
		assertNull(ResponsePolicy.retryAfterMs(null));
		assertNull(ResponsePolicy.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"));
	}

	// ---- queue and backoff

	@Test
	public void backoffDoublesFromThirtySecondsUpToTenMinutes()
	{
		assertEquals(30_000, SubmitQueue.backoffMs(1));
		assertEquals(60_000, SubmitQueue.backoffMs(2));
		assertEquals(120_000, SubmitQueue.backoffMs(3));
		assertEquals(240_000, SubmitQueue.backoffMs(4));
		assertEquals(480_000, SubmitQueue.backoffMs(5));
		assertEquals(600_000, SubmitQueue.backoffMs(6));
		assertEquals(600_000, SubmitQueue.backoffMs(60));
	}

	@Test
	public void queueHoldsAtMostFiftyDroppingTheOldest()
	{
		SubmitQueue q = new SubmitQueue();
		SubmitQueue.Item first = q.add(RunSubmission.session(1, 1000, 0f, java.util.Collections.emptyList()), 7, 0);
		for (int i = 0; i < 55; i++)
		{
			q.add(RunSubmission.session(i, 1000, 0f, java.util.Collections.emptyList()), 7, 0);
		}
		assertEquals(SubmitQueue.CAPACITY, q.size());
		assertFalse(q.contains(first));
	}

	@Test
	public void failedItemWaitsItsBackoffOrRetryAfter()
	{
		SubmitQueue q = new SubmitQueue();
		SubmitQueue.Item a = q.add(RunSubmission.xp(10), 7, 1000);
		assertSame(a, q.due(1000));
		q.retryLater(a, 1000, null);
		assertNull(q.due(30_999));
		assertSame(a, q.due(31_000));
		q.retryLater(a, 31_000, null);
		assertNull(q.due(90_999));
		assertSame(a, q.due(91_000));
		q.retryLater(a, 91_000, 5_000L);
		assertSame(a, q.due(96_000));
		q.done(a);
		assertEquals(0, q.size());
	}

	@Test
	public void newerXpReplacesQueuedXpAndClearDropsAll()
	{
		SubmitQueue q = new SubmitQueue();
		q.add(RunSubmission.xp(10), 7, 0);
		q.add(RunSubmission.session(5, 1000, 0f, java.util.Collections.emptyList()), 7, 0);
		q.add(RunSubmission.xp(20), 7, 0);
		assertEquals(2, q.size());
		assertEquals(Integer.valueOf(20), q.items().get(1).run.xp);
		q.add(RunSubmission.xp(30), 8, 0);
		q.keepOnly(8);
		assertEquals(1, q.size());
		q.clear();
		assertEquals(0, q.size());
	}

	// ---- client-side refresh throttle

	@Test
	public void throttleServesFreshBoardsFromCacheAndSpacesRequests()
	{
		FetchThrottle t = new FetchThrottle();
		assertTrue(t.tryFetch("combo/all", 0));
		assertFalse(t.tryFetch("combo/all", 10_000));
		assertFalse(t.tryFetch("combo/week", 1_000));
		assertTrue(t.tryFetch("combo/week", 2_000));
		assertEquals(20_000, t.waitMs("combo/all", 10_000));
		assertTrue(t.tryFetch("combo/all", 30_000));
		t.failed("combo/all");
		assertTrue(t.tryFetch("combo/all", 32_000));
	}

	// ---- the on-screen board's refresh

	@Test
	public void hudRefreshesWhenMissingStaleOrAMinuteOld()
	{
		long min = LeaderboardService.HUD_REFRESH_MS;
		assertTrue("nothing yet", LeaderboardService.hudDue(0, false, 5_000, 0));
		assertFalse("fresh", LeaderboardService.hudDue(5_000, false, 5_000 + min - 1, 0));
		assertTrue("a minute old", LeaderboardService.hudDue(5_000, false, 5_000 + min, 0));
		assertTrue("marked stale (login, own submit)", LeaderboardService.hudDue(5_000, true, 6_000, 0));
		assertFalse("backing off after a failure", LeaderboardService.hudDue(0, true, 6_000, 6_001));
		assertTrue("back-off over", LeaderboardService.hudDue(0, true, 6_001, 6_001));
	}

	// ---- response parsing

	@Test
	public void parsesALeaderboardWithTheCallersRow()
	{
		String json = "{\"category\":\"combo\",\"period\":\"week\",\"weekStart\":\"2026-10-05\",\"total\":3,"
			+ "\"entries\":[{\"rank\":1,\"displayName\":\"Zezima\",\"score\":90000,\"updatedAt\":\"2026-10-05T10:00:00.000Z\"},"
			+ "{\"rank\":2,\"displayName\":\"Me\",\"score\":5000,\"updatedAt\":\"2026-10-05T11:00:00.000Z\"}],"
			+ "\"you\":{\"rank\":2,\"displayName\":\"Me\",\"score\":5000,\"updatedAt\":\"2026-10-05T11:00:00.000Z\"}}";
		LeaderboardPage p = LeaderboardPage.parse(new Gson(), json);
		assertEquals(2, p.entries.size());
		assertEquals(90000, p.entries.get(0).score);
		assertTrue(p.entries.get(1).sameAs(p.you));
		assertFalse(p.entries.get(0).sameAs(p.you));

		LeaderboardPage none = LeaderboardPage.parse(new Gson(), "{\"category\":\"xp\",\"period\":\"all\","
			+ "\"weekStart\":null,\"total\":0,\"entries\":[],\"you\":null}");
		assertEquals(0, none.entries.size());
		assertNull(none.you);
	}

	@Test
	public void malformedResponsesParseToNull()
	{
		Gson gson = new Gson();
		assertNull(LeaderboardPage.parse(gson, "not json"));
		assertNull(LeaderboardPage.parse(gson, "{\"error\":\"internal\"}"));
		assertNull(LeaderboardPage.parse(gson, ""));
		assertEquals("bad_secret", LeaderboardPage.errorCode(gson, "{\"error\":\"bad_secret\"}"));
		assertNull(LeaderboardPage.errorCode(gson, "<html>"));
		assertEquals(4200, LeaderboardPage.weekBest(gson, "{\"ok\":true,\"kind\":\"combo\",\"stored\":{\"all\":true,"
			+ "\"week\":true},\"best\":{\"all\":5000,\"week\":4200},\"weekStart\":\"2026-10-05\"}"));
		assertEquals(-1, LeaderboardPage.weekBest(gson, "{\"ok\":true,\"kind\":\"xp\"}"));
	}

	// ---- per-account storage

	private static final class MapStore implements ProfileStore
	{
		final Map<String, String> values = new HashMap<>();
		String profile = "acct1";

		@Override
		public String profile()
		{
			return profile;
		}

		@Override
		public String get(String profile, String key)
		{
			return values.get(profile + "." + key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			values.put(profile + "." + key, value);
		}
	}

	@Test
	public void secretIsMadeOncePerAccountAndIsUrlSafe()
	{
		MapStore store = new MapStore();
		LeaderboardStore lb = new LeaderboardStore(store);
		String s = lb.secret();
		assertTrue(s.matches("^[A-Za-z0-9_-]{43}$"));
		assertEquals(s, lb.secret());
		store.profile = "acct2";
		assertNotEquals(s, lb.secret());
		store.profile = null;
		assertNull(lb.secret());
		assertTrue(LeaderboardStore.isKey("leaderboardSecret"));
	}

	@Test
	public void comboWeekBestResetsWithTheWeek()
	{
		LeaderboardStore lb = new LeaderboardStore(new MapStore());
		assertEquals(0, lb.comboWeekBest("2026-10-05"));
		lb.setComboWeekBest("2026-10-05", 1234);
		assertEquals(1234, lb.comboWeekBest("2026-10-05"));
		assertEquals(0, lb.comboWeekBest("2026-10-12"));
	}

	@Test
	public void runBestIsAPersonalBestOnlyWhenBeaten()
	{
		LeaderboardStore lb = new LeaderboardStore(new MapStore());
		assertFalse(lb.recordRun(0));
		assertTrue(lb.recordRun(500));
		assertFalse(lb.recordRun(500));
		assertFalse(lb.recordRun(400));
		assertTrue(lb.recordRun(501));
		assertEquals(501, lb.runBest());
	}

	// ---- display name, as the server sanitises it

	@Test
	public void displayNameIsCleanedOrRefused()
	{
		assertEquals("Zezima", RunSubmission.displayName("Zezima"));
		assertEquals("Iron Man", RunSubmission.displayName("Iron" + (char) 0xA0 + "Man"));
		assertEquals("a b", RunSubmission.displayName("  a   b "));
		assertEquals("Max-Cape_1", RunSubmission.displayName("Max-Cape_1"));
		assertNull(RunSubmission.displayName("ThirteenChars"));
		assertNull(RunSubmission.displayName("<img>"));
		assertNull(RunSubmission.displayName(" "));
		assertNull(RunSubmission.displayName(null));
	}

	// ---- server

	@Test
	public void theServerIsOneFixedHttpsOrigin()
	{
		assertEquals("https://runeskate-leaderboard.runeskate.workers.dev", LeaderboardClient.SERVER);
	}

	@Test
	public void onlyNormalWorldsSubmit()
	{
		// the server keys an account by its hash, but RuneLite keeps a profile (secret, week best) per world type
		assertTrue(LeaderboardService.submitsFrom(RuneScapeProfileType.STANDARD));
		for (RuneScapeProfileType t : RuneScapeProfileType.values())
		{
			if (t != RuneScapeProfileType.STANDARD)
			{
				assertFalse(t.name(), LeaderboardService.submitsFrom(t));
			}
		}
		assertFalse(LeaderboardService.submitsFrom(null));
	}

	private static RunSubmission comboRun(int value)
	{
		return RunSubmission.combo(new com.gielinorskate.scoring.ComboScorer.Landed(value, 0f, 1f,
			java.util.Collections.singletonList(new com.gielinorskate.scoring.ComboScorer.TrickRecord("OLLIE", value, 0,
				0f))));
	}

	@Test
	public void aLostComboSubmitDoesNotRaiseTheWeekBest()
	{
		long monday = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli();
		String week = WeekStart.key(monday);
		SubmitQueue q = new SubmitQueue();
		// nothing confirmed, nothing queued: any combo beats the bar
		assertEquals(0, LeaderboardService.comboBar(0, q, 7, week));
		SubmitQueue.Item big = q.add(comboRun(5000), 7, monday);
		// queued (maybe in backoff): smaller combos are not worth queueing too
		assertEquals(5000, LeaderboardService.comboBar(0, q, 7, week));
		// another account's or last week's queued combos don't count
		assertEquals(0, LeaderboardService.comboBar(0, q, 8, week));
		assertEquals(0, LeaderboardService.comboBar(0, q, 7, WeekStart.key(monday + 7L * 86_400_000L)));
		// the submit is refused (or the queue dropped at logout): the bar falls back to what the server has
		q.done(big);
		assertEquals(1200, LeaderboardService.comboBar(1200, q, 7, week));
		q.add(comboRun(5000), 7, monday);
		q.clear();
		assertEquals(1200, LeaderboardService.comboBar(1200, q, 7, week));
	}

	@Test
	public void xpIsResentUntilTheServerConfirmsIt()
	{
		long gap = LeaderboardService.XP_INTERVAL_MS;
		// never sent
		assertTrue(LeaderboardService.xpDue(500, -1, -1, gap));
		// sent less than 10 minutes ago
		assertFalse(LeaderboardService.xpDue(600, 500, 500, gap - 1));
		assertTrue(LeaderboardService.xpDue(600, 500, 500, gap));
		// the logout submit went unanswered: xpSent still says 500, so 600 goes again after relogging
		assertTrue(LeaderboardService.xpDue(600, 500, -1, gap));
		// the logout submit was confirmed: not sent again
		assertFalse(LeaderboardService.xpDue(600, 500, 600, gap));
		assertFalse(LeaderboardService.xpDue(0, -1, -1, gap));
	}

	@Test
	public void nothingIsSentWhileThePluginIsOff()
	{
		com.gielinorskate.GielinorSkateConfig on = new com.gielinorskate.GielinorSkateConfig()
		{
			@Override
			public boolean submitScores()
			{
				return true;
			}
		};
		LeaderboardService service = new LeaderboardService(null, null, on, null, null, new Gson(), null);
		assertTrue(service.isEnabled());
		service.shutDown();
		assertFalse(service.isEnabled());
		service.startUp();
		assertTrue(service.isEnabled());
	}
}
