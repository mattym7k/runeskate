package com.gielinorskate.leaderboard;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** The leaderboard week: it starts Monday 00:00 UTC, the same as the server's weekStart. Pure. */
public final class WeekStart
{
/** The Monday (UTC) of the week of {@code epochMillis}, as the server writes it ("YYYY-MM-DD"). */
public static String key(long epochMillis)
{
return Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toLocalDate()
.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
}
}
