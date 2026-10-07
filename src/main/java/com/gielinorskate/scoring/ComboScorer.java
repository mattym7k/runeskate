package com.gielinorskate.scoring;

import com.gielinorskate.tricks.SpinNames;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.tricks.TrickKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Pure combo/score bookkeeping, fed by {@link TrickEvent}s drained from the physics layer.
 * Holds no RuneLite dependencies so it can be unit tested directly.
 */
@Singleton
public class ComboScorer
{
	/** How long (seconds) of rolling without a new trick or hold ends a combo as a landing. */
	private static final float ROLL_OUT_SECONDS = 0.5f;
	/** Per-repeat decay applied to a trick's points, floored at {@link #DECAY_FLOOR}. */
	public static final float DECAY_PER_REPEAT = 0.75f;
	public static final float DECAY_FLOOR = 0.25f;
	/** Number of trick names shown by {@link #comboNames()}. */
	private static final int DISPLAY_NAMES = 6;
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

		public Summary(List<Trick> tricks, float grindSeconds, float manualSeconds, int maxSpinHalfTurns, int value,
			int multiplier, boolean clean, int newTricks, boolean longCombo)
		{
			this.tricks = Collections.unmodifiableList(new ArrayList<>(tricks));
			this.grindSeconds = grindSeconds;
			this.manualSeconds = manualSeconds;
			this.maxSpinHalfTurns = maxSpinHalfTurns;
			this.value = value;
			this.multiplier = multiplier;
			this.clean = clean;
			this.newTricks = newTricks;
			this.longCombo = longCombo;
		}
	}

	/** Resolution of a finished combo, surfaced to the HUD. */
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

		private Result(Kind kind, int value, boolean clean)
		{
			this.kind = kind;
			this.value = value;
			this.clean = clean;
		}

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

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof Result))
			{
				return false;
			}
			Result r = (Result) o;
			return kind == r.kind && value == r.value && clean == r.clean;
		}

		@Override
		public int hashCode()
		{
			return 31 * (31 * kind.hashCode() + value) + (clean ? 1 : 0);
		}

		@Override
		public String toString()
		{
			return "Result{" + kind + ", " + value + (clean ? ", clean" : "") + '}';
		}
	}

	/**
	 * One trick entry of a landed combo, as the leaderboard receives it: the {@link Trick} enum name ("SPIN" for a
	 * spin with no trick of its own), its points after repeat decay with the spin bonus but without the
	 * first-landing bonus, its half turns of spin, and the score-clock time it started. Immutable.
	 */
	public static final class TrickRecord
	{
		public final String name;
		public final int points;
		public final int spinHalfTurns;
		/** Score-clock seconds: the trick's pop, the spin's landing, or a hold's start. */
		public final float time;

		public TrickRecord(String name, int points, int spinHalfTurns, float time)
		{
			this.name = name;
			this.points = points;
			this.spinHalfTurns = spinHalfTurns;
			this.time = time;
		}
	}

	/** The combo that last landed, entry by entry, for leaderboard submissions. Immutable. */
	public static final class Landed
	{
		public static final Landed NONE = new Landed(0, 0f, 0f, Collections.emptyList());

		/** The landed value, every bonus included (same as {@link Result#value}). */
		public final int value;
		/** Score-clock seconds the combo began (its first trick or hold start) and ended. */
		public final float start;
		public final float end;
		public final List<TrickRecord> tricks;

		public Landed(int value, float start, float end, List<TrickRecord> tricks)
		{
			this.value = value;
			this.start = start;
			this.end = end;
			this.tricks = Collections.unmodifiableList(new ArrayList<>(tricks));
		}

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

		Entry(Trick trick, boolean fakie, String key, String name, int points, float seconds, int spinHalfTurns,
			float time)
		{
			this.time = time;
			this.trick = trick;
			this.fakie = fakie;
			this.key = key;
			this.name = name;
			this.points = points;
			this.seconds = seconds;
			this.spinHalfTurns = spinHalfTurns;
		}
	}

	private final List<Entry> comboEntries = new ArrayList<>();
	private final Set<String> comboKeys = new HashSet<>();
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
	private Summary lastSummary = Summary.NONE;
	private Landed lastLanded = Landed.NONE;

	private int sessionScore;
	private Result lastResult = Result.NONE;
	/** Trick names of the combo that last landed or bailed, kept for the HUD flash. */
	private List<String> lastComboNames = Collections.emptyList();
	private float lastResultTime = -Float.MAX_VALUE;
	private float lastEventTime = -Float.MAX_VALUE;
	/** Distinct tricks and total entries of the combo that last landed or bailed (for callouts). */
	private int lastResultMultiplier;
	private int lastResultTrickCount;
	/** Bumped on every landing or bail that produced a result, so readers can spot a new one. */
	private int resultSequence;
	/** When the newest trick line last appeared or was renamed, for the HUD's pop-in. */
	private float newestNameTime = -Float.MAX_VALUE;
	/** When the multiplier last went up, for the HUD ring's pulse. */
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
		{
			newestNameTime = now;
		}
		if (size > 0 && multiplier() > multiplierBefore)
		{
			multiplierRiseTime = now;
		}
	}

	private void apply(TrickEvent e, float now)
	{
		switch (e.type)
		{
			case TRICK:
			{
				int index = e.upgradedFrom == null ? -1 : replaceLast(e.upgradedFrom, e.trick, e.fakie, e.displayName());
				if (index < 0)
				{
					addEntry(e.trick, e.fakie, e.trick.name(), e.displayName(), e.trick.points, 0f, 0, now);
					index = comboEntries.size() - 1;
				}
				if (isAirMain(e.trick))
				{
					airMain = index;
				}
				markStart(now);
				lastEventTime = now;
				break;
			}
			case SPIN:
				applySpin(e.spinHalfTurns, now);
				if (!comboEntries.isEmpty())
				{
					markStart(now);
				}
				lastEventTime = now;
				break;
			case HOLD_START:
				if (e.trick != null && (e.trick.kind == TrickKind.MANUAL || e.trick.kind == TrickKind.GRIND))
				{
					airMain = -1; // back on the ground or a rail: the air is over
				}
				lastEventTime = now;
				break;
			case HOLD_END:
				if (e.seconds >= MIN_HOLD_SECONDS)
				{
					addEntry(e.trick, false, e.trick.name(), e.trick.displayName, Math.round(e.trick.points * e.seconds),
						e.seconds, 0, now - e.seconds);
					markStart(now - e.seconds);
				}
				lastEventTime = now;
				break;
			case LANDED:
				resolveLanded(now, now, e.clean);
				break;
			case BAILED:
				resolveBailed(now);
				break;
			default:
				break;
		}
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
		{
			comboStart = t;
		}
	}

	/** Drops the combo in progress without scoring it or showing a result (e.g. leaving skate mode mid-air). */
	public void abandonCombo()
	{
		resetCombo();
	}

	/**
	 * Lands the combo in progress now, without waiting for the roll-out (stepping off the board banks it). Not
	 * clean. Nothing happens when there is no combo.
	 */
	public void bankCombo(float now)
	{
		if (!comboEntries.isEmpty())
		{
			resolveLanded(now, lastEventTime, false);
		}
	}

	public void update(float now, boolean rollingWithoutHold)
	{
		if (comboEntries.isEmpty())
		{
			return;
		}
		if (rollingWithoutHold && (now - lastEventTime) >= ROLL_OUT_SECONDS)
		{
			// the combo ended with its last trick, not after the roll-out wait
			resolveLanded(now, lastEventTime, false);
		}
	}

	/** A pop or a board flip (not a front or back flip of the body) is the trick a spin in the same air renames. */
	private static boolean isAirMain(Trick trick)
	{
		return (trick.kind == TrickKind.POP || trick.kind == TrickKind.FLIP) && trick.bodyFlipTurns == 0f;
	}

	private void addEntry(Trick trick, boolean fakie, String key, String name, int basePoints, float seconds,
		int spinHalfTurns, float time)
	{
		comboEntries.add(newEntry(trick, fakie, key, name, basePoints, seconds, spinHalfTurns, time));
		comboKeys.add(key);
	}

	private Entry newEntry(Trick trick, boolean fakie, String key, String name, int basePoints, float seconds,
		int spinHalfTurns, float time)
	{
		int n = sessionRepeatCounts.getOrDefault(key, 0);
		float decay = (float) Math.max(DECAY_FLOOR, Math.pow(DECAY_PER_REPEAT, n));
		sessionRepeatCounts.put(key, n + 1);
		return new Entry(trick, fakie, key, name, Math.round(basePoints * decay), seconds, spinHalfTurns, time);
	}

	/**
	 * Replaces the last entry of {@code from} with {@code trick} (a re-flick upgrade), undoing the
	 * replaced trick's repeat count. Returns its index, or -1 when the combo holds no {@code from}.
	 */
	private int replaceLast(Trick from, Trick trick, boolean fakie, String name)
	{
		for (int i = comboEntries.size() - 1; i >= 0; i--)
		{
			if (comboEntries.get(i).trick == from)
			{
				replaceAt(i, trick, fakie, trick.name(), name, trick.points, 0);
				return i;
			}
		}
		return -1;
	}

	/** Swaps entry {@code i} for a new one, undoing the old entry's repeat count first. */
	private void replaceAt(int i, Trick trick, boolean fakie, String key, String name, int basePoints,
		int spinHalfTurns)
	{
		Entry old = comboEntries.get(i);
		sessionRepeatCounts.computeIfPresent(old.key, (k, n) -> n > 1 ? n - 1 : null);
		comboEntries.set(i, newEntry(trick, fakie, key, name, basePoints, 0f, spinHalfTurns, old.time));
		comboKeys.clear();
		for (Entry entry : comboEntries)
		{
			comboKeys.add(entry.key);
		}
	}

	/**
	 * A landed spin: renames the air's main trick ("FS 180 Kickflip") and adds {@link SpinNames#bonus}, decayed
	 * by the spun trick's own key; with no trick in that air it is an entry of its own ("BS 360").
	 */
	private void applySpin(int halfTurns, float now)
	{
		if (halfTurns == 0)
		{
			return;
		}
		int amount = Math.abs(halfTurns);
		if (airMain >= 0 && airMain < comboEntries.size() && comboEntries.get(airMain).trick != null)
		{
			Entry main = comboEntries.get(airMain);
			String spun = SpinNames.name(main.trick.displayName, halfTurns);
			replaceAt(airMain, main.trick, main.fakie, main.trick.name() + "/" + amount,
				main.fakie ? "Fakie " + spun : spun, main.trick.points + SpinNames.bonus(halfTurns), amount);
		}
		else
		{
			addEntry(null, false, "SPIN/" + amount, SpinNames.label(halfTurns), SpinNames.bonus(halfTurns), 0f,
				amount, now);
		}
		airMain = -1;
	}

	/**
	 * @param now when the result shows
	 * @param end when the combo ended, for {@link #LONG_COMBO_SECONDS}
	 */
	private void resolveLanded(float now, float end, boolean clean)
	{
		if (comboEntries.isEmpty())
		{
			// a plain drop off a curb (roll-off, then land) has nothing to score or show
			return;
		}
		int points = comboPoints();
		int multiplier = multiplier();
		int newTricks = 0;
		boolean longCombo = false;
		if (landedKeys != null)
		{
			// variety: each trick's first landing this session scores a quarter more (the first entry of its key)
			Set<String> fresh = new HashSet<>();
			for (Entry entry : comboEntries)
			{
				if (!landedKeys.contains(entry.key) && fresh.add(entry.key))
				{
					points += Math.round(entry.points * FIRST_LANDING_BONUS);
					newTricks++;
				}
			}
			landedKeys.addAll(fresh);
			longCombo = !Float.isNaN(comboStart) && end - comboStart > LONG_COMBO_SECONDS;
			if (longCombo)
			{
				multiplier++;
			}
		}
		int value = points * multiplier;
		if (clean)
		{
			value = Math.round(value * (1f + CLEAN_BONUS));
		}
		sessionScore += value;
		lastSummary = summarize(value, multiplier, clean, newTricks, longCombo);
		lastLanded = record(value, end);
		rememberSummary();
		lastComboNames = comboNames();
		lastResult = clean ? Result.landedClean(value) : Result.landed(value);
		lastResultTime = now;
		resetCombo();
	}

	private Landed record(int value, float end)
	{
		List<TrickRecord> records = new ArrayList<>(comboEntries.size());
		float start = end;
		for (Entry entry : comboEntries)
		{
			records.add(new TrickRecord(entry.trick == null ? "SPIN" : entry.trick.name(), entry.points,
				entry.spinHalfTurns, entry.time));
			start = Math.min(start, entry.time);
		}
		if (!Float.isNaN(comboStart))
		{
			start = Math.min(start, comboStart);
		}
		return new Landed(value, start, Math.max(start, end), records);
	}

	private Summary summarize(int value, int multiplier, boolean clean, int newTricks, boolean longCombo)
	{
		List<Trick> tricks = new ArrayList<>(comboEntries.size());
		float grind = 0f;
		float manual = 0f;
		int spin = 0;
		for (Entry entry : comboEntries)
		{
			if (entry.trick != null)
			{
				tricks.add(entry.trick);
				if (entry.trick.kind == TrickKind.GRIND)
				{
					grind += entry.seconds;
				}
				else if (entry.trick.kind == TrickKind.MANUAL)
				{
					manual += entry.seconds;
				}
			}
			spin = Math.max(spin, entry.spinHalfTurns);
		}
		return new Summary(tricks, grind, manual, spin, value, multiplier, clean, newTricks, longCombo);
	}

	private void resolveBailed(float now)
	{
		rememberSummary();
		lastComboNames = comboNames();
		lastResult = Result.bailed(comboPoints() * multiplier());
		lastResultTime = now;
		resetCombo();
	}

	private void rememberSummary()
	{
		lastResultMultiplier = multiplier();
		lastResultTrickCount = comboEntries.size();
		resultSequence++;
	}

	private void resetCombo()
	{
		comboEntries.clear();
		comboKeys.clear();
		airMain = -1;
		lastEventTime = -Float.MAX_VALUE;
		comboStart = Float.NaN;
	}

	/** What the combo that last landed held; {@link Summary#NONE} before any landing. Bails leave it as it was. */
	public Summary lastSummary()
	{
		return lastSummary;
	}

	/** The combo that last landed, trick by trick ({@link Landed#NONE} before any). Bails leave it as it was. */
	public Landed lastLanded()
	{
		return lastLanded;
	}

	/** True while a combo is in progress (it has at least one trick entry). */
	public boolean hasCombo()
	{
		return !comboEntries.isEmpty();
	}

	/** Names of the combo that last landed or bailed (for the result flash). */
	public List<String> lastComboNames()
	{
		return lastComboNames;
	}

	public List<String> comboNames()
	{
		int size = comboEntries.size();
		int from = Math.max(0, size - DISPLAY_NAMES);
		List<String> names = new ArrayList<>(size - from);
		for (int i = from; i < size; i++)
		{
			names.add(comboEntries.get(i).name);
		}
		return Collections.unmodifiableList(names);
	}

	public int comboPoints()
	{
		int sum = 0;
		for (Entry entry : comboEntries)
		{
			sum += entry.points;
		}
		return sum;
	}

	public int multiplier()
	{
		return comboKeys.size();
	}

	public int sessionScore()
	{
		return sessionScore;
	}

	public Result lastResult()
	{
		return lastResult;
	}

	public float lastResultAge(float now)
	{
		return now - lastResultTime;
	}

	/** Increases by one on every landing or bail that produced a {@link #lastResult()}. */
	public int resultSequence()
	{
		return resultSequence;
	}

	/** Distinct tricks (the multiplier) of the combo that last landed or bailed. */
	public int lastResultMultiplier()
	{
		return lastResultMultiplier;
	}

	/** Every trick entry (not just the shown names) of the combo that last landed or bailed. */
	public int lastResultTrickCount()
	{
		return lastResultTrickCount;
	}

	/** Score-clock time the newest trick line appeared or was renamed; -Float.MAX_VALUE before any. */
	public float newestNameTime()
	{
		return newestNameTime;
	}

	/** Score-clock time the multiplier last went up; -Float.MAX_VALUE before any. */
	public float multiplierRiseTime()
	{
		return multiplierRiseTime;
	}
}
