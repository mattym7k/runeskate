package com.gielinorskate.progression;

import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The three goals of the UTC day for the logged-in account, kept in its RuneScape profile so stopping and starting
 * skating (or logging out and in) never rerolls them: a completed goal stays completed, and pays its bonus once,
 * until the next UTC day. Each account has its own; switching accounts loads that account's. Saved state goes
 * back to the profile it was loaded from. While logged out the goals live in memory only. Pure apart from its
 * {@link ProfileStore}; client thread.
 */
public final class DailyGoals
{
	/** Saved key (profile config) of the UTC day the saved goals belong to, "2026-10-05"; never renamed. */
	public static final String DAY_KEY = "goalsDay";
	/** Saved key (profile config) of the day's goals and their progress ({@link #encode}); never renamed. */
	public static final String STATE_KEY = "goalsState";

	private static final String FORMAT_VERSION = "1";

	private final ProfileStore store;
	private final SessionGoals goals = new SessionGoals();
	/** The profile the goals came from (and are saved to); null when logged out. */
	private String profile;
	/** The UTC day the goals belong to; null before any. */
	private LocalDate day;

	public DailyGoals(ProfileStore store)
	{
		this.store = store;
	}

	/** The logged-in account changed (or the plugin started): its goals for {@code today}, rolled if it has none. */
	public void load(LocalDate today, Random random)
	{
		profile = store.profile();
		day = null;
		if (profile != null && today.toString().equals(store.get(profile, DAY_KEY)))
		{
			List<SessionGoals.Goal> saved = decode(store.get(profile, STATE_KEY));
			if (saved != null)
			{
				goals.restore(saved);
				day = today;
			}
		}
		if (day == null)
		{
			roll(today, random);
		}
	}

	/** Skating started: the goals stay unless the UTC day has turned. */
	public void startSession(LocalDate today, Random random)
	{
		if (!today.equals(day))
		{
			roll(today, random);
		}
	}

	/** Counts a landed combo (rolling first if the day turned); returns the goals it completed. */
	public List<SessionGoals.Goal> onLanded(ComboScorer.Summary combo, LocalDate today, Random random)
	{
		startSession(today, random);
		String before = encode(goals.goals());
		List<SessionGoals.Goal> completed = goals.onLanded(combo);
		if (!before.equals(encode(goals.goals())))
		{
			save();
		}
		return completed;
	}

	public SessionGoals goals()
	{
		return goals;
	}

	private void roll(LocalDate today, Random random)
	{
		goals.start(random);
		day = today;
		save();
	}

	private void save()
	{
		if (profile == null || day == null)
		{
			return;
		}
		store.set(profile, DAY_KEY, day.toString());
		store.set(profile, STATE_KEY, encode(goals.goals()));
	}

	/**
	 * "1|TYPE,progress,done,TRICK+TRICK|..." : the format version, then each goal's type name, uncapped progress,
	 * 1/0 for done, and the tricks a "different" goal has counted.
	 */
	static String encode(List<SessionGoals.Goal> list)
	{
		StringBuilder sb = new StringBuilder(FORMAT_VERSION);
		for (SessionGoals.Goal g : list)
		{
			sb.append('|').append(g.type.name()).append(',').append(g.rawProgress()).append(',')
				.append(g.isDone() ? '1' : '0').append(',');
			boolean first = true;
			Set<Trick> seen = EnumSet.noneOf(Trick.class);
			seen.addAll(g.seen());
			for (Trick t : seen)
			{
				sb.append(first ? "" : "+").append(t.name());
				first = false;
			}
		}
		return sb.toString();
	}

	/**
	 * The goals in {@code s}, or null when it is missing or not {@link SessionGoals#GOALS_PER_SESSION} distinct
	 * known goals with finite, non-negative progress. Unknown trick names are dropped.
	 */
	static List<SessionGoals.Goal> decode(String s)
	{
		if (s == null)
		{
			return null;
		}
		String[] parts = s.split("\\|", -1);
		if (parts.length != SessionGoals.GOALS_PER_SESSION + 1 || !FORMAT_VERSION.equals(parts[0]))
		{
			return null;
		}
		List<SessionGoals.Goal> out = new ArrayList<>(SessionGoals.GOALS_PER_SESSION);
		Set<SessionGoals.Type> types = EnumSet.noneOf(SessionGoals.Type.class);
		try
		{
			for (int i = 1; i < parts.length; i++)
			{
				String[] f = parts[i].split(",", -1);
				if (f.length != 4)
				{
					return null;
				}
				SessionGoals.Type type = SessionGoals.Type.valueOf(f[0]);
				float progress = Float.parseFloat(f[1]);
				if (!types.add(type) || Float.isNaN(progress) || Float.isInfinite(progress) || progress < 0f
					|| !("0".equals(f[2]) || "1".equals(f[2])))
				{
					return null;
				}
				Set<Trick> seen = EnumSet.noneOf(Trick.class);
				for (String name : f[3].isEmpty() ? new String[0] : f[3].split("\\+"))
				{
					try
					{
						seen.add(Trick.valueOf(name));
					}
					catch (IllegalArgumentException e)
					{
						// a trick that no longer exists: not counted
					}
				}
				out.add(new SessionGoals.Goal(type, progress, "1".equals(f[2]), seen));
			}
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
		return out;
	}
}
