package com.gielinorskate.leaderboard;

import com.gielinorskate.progression.ProfileStore;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;

/**
* What the leaderboard keeps per account (RuneScape profile config): the secret that proves the account is ours
* to the server, this week's best combo (only beaten combos are submitted) and the best timed run (the result
* banner's PB marker). Never renamed keys; the secret is never logged. Pure apart from its {@link ProfileStore}.
*/
@RequiredArgsConstructor
public final class LeaderboardStore
{
/** Saved keys (profile config); never renamed. */
public static final String SECRET_KEY = "leaderboardSecret";
public static final String COMBO_WEEK_KEY = "leaderboardComboWeek";
public static final String RUN_BEST_KEY = "timedRunBest";

private static final SecureRandom RANDOM = new SecureRandom();

private final ProfileStore store;

/** True for this class's per-account keys. */
public static boolean isKey(String key)
{
return SECRET_KEY.equals(key) || COMBO_WEEK_KEY.equals(key) || RUN_BEST_KEY.equals(key);
}

/** The account's secret, made and saved on first use; null when logged out. */
public String secret()
{
String profile = store.profile();
if (profile == null)
return null;
String s = store.get(profile, SECRET_KEY);
if (s == null || !s.matches("^[A-Za-z0-9_-]{32,128}$"))
{
// base64url (no padding) of 32 random bytes: 43 characters the server accepts
byte[] bytes = new byte[32];
RANDOM.nextBytes(bytes);
s = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
store.set(profile, SECRET_KEY, s);
}
return s;
}

/** This account's best combo of the week {@code week} ("YYYY-MM-DD"), 0 when none (or another week's). */
public long comboWeekBest(String week)
{
String v = get(COMBO_WEEK_KEY);
return v != null && v.startsWith(week + ":") ? parse(v.substring(week.length() + 1)) : 0;
}

public void setComboWeekBest(String week, long score)
{
String profile = store.profile();
if (profile != null)
store.set(profile, COMBO_WEEK_KEY, week + ":" + score);
}

/** The best timed run of this account, 0 when none. */
public long runBest()
{
return parse(get(RUN_BEST_KEY));
}

/** Records a finished run; true when it is a new personal best (a first run with points counts). */
public boolean recordRun(long score)
{
String profile = store.profile();
if (profile == null || score <= runBest())
return false;
store.set(profile, RUN_BEST_KEY, Long.toString(score));
return true;
}

private String get(String key)
{
String profile = store.profile();
return profile == null ? null : store.get(profile, key);
}

private static long parse(String v)
{
try
{
return v == null ? 0 : Math.max(0, Long.parseLong(v.trim()));
}
catch (NumberFormatException e)
{
return 0;
}
}
}
