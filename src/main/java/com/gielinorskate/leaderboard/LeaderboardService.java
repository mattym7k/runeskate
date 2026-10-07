package com.gielinorskate.leaderboard;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.scoring.ComboScorer;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;

/**
 * The online leaderboard, opt-in ("Submit scores to the leaderboard", off by default). While it is off, or the
 * server URL is still the placeholder, nothing here touches the network. When on: landed combos that beat this
 * week's best, finished timed runs, and the Skating XP (at most every 10 minutes, and on logout) are queued and
 * sent with the account's own hash, name and secret; never anything about other players. Failed sends retry
 * with backoff (an in-memory queue of at most 50, dropped on logout). The side panel's boards are fetched here,
 * throttled, and so is the on-screen board's (on enable, on login, after an own submit, and at most once a minute
 * while it shows; the overlay draws its {@link Hud} snapshot). Only normal (STANDARD) worlds submit: RuneLite keeps a separate profile, so a separate secret and
 * week best, for each world type (Leagues, Deadman, Beta...) of one account, while the server keys the account by
 * its hash alone. State lives on the client thread; HTTP answers hop back to it, and panel updates go to the EDT.
 */
@Slf4j
@Singleton
public class LeaderboardService
{
	/** The panel's line on a world that does not submit. */
	static final String NORMAL_WORLDS_ONLY = "Leaderboards are for normal worlds only: nothing is submitted from "
		+ "this world.";

	/** Least time between two XP submits. */
	static final long XP_INTERVAL_MS = 10 * 60_000L;

	/** The on-screen board is fetched again after this long while it shows. */
	static final long HUD_REFRESH_MS = 60_000L;
	/** After a failed fetch, the on-screen board waits this long before trying again. */
	static final long HUD_RETRY_MS = 30_000L;

	/**
	 * What the on-screen leaderboard shows: a snapshot made on the client thread, read by the overlay as it draws.
	 * Immutable.
	 */
	public static final class Hud
	{
		public final View.State state;
		/** Logged in on a world that does not submit. */
		public final boolean otherWorld;
		public final String category;
		public final String period;
		/** The board, or null while loading / on error. */
		public final LeaderboardPage page;
		/** A line to show when there is no board (an error), or null. */
		public final String message;

		public Hud(View.State state, boolean otherWorld, String category, String period, LeaderboardPage page,
			String message)
		{
			this.state = state;
			this.otherWorld = otherWorld;
			this.category = category;
			this.period = period;
			this.page = page;
			this.message = message;
		}
	}

	/** What the panel's leaderboard section shows: the feature's state, and a board or a message. Immutable. */
	public static final class View
	{
		public enum State
		{
			/** The setting is off: "turn on in settings". */
			OFF,
			ON
		}

		public final State state;
		public final String category;
		public final String period;
		/** The board, or null while loading / on error. */
		public final LeaderboardPage page;
		/** A line to show (loading, an error, an account problem), or null. */
		public final String message;
		/** Wall-clock ms the board was fetched, 0 when none. */
		public final long fetchedAt;

		View(State state, String category, String period, LeaderboardPage page, String message, long fetchedAt)
		{
			this.state = state;
			this.category = category;
			this.period = period;
			this.page = page;
			this.message = message;
			this.fetchedAt = fetchedAt;
		}
	}

	/** Who is logged in, as the server sees it. */
	private static final class Identity
	{
		final long hash;
		final String name;

		Identity(long hash, String name)
		{
			this.hash = hash;
			this.name = name;
		}
	}

	private static final class Cached
	{
		final LeaderboardPage page;
		final long at;

		Cached(LeaderboardPage page, long at)
		{
			this.page = page;
			this.at = at;
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final GielinorSkateConfig config;
	private final ConfigManager configManager;
	private final LeaderboardClient api;
	private final Gson gson;
	private final ProgressionService progression;
	private final LeaderboardStore store;

	private final SubmitQueue queue = new SubmitQueue();
	private final FetchThrottle throttle = new FetchThrottle();
	private final Map<String, Cached> boards = new HashMap<>();
	/** Bumped when the queue is dropped, so answers to old requests are ignored. */
	private int generation;
	private boolean inFlight;
	/** The account problem the panel shows (claimed by another install, banned), or null. */
	private String problem;

	/** The logged-in account, its XP and secret, as of the latest tick (for the XP submit on logout). */
	private Identity lastIdentity;
	private int lastXp;
	private boolean lastDev;
	private String lastSecret;
	/** The XP last queued, when, and for which account (kept over a relog, so the 10 minutes still hold). */
	private int xpSent = -1;
	private long xpSentAt = Long.MIN_VALUE / 2;
	private long xpAccount;
	/** The XP the server last stored for this account (-1 when unknown). */
	private int xpConfirmed = -1;

	/** The plugin is off: nothing is sent (this singleton outlives the plugin's shutdown). */
	private volatile boolean stopped;
	/** Logged in on a world that does not submit (as of the latest tick). */
	private boolean otherWorld;

	/** The panel's section (on the EDT); null when there is none. */
	private Consumer<View> viewListener;
	private String shownCategory = "combo";
	private String shownPeriod = "all";
	/** The panel's board could not be fetched yet (too soon after another request): a retry is scheduled. */
	private boolean panelRetry;

	/** The on-screen board: which, what it shows, when it was fetched and whether it must be fetched again. */
	private String hudCategory = "combo";
	private String hudPeriod = "week";
	private volatile Hud hud = new Hud(View.State.OFF, false, "combo", "week", null, null);
	private long hudFetchedAt;
	private boolean hudStale = true;
	private long hudRetryAt;
	/** The account the boards were fetched for (their "you" rows), -1 when logged out. */
	private long boardsAccount = -1;

	@Inject
	LeaderboardService(Client client, ClientThread clientThread, GielinorSkateConfig config, ConfigManager configManager,
		LeaderboardClient api, Gson gson, ProgressionService progression)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.configManager = configManager;
		this.api = api;
		this.gson = gson;
		this.progression = progression;
		this.store = new LeaderboardStore(new ProgressionService.ConfigProfileStore(configManager));
	}

	/** The plugin is on and the setting is on: the only case in which anything is sent. */
	public boolean isEnabled()
	{
		return !stopped && config.submitScores();
	}

	/** Plugin start: sending may begin. Any thread. */
	public void startUp()
	{
		stopped = false;
	}

	/**
	 * Plugin shutdown: from now on nothing is sent (an answer still on its way sends nothing more) until
	 * {@link #startUp}; {@link #onConfigChanged} on the client thread then drops what is queued. Any thread.
	 */
	public void shutDown()
	{
		stopped = true;
	}

	private View.State state()
	{
		return config.submitScores() ? View.State.ON : View.State.OFF;
	}

	/** Whether a world of profile type {@code type} submits: normal worlds only. */
	static boolean submitsFrom(RuneScapeProfileType type)
	{
		return type == RuneScapeProfileType.STANDARD;
	}

	/** Logged in on a world that does not submit. Client thread. */
	private boolean onOtherWorld()
	{
		return client.getGameState() == GameState.LOGGED_IN && !submitsFrom(RuneScapeProfileType.getCurrent(client));
	}

	/** The logged-in account with a name the server takes, on a normal world, or null. Client thread. */
	private Identity identity()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !submitsFrom(RuneScapeProfileType.getCurrent(client)))
		{
			return null;
		}
		long hash = client.getAccountHash();
		Player p = client.getLocalPlayer();
		String name = p == null ? null : RunSubmission.displayName(p.getName());
		return hash == -1 || name == null ? null : new Identity(hash, name);
	}

	/** The progression loaded is the logged-in account's (so its XP and dev flag are this account's). */
	private boolean progressionIsCurrent()
	{
		String profile = progression.profile();
		return profile != null && profile.equals(configManager.getRSProfileKey());
	}

	// ---- submitting

	/**
	 * What a combo of week {@code week} must beat to be worth queueing: the best the server has confirmed, or a
	 * higher one still queued or in flight. A combo dropped unsent (refused, or the queue cleared at logout) no
	 * longer counts.
	 */
	static long comboBar(long confirmed, SubmitQueue queue, long accountHash, String week)
	{
		return Math.max(confirmed, queue.highestCombo(accountHash, week));
	}

	/** A combo landed: queued when it beats this account's best of the week. Client thread. */
	public void onComboLanded(ComboScorer.Landed landed)
	{
		if (!isEnabled())
		{
			return;
		}
		Identity id = identity();
		if (id == null || landed.value <= 0)
		{
			return;
		}
		String week = WeekStart.key(System.currentTimeMillis());
		if (landed.value <= comboBar(store.comboWeekBest(week), queue, id.hash, week))
		{
			return;
		}
		RunSubmission run = RunSubmission.combo(landed);
		String refused = RunBounds.check(run);
		if (refused != null)
		{
			// the server would refuse it (e.g. a spin past its cap, or more than 200 tricks): not sent
			log.debug("Leaderboard: combo not submitted ({})", refused);
			return;
		}
		// the week best is raised only once the server has stored it (a lost submit must not block the week)
		queue.add(run, id.hash, System.currentTimeMillis());
		pump();
	}

	/** A timed run finished: queued. Client thread. */
	public void onRunFinished(TimedRun.Finished run)
	{
		if (!isEnabled())
		{
			return;
		}
		Identity id = identity();
		if (id == null || run.score <= 0)
		{
			return;
		}
		RunSubmission s = RunSubmission.session(run.score, run.durationMs, run.start, run.tricks);
		String refused = RunBounds.check(s);
		if (refused != null)
		{
			log.debug("Leaderboard: timed run not submitted ({})", refused);
			return;
		}
		queue.add(s, id.hash, System.currentTimeMillis());
		pump();
	}

	/** Every game tick: notes the account, queues the XP when due, sends what is due. Client thread. */
	public void tick()
	{
		if (!isEnabled())
		{
			return;
		}
		noteWorld();
		refreshHud();
		Identity id = identity();
		if (id == null || !progressionIsCurrent())
		{
			return;
		}
		if (xpAccount != id.hash)
		{
			xpAccount = id.hash;
			xpSent = -1;
			xpSentAt = Long.MIN_VALUE / 2;
			xpConfirmed = -1;
			problem = null;
		}
		lastIdentity = id;
		lastXp = progression.xp();
		lastDev = progression.devLevelSet();
		lastSecret = store.secret();
		long now = System.currentTimeMillis();
		if (xpDue(lastXp, xpSent, xpConfirmed, now - xpSentAt))
		{
			queue.add(RunSubmission.xp(lastXp), id.hash, now);
			xpSent = lastXp;
			xpSentAt = now;
		}
		pump();
	}

	/**
	 * Whether to queue the XP {@code xp}: not yet sent nor confirmed by the server, and at least
	 * {@link #XP_INTERVAL_MS} since the last XP submit ({@code sinceSent} ms).
	 */
	static boolean xpDue(int xp, int sent, int confirmed, long sinceSent)
	{
		return xp > 0 && xp != sent && xp != confirmed && sinceSent >= XP_INTERVAL_MS;
	}

	/**
	 * Logged out: the latest XP is sent once (no retry), then the queue is dropped. It counts as stored only when
	 * the server says so; otherwise it goes again on a later login. Uses what the last tick saw,
	 * since the account may already be gone. Client thread.
	 */
	public void onLogout()
	{
		Identity id = lastIdentity;
		if (isEnabled() && id != null && lastSecret != null && lastXp > 0 && lastXp != xpConfirmed)
		{
			RunSubmission.Body body = new RunSubmission.Body(RunSubmission.xp(lastXp), Long.toString(id.hash),
				lastSecret, id.name, LeaderboardClient.VERSION, lastDev);
			int xp = lastXp;
			long hash = id.hash;
			api.submit(body, r ->
			{
				log.debug("Leaderboard: logout XP submit answered {}", r.status);
				String error = r.body == null ? null : LeaderboardPage.errorCode(gson, r.body);
				if (ResponsePolicy.classify(r.status, error) == ResponsePolicy.Action.OK)
				{
					clientThread.invoke(() ->
					{
						if (xpAccount == hash)
						{
							xpConfirmed = xp;
						}
					});
				}
			});
		}
		dropQueue();
		boardsAccount = -1;
		if (otherWorld)
		{
			otherWorld = false;
			publish(boards.get(shownCategory + "/" + shownPeriod), null);
			updateHud(hud.page, null);
		}
	}

	/** Notes whether this world submits; the panel and the on-screen board are told when that changes. */
	private void noteWorld()
	{
		boolean other = onOtherWorld();
		if (other != otherWorld)
		{
			otherWorld = other;
			publish(boards.get(shownCategory + "/" + shownPeriod), null);
			updateHud(hud.page, null);
		}
	}

	/**
	 * Logged in (also after each loading screen): for a different account than the boards were fetched for,
	 * they are fetched again (their "you" rows are per account) and the on-screen board refreshes. Client thread.
	 */
	public void onLoggedIn()
	{
		long hash = client.getAccountHash();
		if (hash == boardsAccount)
		{
			return;
		}
		boardsAccount = hash;
		boards.clear();
		throttle.clear();
		hudStale = true;
		hudRetryAt = 0;
		if (isEnabled())
		{
			noteWorld();
			refreshHud();
		}
	}

	/**
	 * The setting or URL changed (and at start and shutdown): off drops everything queued; the panel is told.
	 * Client thread.
	 */
	public void onConfigChanged()
	{
		if (!isEnabled())
		{
			dropQueue();
		}
		boards.clear();
		throttle.clear();
		// the panel asks for a board when it is open (or opened)
		publish(null, null);
		readHudBoard();
		hudFetchedAt = 0;
		hudStale = true;
		hudRetryAt = 0;
		updateHud(null, null);
		if (isEnabled())
		{
			// e.g. just turned on while on another world: the status, not a fetch
			noteWorld();
		}
		refreshHud();
	}

	private void dropQueue()
	{
		queue.clear();
		generation++;
		inFlight = false;
		lastIdentity = null;
		lastSecret = null;
		lastXp = 0;
	}

	/** Sends the oldest due item, one request at a time. Client thread. */
	private void pump()
	{
		if (inFlight || !isEnabled())
		{
			return;
		}
		Identity id = identity();
		if (id == null || !progressionIsCurrent())
		{
			return;
		}
		queue.keepOnly(id.hash);
		long now = System.currentTimeMillis();
		SubmitQueue.Item item = queue.due(now);
		if (item == null)
		{
			return;
		}
		String secret = store.secret();
		if (secret == null)
		{
			return;
		}
		RunSubmission.Body body = new RunSubmission.Body(item.run, Long.toString(id.hash), secret, id.name,
			LeaderboardClient.VERSION, progression.devLevelSet());
		int gen = generation;
		inFlight = true;
		api.submit(body, r -> clientThread.invoke(() -> onSubmitted(gen, item, id, secret, r)));
	}

	private void onSubmitted(int gen, SubmitQueue.Item item, Identity id, String secret, LeaderboardClient.Result r)
	{
		if (gen != generation)
		{
			return;
		}
		inFlight = false;
		String error = r.body == null ? null : LeaderboardPage.errorCode(gson, r.body);
		long now = System.currentTimeMillis();
		ResponsePolicy.Action action = ResponsePolicy.classify(r.status, error);
		if (action == ResponsePolicy.Action.CLAIM && item.claimed)
		{
			// claimed already and still not found: try again later rather than claim in a loop
			action = ResponsePolicy.Action.RETRY;
		}
		switch (action)
		{
			case OK:
				queue.done(item);
				if (RunSubmission.XP.equals(item.run.kind) && item.accountHash == xpAccount)
				{
					xpConfirmed = item.run.xp;
				}
				if (RunSubmission.COMBO.equals(item.run.kind))
				{
					// stored: it, or the server's best of the week (e.g. from another computer), raises ours
					String week = WeekStart.key(now);
					long sent = week.equals(item.week) && item.run.score != null ? item.run.score : 0;
					long best = Math.max(sent, LeaderboardPage.weekBest(gson, r.body));
					if (best > store.comboWeekBest(week))
					{
						store.setComboWeekBest(week, best);
					}
				}
				boards.clear();
				// the on-screen board shows the new score (when the throttle allows)
				hudStale = true;
				break;
			case CLAIM:
				claim(gen, item, id, secret);
				return;
			case RETRY:
				queue.retryLater(item, now, r.status == 429 ? ResponsePolicy.retryAfterMs(r.retryAfter) : null);
				log.debug("Leaderboard: {} submit will be retried (status {})", item.run.kind, r.status);
				break;
			default:
				queue.done(item);
				log.debug("Leaderboard: {} submit refused ({} {})", item.run.kind, r.status, error);
				noteProblem(error);
				break;
		}
		pump();
	}

	/** First use: binds this account to our secret, then sends the item again. */
	private void claim(int gen, SubmitQueue.Item item, Identity id, String secret)
	{
		inFlight = true;
		item.claimed = true;
		RunSubmission.Claim body = new RunSubmission.Claim(Long.toString(id.hash), id.name, secret);
		api.claim(body, r -> clientThread.invoke(() ->
		{
			if (gen != generation)
			{
				return;
			}
			inFlight = false;
			String error = r.body == null ? null : LeaderboardPage.errorCode(gson, r.body);
			long now = System.currentTimeMillis();
			ResponsePolicy.Action action = ResponsePolicy.classify(r.status, error);
			if (action == ResponsePolicy.Action.OK)
			{
				queue.retryNow(item, now);
			}
			else if (action == ResponsePolicy.Action.RETRY)
			{
				item.claimed = false;
				queue.retryLater(item, now, r.status == 429 ? ResponsePolicy.retryAfterMs(r.retryAfter) : null);
			}
			else
			{
				queue.done(item);
				log.debug("Leaderboard: claim refused ({} {})", r.status, error);
				noteProblem(error);
			}
			pump();
		}));
	}

	private void noteProblem(String error)
	{
		String text = null;
		if ("already_claimed".equals(error) || "bad_secret".equals(error))
		{
			text = "This account's leaderboard entry was claimed from another computer or install, so scores "
				+ "from here can't be submitted.";
		}
		else if ("banned".equals(error))
		{
			text = "This account can't submit to the leaderboard.";
		}
		if (text != null && !text.equals(problem))
		{
			problem = text;
			publish(null, null);
		}
	}

	// ---- the panel's boards

	/** Who shows the boards (the panel section, called on the EDT). */
	public void setViewListener(Consumer<View> listener)
	{
		viewListener = listener;
	}

	/**
	 * The panel wants a board ({@code category} combo/session/xp, {@code period} all/week). Served from cache when
	 * fresh; {@code refresh} asks for a new one, still throttled. Client thread.
	 */
	public void fetch(String category, String period, boolean refresh)
	{
		shownCategory = category;
		shownPeriod = period;
		if (!isEnabled())
		{
			publish(null, null);
			return;
		}
		String key = category + "/" + period;
		long now = System.currentTimeMillis();
		Cached cached = boards.get(key);
		if (cached != null && !refresh)
		{
			publish(cached, null);
			return;
		}
		if (!throttle.tryFetch(key, now))
		{
			long waitMs = throttle.waitMs(key, now);
			long wait = (waitMs + 999) / 1000;
			publish(cached, cached != null ? "Up to date. Refresh again in " + wait + " s."
				: "Please wait " + wait + " s.");
			if (cached == null && waitMs <= FetchThrottle.MIN_GAP_MS)
			{
				// only just after another request (e.g. the on-screen board's): fetched as soon as it may be
				retryPanel();
			}
			return;
		}
		publish(cached, "Loading...");
		load(key, category, period);
	}

	/** Fetches the panel's board once the throttle allows (a moment), unless it is already in or the feature is off. */
	private void retryPanel()
	{
		if (panelRetry)
		{
			return;
		}
		panelRetry = true;
		clientThread.invokeLater(() ->
		{
			if (!isEnabled())
			{
				panelRetry = false;
				return true;
			}
			String key = shownCategory + "/" + shownPeriod;
			if (throttle.waitMs(key, System.currentTimeMillis()) > 0)
			{
				// asked again on the next client cycle
				return false;
			}
			panelRetry = false;
			if (!boards.containsKey(key))
			{
				fetch(shownCategory, shownPeriod, false);
			}
			return true;
		});
	}

	/**
	 * Requests board {@code key} (the throttle already said yes); the answer goes to the panel and to the
	 * on-screen board when they show it. The request runs on OkHttp's threads; its answer comes back here.
	 */
	private void load(String key, String category, String period)
	{
		long hash = client.getAccountHash();
		String account = hash == -1 ? null : Long.toString(hash);
		api.leaderboard(category, period, account, r ->
		{
			LeaderboardPage page = r.status == 200 && r.body != null ? LeaderboardPage.parse(gson, r.body) : null;
			clientThread.invoke(() ->
			{
				boolean forPanel = category.equals(shownCategory) && period.equals(shownPeriod);
				boolean forHud = key.equals(hudKey());
				if (page == null)
				{
					throttle.failed(key);
					if (forPanel)
					{
						publish(boards.get(key), r.status == 429 ? "The leaderboard is busy. Try again in a minute."
							: "Couldn't load the leaderboard. Try Refresh in a moment.");
					}
					if (forHud)
					{
						hudRetryAt = System.currentTimeMillis() + HUD_RETRY_MS;
						if (hud.page == null)
						{
							updateHud(null, r.status == 429 ? "The leaderboard is busy." : "Couldn't load the leaderboard.");
						}
					}
					return;
				}
				Cached c = new Cached(page, System.currentTimeMillis());
				boards.put(key, c);
				if (forPanel)
				{
					publish(c, null);
				}
				if (forHud)
				{
					showHud(c);
				}
			});
		});
	}

	// ---- the on-screen board

	/** The on-screen board's snapshot. Any thread. */
	public Hud hud()
	{
		return hud;
	}

	private String hudKey()
	{
		return hudCategory + "/" + hudPeriod;
	}

	/** Reads which board the on-screen leaderboard shows from the settings (unknown values fall back). */
	private void readHudBoard()
	{
		String c = config.leaderboardOverlayBoard();
		hudCategory = "session".equals(c) || "xp".equals(c) ? c : "combo";
		hudPeriod = "all".equals(config.leaderboardOverlayPeriod()) ? "all" : "week";
	}

	/**
	 * Whether the on-screen board should be fetched: never fetched ({@code fetchedAt} 0), marked stale (login,
	 * own submit, setting changed) or {@link #HUD_REFRESH_MS} old, and not backing off after a failure.
	 */
	static boolean hudDue(long fetchedAt, boolean stale, long now, long retryAt)
	{
		return now >= retryAt && (fetchedAt == 0 || stale || now - fetchedAt >= HUD_REFRESH_MS);
	}

	/** The on-screen leaderboard's settings changed (shown, board, period, collapsed). Client thread. */
	public void onHudConfigChanged()
	{
		String key = hudKey();
		readHudBoard();
		if (!key.equals(hudKey()))
		{
			hudFetchedAt = 0;
			hudStale = true;
			hudRetryAt = 0;
			updateHud(null, null);
		}
		refreshHud();
	}

	/**
	 * Keeps the on-screen board fresh while it shows (on, expanded, on a normal world): served from the boards
	 * already fetched (e.g. by the panel) when newer, else fetched when due and the throttle allows. Client thread.
	 */
	private void refreshHud()
	{
		if (!isEnabled() || !config.showLeaderboardOverlay() || config.leaderboardOverlayCollapsed() || otherWorld)
		{
			return;
		}
		String key = hudKey();
		Cached cached = boards.get(key);
		if (cached != null && cached.at > hudFetchedAt)
		{
			showHud(cached);
		}
		long now = System.currentTimeMillis();
		if (!hudDue(hudFetchedAt, hudStale, now, hudRetryAt) || !throttle.tryFetch(key, now))
		{
			return;
		}
		load(key, hudCategory, hudPeriod);
	}

	private void showHud(Cached c)
	{
		hudFetchedAt = c.at;
		hudStale = false;
		hudRetryAt = 0;
		updateHud(c.page, null);
	}

	/** Makes the overlay's snapshot. */
	private void updateHud(LeaderboardPage page, String message)
	{
		View.State state = state();
		hud = new Hud(state, otherWorld, hudCategory, hudPeriod, state == View.State.ON ? page : null, message);
	}

	/** Sends the panel what to show for the board on display. */
	private void publish(Cached board, String message)
	{
		Consumer<View> l = viewListener;
		if (l == null)
		{
			return;
		}
		View.State state = state();
		String line = message != null ? message : state != View.State.ON ? null : otherWorld ? NORMAL_WORLDS_ONLY
			: problem;
		View view = new View(state, shownCategory, shownPeriod, state == View.State.ON && board != null ? board.page
			: null, line, board == null ? 0 : board.at);
		SwingUtilities.invokeLater(() -> l.accept(view));
	}
}
