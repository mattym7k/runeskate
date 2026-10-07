package com.gielinorskate.leaderboard;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One leaderboard as {@code GET /v1/leaderboard} returns it: the top 100 and the caller's own row. Parsed with
 * Gson; anything malformed parses to null rather than throwing. Immutable once parsed.
 */
public final class LeaderboardPage
{
	public static final class Row
	{
		public final int rank;
		public final String displayName;
		public final long score;
		public final String updatedAt;

		public Row(int rank, String displayName, long score, String updatedAt)
		{
			this.rank = rank;
			this.displayName = displayName;
			this.score = score;
			this.updatedAt = updatedAt;
		}

		/** The same row (rank and name), e.g. the caller's own row in the top 100. */
		public boolean sameAs(Row other)
		{
			return other != null && rank == other.rank && Objects.equals(displayName, other.displayName);
		}
	}

	public final String category;
	public final String period;
	public final String weekStart;
	public final int total;
	public final List<Row> entries;
	/** The caller's row, or null when the caller is not on this board. */
	public final Row you;

	LeaderboardPage(String category, String period, String weekStart, int total, List<Row> entries, Row you)
	{
		this.category = category;
		this.period = period;
		this.weekStart = weekStart;
		this.total = total;
		this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
		this.you = you;
	}

	/** Gson's view of the JSON. */
	private static final class Raw
	{
		String category;
		String period;
		String weekStart;
		Integer total;
		List<RawRow> entries;
		RawRow you;
	}

	private static final class RawRow
	{
		Integer rank;
		String displayName;
		Long score;
		String updatedAt;

		Row toRow()
		{
			return rank == null || score == null || displayName == null ? null
				: new Row(rank, displayName, score, updatedAt);
		}
	}

	/** The page in {@code json}, or null when it is not a leaderboard response. */
	public static LeaderboardPage parse(Gson gson, String json)
	{
		Raw raw;
		try
		{
			raw = gson.fromJson(json, Raw.class);
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException e)
		{
			return null;
		}
		if (raw == null || raw.entries == null || raw.category == null || raw.period == null)
		{
			return null;
		}
		List<Row> rows = new ArrayList<>(raw.entries.size());
		for (RawRow r : raw.entries)
		{
			Row row = r == null ? null : r.toRow();
			if (row != null)
			{
				rows.add(row);
			}
		}
		return new LeaderboardPage(raw.category, raw.period, raw.weekStart, raw.total == null ? rows.size()
			: raw.total, rows, raw.you == null ? null : raw.you.toRow());
	}

	/** An error body's code ({"error":"bad_secret"}), or null. */
	public static String errorCode(Gson gson, String json)
	{
		try
		{
			ErrorBody e = gson.fromJson(json, ErrorBody.class);
			return e == null ? null : e.error;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	/** The week best a combo or session submit answered with ({"best":{"week":n}}), or -1. */
	public static long weekBest(Gson gson, String json)
	{
		try
		{
			RunResponse r = gson.fromJson(json, RunResponse.class);
			return r == null || r.best == null || r.best.week == null ? -1 : r.best.week;
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException e)
		{
			return -1;
		}
	}

	private static final class ErrorBody
	{
		String error;
	}

	private static final class RunResponse
	{
		Best best;
	}

	private static final class Best
	{
		Long week;
	}
}
