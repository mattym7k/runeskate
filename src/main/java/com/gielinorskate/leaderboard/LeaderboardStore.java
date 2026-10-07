package com.gielinorskate.leaderboard;

import com.gielinorskate.progression.ProfileStore;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * What the leaderboard keeps per account (RuneScape profile config): the secret that proves the account is ours
 * to the server, this week's best combo (only beaten combos are submitted) and the best timed run (the result
 * banner's PB marker). Never renamed keys; the secret is never logged. Pure apart from its {@link ProfileStore}.
 */
public final class LeaderboardStore
{
	/** Saved keys (profile config); never renamed. */
	public static final String SECRET_KEY = "leaderboardSecret";
	public static final String COMBO_WEEK_KEY = "leaderboardComboWeek";
	public static final String RUN_BEST_KEY = "timedRunBest";

	private static final Pattern SECRET = Pattern.compile("^[A-Za-z0-9_-]{32,128}$");

	private final ProfileStore store;
	private final SecureRandom random;

	public LeaderboardStore(ProfileStore store)
	{
		this(store, new SecureRandom());
	}

	LeaderboardStore(ProfileStore store, SecureRandom random)
	{
		this.store = store;
		this.random = random;
	}

	/** True for this class's per-account keys. */
	public static boolean isKey(String key)
	{
		return SECRET_KEY.equals(key) || COMBO_WEEK_KEY.equals(key) || RUN_BEST_KEY.equals(key);
	}

	/** base64url (no padding) of 32 random bytes: 43 characters the server accepts. */
	public static String newSecret(SecureRandom random)
	{
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** The account's secret, made and saved on first use; null when logged out. */
	public String secret()
	{
		String profile = store.profile();
		if (profile == null)
		{
			return null;
		}
		String s = store.get(profile, SECRET_KEY);
		if (s == null || !SECRET.matcher(s).matches())
		{
			s = newSecret(random);
			store.set(profile, SECRET_KEY, s);
		}
		return s;
	}

	/** This account's best combo of the week {@code week} ("YYYY-MM-DD"), 0 when none (or another week's). */
	public long comboWeekBest(String week)
	{
		String profile = store.profile();
		String v = profile == null ? null : store.get(profile, COMBO_WEEK_KEY);
		if (v == null)
		{
			return 0;
		}
		int colon = v.indexOf(':');
		if (colon < 0 || !v.substring(0, colon).equals(week))
		{
			return 0;
		}
		return parse(v.substring(colon + 1));
	}

	public void setComboWeekBest(String week, long score)
	{
		String profile = store.profile();
		if (profile != null)
		{
			store.set(profile, COMBO_WEEK_KEY, week + ":" + score);
		}
	}

	/** The best timed run of this account, 0 when none. */
	public long runBest()
	{
		String profile = store.profile();
		return profile == null ? 0 : parse(store.get(profile, RUN_BEST_KEY));
	}

	/** Records a finished run; true when it is a new personal best (a first run with points counts). */
	public boolean recordRun(long score)
	{
		String profile = store.profile();
		if (profile == null || score <= runBest())
		{
			return false;
		}
		store.set(profile, RUN_BEST_KEY, Long.toString(score));
		return true;
	}

	private static long parse(String v)
	{
		if (v == null)
		{
			return 0;
		}
		try
		{
			return Math.max(0, Long.parseLong(v.trim()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
