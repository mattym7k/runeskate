package com.gielinorskate.duel;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.duel.DuelStateMachine.Cause;
import com.gielinorskate.duel.DuelStateMachine.Outcome;
import com.gielinorskate.duel.DuelStateMachine.Phase;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.party.PartyGhostService;
import com.gielinorskate.party.SkateGhostStop;
import com.gielinorskate.party.SkateGhostUpdate;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.session.SafetyRules;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyMessage;

/**
 * The Skate Duel in the client: feeds the {@link DuelStateMachine} from the party, the skate session and the
 * panel, sends its messages through the party ghosts' shared budget, and turns what happens into chat lines,
 * sounds, hitsplats and the panel and HUD state. Only RuneLite Party messages, our own overlay and our own panel:
 * no real game state, player, actor or menu is touched.
 *
 * <p>Threads: party messages arrive on the party websocket's thread and hop to the client thread, as the ghost
 * code does; the panel's buttons hop there too. The machine is also guarded by this object's lock, so the
 * plugin's shutdown can forfeit from its own thread before the message types are unregistered.
 */
@Slf4j
@Singleton
public class DuelService
{
	/** The panel is refreshed at most this often (it only changes when its view does). */
	private static final float VIEW_INTERVAL = 0.25f;
	/** "FIGHT!" stays up this long after the countdown. */
	private static final float FIGHT_SHOW = 1f;

	private final Client client;
	private final ClientThread clientThread;
	private final GielinorSkateConfig config;
	private final ScoreClock clock;
	private final PartyGhostService ghosts;
	private final SkateFeedback feedback;
	private final DuelStateMachine machine;
	private final DuelRecord record;
	private final DuelSplats splats = new DuelSplats();

	/** Client thread (or under the lock). */
	private boolean skating;
	/** In a PvP area or instance as of the latest frame. */
	private boolean blocked;
	private int lastCount;
	private float fightAt = Float.NEGATIVE_INFINITY;
	private Outcome bannerOutcome;
	private String bannerTitle;
	private String bannerReason;
	private float bannerAt = Float.NEGATIVE_INFINITY;
	/** The plugin is stopping: no chat or sounds from here on. */
	private boolean muted;
	private Consumer<DuelView> viewListener;
	private DuelView lastView;
	private float nextView;
	/** The ending the local skater should play for the latest finished duel, until the session takes it. */
	private DuelEnding pendingEnding = DuelEnding.NONE;

	@Inject
	DuelService(Client client, ClientThread clientThread, GielinorSkateConfig config, ScoreClock clock,
		PartyGhostService ghosts, SkateFeedback feedback, ConfigManager configManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.clock = clock;
		this.ghosts = ghosts;
		this.feedback = feedback;
		this.record = new DuelRecord(new ProgressionService.ConfigProfileStore(configManager));
		SecureRandom random = new SecureRandom();
		this.machine = new DuelStateMachine(new DuelStateMachine.Outbox()
		{
			@Override
			public void send(PartyMessage message)
			{
				ghosts.sendDuel(message, false);
			}

			@Override
			public void sendLastWord(PartyMessage message)
			{
				ghosts.sendDuel(message, true);
			}
		}, new Events(), random::nextLong);
	}

	/** Plugin start. */
	public synchronized void startUp()
	{
		muted = false;
		record.load();
	}

	/**
	 * Plugin shutdown, before the party ghosts close and the message types are unregistered: a duel is forfeit and
	 * a challenge taken back (queued here, sent as the ghost hub closes). Any thread.
	 */
	public synchronized void shutDown()
	{
		muted = true;
		machine.quit(Cause.SHUTDOWN, clock.now());
		pendingEnding = DuelEnding.NONE;
		splats.clear();
		viewListener = null;
	}

	/** Who gets the panel's view (on the client thread; the panel hands it to Swing). */
	public synchronized void setViewListener(Consumer<DuelView> listener)
	{
		viewListener = listener;
		lastView = null;
		nextView = 0f;
	}

	/** The account changed: its win / loss record. Client thread. */
	public synchronized void onProfileChanged()
	{
		record.load();
		lastView = null;
	}

	/** Logging out: a duel is forfeit while the party can still hear it. Client thread. */
	public synchronized void onLogout()
	{
		float now = clock.now();
		machine.quit(Cause.LEFT, now);
		ghosts.flushDuel(now);
	}

	/** Hopping worlds: a duel is fought on one world, so it is forfeit (a challenge taken back). Client thread. */
	public synchronized void onHop()
	{
		float now = clock.now();
		machine.quit(Cause.HOPPED, now);
		ghosts.flushDuel(now);
	}

	/** Once a frame, skating or not. Client thread. */
	public void tick(boolean skatingNow)
	{
		float now = clock.now();
		boolean allowed = config.allowDuelChallenges();
		ghosts.setDuelCapable(allowed);
		boolean blockedNow = blocked(skatingNow);
		synchronized (this)
		{
			try
			{
				skating = skatingNow;
				if (!skatingNow)
				{
					// an ending is only for a skater still skating: never a stale one on the next start
					pendingEnding = DuelEnding.NONE;
				}
				blocked = blockedNow;
				machine.setLocalId(ghosts.localMemberId());
				// turned off: a duel is forfeit, a challenge taken back or declined
				machine.allowed(allowed, now);
				// the other side's silence rule listens to our ghost updates: no sharing, no duel
				machine.sharing(config.shareWithParty(), now);
				machine.tick(now, skatingNow, blockedNow, ghosts.inParty());
				countdownSounds(now);
			}
			catch (RuntimeException e)
			{
				log.warn("Skate Duel tick failed", e);
			}
		}
		// in a PvP area or instance only a forfeit's one last word goes out
		ghosts.flushDuel(now, blockedNow);
		publishView(now, allowed);
	}

	/** In a PvP area or instance (or a PvP world), where nothing goes to the party, as for ghosts. */
	private boolean blocked(boolean skatingNow)
	{
		if (skatingNow && ghosts.isSendBlocked())
		{
			return true;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		WorldView wv = client.getTopLevelWorldView();
		if (wv != null && wv.isInstance())
		{
			return true;
		}
		return SafetyRules.inPvpArea(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1,
			client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1)
			|| SafetyRules.isOptInPvpWorld(client.getWorldType());
	}

	private void countdownSounds(float now)
	{
		if (machine.phase() != Phase.COUNTDOWN)
		{
			lastCount = 0;
			return;
		}
		int count = (int) Math.ceil(DuelStateMachine.COUNTDOWN - machine.phaseAge(now));
		if (count != lastCount && count > 0)
		{
			lastCount = count;
			sound(SkateFeedback.DuelSound.COUNT, now);
		}
	}

	// ---- From the skate session (client thread)

	/** A combo landed with {@code value} banked. */
	public synchronized void onComboLanded(int value, int trickCount)
	{
		try
		{
			machine.comboLanded(value, trickCount, clock.now());
		}
		catch (RuntimeException e)
		{
			log.warn("Skate Duel hit failed", e);
		}
	}

	/** In a duel's countdown or fight: a bail always knocks the skater off and R is off. Client thread. */
	public synchronized boolean isDueling()
	{
		Phase p = machine.phase();
		return p == Phase.COUNTDOWN || p == Phase.FIGHT;
	}

	/**
	 * The cosmetic ending of the duel that just finished (a tantrum or a celebration), once: NONE when there is none
	 * or it was already taken. Client thread.
	 */
	public synchronized DuelEnding takeEnding()
	{
		DuelEnding e = pendingEnding;
		pendingEnding = DuelEnding.NONE;
		return e;
	}

	/** The local skater bailed. */
	public synchronized void onBail()
	{
		try
		{
			machine.bail(clock.now());
		}
		catch (RuntimeException e)
		{
			log.warn("Skate Duel bail failed", e);
		}
	}

	// ---- From the panel (client thread)

	public synchronized void challenge(long memberId)
	{
		if (!config.allowDuelChallenges() || !config.shareWithParty() || !skating || blocked(skating))
		{
			return;
		}
		machine.setLocalId(ghosts.localMemberId());
		if (ghosts.duelCandidates(client.getWorld(), clock.now()).contains(memberId))
		{
			machine.challenge(memberId, clock.now());
		}
		lastView = null;
	}

	public synchronized void accept()
	{
		if (!skating)
		{
			chat("Start skating to accept the Skate Duel.");
			return;
		}
		if (blocked(skating))
		{
			chat("No Skate Duels in PvP areas or instances.");
			return;
		}
		if (!config.shareWithParty())
		{
			chat("Turn on \"Share my skater with my party\" to accept the Skate Duel.");
			return;
		}
		machine.accept(clock.now());
		lastView = null;
	}

	public synchronized void decline()
	{
		machine.decline(clock.now());
		lastView = null;
	}

	public synchronized void withdraw()
	{
		machine.withdraw(clock.now());
		lastView = null;
	}

	// ---- From the party (any thread: hop to the client thread)

	private boolean isLocal(long memberId)
	{
		return memberId == ghosts.localMemberId();
	}

	@Subscribe
	public void onSkateDuelChallenge(SkateDuelChallenge m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> onParty(() -> machine.onChallenge(id, m, clock.now(),
			config.allowDuelChallenges() && config.shareWithParty())));
	}

	@Subscribe
	public void onSkateDuelReply(SkateDuelReply m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> onParty(() -> machine.onReply(id, m, clock.now())));
	}

	@Subscribe
	public void onSkateDuelHit(SkateDuelHit m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> onParty(() -> machine.onHit(id, m, clock.now())));
	}

	@Subscribe
	public void onSkateDuelEnd(SkateDuelEnd m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> onParty(() -> machine.onEnd(id, m, clock.now())));
	}

	/** Ghost updates and stops say the member is still there (the 20 s silence rule). */
	@Subscribe
	public void onSkateGhostUpdate(SkateGhostUpdate m)
	{
		heard(m.getMemberId());
	}

	@Subscribe
	public void onSkateGhostStop(SkateGhostStop m)
	{
		heard(m.getMemberId());
	}

	private void heard(long id)
	{
		if (!isLocal(id))
		{
			clientThread.invoke(() -> onParty(() -> machine.heard(id, clock.now())));
		}
	}

	@Subscribe
	public void onUserPart(UserPart e)
	{
		long id = e.getMemberId();
		clientThread.invoke(() -> onParty(() -> machine.memberLeft(id, clock.now())));
	}

	@Subscribe
	public void onPartyChanged(PartyChanged e)
	{
		clientThread.invoke(() -> onParty(() -> machine.leftParty(clock.now())));
	}

	private synchronized void onParty(Runnable r)
	{
		try
		{
			machine.setLocalId(ghosts.localMemberId());
			r.run();
		}
		catch (RuntimeException e)
		{
			log.warn("Skate Duel message failed", e);
		}
	}

	// ---- Events: chat, sounds, hitsplats, record (client thread, or muted at shutdown)

	private final class Events implements DuelStateMachine.Listener
	{
		@Override
		public void challenged(long fromId)
		{
			chat(DuelLines.challenged(chatName(fromId)));
			sound(SkateFeedback.DuelSound.CHALLENGE, clock.now());
			lastView = null;
		}

		@Override
		public void challengeSent(long toId)
		{
			chat(DuelLines.challengeSent(chatName(toId)));
		}

		@Override
		public void declined(long byId)
		{
			chat(DuelLines.declined(chatName(byId)));
			lastView = null;
		}

		@Override
		public void expired(long otherId, boolean mine)
		{
			chat(DuelLines.expired(chatName(otherId), mine));
			lastView = null;
		}

		@Override
		public void countdown(long opponentId)
		{
			splats.clear();
			bannerOutcome = null;
			lastCount = 0;
			chat(DuelLines.countdown(chatName(opponentId)));
			lastView = null;
		}

		@Override
		public void fight()
		{
			fightAt = clock.now();
			sound(SkateFeedback.DuelSound.FIGHT, fightAt);
		}

		@Override
		public void hit(boolean onMe, int damage, boolean selfInflicted)
		{
			float now = clock.now();
			splats.add(onMe, damage, now);
			sound(SkateFeedback.DuelSound.HIT, now);
		}

		@Override
		public void voided(Outcome was, long opponentId)
		{
			record.unrecord(was);
			bannerOutcome = null;
			lastView = null;
			chat(DuelLines.over(Outcome.CANCELLED, Cause.BOTH_QUIT, chatName(opponentId)));
		}

		@Override
		public void over(Outcome outcome, Cause cause, long opponentId)
		{
			// counted even at shutdown: a forfeit is a loss
			record.record(outcome);
			lastView = null;
			if (muted)
			{
				return;
			}
			// a tantrum or a cheer for the skate session to play (it takes it next frame)
			pendingEnding = DuelEnding.pick(outcome, cause, config.duelEndings(), blocked);
			String name = name(opponentId);
			chat(DuelLines.over(outcome, cause, chatName(opponentId)));
			if (outcome == Outcome.CANCELLED)
			{
				return;
			}
			float now = clock.now();
			bannerOutcome = outcome;
			bannerTitle = DuelLines.banner(outcome, name);
			bannerReason = DuelLines.reason(outcome, cause, name);
			bannerAt = now;
			sound(outcome == Outcome.WIN ? SkateFeedback.DuelSound.WIN
				: outcome == Outcome.LOSS ? SkateFeedback.DuelSound.LOSE : SkateFeedback.DuelSound.FIGHT, now);
		}
	}

	/** A member's name for a chat line: tags in it are shown as text, not run. */
	private String chatName(long memberId)
	{
		return DuelLines.chatName(ghosts.memberName(memberId));
	}

	private String name(long memberId)
	{
		return DuelLines.name(ghosts.memberName(memberId));
	}

	private void chat(String line)
	{
		if (!muted)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
		}
	}

	private void sound(SkateFeedback.DuelSound s, float now)
	{
		if (!muted)
		{
			feedback.playDuelSound(s, now);
		}
	}

	// ---- Panel

	private void publishView(float now, boolean allowed)
	{
		Consumer<DuelView> listener;
		DuelView view;
		synchronized (this)
		{
			listener = viewListener;
			if (listener == null || (now < nextView && lastView != null))
			{
				return;
			}
			nextView = now + VIEW_INTERVAL;
			List<DuelView.Member> members = new ArrayList<>();
			for (long id : ghosts.duelCandidates(client.getWorld(), now))
			{
				members.add(new DuelView.Member(id, ghosts.memberName(id)));
			}
			Phase phase = machine.phase();
			String result = phase == Phase.OVER && machine.outcome() != null && machine.cause() != null
				? DuelLines.over(machine.outcome(), machine.cause(), ghosts.memberName(machine.opponentId())) : null;
			view = new DuelView(allowed, skating, ghosts.inParty(), members, phase,
				phase == Phase.IDLE ? null : ghosts.memberName(machine.opponentId()), machine.myHp(), machine.oppHp(),
				record.wins(), record.losses(), result, blocked, config.shareWithParty());
			if (view.equals(lastView))
			{
				return;
			}
			lastView = view;
		}
		listener.accept(view);
	}

	// ---- HUD

	/** What the duel overlay draws this frame. Immutable. */
	public static final class Hud
	{
		public final Phase phase;
		/** Show the two HP bars (a duel on, or its result up). */
		public final boolean bars;
		public final String myName;
		public final String oppName;
		public final int myHp;
		public final int oppHp;
		public final long opponentId;
		/** "3", "2", "1", "FIGHT!" or null. */
		public final String countdown;
		/** A line under the bars (a challenge to answer, get back on...), or null. */
		public final String prompt;
		public final Outcome bannerOutcome;
		public final String bannerTitle;
		public final String bannerReason;
		public final float bannerAlpha;
		public final List<DuelSplats.Splat> mySplats;
		public final List<DuelSplats.Splat> oppSplats;
		public final float now;

		Hud(Phase phase, boolean bars, String myName, String oppName, int myHp, int oppHp, long opponentId,
			String countdown, String prompt, Outcome bannerOutcome, String bannerTitle, String bannerReason,
			float bannerAlpha, List<DuelSplats.Splat> mySplats, List<DuelSplats.Splat> oppSplats, float now)
		{
			this.phase = phase;
			this.bars = bars;
			this.myName = myName;
			this.oppName = oppName;
			this.myHp = myHp;
			this.oppHp = oppHp;
			this.opponentId = opponentId;
			this.countdown = countdown;
			this.prompt = prompt;
			this.bannerOutcome = bannerOutcome;
			this.bannerTitle = bannerTitle;
			this.bannerReason = bannerReason;
			this.bannerAlpha = bannerAlpha;
			this.mySplats = mySplats;
			this.oppSplats = oppSplats;
			this.now = now;
		}
	}

	/** This frame's HUD, or null when there is nothing to draw. Client thread. */
	public synchronized Hud hud()
	{
		float now = clock.now();
		Phase phase = machine.phase();
		float bannerAge = now - bannerAt;
		boolean banner = bannerOutcome != null && bannerAge < DuelStateMachine.RESULT_SECONDS;
		List<DuelSplats.Splat> mine = splats.live(true, now);
		List<DuelSplats.Splat> theirs = splats.live(false, now);
		boolean bars = phase == Phase.COUNTDOWN || phase == Phase.FIGHT || (phase == Phase.OVER && banner);
		String prompt = prompt(phase, now);
		if (!bars && !banner && prompt == null && mine.isEmpty() && theirs.isEmpty())
		{
			return null;
		}
		String countdown = null;
		if (phase == Phase.COUNTDOWN)
		{
			countdown = Integer.toString(Math.max(1, (int) Math.ceil(DuelStateMachine.COUNTDOWN - machine.phaseAge(now))));
		}
		else if (phase == Phase.FIGHT && now - fightAt < FIGHT_SHOW)
		{
			countdown = "FIGHT!";
		}
		Player me = client.getLocalPlayer();
		String myName = me == null || me.getName() == null ? "You" : me.getName();
		float alpha = banner ? Math.min(1f, (DuelStateMachine.RESULT_SECONDS - bannerAge) / 1f) : 0f;
		return new Hud(phase, bars, myName, name(machine.opponentId()), machine.myHp(), machine.oppHp(),
			machine.opponentId(), countdown, prompt, banner ? bannerOutcome : null, bannerTitle, bannerReason, alpha,
			mine.isEmpty() ? Collections.emptyList() : mine, theirs.isEmpty() ? Collections.emptyList() : theirs, now);
	}

	private String prompt(Phase phase, float now)
	{
		switch (phase)
		{
			case CHALLENGED:
				return name(machine.opponentId()) + " challenges you to a Skate Duel! Answer in the RuneSkate panel ("
					+ (int) Math.ceil(machine.challengeLeft(now)) + "s)";
			case CHALLENGING:
				return "Skate Duel: waiting for " + name(machine.opponentId()) + " ("
					+ (int) Math.ceil(machine.challengeLeft(now)) + "s)";
			case COUNTDOWN:
			case FIGHT:
				float left = machine.restartLeft(now);
				if (!Float.isNaN(left))
				{
					return "Start skating again within " + (int) Math.ceil(left) + "s or forfeit!";
				}
				if (machine.isSelfKo())
				{
					return "You're down...";
				}
				return null;
			default:
				return null;
		}
	}
}
