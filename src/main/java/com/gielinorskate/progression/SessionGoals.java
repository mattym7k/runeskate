package com.gielinorskate.progression;

import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import com.gielinorskate.ui.TrickGuide;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Three goals picked at random from a pool ("Land 5 different flips"), counted from the combos that land; they
 * last the UTC day per account ({@link DailyGoals}). Completing one is worth {@link #BONUS_XP} Skating XP. Pure.
 */
public final class SessionGoals
{
	public static final int GOALS_PER_SESSION = 3;
	public static final int BONUS_XP = 2_000;

	/** The pool. Each goal counts something from landed combos up to its target. */
	public enum Type
	{
		DIFFERENT_FLIPS("Land 5 different flips", 5),
		GRIND_SECONDS("Grind for 10 seconds total", 10),
		BIG_COMBO("Score a 20k combo", 20_000),
		BODY_FLIP("Land a body flip", 1),
		SHIFT_TRICKS("Land 3 Shift tricks", 3),
		TOTAL_POINTS("Total 50k points", 50_000),
		MANUAL_SECONDS("Manual for 5 seconds total", 5),
		SPIN_360("Land a 360 spin", 1),
		CLEAN_LANDINGS("Land 5 clean combos", 5),
		MULTIPLIER("Land a x5 combo", 5),
		DIFFERENT_GRINDS("Land 3 different grinds", 3),
		GRABS("Land 3 grabs", 3);

		public final String text;
		public final int target;

		Type(String text, int target)
		{
			this.text = text;
			this.target = target;
		}
	}

	/** One goal of this session and how far it has got. */
	public static final class Goal
	{
		public final Type type;
		private float progress;
		private boolean done;
		/** Tricks already counted, for the "different" goals. */
		private final Set<Trick> seen = new HashSet<>();

		Goal(Type type)
		{
			this.type = type;
		}

		/** A goal restored from saved state ({@link DailyGoals}). */
		Goal(Type type, float progress, boolean done, Set<Trick> seen)
		{
			this.type = type;
			this.progress = progress;
			this.done = done;
			this.seen.addAll(seen);
		}

		/** Uncapped progress, as saved. */
		float rawProgress()
		{
			return progress;
		}

		/** Tricks counted so far by a "different" goal, as saved. */
		Set<Trick> seen()
		{
			return Collections.unmodifiableSet(seen);
		}

		public boolean isDone()
		{
			return done;
		}

		/** Progress towards the target, capped at it. */
		public float progress()
		{
			return Math.min(progress, type.target);
		}

		/** "3/5", "4.2/10 s", "12,300/20,000"; "Done" once complete. */
		public String progressText()
		{
			if (done)
			{
				return "Done";
			}
			switch (type)
			{
				case GRIND_SECONDS:
				case MANUAL_SECONDS:
					return String.format("%.1f/%d s", Math.floor(progress() * 10f) / 10f, type.target);
				case BIG_COMBO:
				case TOTAL_POINTS:
					return String.format("%,d/%,d", (int) progress(), type.target);
				default:
					return (int) progress() + "/" + type.target;
			}
		}
	}

	/** Shift tricks (Shift held as the flick fires), as the trick guide groups them. */
	private static final Set<Trick> SHIFT = shiftTricks();

	private final List<Goal> goals = new ArrayList<>(GOALS_PER_SESSION);

	private static Set<Trick> shiftTricks()
	{
		Set<Trick> out = EnumSet.noneOf(Trick.class);
		for (Trick t : Trick.values())
		{
			if (TrickGuide.entry(t).group == TrickGuide.Group.SHIFT)
			{
				out.add(t);
			}
		}
		return out;
	}

	static boolean isShiftTrick(Trick t)
	{
		return SHIFT.contains(t);
	}

	/** A new session: three different goals from the pool. */
	public void start(Random random)
	{
		List<Type> pool = new ArrayList<>();
		Collections.addAll(pool, Type.values());
		Collections.shuffle(pool, random);
		start(pool.subList(0, GOALS_PER_SESSION));
	}

	/** A new session with these goals. */
	public void start(List<Type> types)
	{
		goals.clear();
		for (Type t : types)
		{
			goals.add(new Goal(t));
		}
	}

	/** Goals restored from saved state. */
	void restore(List<Goal> restored)
	{
		goals.clear();
		goals.addAll(restored);
	}

	public List<Goal> goals()
	{
		return Collections.unmodifiableList(goals);
	}

	/** The first goal not yet done, or null when all are (or there are none). */
	public Goal current()
	{
		for (Goal g : goals)
		{
			if (!g.done)
			{
				return g;
			}
		}
		return null;
	}

	/** Counts a landed combo; returns the goals it completed. */
	public List<Goal> onLanded(ComboScorer.Summary combo)
	{
		List<Goal> completed = new ArrayList<>(1);
		for (Goal g : goals)
		{
			if (g.done)
			{
				continue;
			}
			count(g, combo);
			if (g.progress >= g.type.target)
			{
				g.done = true;
				completed.add(g);
			}
		}
		return completed;
	}

	private static void count(Goal g, ComboScorer.Summary combo)
	{
		switch (g.type)
		{
			case DIFFERENT_FLIPS:
				for (Trick t : combo.tricks)
				{
					if (t.kind == TrickKind.FLIP && t.bodyFlipTurns == 0f)
					{
						g.seen.add(t);
					}
				}
				g.progress = g.seen.size();
				break;
			case DIFFERENT_GRINDS:
				for (Trick t : combo.tricks)
				{
					if (t.kind == TrickKind.GRIND)
					{
						g.seen.add(t);
					}
				}
				g.progress = g.seen.size();
				break;
			case GRIND_SECONDS:
				g.progress += combo.grindSeconds;
				break;
			case MANUAL_SECONDS:
				g.progress += combo.manualSeconds;
				break;
			case BIG_COMBO:
				g.progress = Math.max(g.progress, combo.value);
				break;
			case TOTAL_POINTS:
				g.progress += combo.value;
				break;
			case BODY_FLIP:
				g.progress += countOf(combo, t -> t.bodyFlipTurns != 0f);
				break;
			case SHIFT_TRICKS:
				g.progress += countOf(combo, SessionGoals::isShiftTrick);
				break;
			case GRABS:
				g.progress += countOf(combo, t -> t.kind == TrickKind.GRAB);
				break;
			case SPIN_360:
				g.progress = combo.maxSpinHalfTurns >= 2 ? 1 : g.progress;
				break;
			case CLEAN_LANDINGS:
				g.progress += combo.clean ? 1 : 0;
				break;
			case MULTIPLIER:
				g.progress = Math.max(g.progress, combo.multiplier);
				break;
			default:
				break;
		}
	}

	private static int countOf(ComboScorer.Summary combo, Predicate<Trick> test)
	{
		int n = 0;
		for (Trick t : combo.tricks)
		{
			if (test.test(t))
			{
				n++;
			}
		}
		return n;
	}
}
