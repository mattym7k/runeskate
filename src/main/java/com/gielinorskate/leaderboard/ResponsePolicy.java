package com.gielinorskate.leaderboard;

/**
 * What to do with a server answer to a claim or submit (backend/API.md "Errors and retries"): on 404
 * {@code not_claimed} claim and retry; never retry 400, 403, 409 or 422; retry 429 honouring Retry-After; retry
 * 5xx and network failures with backoff. Pure.
 */
public final class ResponsePolicy
{
	public enum Action
	{
		/** Stored (or answered): done. */
		OK,
		/** The account is not claimed yet: claim, then send again. */
		CLAIM,
		/** Try again later. */
		RETRY,
		/** Never retried. */
		DROP
	}

	private ResponsePolicy()
	{
	}

	/**
	 * @param status HTTP status, or 0 when the request failed before any answer (network, DNS, timeout)
	 * @param error the body's {@code error} code, or null
	 */
	public static Action classify(int status, String error)
	{
		if (status >= 200 && status < 300)
		{
			return Action.OK;
		}
		if (status == 404 && "not_claimed".equals(error))
		{
			return Action.CLAIM;
		}
		if (status == 0 || status == 429 || status >= 500)
		{
			return Action.RETRY;
		}
		return Action.DROP;
	}

	/** Retry-After in milliseconds (at least 1 s), or null when absent or not a number of seconds. */
	public static Long retryAfterMs(String header)
	{
		if (header == null)
		{
			return null;
		}
		try
		{
			long seconds = Long.parseLong(header.trim());
			return Math.max(1L, Math.min(seconds, 3600L)) * 1000L;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
