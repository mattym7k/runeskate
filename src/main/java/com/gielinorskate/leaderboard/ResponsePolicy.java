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

/**
* @param status HTTP status, or 0 when the request failed before any answer (network, DNS, timeout)
* @param error the body's {@code error} code, or null
*/
public static Action classify(int status, String error)
{
return status >= 200 && status < 300 ? Action.OK : status == 404 && "not_claimed".equals(error) ? Action.CLAIM
: status == 0 || status == 429 || status >= 500 ? Action.RETRY : Action.DROP;
}

/** Retry-After in milliseconds (at least 1 s, at most an hour), or null when absent or not a number of seconds. */
public static Long retryAfterMs(String header)
{
try
{
return header == null ? null : Math.max(1L, Math.min(Long.parseLong(header.trim()), 3600L)) * 1000L;
}
catch (NumberFormatException e)
{
return null;
}
}
}
