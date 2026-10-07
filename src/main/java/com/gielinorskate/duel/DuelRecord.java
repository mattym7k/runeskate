package com.gielinorskate.duel;

import com.gielinorskate.progression.ProfileStore;

/**
 * Skate Duel wins and losses of the logged-in account, saved in its RuneScape profile (draws and called-off
 * duels are not counted). Client thread.
 */
public final class DuelRecord
{
	public static final String WINS_KEY = "duelWins";
	public static final String LOSSES_KEY = "duelLosses";

	private final ProfileStore store;
	/** The profile the counts were loaded from, or null. */
	private String profile;
	private int wins;
	private int losses;

	public DuelRecord(ProfileStore store)
	{
		this.store = store;
	}

	/** True for the per-account record keys (they are not settings). */
	public static boolean isKey(String key)
	{
		return WINS_KEY.equals(key) || LOSSES_KEY.equals(key);
	}

	/** The account changed (or the plugin started): its counts. */
	public void load()
	{
		profile = store.profile();
		wins = profile == null ? 0 : read(WINS_KEY);
		losses = profile == null ? 0 : read(LOSSES_KEY);
	}

	/** Counts a finished duel for the logged-in account. */
	public void record(DuelStateMachine.Outcome outcome)
	{
		if (outcome != DuelStateMachine.Outcome.WIN && outcome != DuelStateMachine.Outcome.LOSS)
		{
			return;
		}
		String now = store.profile();
		if (now == null)
		{
			return;
		}
		if (!now.equals(profile))
		{
			load();
		}
		if (outcome == DuelStateMachine.Outcome.WIN)
		{
			wins++;
			store.set(profile, WINS_KEY, Integer.toString(wins));
		}
		else
		{
			losses++;
			store.set(profile, LOSSES_KEY, Integer.toString(losses));
		}
	}

	/** Takes back a counted result that no longer stands (a loss voided when both quit at once). */
	public void unrecord(DuelStateMachine.Outcome outcome)
	{
		if (outcome != DuelStateMachine.Outcome.WIN && outcome != DuelStateMachine.Outcome.LOSS)
		{
			return;
		}
		String now = store.profile();
		if (now == null)
		{
			return;
		}
		if (!now.equals(profile))
		{
			load();
		}
		if (outcome == DuelStateMachine.Outcome.WIN)
		{
			wins = Math.max(0, wins - 1);
			store.set(profile, WINS_KEY, Integer.toString(wins));
		}
		else
		{
			losses = Math.max(0, losses - 1);
			store.set(profile, LOSSES_KEY, Integer.toString(losses));
		}
	}

	public int wins()
	{
		return wins;
	}

	public int losses()
	{
		return losses;
	}

	private int read(String key)
	{
		String v = store.get(profile, key);
		if (v == null)
		{
			return 0;
		}
		try
		{
			return Math.max(0, Integer.parseInt(v.trim()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
