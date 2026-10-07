package com.gielinorskate.leaderboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class LeaderboardClientTest
{
	/** Fetches a board through a client that answers locally, and returns the request it would have sent. */
	private static Request sent(String accountHash) throws Exception
	{
		CompletableFuture<Request> seen = new CompletableFuture<>();
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			seen.complete(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
				.body(ResponseBody.create(MediaType.parse("application/json"), "{}")).build();
		}).build();
		CompletableFuture<LeaderboardClient.Result> done = new CompletableFuture<>();
		new LeaderboardClient(http, new Gson()).leaderboard("combo", "week", accountHash, done::complete);
		assertEquals(200, done.get(10, TimeUnit.SECONDS).status);
		return seen.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void theAccountHashGoesInAHeaderNotTheUrl() throws Exception
	{
		Request r = sent("1234567890");
		assertEquals("1234567890", r.header("X-Gs-Account"));
		assertNull(r.url().queryParameter("account"));
		assertEquals("runeskate-leaderboard.runeskate.workers.dev", r.url().host());
		assertEquals("/v1/leaderboard", r.url().encodedPath());
		assertEquals("combo", r.url().queryParameter("category"));
		assertEquals("week", r.url().queryParameter("period"));
		assertEquals(LeaderboardClient.VERSION, r.header("X-Gs-Version"));
	}

	@Test
	public void noAccountSendsNoAccountHeader() throws Exception
	{
		assertNull(sent(null).header("X-Gs-Account"));
	}
}
