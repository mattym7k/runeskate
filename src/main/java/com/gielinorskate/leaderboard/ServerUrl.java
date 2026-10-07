package com.gielinorskate.leaderboard;

import java.util.regex.Pattern;

/** The configured leaderboard server URL: usable or not. Pure. */
public final class ServerUrl
{
	/** The placeholder shipped until the Worker is deployed; while it is set, the feature stays inert. */
	public static final String PLACEHOLDER = "https://gielinor-skate.CHANGE-ME.workers.dev";

	private static final Pattern HTTPS = Pattern.compile("^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/?$");

	private ServerUrl()
	{
	}

	/** True for an https origin that is not the placeholder. */
	public static boolean isConfigured(String url)
	{
		return url != null && !url.contains("CHANGE-ME") && HTTPS.matcher(url.trim()).matches();
	}

	/** The URL without a trailing slash. */
	public static String base(String url)
	{
		String u = url.trim();
		return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
	}
}
