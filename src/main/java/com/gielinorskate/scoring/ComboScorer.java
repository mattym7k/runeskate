package com.gielinorskate.scoring;

import static com.gielinorskate.tricks.TrickKind.*;

import com.gielinorskate.tricks.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.*;
import lombok.experimental.Accessors;

/**
* Pure combo/score bookkeeping, fed by {@link TrickEvent}s drained from the physics layer.
* Holds no RuneLite dependencies so it can be unit tested directly.
*/
@Singleton
@Accessors(fluent = true)
public class ComboScorer
{
/** Per-repeat decay applied to a trick's points, floored at {@link #DECAY_FLOOR}. */
public static final float DECAY_PER_REPEAT = 0.75f;
public static final float DECAY_FLOOR = 0.25f;
/**
* Holds shorter than this are dropped (a tap of the manual key or a grab key is not a trick). Was
* 0.25 s while manuals came from a still mouse wind-up and flicks cut stray ones short; on the
* manual key a deliberate manual-to-pop is often only 0.2 s, i.e. 10 steps of 0.02 s.
*/
public static final float MIN_HOLD_SECONDS = 0.15f;
/** Fraction added to a combo that lands clean (board lined up within 15 degrees, flip finished). */
public static final float CLEAN_BONUS = 0.1f;
/** Variety: fraction added to a trick's points the first time it lands in a skate session (by decay key). */
public static final float FIRST_LANDING_BONUS = 0.25f;
/** Variety: a combo lasting longer than this (first trick or hold start to landing) gets one more multiplier. */
public static final float LONG_COMBO_SECONDS = 8f;

/** What a landed combo held, for progression (session goals, XP). Immutable. */
@AllArgsConstructor
public static final class Summary
{
/** A summary of nothing (before any landing). */
public static final Summary NONE = new Summary(Collections.emptyList(), 0f, 0f, 0, 0, 0, false, 0, false);

/** The combo's tricks in order (spins on their own are left out). */
public final List<Trick> tricks;
/** Seconds of grinding and of manuals in the combo. */
public final float grindSeconds;
public final float manualSeconds;
/** Biggest spin in the combo, in half turns either way (2 = a 360). */
public final int maxSpinHalfTurns;
/** The landed value (same as {@link Result#value}) and multiplier, bonuses included. */
public final int value;
public final int multiplier;
public final boolean clean;
/** Tricks landed for the first time this session (each got {@link #FIRST_LANDING_BONUS}). */
public final int newTricks;
/** Lasted longer than {@link #LONG_COMBO_SECONDS} (got one more multiplier). */
public final boolean longCombo;
}

/** Resolution of a finished combo, surfaced to the HUD. */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public static final class Result
{
public enum Kind
{
NONE,
LANDED,
BAILED
}

public static final Result NONE = new Result(Kind.NONE, 0, false);

public final Kind kind;
public final int value;
/** A combo landed clean (see {@link ComboScorer#CLEAN_BONUS}); its value already includes the bonus. */
public final boolean clean;

public static Result landed(int value)
{
return new Result(Kind.LANDED, value, false);
}

/** A clean landing ({@link TrickEvent#clean}); {@code value} includes the bonus. */
public static Result landedClean(int value)
{
return new Result(Kind.LANDED, value, true);
}

/** @param lostValue the combo value that was lost (shown struck through) */
public static Result bailed(int lostValue)
{
return new Result(Kind.BAILED, lostValue, false);
}

public boolean isLanded()
{
return kind == Kind.LANDED;
}

public boolean isBailed()
{
return kind == Kind.BAILED;
}
}

/**
* One trick entry of a landed combo, as the leaderboard receives it: the {@link Trick} enum name ("SPIN" for a
* spin with no trick of its own), its points after repeat decay with the spin bonus but without the
* first-landing bonus, its half turns of spin, and the score-clock time it started (the trick's pop, the
* spin's landing, or a hold's start). Immutable.
*/
@AllArgsConstructor
public static final class TrickRecord
{
public final String name;
public final int points;
public final int spinHalfTurns;
public final float time;
}

/** The combo that last landed, entry by entry, for leaderboard submissions. Immutable. */
@AllArgsConstructor
public static final class Landed
{
public static final Landed NONE = new Landed(0, 0f, 0f, Collections.emptyList());

/** The landed value, every bonus included (same as {@link Result#value}). */
public final int value;
/** Score-clock seconds the combo began (its first trick or hold start) and ended. */
public final float start;
public final float end;
public final List<TrickRecord> tricks;

/** Milliseconds from start to end, on the score clock (never negative). */
public int durationMs()
{
return Math.max(0, Math.round((end - start) * 1000f));
}
}

/**
* One scored trick. {@code key} identifies it for repetition decay and the distinct-trick multiplier: the
* trick's enum name, plus "/n" for a spun trick of n half turns either way ("OLLIE/1" for a BS or FS 180
* Ollie), or "SPIN/n" for a spin with no trick of its own. Fakie or not, and the spin direction, do not
* change the key.
*/
@AllArgsConstructor
private static final class Entry
{
/** Null for a spin on its own. */
final Trick trick;
final boolean fakie;
final String key;
final String name;
final int points;
/** Seconds held, for a grab, manual or grind; 0 otherwise. */
final float seconds;
/** Half turns of spin either way (0 when none). */
final int spinHalfTurns;
/** Score-clock time the entry began (a pop, a spin's landing, a hold's start). */
final float time;
}

private final List<Entry> comboEntries = new ArrayList<>();
private final Map<String, Integer> sessionRepeatCounts = new HashMap<>();
/**
* Index of the current air's main trick (its pop, or the last board flip started in it), which a SPIN
* renames; -1 when there is none (a roll-off, or the air ended in a manual or grind).
*/
private int airMain = -1;

/**
* Decay keys landed so far this skate session, for {@link #FIRST_LANDING_BONUS}; null until
* {@link #startSession()} (the variety bonuses only apply inside a skate session).
*/
private Set<String> landedKeys;
/** Score-clock time the combo in progress began (its first trick, or its first hold's start); NaN when none. */
private float comboStart = Float.NaN;
/** What the combo that last landed held; {@link Summary#NONE} before any landing. Bails leave it as it was. */
@Getter
private Summary lastSummary = Summary.NONE;
/** The combo that last landed, trick by trick ({@link Landed#NONE} before any). Bails leave it as it was. */
@Getter
private Landed lastLanded = Landed.NONE;

@Getter
private int sessionScore;
@Getter
private Result lastResult = Result.NONE;
/** Trick names of the combo that last landed or bailed, kept for the HUD's result flash. */
@Getter
private List<String> lastComboNames = Collections.emptyList();
private float lastResultTime = -Float.MAX_VALUE;
private float lastEventTime = -Float.MAX_VALUE;
/** Distinct tricks (the multiplier) and every trick entry of the combo that last landed or bailed (for callouts). */
@Getter
private int lastResultMultiplier;
@Getter
private int lastResultTrickCount;
/** Bumped on every landing or bail that produced a {@link #lastResult()}, so readers can spot a new one. */
@Getter
private int resultSequence;
/** Score-clock time the newest trick line last appeared or was renamed, for the HUD's pop-in; -Float.MAX_VALUE before any. */
@Getter
private float newestNameTime = -Float.MAX_VALUE;
/** Score-clock time the multiplier last went up, for the HUD ring's pulse; -Float.MAX_VALUE before any. */
@Getter
private float multiplierRiseTime = -Float.MAX_VALUE;

/** Scores one event; a LANDED event with {@link TrickEvent#clean} adds {@link #CLEAN_BONUS} to the landed combo. */
public void accept(TrickEvent e, float now)
{
int sizeBefore = comboEntries.size();
String newestBefore = sizeBefore == 0 ? null : comboEntries.get(sizeBefore - 1).name;
int multiplierBefore = multiplier();
apply(e, now);
int size = comboEntries.size();
if (size > 0 && (size != sizeBefore || !comboEntries.get(size - 1).name.equals(newestBefore)))
newestNameTime = now;
if (size > 0 && multiplier() > multiplierBefore)
multiplierRiseTime = now;
}

private void apply(TrickEvent e, float now)
{
Trick t = e.trick;
switch (e.type)
{
case TRICK:
// a re-flick upgrade replaces the last entry of the flip it upgrades
int index = put(e.upgradedFrom == null ? -1
: comboEntries.stream().map(x -> x.trick).collect(Collectors.toList()).lastIndexOf(e.upgradedFrom),
t, e.fakie, t.name(), e.displayName(), t.points, 0f, 0, now);
// a pop or a board flip (not a front or back flip of the body) is the trick a spin in the same air renames
if ((t.kind == POP || t.kind == FLIP) && t.bodyFlipTurns == 0f)
airMain = index;
markStart(now);
break;
case SPIN:
int amount = Math.abs(e.spinHalfTurns);
if (amount > 0)
{
// a landed spin renames the air's main trick ("FS 180 Kickflip") and adds SpinNames.bonus, decayed
// by the spun trick's own key; with no trick in that air it is an entry of its own ("BS 360")
Entry main = airMain >= 0 && airMain < comboEntries.size() ? comboEntries.get(airMain) : null;
if (main != null && main.trick != null)
{
String spun = SpinNames.name(main.trick.displayName, e.spinHalfTurns);
put(airMain, main.trick, main.fakie, main.trick.name() + "/" + amount,
main.fakie ? "Fakie " + spun : spun, main.trick.points + SpinNames.bonus(amount), 0f, amount, 0f);
}
else
put(-1, null, false, "SPIN/" + amount, SpinNames.label(e.spinHalfTurns), SpinNames.bonus(amount),
0f, amount, now);
airMain = -1;
}
if (!comboEntries.isEmpty())
markStart(now);
break;
case HOLD_START:
if (t != null && (t.kind == MANUAL || t.kind == GRIND))
{
airMain = -1; // back on the ground or a rail: the air is over
}
break;
case HOLD_END:
if (e.seconds >= MIN_HOLD_SECONDS)
{
put(-1, t, false, t.name(), t.displayName, Math.round(t.points * e.seconds), e.seconds, 0,
now - e.seconds);
markStart(now - e.seconds);
}
break;
case LANDED:
resolveLanded(now, now, e.clean);
return;
case BAILED:
finish(Result.bailed(comboPoints() * multiplier()), now);
return;
}
lastEventTime = now;
}

/**
* Puts a new entry, its points decayed by the repeats of its key this session, at index {@code i} (undoing the
* replaced entry's repeat count and keeping its time) or, when {@code i} is negative, at the end. Returns its
* index.
*/
private int put(int i, Trick trick, boolean fakie, String key, String name, int basePoints, float seconds,
int spinHalfTurns, float time)
{
if (i >= 0)
{
Entry old = comboEntries.get(i);
sessionRepeatCounts.computeIfPresent(old.key, (k, n) -> n > 1 ? n - 1 : null);
time = old.time;
}
int n = sessionRepeatCounts.getOrDefault(key, 0);
sessionRepeatCounts.put(key, n + 1);
float decay = (float) Math.max(DECAY_FLOOR, Math.pow(DECAY_PER_REPEAT, n));
Entry entry = new Entry(trick, fakie, key, name, Math.round(basePoints * decay), seconds, spinHalfTurns, time);
if (i < 0)
{
comboEntries.add(entry);
return comboEntries.size() - 1;
}
comboEntries.set(i, entry);
return i;
}

/**
* A new skate session: from now on the variety bonuses apply, and every trick counts as new again for
* {@link #FIRST_LANDING_BONUS}.
*/
public void startSession()
{
landedKeys = new HashSet<>();
}

private void markStart(float t)
{
if (Float.isNaN(comboStart) || t < comboStart)
comboStart = t;
}

/**
* Lands the combo in progress now, without waiting for the roll-out (stepping off the board banks it). Not
* clean. Nothing happens when there is no combo.
*/
public void bankCombo(float now)
{
resolveLanded(now, lastEventTime, false);
}

public void update(float now, boolean rollingWithoutHold)
{
// half a second of rolling without a new trick or hold ends a combo as a landing
if (rollingWithoutHold && now - lastEventTime >= 0.5f)
// the combo ended with its last trick, not after the roll-out wait
resolveLanded(now, lastEventTime, false);
}

/**
* @param now when the result shows
* @param end when the combo ended, for {@link #LONG_COMBO_SECONDS}
*/
private void resolveLanded(float now, float end, boolean clean)
{
if (comboEntries.isEmpty())
// a plain drop off a curb (roll-off, then land) has nothing to score or show
return;
int points = 0;
int newTricks = 0;
int spin = 0;
float grind = 0f;
float manual = 0f;
float start = Float.isNaN(comboStart) ? end : Math.min(end, comboStart);
List<Trick> tricks = new ArrayList<>();
List<TrickRecord> records = new ArrayList<>();
Set<String> fresh = new HashSet<>();
for (Entry entry : comboEntries)
{
points += entry.points;
// variety: each trick's first landing this session scores a quarter more (the first entry of its key)
if (landedKeys != null && !landedKeys.contains(entry.key) && fresh.add(entry.key))
{
points += Math.round(entry.points * FIRST_LANDING_BONUS);
newTricks++;
}
records.add(new TrickRecord(entry.trick == null ? "SPIN" : entry.trick.name(), entry.points,
entry.spinHalfTurns, entry.time));
start = Math.min(start, entry.time);
spin = Math.max(spin, entry.spinHalfTurns);
if (entry.trick != null)
{
tricks.add(entry.trick);
grind += entry.trick.kind == GRIND ? entry.seconds : 0f;
manual += entry.trick.kind == MANUAL ? entry.seconds : 0f;
}
}
boolean longCombo = landedKeys != null && !Float.isNaN(comboStart) && end - comboStart > LONG_COMBO_SECONDS;
int multiplier = multiplier() + (longCombo ? 1 : 0);
int value = points * multiplier;
if (clean)
value = Math.round(value * (1f + CLEAN_BONUS));
if (landedKeys != null)
landedKeys.addAll(fresh);
sessionScore += value;
lastSummary = new Summary(Collections.unmodifiableList(tricks), grind, manual, spin, value, multiplier, clean,
newTricks, longCombo);
lastLanded = new Landed(value, start, Math.max(start, end), Collections.unmodifiableList(records));
finish(clean ? Result.landedClean(value) : Result.landed(value), now);
}

/** Shows {@code result} for the combo in progress and clears it. */
private void finish(Result result, float now)
{
lastResultMultiplier = multiplier();
lastResultTrickCount = comboEntries.size();
resultSequence++;
lastComboNames = comboNames();
lastResult = result;
lastResultTime = now;
abandonCombo();
}

/** Drops the combo in progress without scoring it or showing a result (e.g. leaving skate mode mid-air). */
public void abandonCombo()
{
comboEntries.clear();
airMain = -1;
lastEventTime = -Float.MAX_VALUE;
comboStart = Float.NaN;
}

/** True while a combo is in progress (it has at least one trick entry). */
public boolean hasCombo()
{
return !comboEntries.isEmpty();
}

/** The last 6 trick names of the combo in progress. */
public List<String> comboNames()
{
int size = comboEntries.size();
return Collections.unmodifiableList(comboEntries.subList(Math.max(0, size - 6), size).stream()
.map(e -> e.name).collect(Collectors.toList()));
}

public int comboPoints()
{
return comboEntries.stream().mapToInt(e -> e.points).sum();
}

/** Distinct trick keys in the combo in progress. */
public int multiplier()
{
return (int) comboEntries.stream().map(e -> e.key).distinct().count();
}

public float lastResultAge(float now)
{
return now - lastResultTime;
}
}
