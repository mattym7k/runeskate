package com.gielinorskate.leaderboard;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The leaderboard server's three calls over RuneLite's injected {@link OkHttpClient}. Every call is queued with
 * {@code enqueue} (never run on the caller's thread); {@code done} is called on an OkHttp thread, so callers hop
 * to the client thread or the EDT themselves. Bodies are never logged (they hold the secret).
 */
@Slf4j
@Singleton
public class LeaderboardClient
{
	/** Sent as X-Gs-Version and pluginVersion: 1-32 of [A-Za-z0-9._+-]. */
	public static final String VERSION = "1.0.0";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	/** Biggest answer read (a top-100 board is a few KiB). */
	private static final long MAX_RESPONSE_BYTES = 256 * 1024;

	/** A finished request: the HTTP status (0 when no answer came), the body, the Retry-After header. */
	public static final class Result
	{
		public final int status;
		public final String body;
		public final String retryAfter;

		Result(int status, String body, String retryAfter)
		{
			this.status = status;
			this.body = body;
			this.retryAfter = retryAfter;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	LeaderboardClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	public void claim(String baseUrl, RunSubmission.Claim claim, Consumer<Result> done)
	{
		post(baseUrl, "v1/claim", gson.toJson(claim), done);
	}

	public void submit(String baseUrl, RunSubmission.Body body, Consumer<Result> done)
	{
		post(baseUrl, "v1/runs", gson.toJson(body), done);
	}

	/** {@code GET /v1/leaderboard}; {@code accountHash} is the caller's own (for "you"), or null. */
	public void leaderboard(String baseUrl, String category, String period, String accountHash,
		Consumer<Result> done)
	{
		HttpUrl base = HttpUrl.parse(ServerUrl.base(baseUrl));
		if (base == null)
		{
			done.accept(new Result(0, null, null));
			return;
		}
		HttpUrl.Builder url = base.newBuilder().addPathSegments("v1/leaderboard")
			.addQueryParameter("category", category)
			.addQueryParameter("period", period);
		if (accountHash != null)
		{
			url.addQueryParameter("account", accountHash);
		}
		send(new Request.Builder().url(url.build()).header("X-Gs-Version", VERSION).get().build(), done);
	}

	private void post(String baseUrl, String path, String json, Consumer<Result> done)
	{
		HttpUrl base = HttpUrl.parse(ServerUrl.base(baseUrl));
		if (base == null)
		{
			done.accept(new Result(0, null, null));
			return;
		}
		Request request = new Request.Builder()
			.url(base.newBuilder().addPathSegments(path).build())
			.header("X-Gs-Version", VERSION)
			.post(RequestBody.create(JSON, json))
			.build();
		send(request, done);
	}

	private void send(Request request, Consumer<Result> done)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Leaderboard request to {} failed: {}", request.url().encodedPath(), e.toString());
				done.accept(new Result(0, null, null));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					ResponseBody body = r.body();
					String text = body == null ? null : body.source().request(MAX_RESPONSE_BYTES) ? null
						: body.string();
					done.accept(new Result(r.code(), text, r.header("Retry-After")));
				}
				catch (IOException e)
				{
					log.debug("Leaderboard response from {} unreadable: {}", request.url().encodedPath(), e.toString());
					done.accept(new Result(0, null, null));
				}
			}
		});
	}
}
