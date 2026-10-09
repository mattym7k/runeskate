package com.gielinorskate.duel;

import com.gielinorskate.duel.DuelStateMachine.Outcome;
import com.gielinorskate.progression.ProfileStore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
* Skate Duel wins and losses of the logged-in account, saved in its RuneScape profile (draws and called-off
* duels are not counted). Client thread.
*/
@RequiredArgsConstructor
public final class DuelRecord
{
public static final String WINS_KEY = "duelWins";
public static final String LOSSES_KEY = "duelLosses";

private final ProfileStore store;
/** The profile the counts were loaded from, or null. */
private String profile;
@Getter
private int wins;
@Getter
private int losses;

/** True for the per-account record keys (they are not settings). */
public static boolean isKey(String key)
{
return WINS_KEY.equals(key) || LOSSES_KEY.equals(key);
}

/** The account changed (or the plugin started): its counts. */
public void load()
{
profile = store.profile();
wins = read(WINS_KEY);
losses = read(LOSSES_KEY);
}

/**
* Counts a finished duel for the logged-in account ({@code delta} 1), or takes back a counted result that no
* longer stands ({@code delta} -1: a loss voided when both quit at once). Only wins and losses count.
*/
public void add(Outcome outcome, int delta)
{
String now = store.profile();
if (outcome != Outcome.WIN && outcome != Outcome.LOSS || now == null)
return;
if (!now.equals(profile))
load();
if (outcome == Outcome.WIN)
{
wins = Math.max(0, wins + delta);
store.set(profile, WINS_KEY, Integer.toString(wins));
}
else
{
losses = Math.max(0, losses + delta);
store.set(profile, LOSSES_KEY, Integer.toString(losses));
}
}

private int read(String key)
{
String v = profile == null ? null : store.get(profile, key);
try
{
return v == null ? 0 : Math.max(0, Integer.parseInt(v.trim()));
}
catch (NumberFormatException e)
{
return 0;
}
}
}
