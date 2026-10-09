package com.gielinorskate.leaderboard;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

/**
* One leaderboard as {@code GET /v1/leaderboard} returns it: the top 100 and the caller's own row. Parsed with
* Gson; anything malformed parses to null rather than throwing. Immutable once parsed.
*/
@AllArgsConstructor
public final class LeaderboardPage
{
@AllArgsConstructor
public static final class Row
{
public final int rank;
public final String displayName;
public final long score;

/** The same row (rank and name), e.g. the caller's own row in the top 100. */
public boolean sameAs(Row other)
{
return other != null && rank == other.rank && Objects.equals(displayName, other.displayName);
}
}

public final List<Row> entries;
/** The caller's row, or null when the caller is not on this board. */
public final Row you;

/** Gson's view of the JSON (its other fields, weekStart and total, are not used). */
private static final class Raw
{
String category;
String period;
List<RawRow> entries;
RawRow you;
}

private static final class RawRow
{
Integer rank;
String displayName;
Long score;

Row toRow()
{
return rank == null || score == null || displayName == null ? null : new Row(rank, displayName, score);
}
}

/** The page in {@code json}, or null when it is not a leaderboard response. */
public static LeaderboardPage parse(Gson gson, String json)
{
Raw raw = read(gson, json, Raw.class);
if (raw == null || raw.entries == null || raw.category == null || raw.period == null)
return null;
List<Row> rows = raw.entries.stream().map(r -> r == null ? null : r.toRow()).filter(Objects::nonNull)
.collect(Collectors.toList());
return new LeaderboardPage(Collections.unmodifiableList(rows), raw.you == null ? null : raw.you.toRow());
}

/** An error body's code ({"error":"bad_secret"}), or null. */
public static String errorCode(Gson gson, String json)
{
ErrorBody e = read(gson, json, ErrorBody.class);
return e == null ? null : e.error;
}

/** The week best a combo or session submit answered with ({"best":{"week":n}}), or -1. */
public static long weekBest(Gson gson, String json)
{
RunResponse r = read(gson, json, RunResponse.class);
return r == null || r.best == null || r.best.week == null ? -1 : r.best.week;
}

/** {@code json} as a {@code type}, or null when it is not one. */
private static <T> T read(Gson gson, String json, Class<T> type)
{
try
{
return gson.fromJson(json, type);
}
catch (JsonParseException | IllegalStateException | NumberFormatException e)
{
return null;
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
