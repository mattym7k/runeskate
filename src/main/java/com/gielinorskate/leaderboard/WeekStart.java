package com.gielinorskate.leaderboard;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/** The leaderboard week: it starts Monday 00:00 UTC, the same as the server's weekStart. Pure. */
public final class WeekStart
{
	private WeekStart()
	{
	}

	/** The Monday (UTC) of the week holding {@code instant}. */
	public static LocalDate of(Instant instant)
	{
		LocalDate day = instant.atOffset(ZoneOffset.UTC).toLocalDate();
		return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/** The week of {@code epochMillis}, as the server writes it ("YYYY-MM-DD"). */
	public static String key(long epochMillis)
	{
		return of(Instant.ofEpochMilli(epochMillis)).toString();
	}
}
