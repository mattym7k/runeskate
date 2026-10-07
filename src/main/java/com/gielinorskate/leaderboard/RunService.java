package com.gielinorskate.leaderboard;

import com.gielinorskate.SkateChat;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.scoring.ScoreClock;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

/**
 * The timed 2-minute run around {@link TimedRun}: started from the panel (or {@code ::skaterun}) while skating,
 * fed the session's landed and bailed combos and its frames, cancelled when skating stops. A finished run gets a
 * chat line, a HUD result banner with a PB marker (the best is kept per account), and is submitted when online is
 * on. Client thread (the HUD reads it while rendering, also on the client thread).
 */
@Singleton
public class RunService
{
	/** How long the result banner shows. */
	public static final float BANNER_SECONDS = 6f;
	/** How long "GO!" shows after the countdown. */
	public static final float GO_SECONDS = 0.8f;

	/** What the panel shows of the timed run. Immutable. */
	public static final class PanelView
	{
		public final boolean running;
		public final long best;

		PanelView(boolean running, long best)
		{
			this.running = running;
			this.best = best;
		}
	}

	private final Client client;
	@Inject
	private SkateChat skateChat;
	private final ScoreClock clock;
	private final LeaderboardService leaderboard;
	private final LeaderboardStore store;
	private final TimedRun run = new TimedRun();

	private String bannerScore;
	private boolean bannerPb;
	private float bannerAt = Float.NEGATIVE_INFINITY;
	/** When the latest run proper started (for "GO!"). */
	private float goAt = Float.NEGATIVE_INFINITY;
	private Consumer<PanelView> listener;

	@Inject
	RunService(Client client, ScoreClock clock, LeaderboardService leaderboard, ConfigManager configManager)
	{
		this.client = client;
		this.clock = clock;
		this.leaderboard = leaderboard;
		this.store = new LeaderboardStore(new ProgressionService.ConfigProfileStore(configManager));
	}

	/** Who shows the run in the panel (the plugin hands it to Swing). */
	public void setListener(Consumer<PanelView> listener)
	{
		this.listener = listener;
		publish();
	}

	/** Starts a run, or cancels the one going (the panel's button). {@code skating}: only while skating. */
	public void toggle(boolean skating)
	{
		if (run.isActive())
		{
			run.cancel();
			chat("Timed run cancelled.");
			publish();
			return;
		}
		start(skating);
	}

	/** Starts the countdown of a new run; says why not when it can't. */
	public void start(boolean skating)
	{
		if (!skating)
		{
			chat("Start skating first: the 2-minute run counts what you land while skating.");
			return;
		}
		if (!run.start(clock.now()))
		{
			chat("A timed run is already going.");
			return;
		}
		bannerAt = Float.NEGATIVE_INFINITY;
		chat("Timed run: everything you land in the next 2 minutes counts. Bails don't end it. 3, 2, 1...");
		publish();
	}

	/** Skating stopped: the run is dropped. */
	public void cancel()
	{
		if (run.cancel())
		{
			chat("Timed run cancelled: skating stopped.");
			publish();
		}
	}

	public void onComboLanded(ComboScorer.Landed combo)
	{
		finished(run.onComboLanded(combo, clock.now()));
	}

	public void onComboBailed()
	{
		finished(run.onComboBailed(clock.now()));
	}

	/** Each skating frame, on the board or on foot. */
	public void tick(boolean comboInProgress)
	{
		if (!run.isActive())
		{
			return;
		}
		float now = clock.now();
		TimedRun.Phase before = run.phase();
		TimedRun.Finished f = run.update(now, comboInProgress);
		if (before == TimedRun.Phase.COUNTDOWN && run.phase() != TimedRun.Phase.COUNTDOWN)
		{
			goAt = now;
		}
		finished(f);
	}

	private void finished(TimedRun.Finished f)
	{
		if (f == null)
		{
			return;
		}
		boolean pb = f.score > 0 && store.recordRun(f.score);
		bannerScore = String.format("%,d", f.score);
		bannerPb = pb;
		bannerAt = clock.now();
		chat("Timed run over: " + bannerScore + " points from " + f.combos + (f.combos == 1 ? " combo" : " combos")
			+ (pb ? ". New personal best!" : ", best " + String.format("%,d", store.runBest()) + "."));
		leaderboard.onRunFinished(f);
		publish();
	}

	private void chat(String message)
	{
		skateChat.send(message);
	}

	private void publish()
	{
		Consumer<PanelView> l = listener;
		if (l != null)
		{
			l.accept(new PanelView(run.isActive(), store.runBest()));
		}
	}

	/** The account changed: the panel's best is the new account's. */
	public void onProfileChanged()
	{
		publish();
	}

	// ---- for the HUD

	public boolean isActive()
	{
		return run.isActive();
	}

	/** "3", "2", "1" during the countdown, "GO!" just after, else null. */
	public String countdownText()
	{
		float now = clock.now();
		if (run.phase() == TimedRun.Phase.COUNTDOWN)
		{
			return Integer.toString(Math.max(1, (int) Math.ceil(run.countdownLeft(now))));
		}
		return now - goAt < GO_SECONDS && run.isActive() ? "GO!" : null;
	}

	/** The run's clock ("1:42"), or null when no run is going. */
	public String timerText()
	{
		if (!run.isActive())
		{
			return null;
		}
		int left = (int) Math.ceil(run.timeLeft(clock.now()));
		return left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60;
	}

	/** Points banked so far in the run. */
	public long runScore()
	{
		return run.score();
	}

	/** The result banner's score, or null when it is not showing. */
	public String bannerScore()
	{
		return bannerScore != null && bannerAge() < BANNER_SECONDS ? bannerScore : null;
	}

	public boolean bannerIsPb()
	{
		return bannerPb;
	}

	public float bannerAge()
	{
		return clock.now() - bannerAt;
	}
}
