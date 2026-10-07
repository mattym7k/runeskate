package com.gielinorskate.ui;

import com.gielinorskate.progression.BoardLook;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the side panel's "Skating" section shows: level, XP, the board designs in use and the session goals. Made on the client thread, shown
 * on the EDT.
 */
public final class ProgressState
{
	public final int level;
	public final int xp;
	/** XP still needed for the next level; 0 at 99. */
	public final int xpToNext;
	/** Through the current level, 0..1. */
	public final float progress;
	/** The board designs in use. */
	public final BoardLook look;
	/** This session's goals (empty before the first session). */
	public final List<GoalView> goals;

	/** One session goal as the panel shows it. */
	public static final class GoalView
	{
		public final String text;
		/** "3/5", or "Done". */
		public final String progress;
		public final boolean done;

		public GoalView(String text, String progress, boolean done)
		{
			this.text = text;
			this.progress = progress;
			this.done = done;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof GoalView))
			{
				return false;
			}
			GoalView g = (GoalView) o;
			return done == g.done && text.equals(g.text) && progress.equals(g.progress);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(text, progress, done);
		}
	}

	public ProgressState(int level, int xp, int xpToNext, float progress, BoardLook look)
	{
		this(level, xp, xpToNext, progress, look, Collections.emptyList());
	}

	public ProgressState(int level, int xp, int xpToNext, float progress, BoardLook look, List<GoalView> goals)
	{
		this.goals = Collections.unmodifiableList(new ArrayList<>(goals));
		this.look = look;
		this.level = level;
		this.xp = xp;
		this.xpToNext = xpToNext;
		this.progress = progress;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof ProgressState))
		{
			return false;
		}
		ProgressState s = (ProgressState) o;
		return level == s.level && xp == s.xp && xpToNext == s.xpToNext && progress == s.progress
			&& look.equals(s.look) && goals.equals(s.goals);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(level, xp, xpToNext, progress, look, goals);
	}
}
