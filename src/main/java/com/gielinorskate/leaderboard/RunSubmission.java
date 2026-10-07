package com.gielinorskate.leaderboard;

import com.gielinorskate.scoring.ComboScorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The run part of a {@code POST /v1/runs} body (backend/API.md): kind, score or XP, the trick entries and the
 * duration. The identity (account hash, secret, display name) and version are added only when it is sent
 * ({@link Body}), so a queued run never holds the secret. Built from the scorer's {@link ComboScorer.Landed}
 * records. Immutable; pure.
 */
public final class RunSubmission
{
	public static final String COMBO = "combo";
	public static final String SESSION = "session";
	public static final String XP = "xp";

	public final String kind;
	/** Null for an XP submission. */
	public final Long score;
	/** Null except for an XP submission. */
	public final Integer xp;
	/** Null when left out (XP, or a session with more than {@link RunBounds#MAX_TRICKS} entries). */
	public final List<TrickEntry> tricks;
	public final Integer durationMs;

	RunSubmission(String kind, Long score, Integer xp, List<TrickEntry> tricks, Integer durationMs)
	{
		this.kind = kind;
		this.score = score;
		this.xp = xp;
		this.tricks = tricks == null ? null : Collections.unmodifiableList(new ArrayList<>(tricks));
		this.durationMs = durationMs;
	}

	/** One trick of a run, as the server takes it: {@code {name, points, t, spin}}. */
	public static final class TrickEntry
	{
		public final String name;
		public final int points;
		/** Milliseconds from the start of the combo or run. */
		public final int t;
		/** Half turns of spin either way (0 = none). */
		public final int spin;

		public TrickEntry(String name, int points, int t, int spin)
		{
			this.name = name;
			this.points = points;
			this.t = t;
			this.spin = spin;
		}
	}

	/**
	 * The player's own name as the server takes it (validate.ts sanitizeDisplayName): non-breaking spaces become
	 * spaces, whitespace runs collapse, ends are trimmed; null when the result is not 1-12 of letters, digits,
	 * space, '-' or '_'.
	 */
	public static String displayName(String raw)
	{
		if (raw == null || raw.length() > 64)
		{
			return null;
		}
		String name = raw.replace((char) 0xA0, ' ').replaceAll("\\s+", " ").trim();
		return DISPLAY_NAME.matcher(name).matches() ? name : null;
	}

	private static final java.util.regex.Pattern DISPLAY_NAME = java.util.regex.Pattern.compile(
		"^[A-Za-z0-9 _-]{1,12}$");

	/** A landed combo: its value, its entries timed from its start, and its length on the score clock. */
	public static RunSubmission combo(ComboScorer.Landed landed)
	{
		int durationMs = landed.durationMs();
		return new RunSubmission(COMBO, (long) landed.value, null, entries(landed.tricks, landed.start, durationMs),
			durationMs);
	}

	/**
	 * A finished timed run: the summed score, its length, and every trick banked in it timed from the run's start
	 * (score-clock seconds {@code start}), or no trick list when there are more than {@link RunBounds#MAX_TRICKS}.
	 */
	public static RunSubmission session(long score, int durationMs, float start, List<ComboScorer.TrickRecord> tricks)
	{
		List<TrickEntry> list = tricks.size() > RunBounds.MAX_TRICKS ? null : entries(tricks, start, durationMs);
		return new RunSubmission(SESSION, score, null, list, durationMs);
	}

	public static RunSubmission xp(int xp)
	{
		return new RunSubmission(XP, null, xp, null, null);
	}

	/** Entries timed in ms from {@code start}, kept within 0..durationMs. */
	private static List<TrickEntry> entries(List<ComboScorer.TrickRecord> records, float start, int durationMs)
	{
		List<TrickEntry> out = new ArrayList<>(records.size());
		for (ComboScorer.TrickRecord r : records)
		{
			int t = Math.round((r.time - start) * 1000f);
			out.add(new TrickEntry(r.name, Math.max(0, r.points), Math.max(0, Math.min(durationMs, t)),
				r.spinHalfTurns));
		}
		return out;
	}

	/**
	 * The JSON body sent (Gson leaves out null fields). Holds the secret: never logged, never kept beyond the
	 * request.
	 */
	public static final class Body
	{
		final String accountHash;
		final String secret;
		final String displayName;
		final String kind;
		final Long score;
		final Integer xp;
		final List<TrickEntry> tricks;
		final Integer durationMs;
		final String pluginVersion;
		final Boolean devLevelSet;

		public Body(RunSubmission run, String accountHash, String secret, String displayName, String pluginVersion,
			boolean devLevelSet)
		{
			this.accountHash = accountHash;
			this.secret = secret;
			this.displayName = displayName;
			this.kind = run.kind;
			this.score = run.score;
			this.xp = run.xp;
			this.tricks = run.tricks;
			this.durationMs = run.durationMs;
			this.pluginVersion = pluginVersion;
			this.devLevelSet = devLevelSet ? Boolean.TRUE : null;
		}

		@Override
		public String toString()
		{
			return "RunSubmission.Body{" + kind + "}";
		}
	}

	/** The claim body: {@code {accountHash, displayName, secret}}. */
	public static final class Claim
	{
		final String accountHash;
		final String displayName;
		final String secret;

		public Claim(String accountHash, String displayName, String secret)
		{
			this.accountHash = accountHash;
			this.displayName = displayName;
			this.secret = secret;
		}

		@Override
		public String toString()
		{
			return "RunSubmission.Claim";
		}
	}
}
