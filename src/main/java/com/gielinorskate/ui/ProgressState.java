package com.gielinorskate.ui;

import com.gielinorskate.progression.BoardLook;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/**
 * What the side panel's "Skating" section shows: level, XP, the board designs in use and the session goals. Made on
 * the client thread, shown on the EDT.
 */
@AllArgsConstructor
@EqualsAndHashCode
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
	@AllArgsConstructor
	@EqualsAndHashCode
	public static final class GoalView
	{
		public final String text;
		/** "3/5", or "Done". */
		public final String progress;
		public final boolean done;
	}
}
