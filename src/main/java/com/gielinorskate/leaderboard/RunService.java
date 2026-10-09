package com.gielinorskate.leaderboard;

import com.gielinorskate.SkateChat;
import com.gielinorskate.Text;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.scoring.ScoreClock;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
* The timed 2-minute run around {@link TimedRun}: started from the panel while skating, fed the session's landed
* and bailed combos and its frames, cancelled when skating stops. A finished run gets a chat line, a HUD result
* banner with a PB marker (the best is kept per account), and is submitted when online is on. Client thread (the
* HUD reads it while rendering, also on the client thread).
*/
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RunService
{
/** How long the result banner shows. */
public static final float BANNER_SECONDS = 6f;

/** What the panel shows of the timed run. Immutable. */
@AllArgsConstructor
public static final class PanelView
{
public final boolean running;
public final long best;
}

@Inject
private SkateChat skateChat;
private final ScoreClock clock;
private final LeaderboardService leaderboard;
private final TimedRun run = new TimedRun();

private String bannerScore;
private boolean bannerPb;
private float bannerAt = Float.NEGATIVE_INFINITY;
/** When the latest run proper started (for "GO!"). */
private float goAt = Float.NEGATIVE_INFINITY;
private Consumer<PanelView> listener;

/** Who shows the run in the panel (the plugin hands it to Swing). */
public void setListener(Consumer<PanelView> listener)
{
this.listener = listener;
publish();
}

/**
* Cancels the run going, or starts the countdown of a new one (the panel's button). {@code skating}: a run
* starts only while skating, else the player is told why not.
*/
public void toggle(boolean skating)
{
if (run.cancel())
chat("Timed run cancelled.");
else if (!skating)
{
chat(Text.get("rs.first"));
return;
}
else
{
run.start(clock.now());
bannerAt = Float.NEGATIVE_INFINITY;
chat(Text.get("rs.start"));
}
publish();
}

/** Skating stopped: the run is dropped. */
public void cancel()
{
if (run.cancel())
{
chat(Text.get("rs.stopped"));
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
return;
float now = clock.now();
boolean counting = run.phase() == TimedRun.Phase.COUNTDOWN;
TimedRun.Finished f = run.update(now, comboInProgress);
if (counting && run.phase() != TimedRun.Phase.COUNTDOWN)
goAt = now;
finished(f);
}

private void finished(TimedRun.Finished f)
{
if (f == null)
return;
bannerPb = f.score > 0 && leaderboard.store().recordRun(f.score);
bannerScore = String.format("%,d", f.score);
bannerAt = clock.now();
chat(Text.get("rs.over." + bannerPb, bannerScore, f.combos, f.combos == 1 ? "" : "s",
String.format("%,d", leaderboard.store().runBest())));
leaderboard.onRunFinished(f);
publish();
}

private void chat(String message)
{
skateChat.send(message);
}

private void publish()
{
if (listener != null)
listener.accept(new PanelView(run.isActive(), leaderboard.store().runBest()));
}

/** The account changed: the panel's best is the new account's. */
public void onProfileChanged()
{
publish();
}

// ---- for the HUD

/** "3", "2", "1" during the countdown, "GO!" for 0.8 s just after, else null. */
public String countdownText()
{
float now = clock.now();
if (run.phase() == TimedRun.Phase.COUNTDOWN)
return Integer.toString(Math.max(1, (int) Math.ceil(run.countdownLeft(now))));
return now - goAt < 0.8f && run.isActive() ? "GO!" : null;
}

/** The run's clock ("1:42"), or null when no run is going. */
public String timerText()
{
int left = (int) Math.ceil(run.timeLeft(clock.now()));
return run.isActive() ? left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60 : null;
}

/** Points banked so far in the run. */
public long runScore()
{
return run.score();
}

/** The result banner's score, or null when it is not showing. */
public String bannerScore()
{
return bannerAge() < BANNER_SECONDS ? bannerScore : null;
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
