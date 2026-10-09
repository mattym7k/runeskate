package com.gielinorskate.leaderboard;

import com.gielinorskate.scoring.ComboScorer;
import java.util.*;
import lombok.AllArgsConstructor;

/**
* The timed 2-minute session run: a 3-2-1 countdown, then {@link #RUN_SECONDS} during which every banked combo is
* summed. Bailing does not end it, and the clock keeps running on foot. A combo still going at 0:00 is banked if
* it lands within {@link #GRACE_SECONDS}. Times are score-clock seconds. Pure; client thread.
*/
public final class TimedRun
{
public static final float COUNTDOWN_SECONDS = 3f;
public static final float RUN_SECONDS = 120f;
public static final float GRACE_SECONDS = 3f;

public enum Phase
{
IDLE,
COUNTDOWN,
RUNNING,
/** Past 0:00, waiting up to {@link #GRACE_SECONDS} for the combo in progress to land. */
GRACE
}

/** A run that reached its end. Immutable. */
@AllArgsConstructor
public static final class Finished
{
public final long score;
public final int durationMs;
/** Score-clock seconds the run started (the countdown's end). */
public final float start;
public final List<ComboScorer.TrickRecord> tricks;
public final int combos;
}

private Phase phase = Phase.IDLE;
/** When the run proper starts (the countdown's end). */
private float startAt;
private long score;
private int combos;
private List<ComboScorer.TrickRecord> tricks = new ArrayList<>();
/** When the latest combo was banked (for a landing during the grace period). */
private float lastBankAt;

public Phase phase()
{
return phase;
}

public boolean isActive()
{
return phase != Phase.IDLE;
}

/** Starts the countdown; false (nothing changes) when a run is already going. */
public boolean start(float now)
{
if (isActive())
return false;
phase = Phase.COUNTDOWN;
startAt = now + COUNTDOWN_SECONDS;
score = 0;
combos = 0;
tricks = new ArrayList<>();
lastBankAt = startAt;
return true;
}

/** Drops the run without a result (skating stopped). True when one was going. */
public boolean cancel()
{
boolean was = isActive();
phase = Phase.IDLE;
return was;
}

/**
* A combo landed (or was banked) at {@code now}. Counted while the run is going, and during the grace period,
* where it also ends the run. A combo begun before GO (e.g. a manual held through the countdown) is not
* counted: its points were made outside the run.
*
* @return the finished run when this landing ended it, else null
*/
public Finished onComboLanded(ComboScorer.Landed combo, float now)
{
if (phase == Phase.COUNTDOWN && now >= startAt)
phase = Phase.RUNNING;
if (phase != Phase.RUNNING && phase != Phase.GRACE)
return null;
if (now > startAt + RUN_SECONDS + GRACE_SECONDS)
return finish();
if (combo.start >= startAt)
{
score += combo.value;
combos++;
tricks.addAll(combo.tricks);
lastBankAt = now;
}
// landed at or after 0:00 (within the grace period): the run is over
return phase == Phase.GRACE || now >= startAt + RUN_SECONDS ? finish() : null;
}

/** A combo bailed: the run goes on, but a grace period has nothing left to wait for. */
public Finished onComboBailed(float now)
{
return phase == Phase.GRACE ? finish() : null;
}

/**
* Advances the clock.
*
* @param comboInProgress a combo is going now (it may still land within the grace period)
* @return the finished run when it ended now, else null
*/
public Finished update(float now, boolean comboInProgress)
{
if (phase == Phase.COUNTDOWN && now >= startAt)
phase = Phase.RUNNING;
boolean graceLeft = comboInProgress && now < startAt + RUN_SECONDS + GRACE_SECONDS;
if (phase == Phase.RUNNING && now >= startAt + RUN_SECONDS && graceLeft)
{
phase = Phase.GRACE;
return null;
}
return phase == Phase.RUNNING && now >= startAt + RUN_SECONDS || phase == Phase.GRACE && !graceLeft
? finish() : null;
}

private Finished finish()
{
float end = Math.max(startAt + RUN_SECONDS, lastBankAt);
int durationMs = Math.min(RunBounds.MAX_SESSION_MS, Math.max(1, Math.round((end - startAt) * 1000f)));
phase = Phase.IDLE;
// the list is handed over whole: the next run starts a new one
return new Finished(score, durationMs, startAt, Collections.unmodifiableList(tricks), combos);
}

/** Seconds left on the countdown (3..0), while counting down. */
public float countdownLeft(float now)
{
return phase == Phase.COUNTDOWN ? Math.max(0f, startAt - now) : 0f;
}

/** Seconds left on the run's clock (120..0); 0 in the grace period. */
public float timeLeft(float now)
{
return phase == Phase.COUNTDOWN ? RUN_SECONDS
: phase == Phase.RUNNING ? Math.max(0f, startAt + RUN_SECONDS - Math.max(now, startAt)) : 0f;
}

/** Points banked so far this run. */
public long score()
{
return score;
}
}
