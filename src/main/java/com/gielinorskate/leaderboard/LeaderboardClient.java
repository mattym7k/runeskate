package com.gielinorskate.leaderboard;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

/**
* The leaderboard server's three calls over RuneLite's injected {@link OkHttpClient}. Every call is queued with
* {@code enqueue} (never run on the caller's thread); {@code done} is called on an OkHttp thread, so callers hop
* to the client thread or the EDT themselves. Bodies are never logged (they hold the secret).
*/
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LeaderboardClient
{
/** Sent as X-Gs-Version and pluginVersion: 1-32 of [A-Za-z0-9._+-]. */
public static final String VERSION = "1.0.0";
/** The RuneSkate leaderboard server: the only host this plugin talks to, and only while "Submit scores" is on. */
public static final String SERVER = "https://runeskate-leaderboard.runeskate.workers.dev";
private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

/** A finished request: the HTTP status (0 when no answer came), the body, the Retry-After header. */
@AllArgsConstructor
public static final class Result
{
static final Result FAILED = new Result(0, null, null);

public final int status;
public final String body;
public final String retryAfter;

/** The body's error code, or null. */
String error(Gson gson)
{
return body == null ? null : LeaderboardPage.errorCode(gson, body);
}

/** The wait a 429 asked for (Retry-After), or null for the usual backoff. */
Long retryAfterMs()
{
return status == 429 ? ResponsePolicy.retryAfterMs(retryAfter) : null;
}
}

private final OkHttpClient http;
private final Gson gson;

public void claim(RunSubmission.Claim claim, Consumer<Result> done)
{
post("v1/claim", gson.toJson(claim), done);
}

public void submit(RunSubmission.Body body, Consumer<Result> done)
{
post("v1/runs", gson.toJson(body), done);
}

/** {@code GET /v1/leaderboard}; {@code accountHash} is the caller's own (for "you"), or null. */
public void leaderboard(String category, String period, String accountHash, Consumer<Result> done)
{
HttpUrl url = url("v1/leaderboard").newBuilder().addQueryParameter("category", category)
.addQueryParameter("period", period).build();
Request.Builder request = new Request.Builder().url(url).header("X-Gs-Version", VERSION);
if (accountHash != null)
// a header, not the query string, so the hash stays out of URLs and access logs
request.header("X-Gs-Account", accountHash);
send(request.build(), done);
}

private static HttpUrl url(String path)
{
return HttpUrl.get(SERVER).newBuilder().addPathSegments(path).build();
}

private void post(String path, String json, Consumer<Result> done)
{
send(new Request.Builder().url(url(path)).header("X-Gs-Version", VERSION)
.post(RequestBody.create(JSON, json)).build(), done);
}

private void send(Request request, Consumer<Result> done)
{
String path = request.url().encodedPath();
http.newCall(request).enqueue(new Callback()
{
@Override
public void onFailure(Call call, IOException e)
{
log.debug("Leaderboard request to {} failed: {}", path, e.toString());
done.accept(Result.FAILED);
}

@Override
public void onResponse(Call call, Response response)
{
try (Response r = response)
{
// the biggest answer read is 256 KiB (a top-100 board is a few KiB)
ResponseBody body = r.body();
String text = body == null || body.source().request(256 * 1024) ? null : body.string();
done.accept(new Result(r.code(), text, r.header("Retry-After")));
}
catch (IOException e)
{
log.debug("Leaderboard response from {} unreadable: {}", path, e.toString());
done.accept(Result.FAILED);
}
}
});
}
}
