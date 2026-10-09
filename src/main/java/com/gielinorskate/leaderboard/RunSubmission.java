package com.gielinorskate.leaderboard;

import com.gielinorskate.scoring.ComboScorer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

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

private static final Pattern DISPLAY_NAME = Pattern.compile("^[A-Za-z0-9 _-]{1,12}$");

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
@AllArgsConstructor
public static final class TrickEntry
{
public final String name;
public final int points;
/** Milliseconds from the start of the combo or run. */
public final int t;
/** Half turns of spin either way (0 = none). */
public final int spin;
}

/**
* The player's own name as the server takes it (validate.ts sanitizeDisplayName): non-breaking spaces become
* spaces, whitespace runs collapse, ends are trimmed; null when the result is not 1-12 of letters, digits,
* space, '-' or '_'.
*/
public static String displayName(String raw)
{
if (raw == null || raw.length() > 64)
return null;
String name = raw.replace((char) 0xA0, ' ').replaceAll("\\s+", " ").trim();
return DISPLAY_NAME.matcher(name).matches() ? name : null;
}

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
return new RunSubmission(SESSION, score, null, tricks.size() > RunBounds.MAX_TRICKS ? null
: entries(tricks, start, durationMs), durationMs);
}

public static RunSubmission xp(int xp)
{
return new RunSubmission(XP, null, xp, null, null);
}

/** Entries timed in ms from {@code start}, kept within 0..durationMs. */
private static List<TrickEntry> entries(List<ComboScorer.TrickRecord> records, float start, int durationMs)
{
return records.stream().map(r -> new TrickEntry(r.name, Math.max(0, r.points),
Math.max(0, Math.min(durationMs, Math.round((r.time - start) * 1000f))), r.spinHalfTurns))
.collect(Collectors.toList());
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

public Body(RunSubmission run, String accountHash, String secret, String displayName, String pluginVersion)
{
this.accountHash = accountHash;
this.secret = secret;
this.displayName = displayName;
kind = run.kind;
score = run.score;
xp = run.xp;
tricks = run.tricks;
durationMs = run.durationMs;
this.pluginVersion = pluginVersion;
}

@Override
public String toString()
{
return "RunSubmission.Body{" + kind + "}";
}
}

/** The claim body: {@code {accountHash, displayName, secret}}. */
@AllArgsConstructor
public static final class Claim
{
final String accountHash;
final String displayName;
final String secret;

@Override
public String toString()
{
return "RunSubmission.Claim";
}
}
}
