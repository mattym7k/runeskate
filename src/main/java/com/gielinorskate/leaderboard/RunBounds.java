package com.gielinorskate.leaderboard;

import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.SpinNames;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A Java port of the leaderboard server's input bounds ({@code backend/src/validate.ts}) and plausibility checks
 * ({@code backend/src/plausibility.ts}), so the client never sends a run the server would refuse, and the tests can
 * hold the two sides to the same rules. The trick table is derived from {@link Trick} exactly as the backend's
 * {@code tricks.json} is (TricksJsonExportTest generates it; RunBoundsTest checks this port reads the same). Pure.
 */
public final class RunBounds
{
	/** Same as validate.ts. */
	public static final int MAX_TRICKS = 200;
	public static final long MAX_SCORE = 2_147_483_647L;
	public static final int MAX_XP = 200_000_000;
	public static final int MAX_COMBO_MS = 30 * 60_000;
	public static final int MAX_SESSION_MS = 125_000;
	public static final int MAX_TRICK_POINTS = 1_000_000;
	public static final int MAX_SPIN_FIELD = 100;
	/** Same as tricks.json (TricksJsonExportTest's constants). */
	public static final int MAX_SPIN_HALF_TURNS = 10;
	public static final int MAX_HOLD_SECONDS = 120;
	static final float LATE_FLIP_MIN_FRACTION = 0.6f;
	/** Same as plausibility.ts. */
	static final double DURATION_SLACK_FRACTION = 0.05;
	static final double DURATION_SLACK_MS = 250;
	static final double SESSION_CARRY_OVER_MS = 10_000;
	/** The bonuses as the JSON table writes them (a float printed as its shortest decimal, read as a double). */
	static final double FIRST_LANDING_BONUS = Double.parseDouble(Float.toString(ComboScorer.FIRST_LANDING_BONUS));
	static final double CLEAN_BONUS = Double.parseDouble(Float.toString(ComboScorer.CLEAN_BONUS));
	static final double LONG_COMBO_MS = ComboScorer.LONG_COMBO_SECONDS * 1000.0;
	static final int LONG_COMBO_EXTRA_MULTIPLIER = 1;

	private static final Pattern NAME = Pattern.compile("^[A-Z0-9_]{1,40}$");

	private RunBounds()
	{
	}

	/** One row of the trick table, as tricks.json has it. */
	public static final class Info
	{
		public final int points;
		public final boolean hold;
		public final boolean spinnable;
		public final int maxPoints;
		public final int minMs;

		Info(int points, boolean hold, boolean spinnable, int maxPoints, int minMs)
		{
			this.points = points;
			this.hold = hold;
			this.spinnable = spinnable;
			this.maxPoints = maxPoints;
			this.minMs = minMs;
		}
	}

	/** The table row for an enum name or "SPIN"; null for an unknown name. */
	public static Info info(String name)
	{
		int spinMax = SpinNames.bonus(MAX_SPIN_HALF_TURNS);
		if ("SPIN".equals(name))
		{
			return new Info(0, false, true, spinMax, 0);
		}
		Trick t;
		try
		{
			t = Trick.valueOf(name);
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return null;
		}
		boolean hold = t.kind == TrickKind.GRAB || t.kind == TrickKind.MANUAL || t.kind == TrickKind.GRIND;
		boolean spinnable = (t.kind == TrickKind.POP || t.kind == TrickKind.FLIP) && t.bodyFlipTurns == 0f;
		int maxPoints = hold ? t.points * MAX_HOLD_SECONDS : t.points + (spinnable ? spinMax : 0);
		int minMs;
		if (hold)
		{
			minMs = Math.round(ComboScorer.MIN_HOLD_SECONDS * 1000f);
		}
		else if (t.kind == TrickKind.FLIP && t.duration > 0f)
		{
			minMs = Math.round(LATE_FLIP_MIN_FRACTION * t.duration * 1000f);
		}
		else
		{
			minMs = 0;
		}
		return new Info(t.points, hold, spinnable, maxPoints, minMs);
	}

	/**
	 * Why the server would refuse this run (its 400 field or 422 reason, e.g. "trick_points:3"), or null when it
	 * passes. Identity fields are not checked here.
	 */
	public static String check(RunSubmission run)
	{
		String bad = checkFields(run);
		return bad != null ? bad : checkPlausible(run);
	}

	/** validate.ts's parseRun bounds (identity left out). */
	static String checkFields(RunSubmission run)
	{
		if (RunSubmission.XP.equals(run.kind))
		{
			return run.xp == null || run.xp < 0 || run.xp > MAX_XP ? "xp" : null;
		}
		boolean combo = RunSubmission.COMBO.equals(run.kind);
		if (!combo && !RunSubmission.SESSION.equals(run.kind))
		{
			return "kind";
		}
		if (run.score == null || run.score < (combo ? 1 : 0) || run.score > MAX_SCORE)
		{
			return "score";
		}
		if (run.durationMs == null || run.durationMs < 0 || run.durationMs > (combo ? MAX_COMBO_MS : MAX_SCORE))
		{
			return "durationMs";
		}
		if (run.tricks == null)
		{
			return combo ? "tricks" : null;
		}
		if (run.tricks.size() > MAX_TRICKS || (combo && run.tricks.isEmpty()))
		{
			return "tricks";
		}
		for (int i = 0; i < run.tricks.size(); i++)
		{
			RunSubmission.TrickEntry e = run.tricks.get(i);
			if (e.name == null || !NAME.matcher(e.name).matches())
			{
				return "tricks[" + i + "].name";
			}
			if (e.points < 0 || e.points > MAX_TRICK_POINTS)
			{
				return "tricks[" + i + "].points";
			}
			if (e.t < 0 || (long) e.t > (long) run.durationMs + 1000)
			{
				return "tricks[" + i + "].t";
			}
			if (e.spin < 0 || e.spin > MAX_SPIN_FIELD)
			{
				return "tricks[" + i + "].spin";
			}
		}
		return null;
	}

	/** plausibility.ts's checkRun. */
	static String checkPlausible(RunSubmission run)
	{
		if (RunSubmission.XP.equals(run.kind))
		{
			return null;
		}
		boolean session = RunSubmission.SESSION.equals(run.kind);
		long durationMs = run.durationMs == null ? 0 : run.durationMs;
		if (session && (durationMs > MAX_SESSION_MS || durationMs < 1))
		{
			return "session_duration";
		}
		if (run.tricks == null)
		{
			return null;
		}
		for (int i = 0; i < run.tricks.size(); i++)
		{
			String bad = checkEntry(run.tricks.get(i), i);
			if (bad != null)
			{
				return bad;
			}
		}
		double slackened = durationMs * (1 + DURATION_SLACK_FRACTION) + DURATION_SLACK_MS;
		double allowed = slackened + (session ? SESSION_CARRY_OVER_MS : 0);
		if (minimumDurationMs(run.tricks) > allowed)
		{
			return "duration";
		}
		boolean longComboPossible = session || slackened > LONG_COMBO_MS;
		if (run.score > maximumScore(run.tricks, longComboPossible))
		{
			return "score";
		}
		return null;
	}

	private static String checkEntry(RunSubmission.TrickEntry e, int i)
	{
		Info info = info(e.name);
		if (info == null)
		{
			return "unknown_trick:" + i;
		}
		if (e.spin > MAX_SPIN_HALF_TURNS || (e.spin > 0 && !info.spinnable))
		{
			return "trick_spin:" + i;
		}
		if ("SPIN".equals(e.name) && e.spin == 0)
		{
			return "trick_spin:" + i;
		}
		double max = info.hold ? info.maxPoints : info.points + (double) SpinNames.POINTS_PER_HALF_TURN * e.spin;
		if (e.points > Math.min(max, info.maxPoints))
		{
			return "trick_points:" + i;
		}
		return null;
	}

	/** The shortest time the entries could have taken (plausibility.ts minimumDurationMs). */
	public static double minimumDurationMs(List<RunSubmission.TrickEntry> tricks)
	{
		double holds = 0;
		double flips = 0;
		for (RunSubmission.TrickEntry e : tricks)
		{
			Info info = info(e.name);
			if (info.hold)
			{
				double implied = info.points > 0 ? (Math.max(0, e.points - 1) / (double) info.points) * 1000 : 0;
				holds += Math.max(info.minMs, implied);
			}
			else
			{
				flips += info.minMs;
			}
		}
		return Math.max(holds, flips);
	}

	/** The highest score the entries could produce (plausibility.ts maximumScore). */
	public static double maximumScore(List<RunSubmission.TrickEntry> tricks, boolean longComboPossible)
	{
		double points = 0;
		Set<String> keys = new HashSet<>();
		for (RunSubmission.TrickEntry e : tricks)
		{
			points += e.points * (1 + FIRST_LANDING_BONUS) + 0.5;
			keys.add(e.spin > 0 ? e.name + "/" + e.spin : e.name);
		}
		int multiplier = keys.size() + (longComboPossible ? LONG_COMBO_EXTRA_MULTIPLIER : 0);
		return Math.ceil(points * multiplier * (1 + CLEAN_BONUS)) + 1;
	}
}
