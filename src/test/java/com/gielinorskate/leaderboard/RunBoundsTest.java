package com.gielinorskate.leaderboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.tricks.TrickKind;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

/**
 * Client and server must not silently disagree: submissions built from real {@link ComboScorer} runs pass the
 * Java port of the server's checks ({@link RunBounds}), the port reads the same trick table as the server's
 * tricks.json, and tampered runs fail with the server's reason codes.
 */
public class RunBoundsTest
{
	private static final Gson GSON = new Gson();

	// ---- the port's table is the server's table

	@Test
	public void portedTableMatchesBackendTricksJson() throws IOException
	{
		File json = new File("../backend/src/tricks.json");
		assumeTrue("no backend next to the plugin", json.isFile());
		JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(json.toPath()),
			StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(RunBounds.MAX_SPIN_HALF_TURNS, root.get("maxSpinHalfTurns").getAsInt());
		assertEquals(RunBounds.MAX_HOLD_SECONDS, root.get("maxHoldSeconds").getAsInt());
		assertEquals(RunBounds.FIRST_LANDING_BONUS, root.get("firstLandingBonus").getAsDouble(), 0);
		assertEquals(RunBounds.CLEAN_BONUS, root.get("cleanBonus").getAsDouble(), 0);
		assertEquals(RunBounds.LONG_COMBO_MS, root.get("longComboSeconds").getAsDouble() * 1000, 0);
		assertEquals(RunBounds.LONG_COMBO_EXTRA_MULTIPLIER, root.get("longComboExtraMultiplier").getAsInt());
		JsonObject tricks = root.getAsJsonObject("tricks");
		assertEquals(Trick.values().length + 1, tricks.size());
		for (Map.Entry<String, JsonElement> e : tricks.entrySet())
		{
			JsonObject row = e.getValue().getAsJsonObject();
			RunBounds.Info info = RunBounds.info(e.getKey());
			assertNotNull(e.getKey(), info);
			assertEquals(e.getKey(), row.get("points").getAsInt(), info.points);
			assertEquals(e.getKey(), row.get("hold").getAsBoolean(), info.hold);
			assertEquals(e.getKey(), row.get("spinnable").getAsBoolean(), info.spinnable);
			assertEquals(e.getKey(), row.get("maxPoints").getAsInt(), info.maxPoints);
			assertEquals(e.getKey(), row.get("minMs").getAsInt(), info.minMs);
		}
	}

	// ---- realistic combos pass

	private static void assertPasses(ComboScorer scorer)
	{
		ComboScorer.Landed landed = scorer.lastLanded();
		assertEquals(scorer.lastResult().value, landed.value);
		RunSubmission run = RunSubmission.combo(landed);
		assertEquals("combo " + describe(run), null, RunBounds.check(run));
	}

	private static String describe(RunSubmission run)
	{
		return GSON.toJson(run);
	}

	@Test
	public void kickflipEntryIsItsEnumNameWithDecayedPointsAndNoFirstLandingBonus()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 10f);
		scorer.accept(TrickEvent.landed(true), 10.6f);
		RunSubmission run = RunSubmission.combo(scorer.lastLanded());
		assertEquals(RunSubmission.COMBO, run.kind);
		// 300 * 1.25 first landing, clean: round(375 * 1.1)
		assertEquals(Long.valueOf(413), run.score);
		assertEquals(Integer.valueOf(600), run.durationMs);
		assertEquals(1, run.tricks.size());
		RunSubmission.TrickEntry e = run.tricks.get(0);
		assertEquals("KICKFLIP", e.name);
		assertEquals(300, e.points);
		assertEquals(0, e.t);
		assertEquals(0, e.spin);
		assertNull(RunBounds.check(run));
	}

	@Test
	public void spunTrickCarriesSpinBonusAndHalfTurns()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f);
		scorer.accept(TrickEvent.spin(-2), 0.9f);
		scorer.accept(TrickEvent.landed(), 0.9f);
		RunSubmission run = RunSubmission.combo(scorer.lastLanded());
		assertEquals("OLLIE", run.tricks.get(0).name);
		assertEquals(0, run.tricks.get(0).spin);
		assertEquals("KICKFLIP", run.tricks.get(1).name);
		assertEquals(2, run.tricks.get(1).spin);
		assertEquals(300 + 2 * 150, run.tricks.get(1).points);
		assertEquals(100, run.tricks.get(1).t);
		assertNull(RunBounds.check(run));
	}

	@Test
	public void loneSpinIsNamedSpin()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		scorer.accept(TrickEvent.trick(Trick.FRONTFLIP), 0f);
		scorer.accept(TrickEvent.spin(3), 1.2f);
		scorer.accept(TrickEvent.landed(), 1.2f);
		RunSubmission run = RunSubmission.combo(scorer.lastLanded());
		RunSubmission.TrickEntry spin = run.tricks.get(1);
		assertEquals("SPIN", spin.name);
		assertEquals(3, spin.spin);
		assertEquals(450, spin.points);
		assertEquals(1200, spin.t);
		assertNull(RunBounds.check(run));
	}

	@Test
	public void holdsAreTimedFromTheirStartAndDurationIsFromTheScoreClock()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 5f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 2f), 7f);
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 7f);
		scorer.accept(TrickEvent.holdStart(Trick.FIFTY_FIFTY), 7.5f);
		scorer.accept(TrickEvent.holdEnd(Trick.FIFTY_FIFTY, 1.5f), 9f);
		// rolled away: the combo ends with its last event, the roll-out lands it
		scorer.update(9.6f, true);
		RunSubmission run = RunSubmission.combo(scorer.lastLanded());
		assertEquals(Integer.valueOf(4000), run.durationMs);
		assertEquals("MANUAL", run.tricks.get(0).name);
		assertEquals(0, run.tricks.get(0).t);
		assertEquals(300, run.tricks.get(0).points);
		assertEquals(2000, run.tricks.get(1).t);
		assertEquals("FIFTY_FIFTY", run.tricks.get(2).name);
		assertEquals(2500, run.tricks.get(2).t);
		assertEquals(300, run.tricks.get(2).points);
		assertNull(RunBounds.check(run));
	}

	@Test
	public void repeatsDecayAndALongCleanComboStillPasses()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		float t = 0f;
		for (int i = 0; i < 6; i++)
		{
			scorer.accept(TrickEvent.trick(Trick.OLLIE), t);
			scorer.accept(TrickEvent.trick(Trick.TRE_FLIP), t + 0.05f);
			scorer.accept(TrickEvent.holdStart(Trick.SMITH), t + 0.6f);
			scorer.accept(TrickEvent.holdEnd(Trick.SMITH, 1.2f), t + 1.8f);
			t += 1.8f;
		}
		scorer.accept(TrickEvent.trick(Trick.OLLIE), t);
		scorer.accept(TrickEvent.landed(true), t + 0.6f);
		assertEquals(true, scorer.lastSummary().longCombo);
		assertPasses(scorer);
		RunSubmission run = RunSubmission.combo(scorer.lastLanded());
		// the sixth tre flip decayed to the floor: 900 * 0.25
		assertEquals(225, run.tricks.get(16).points);
	}

	@Test
	public void upgradedFlipIsTheNewTrick()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.05f);
		scorer.accept(TrickEvent.upgrade(Trick.DOUBLE_KICKFLIP, Trick.KICKFLIP), 0.2f);
		scorer.accept(TrickEvent.landed(), 0.8f);
		RunSubmission run = RunSubmission.combo(scorer.lastLanded());
		assertEquals("DOUBLE_KICKFLIP", run.tricks.get(1).name);
		assertEquals(50, run.tricks.get(1).t);
		assertNull(RunBounds.check(run));
	}

	@Test
	public void bankedComboOnSteppingOffPasses()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 0.6f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 0.4f), 1f);
		scorer.bankCombo(1.1f);
		assertPasses(scorer);
	}

	/**
	 * Random but physically plausible skating: pops, board flips that last at least their own duration, spins,
	 * grabs, manuals and grinds that last as long as they score, landed clean or not, rolled out, or banked.
	 * Every landed combo must pass the server's checks.
	 */
	@Test
	public void randomPlausibleSkatingAlwaysPasses()
	{
		Random random = new Random(1234);
		List<Trick> pops = byKind(TrickKind.POP);
		List<Trick> flips = byKind(TrickKind.FLIP);
		List<Trick> grabs = byKind(TrickKind.GRAB);
		List<Trick> manuals = byKind(TrickKind.MANUAL);
		List<Trick> grinds = byKind(TrickKind.GRIND);
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		float t = 0f;
		int landed = 0;
		for (int combo = 0; combo < 400; combo++)
		{
			int parts = 1 + random.nextInt(8);
			for (int p = 0; p < parts; p++)
			{
				switch (random.nextInt(3))
				{
					case 0:
					{
						// an air: a pop, maybe a flip (taking its time) or a body flip, maybe a grab, maybe a spin
						scorer.accept(TrickEvent.trick(pick(random, pops)), t);
						float air = 0.5f + random.nextFloat();
						if (random.nextBoolean())
						{
							Trick f = pick(random, flips);
							scorer.accept(TrickEvent.trick(f), t + 0.02f);
							air = Math.max(air, 0.02f + f.duration + 0.05f);
						}
						if (random.nextInt(3) == 0)
						{
							Trick g = pick(random, grabs);
							float hold = 0.15f + random.nextFloat() * 0.6f;
							scorer.accept(TrickEvent.holdStart(g), t + 0.1f);
							scorer.accept(TrickEvent.holdEnd(g, hold), t + 0.1f + hold);
							air = Math.max(air, 0.1f + hold + 0.05f);
						}
						if (random.nextBoolean())
						{
							scorer.accept(TrickEvent.spin((random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(4))),
								t + air);
						}
						t += air;
						break;
					}
					case 1:
					{
						Trick m = pick(random, manuals);
						float hold = 0.15f + random.nextFloat() * 3f;
						scorer.accept(TrickEvent.holdStart(m), t);
						t += hold;
						scorer.accept(TrickEvent.holdEnd(m, hold), t);
						break;
					}
					default:
					{
						scorer.accept(TrickEvent.trick(Trick.OLLIE), t);
						Trick g = pick(random, grinds);
						float hold = 0.15f + random.nextFloat() * 4f;
						scorer.accept(TrickEvent.holdStart(g), t + 0.4f);
						t += 0.4f + hold;
						scorer.accept(TrickEvent.holdEnd(g, hold), t);
						break;
					}
				}
			}
			int seq = scorer.resultSequence();
			switch (random.nextInt(4))
			{
				case 0:
					scorer.accept(TrickEvent.landed(true), t);
					break;
				case 1:
					scorer.update(t + 0.5f, true);
					break;
				case 2:
					scorer.bankCombo(t + 0.1f);
					break;
				default:
					scorer.accept(TrickEvent.landed(false), t);
					break;
			}
			t += 1f;
			if (scorer.resultSequence() != seq && scorer.lastResult().isLanded())
			{
				landed++;
				assertPasses(scorer);
			}
		}
		assertEquals(400, landed);
	}

	private static List<Trick> byKind(TrickKind kind)
	{
		List<Trick> out = new ArrayList<>();
		for (Trick t : Trick.values())
		{
			if (t.kind == kind)
			{
				out.add(t);
			}
		}
		return out;
	}

	private static Trick pick(Random random, List<Trick> list)
	{
		return list.get(random.nextInt(list.size()));
	}

	// ---- sessions

	@Test
	public void sessionSumsCombosAndTimesTricksFromTheRunStart()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.startSession();
		List<ComboScorer.TrickRecord> all = new ArrayList<>();
		long score = 0;
		float start = 100f;
		for (int i = 0; i < 30; i++)
		{
			float t = start + i * 3.5f;
			scorer.accept(TrickEvent.trick(Trick.OLLIE), t);
			scorer.accept(TrickEvent.trick(Trick.HEELFLIP), t + 0.05f);
			scorer.accept(TrickEvent.holdStart(Trick.BOARDSLIDE), t + 0.7f);
			scorer.accept(TrickEvent.holdEnd(Trick.BOARDSLIDE, 1.5f), t + 2.2f);
			scorer.accept(TrickEvent.landed(i % 2 == 0), t + 2.6f);
			all.addAll(scorer.lastLanded().tricks);
			score += scorer.lastLanded().value;
		}
		RunSubmission run = RunSubmission.session(score, 120_000, start, all);
		assertEquals(90, run.tricks.size());
		assertEquals(0, run.tricks.get(0).t);
		assertEquals(3500, run.tricks.get(3).t);
		assertNull(RunBounds.check(run));
	}

	@Test
	public void sessionWithMoreThanTwoHundredTricksLeavesTheListOut()
	{
		List<ComboScorer.TrickRecord> many = new ArrayList<>();
		for (int i = 0; i < 201; i++)
		{
			many.add(new ComboScorer.TrickRecord("OLLIE", 100, 0, i * 0.5f));
		}
		RunSubmission run = RunSubmission.session(50_000, 120_000, 0f, many);
		assertNull(run.tricks);
		assertNull(RunBounds.check(run));
		assertEquals("session_duration", RunBounds.check(RunSubmission.session(1, 126_000, 0f, many)));
	}

	// ---- tampering is caught with the server's reasons

	private static RunSubmission combo(long score, int durationMs, RunSubmission.TrickEntry... tricks)
	{
		return new RunSubmission(RunSubmission.COMBO, score, null, Arrays.asList(tricks), durationMs);
	}

	@Test
	public void tamperedRunsFailWithTheServersReasons()
	{
		assertEquals("unknown_trick:0", RunBounds.check(combo(100, 500, new RunSubmission.TrickEntry("MOONWALK", 100,
			0, 0))));
		assertEquals("trick_spin:0", RunBounds.check(combo(100, 500, new RunSubmission.TrickEntry("INDY", 100, 0,
			1))));
		assertEquals("trick_spin:0", RunBounds.check(combo(100, 500, new RunSubmission.TrickEntry("SPIN", 0, 0,
			0))));
		assertEquals("trick_spin:0", RunBounds.check(combo(100, 5000, new RunSubmission.TrickEntry("OLLIE", 100, 0,
			11))));
		assertEquals("trick_points:0", RunBounds.check(combo(100, 500, new RunSubmission.TrickEntry("KICKFLIP", 301,
			0, 0))));
		// a 10 s manual in 1 s
		assertEquals("duration", RunBounds.check(combo(1500, 1000, new RunSubmission.TrickEntry("MANUAL", 1500, 0,
			0))));
		// one kickflip, x1, at most 300 * 1.25 * 1.1
		assertEquals("score", RunBounds.check(combo(500, 500, new RunSubmission.TrickEntry("KICKFLIP", 300, 0, 0))));
		assertNull(RunBounds.check(combo(414, 500, new RunSubmission.TrickEntry("KICKFLIP", 300, 0, 0))));
		assertEquals("score", RunBounds.check(combo(0, 500, new RunSubmission.TrickEntry("KICKFLIP", 300, 0, 0))));
		assertEquals("tricks[0].t", RunBounds.check(combo(100, 500, new RunSubmission.TrickEntry("OLLIE", 100, 1501,
			0))));
		assertEquals("tricks", RunBounds.check(combo(100, 500)));
		assertNull(RunBounds.check(RunSubmission.xp(1000)));
		assertEquals("xp", RunBounds.check(RunSubmission.xp(RunBounds.MAX_XP + 1)));
	}

	// ---- the JSON the server parses

	@Test
	public void bodySerializesTheServersFieldNamesAndLeavesNullsOut()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.landed(), 0.5f);
		RunSubmission.Body body = new RunSubmission.Body(RunSubmission.combo(scorer.lastLanded()), "-12345",
			"s3cr3t", "Zezima", "1.0.0", false);
		JsonObject json = new JsonParser().parse(GSON.toJson(body)).getAsJsonObject();
		assertEquals("-12345", json.get("accountHash").getAsString());
		assertEquals("combo", json.get("kind").getAsString());
		assertEquals(100, json.get("score").getAsInt());
		assertEquals(500, json.get("durationMs").getAsInt());
		assertEquals("1.0.0", json.get("pluginVersion").getAsString());
		assertEquals(false, json.has("xp"));
		assertEquals(false, json.has("devLevelSet"));
		JsonArray tricks = json.getAsJsonArray("tricks");
		JsonObject e = tricks.get(0).getAsJsonObject();
		assertEquals("OLLIE", e.get("name").getAsString());
		assertEquals(100, e.get("points").getAsInt());
		assertEquals(0, e.get("t").getAsInt());
		assertEquals(0, e.get("spin").getAsInt());
		// the secret never shows up in a log line
		assertEquals(false, body.toString().contains("s3cr3t"));

		JsonObject xp = new JsonParser().parse(GSON.toJson(new RunSubmission.Body(RunSubmission.xp(77), "1", "s",
			"A", "1", true))).getAsJsonObject();
		assertEquals(77, xp.get("xp").getAsInt());
		assertEquals(true, xp.get("devLevelSet").getAsBoolean());
		assertEquals(false, xp.has("score"));
		assertEquals(false, xp.has("tricks"));
	}
}
