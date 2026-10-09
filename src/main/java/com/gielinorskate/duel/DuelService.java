package com.gielinorskate.duel;

import com.gielinorskate.*;
import com.gielinorskate.duel.DuelStateMachine.*;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.feedback.SkateFeedback.DuelSound;
import com.gielinorskate.party.*;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.session.SafetyRules;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyMemberMessage;

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
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DuelService
{
/** The panel is refreshed at most this often (it only changes when its view does). */
private static final float VIEW_INTERVAL = 0.25f;

private final Client client;
@Inject
private SkateChat skateChat;
private final ClientThread clientThread;
private final GielinorSkateConfig config;
private final ScoreClock clock;
// injected after construction: the machine below sends through it
@Inject
private PartyGhostService ghosts;
private final SkateFeedback feedback;
private final ConfigManager configManager;
private final DuelStateMachine machine = new DuelStateMachine((m, lastWord) -> ghosts.sendDuel(m, lastWord),
new Events(), new SecureRandom()::nextLong);
/** Made on first use, once the config manager is in. */
@Getter(value = AccessLevel.PRIVATE, lazy = true)
private final DuelRecord record = new DuelRecord(new ProgressionService.ConfigProfileStore(configManager));
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
/** A failed tick was logged (only the first one is). */
private boolean tickFailureLogged;
private Consumer<DuelView> viewListener;
private DuelView lastView;
private float nextView;
/** The ending the local skater should play for the latest finished duel, until the session takes it. */
private DuelEnding pendingEnding = DuelEnding.NONE;

/** Plugin start. */
public synchronized void startUp()
{
muted = false;
getRecord().load();
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
getRecord().load();
lastView = null;
}

/** Logging out: a duel is forfeit while the party can still hear it. Client thread. */
public void onLogout()
{
quitNow(Cause.LEFT);
}

/** Hopping worlds: a duel is fought on one world, so it is forfeit (a challenge taken back). Client thread. */
public void onHop()
{
quitNow(Cause.HOPPED);
}

private synchronized void quitNow(Cause why)
{
float now = clock.now();
machine.quit(why, now);
ghosts.flushDuel(now, blocked);
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
// an ending is only for a skater still skating: never a stale one on the next start
pendingEnding = DuelEnding.NONE;
blocked = blockedNow;
machine.setLocalId(ghosts.localMemberId());
// turned off: a duel is forfeit, a challenge taken back or declined
machine.allowed(allowed, now);
// the other side's silence rule listens to our ghost updates: no sharing, no duel
machine.sharing(config.shareWithParty(), now);
machine.tick(now, skatingNow, blockedNow, ghosts.inParty());
int count = machine.phase() == Phase.COUNTDOWN ? countdown(now) : 0;
if (count != lastCount && count > 0)
sound(DuelSound.COUNT, now);
lastCount = count;
}
catch (RuntimeException e)
{
// once: the tick runs every frame, so a lasting fault would flood the log
if (!tickFailureLogged)
{
tickFailureLogged = true;
log.warn("Skate Duel tick failed", e);
}
}
}
// in a PvP area or instance only a forfeit's one last word goes out
ghosts.flushDuel(now, blockedNow);
publishView(now, allowed);
}

/** The countdown's second showing: 3, 2, 1 (0 once it ran out). */
private int countdown(float now)
{
return (int) Math.ceil(DuelStateMachine.COUNTDOWN - machine.phaseAge(now));
}

/** In a PvP area or instance (or a PvP world), where nothing goes to the party, as for ghosts. */
private boolean blocked(boolean skatingNow)
{
if (skatingNow && ghosts.isSendBlocked())
return true;
if (client.getGameState() != GameState.LOGGED_IN)
return false;
WorldView wv = client.getTopLevelWorldView();
return wv != null && wv.isInstance()
|| SafetyRules.inPvpArea(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1,
client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1)
|| SafetyRules.isOptInPvpWorld(client.getWorldType());
}

// ---- From the skate session (client thread)

/** A combo landed with {@code value} banked. */
public synchronized void onComboLanded(int value, int trickCount)
{
guarded(() -> machine.comboLanded(value, trickCount, clock.now()));
}

/** In a duel's countdown or fight: a bail always knocks the skater off and R is off. Client thread. */
public synchronized boolean isDueling()
{
return machine.inDuel();
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
guarded(() -> machine.bail(clock.now()));
}

// ---- From the panel (client thread)

public synchronized void challenge(long memberId)
{
// the machine itself refuses while duels or sharing are off or in a PvP area or instance
if (skating && ghosts.duelCandidates(client.getWorld(), clock.now()).contains(memberId))
{
machine.setLocalId(ghosts.localMemberId());
machine.challenge(memberId, clock.now());
lastView = null;
}
}

public synchronized void accept()
{
if (!skating)
chat(Text.get("ds.accept"));
else if (blocked(skating))
chat(Text.get("dv.idle.blocked"));
else if (!config.shareWithParty())
chat(Text.get("ds.sharing"));
else
{
machine.accept(clock.now());
lastView = null;
}
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

@Subscribe
public void onSkateDuelChallenge(SkateDuelChallenge m)
{
fromParty(m, id -> machine.onChallenge(id, m, clock.now(),
config.allowDuelChallenges() && config.shareWithParty()));
}

@Subscribe
public void onSkateDuelReply(SkateDuelReply m)
{
fromParty(m, id -> machine.onReply(id, m, clock.now()));
}

@Subscribe
public void onSkateDuelHit(SkateDuelHit m)
{
fromParty(m, id -> machine.onHit(id, m, clock.now()));
}

@Subscribe
public void onSkateDuelEnd(SkateDuelEnd m)
{
fromParty(m, id -> machine.onEnd(id, m, clock.now()));
}

/** Ghost updates and stops say the member is still there (the 20 s silence rule). */
@Subscribe
public void onSkateGhostUpdate(SkateGhostUpdate m)
{
fromParty(m, id -> machine.heard(id, clock.now()));
}

@Subscribe
public void onSkateGhostStop(SkateGhostStop m)
{
fromParty(m, id -> machine.heard(id, clock.now()));
}

@Subscribe
public void onUserPart(UserPart e)
{
long id = e.getMemberId();
onClientThread(() -> machine.memberLeft(id, clock.now()));
}

@Subscribe
public void onPartyChanged(PartyChanged e)
{
onClientThread(() -> machine.leftParty(clock.now()));
}

/** A message from another member (never our own echo), handed to the machine on the client thread. */
private void fromParty(PartyMemberMessage m, LongConsumer action)
{
long id = m.getMemberId();
if (id != ghosts.localMemberId())
onClientThread(() -> action.accept(id));
}

private void onClientThread(Runnable r)
{
clientThread.invoke(() ->
{
synchronized (this)
{
machine.setLocalId(ghosts.localMemberId());
guarded(r);
}
});
}

private void guarded(Runnable r)
{
try
{
r.run();
}
catch (RuntimeException e)
{
log.warn("Skate Duel update failed", e);
}
}

// ---- Events: chat, sounds, hitsplats, record (client thread, or muted at shutdown)

private final class Events implements DuelStateMachine.Listener
{
@Override
public void challenged(long fromId)
{
chat(DuelLines.challenged(chatName(fromId)));
sound(DuelSound.CHALLENGE, clock.now());
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
sound(DuelSound.FIGHT, fightAt);
}

@Override
public void hit(boolean onMe, int damage, boolean selfInflicted)
{
float now = clock.now();
splats.add(onMe, damage, now);
sound(DuelSound.HIT, now);
}

@Override
public void voided(Outcome was, long opponentId)
{
getRecord().add(was, -1);
bannerOutcome = null;
lastView = null;
chat(DuelLines.over(Outcome.CANCELLED, Cause.BOTH_QUIT, chatName(opponentId)));
}

@Override
public void over(Outcome outcome, Cause cause, long opponentId)
{
// counted even at shutdown: a forfeit is a loss
getRecord().add(outcome, 1);
lastView = null;
if (muted)
return;
// a tantrum or a cheer for the skate session to play (it takes it next frame)
pendingEnding = DuelEnding.pick(outcome, cause, config.duelEndings(), blocked);
String name = name(opponentId);
chat(DuelLines.over(outcome, cause, chatName(opponentId)));
if (outcome == Outcome.CANCELLED)
return;
bannerOutcome = outcome;
bannerTitle = DuelLines.banner(outcome, name);
bannerReason = DuelLines.reason(outcome, cause, name);
bannerAt = clock.now();
sound(outcome == Outcome.WIN ? DuelSound.WIN : outcome == Outcome.LOSS ? DuelSound.LOSE : DuelSound.FIGHT,
bannerAt);
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
skateChat.send(line);
}

private void sound(DuelSound s, float now)
{
if (!muted)
feedback.playDuelSound(s, now);
}

// ---- Panel

private void publishView(float now, boolean allowed)
{
Consumer<DuelView> listener;
DuelView view;
synchronized (this)
{
listener = viewListener;
if (listener == null || now < nextView && lastView != null)
return;
nextView = now + VIEW_INTERVAL;
List<DuelView.Member> members = ghosts.duelCandidates(client.getWorld(), now).stream()
.map(id -> new DuelView.Member(id, ghosts.memberName(id))).collect(Collectors.toList());
Phase phase = machine.phase();
String opponent = ghosts.memberName(machine.opponentId());
String result = phase == Phase.OVER && machine.outcome() != null && machine.cause() != null
? DuelLines.over(machine.outcome(), machine.cause(), opponent) : null;
view = new DuelView(allowed, skating, ghosts.inParty(), members, phase,
DuelLines.name(phase == Phase.IDLE ? null : opponent),
machine.myHp(), machine.oppHp(), getRecord().getWins(), getRecord().getLosses(), result, blocked,
config.shareWithParty());
if (view.equals(lastView))
return;
lastView = view;
}
listener.accept(view);
}

// ---- HUD

/** What the duel overlay draws this frame. Immutable. */
@AllArgsConstructor
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
}

/** This frame's HUD, or null when there is nothing to draw. Client thread. */
public synchronized Hud hud()
{
float now = clock.now();
Phase phase = machine.phase();
float bannerLeft = DuelStateMachine.RESULT_SECONDS - (now - bannerAt);
boolean banner = bannerOutcome != null && bannerLeft > 0;
List<DuelSplats.Splat> mine = splats.live(true, now);
List<DuelSplats.Splat> theirs = splats.live(false, now);
boolean bars = isDueling() || phase == Phase.OVER && banner;
String prompt = prompt(phase, now);
if (!bars && !banner && prompt == null && mine.isEmpty() && theirs.isEmpty())
return null;
// "FIGHT!" stays up 1 s after the countdown
String countdown = phase == Phase.COUNTDOWN ? Integer.toString(Math.max(1, countdown(now)))
: phase == Phase.FIGHT && now - fightAt < 1f ? "FIGHT!" : null;
Player me = client.getLocalPlayer();
String myName = me == null || me.getName() == null ? "You" : me.getName();
return new Hud(phase, bars, myName, name(machine.opponentId()), machine.myHp(), machine.oppHp(),
machine.opponentId(), countdown, prompt, banner ? bannerOutcome : null, bannerTitle, bannerReason,
banner ? Math.min(1f, bannerLeft) : 0f, mine, theirs, now);
}

private String prompt(Phase phase, float now)
{
String opponent = name(machine.opponentId());
int challengeLeft = (int) Math.ceil(machine.challengeLeft(now));
switch (phase)
{
case CHALLENGED:
return Text.get("ds.challenged", challengeLeft, opponent);
case CHALLENGING:
return Text.get("ds.waiting", challengeLeft, opponent);
case COUNTDOWN:
case FIGHT:
float left = machine.restartLeft(now);
return !Float.isNaN(left) ? Text.get("ds.restart", (int) Math.ceil(left))
: machine.selfKo() ? "You're down..." : null;
default:
return null;
}
}
}
