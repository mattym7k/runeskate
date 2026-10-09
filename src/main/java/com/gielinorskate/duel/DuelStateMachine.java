package com.gielinorskate.duel;

import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.runelite.client.party.messages.PartyMessage;

/**
* One client's side of a 1v1 Skate Duel, without the client: challenge, accept / decline, expiry, the 3-2-1
* countdown, the fight, KO, forfeits and draws. Nothing here touches real game state: "Skate HP" lives only in
* this object.
*
* <p>Authority: each client deals its own hits (landed combos, through {@link HitCooldown}) and its own bails, and
* decides its own KO. A client whose HP reaches 0 sends End KO; the other, after applying everything the KO'd one
* sent before it (per-sender seq order), answers End SURVIVED and wins. Two KOs that cross on the way (both went
* down before seeing the other's KO: the same moment, as far as the party can tell) make a draw on both sides.
*
* <p>Quitting (stopping skating, leaving, logging out, hopping, a PvP area or instance, a setting turned off) is a
* forfeit in the fight: a loss here, a win there. In the 3 s countdown, before any hit, it calls the duel off
* instead (End CANCEL): no result on either side. Two quits that cross (each side forfeited or called off before
* hearing of the other's) count for nobody: a loss already counted here is taken back ({@link Listener#voided}).
* A side that calls the duel alone (the other went silent or left) tells the other (End TIMEOUT or FORFEIT), and a
* message for a duel already over here is answered once, so both always end with matching results.
*
* <p>Single-threaded (the client thread). Times are seconds on one monotonic clock. The state getters (phase(),
* myHp()...) are for the HUD and panel.
*/
@RequiredArgsConstructor
@Accessors(fluent = true)
public final class DuelStateMachine
{
public static final int START_HP = 99;
public static final int BAIL_DAMAGE = 4;
public static final float CHALLENGE_EXPIRY = 30f;
/** The challenger waits a little longer than the challenged, for a reply still on its way. */
static final float REPLY_GRACE = 2f;
public static final float COUNTDOWN = 3f;
/** Stopped skating: back on within this long or the duel is forfeit. */
public static final float RESTART_GRACE = 10f;
/** Nothing at all from the opponent for this long: they forfeit. */
public static final float SILENCE = 20f;
/** Knocked out with no answer (SURVIVED or a KO) for this long: the duel is lost. */
public static final float KO_WAIT = 10f;
/** The result stays up this long. */
public static final float RESULT_SECONDS = 8f;

public enum Phase
{
IDLE, CHALLENGING, CHALLENGED, COUNTDOWN, FIGHT, OVER
}

/** {@link SkateDuelEnd#reason}. */
public enum EndReason
{
KO, SURVIVED, FORFEIT, CANCEL,
/** The sender stopped hearing from the receiver (silence, or it left the party) and won: the receiver lost. */
TIMEOUT
}

public enum Outcome
{
WIN, LOSS, DRAW, CANCELLED
}

/** Why a duel or challenge ended. */
public enum Cause
{
KO,
/** This client stopped skating for {@link #RESTART_GRACE} s. */
STOPPED_SKATING,
/** This client left the party or logged out. */
LEFT,
/** This client went into a PvP area or instance. */
BLOCKED,
/** This client hopped worlds (a duel is fought on one world). */
HOPPED,
/** This client turned "Allow duel challenges" off. */
DUELS_OFF,
/** This client turned "Share my skater with my party" off (duels need it). */
SHARING_OFF,
/** The plugin stopped. */
SHUTDOWN,
OPPONENT_FORFEIT,
OPPONENT_SILENT,
OPPONENT_LEFT,
/** The opponent stopped hearing from this client and called the duel. */
TIMED_OUT,
/** The challenge was withdrawn, or accepted too late. */
CANCELLED,
/** Both sides quit (forfeit or called off) before hearing of the other's: it counts for nobody. */
BOTH_QUIT
}

/** Where duel messages go: the party, through the shared budget. */
public interface Outbox
{
/**
* {@code lastWord}: a duel's last word (this client's forfeit), the one duel message that may still go out
* from a PvP area or instance, as a ghost's stop does on entering one.
*/
void send(PartyMessage message, boolean lastWord);
}

/** What happened, for chat, sounds, hitsplats and the panel. Called on the machine's thread. */
public interface Listener
{
void challenged(long fromId);

void challengeSent(long toId);

void declined(long byId);

/** A challenge ran out; {@code mine}: the local client made it. */
void expired(long otherId, boolean mine);

void countdown(long opponentId);

void fight();

/** Damage landed: {@code onMe} to the local skater (else the opponent), {@code selfInflicted} from a bail. */
void hit(boolean onMe, int damage, boolean selfInflicted);

void over(Outcome outcome, Cause cause, long opponentId);

/**
* A finished duel's {@code was} result (a LOSS by quitting) no longer counts: the other side quit too before
* hearing of it. The result is now CANCELLED, {@link Cause#BOTH_QUIT}.
*/
void voided(Outcome was, long opponentId);
}

private final Outbox out;
private final Listener listener;
private final LongSupplier ids;
private final SeqInbox<PartyMessage> inbox = new SeqInbox<>();
final HitCooldown cooldown = new HitCooldown();

/** This client's party member ID; 0 when not in a party. */
private long localId;
@Getter
private Phase phase = Phase.IDLE;
private float phaseStart;
/** The current (or latest) duel; 0 before any. */
private long duelId;
/** The opponent or challenge partner; meaningful outside IDLE (and kept for the result). */
@Getter
private long opponentId;
private int mySeq;
@Getter
private int myHp = START_HP;
@Getter
private int oppHp = START_HP;
/** This client went down and sent its KO; waiting for the other's answer. */
@Getter
private boolean selfKo;
private float lastHeard;
private float notSkatingSince = Float.NaN;
/** The latest finished duel's result, or null. */
@Getter
private Outcome outcome;
@Getter
private Cause cause;
/** The last orphan duel answered with a cancel, so a burst of its hits is answered once. */
private long cancelledOrphan;
/** In a PvP area or instance (as of the latest tick): nothing is sent but a forfeit's one last word. */
private boolean blocked;
/** "Share my skater with my party" is on: duels need it (the 20 s silence rule hears ghost updates). */
private boolean sharing = true;
/** "Allow duel challenges" is on. */
private boolean allowed = true;
/** When this client went down ({@link #selfKo}). */
private float koAt;
/** The latest duel that ended after its countdown started (0 before any), for late messages about it. */
private long finishedDuel;
/** The finished duel already answered a late message, so a burst of them is answered once. */
private long answeredDuel;

public void setLocalId(long localId)
{
this.localId = localId;
}

// ---- Local actions

/** Challenges {@code memberId}; false when a duel or challenge is already going. */
public boolean challenge(long memberId, float now)
{
if (!free() || blocked || !sharing || !allowed || localId == 0 || memberId == localId || memberId == 0)
return false;
long id;
do
{
id = ids.getAsLong();
}
while (id == 0 || id == duelId);
duelId = id;
opponentId = memberId;
mySeq = 1;
enter(Phase.CHALLENGING, now);
post(new SkateDuelChallenge(duelId, mySeq, memberId), false);
listener.challengeSent(memberId);
return true;
}

/** Takes back a challenge not yet answered. */
public void withdraw(float now)
{
if (phase == Phase.CHALLENGING)
{
post(end(EndReason.CANCEL), true);
enter(Phase.IDLE, now);
}
}

/** Accepts the challenge received: the countdown starts. */
public boolean accept(float now)
{
if (phase != Phase.CHALLENGED || blocked || !sharing)
return false;
mySeq = 1;
post(new SkateDuelReply(duelId, mySeq, opponentId, true), false);
startCountdown(now);
return true;
}

public void decline(float now)
{
if (phase == Phase.CHALLENGED)
{
post(new SkateDuelReply(duelId, 1, opponentId, false), true);
enter(Phase.IDLE, now);
}
}

/** A combo landed with {@code value} banked: a hit on the opponent, after the cooldown. */
public void comboLanded(int value, int trickCount, float now)
{
int damage = DuelDamage.of(value);
if (phase == Phase.FIGHT && !selfKo && damage > 0)
{
cooldown.offer(damage, value, trickCount);
releaseHits(now);
}
}

/** The local skater bailed: {@link #BAIL_DAMAGE} to itself, at once. */
public void bail(float now)
{
if (phase != Phase.FIGHT || selfKo)
return;
post(new SkateDuelHit(duelId, ++mySeq, localId, BAIL_DAMAGE, 0, 0, true), false);
myHp = Math.max(0, myHp - BAIL_DAMAGE);
listener.hit(true, BAIL_DAMAGE, true);
if (myHp <= 0)
knockedOut(now);
}

/** "Allow duel challenges" now on or off: turned off, a duel is forfeit and a challenge taken back. */
public void allowed(boolean on, float now)
{
allowed = on;
if (!on)
quit(Cause.DUELS_OFF, now);
}

/**
* "Share my skater with my party" now on or off. Duels need it, as the other side's silence rule listens to
* ghost updates: turned off, a duel is forfeit and a challenge taken back.
*/
public void sharing(boolean on, float now)
{
sharing = on;
if (!on)
quit(Cause.SHARING_OFF, now);
}

/**
* Once a frame (skating or not).
*
* @param skating the local player is skating (on the board or on foot)
* @param blocked in a PvP area or instance, where nothing is sent to the party
* @param inParty in a RuneLite party
*/
public void tick(float now, boolean skating, boolean blocked, boolean inParty)
{
this.blocked = blocked;
applyAll(inbox.drain(now), now);
if (inChallenge())
{
if (blocked || !inParty)
// nothing may go out: the other side's challenge runs out by itself
finish(Outcome.CANCELLED, blocked ? Cause.BLOCKED : Cause.LEFT, now);
else if (now - phaseStart >= CHALLENGE_EXPIRY + (phase == Phase.CHALLENGING ? REPLY_GRACE : 0f))
{
listener.expired(opponentId, phase == Phase.CHALLENGING);
enter(Phase.IDLE, now);
}
return;
}
if (phase == Phase.OVER && now - phaseStart >= RESULT_SECONDS)
enter(Phase.IDLE, now);
if (!inDuel())
return;
if (skating)
notSkatingSince = Float.NaN;
else if (Float.isNaN(notSkatingSince))
notSkatingSince = now;
if (!inParty)
// left the party: nobody to tell
leftParty(now);
else if (blocked)
// the one last message, as a ghost's stop on entering a PvP area
forfeit(Cause.BLOCKED, now);
else if (now - notSkatingSince >= RESTART_GRACE)
forfeit(Cause.STOPPED_SKATING, now);
else if (selfKo && now - koAt >= KO_WAIT)
{
// the answer to our KO never came: down is down
sendEnd(EndReason.FORFEIT);
finish(Outcome.LOSS, Cause.KO, now);
}
else if (now - lastHeard >= SILENCE)
opponentGone(Cause.OPPONENT_SILENT, now);
else
{
if (phase == Phase.COUNTDOWN && now - phaseStart >= COUNTDOWN)
{
enter(Phase.FIGHT, now);
listener.fight();
}
releaseHits(now);
}
}

/** Logging out, shutting down, or a duel setting turned off: a duel is forfeit, a challenge taken back. */
public void quit(Cause why, float now)
{
// each of these only acts in its own phase (and the first leaves the phase IDLE)
withdraw(now);
decline(now);
if (inDuel())
forfeit(why, now);
}

/** The local client left (or changed) its party: nothing can be sent any more. */
public void leftParty(float now)
{
if (!free())
finish(phase == Phase.FIGHT ? Outcome.LOSS : Outcome.CANCELLED, Cause.LEFT, now);
}

// ---- From the party

/** Any message (a ghost update too) from {@code memberId}: the opponent is still there. */
public void heard(long memberId, float now)
{
if (memberId == opponentId && memberId != 0)
lastHeard = now;
}

/** A member left the party. */
public void memberLeft(long memberId, float now)
{
if (memberId != opponentId || memberId == 0)
return;
if (inDuel())
opponentGone(Cause.OPPONENT_LEFT, now);
else if (inChallenge())
finish(Outcome.CANCELLED, Cause.OPPONENT_LEFT, now);
}

/**
* A challenge from {@code fromId}.
*
* @param allowed "Allow duel challenges" is on and the local player can duel now
*/
public void onChallenge(long fromId, SkateDuelChallenge m, float now, boolean allowed)
{
// a challenge always starts its sender's seqs at 1: a peer's other first seq could make all it sends later
// look stale
if (m.targetMemberId != localId || localId == 0 || fromId == localId || fromId == 0 || m.duelId == 0
|| m.seq != 1)
return;
heard(fromId, now);
if (blocked)
// nothing may go out, not even a decline: the challenge runs out on the other side
return;
// challenges that crossed (or a new one replacing an old): the higher duel ID stands on both sides
boolean takeIt = allowed && (free() || inChallenge() && fromId == opponentId
&& (phase == Phase.CHALLENGED || m.duelId > duelId));
if (!takeIt)
{
post(new SkateDuelReply(m.duelId, 1, fromId, false), false);
return;
}
duelId = m.duelId;
opponentId = fromId;
mySeq = 0;
inbox.reset(1);
enter(Phase.CHALLENGED, now);
lastHeard = now;
listener.challenged(fromId);
}

public void onReply(long fromId, SkateDuelReply m, float now)
{
// a reply is always its sender's seq 1 (as a challenge is)
if (m.targetMemberId != localId || localId == 0 || m.seq != 1)
return;
heard(fromId, now);
if (phase == Phase.CHALLENGING && m.duelId == duelId && fromId == opponentId)
{
if (m.accepted)
{
inbox.reset(1);
startCountdown(now);
}
else
{
enter(Phase.IDLE, now);
listener.declined(fromId);
}
}
else if (m.accepted && m.duelId != 0 && !(m.duelId == duelId && inDuel()))
// accepted too late (expired or withdrawn), or a duel this client has no record of: call it off
post(new SkateDuelEnd(m.duelId, 2, fromId, EndReason.CANCEL.name()), false);
}

public void onHit(long fromId, SkateDuelHit m, float now)
{
heard(fromId, now);
receive(fromId, m.duelId, m.seq, m, now);
}

public void onEnd(long fromId, SkateDuelEnd m, float now)
{
heard(fromId, now);
receive(fromId, m.duelId, m.seq, m, now);
}

private void receive(long fromId, long msgDuel, int seq, PartyMessage m, float now)
{
if (fromId == localId || localId == 0)
return;
boolean ours = msgDuel == duelId && fromId == opponentId && duelId != 0;
boolean finished = ours && msgDuel == finishedDuel;
if (ours && (inDuel() || phase == Phase.CHALLENGED))
applyAll(inbox.offer(seq, m, now), now);
else if (finished && outcome == Outcome.LOSS && m instanceof SkateDuelEnd && quitWord((SkateDuelEnd) m))
{
// both quit before hearing of the other's: neither counts it
outcome = Outcome.CANCELLED;
cause = Cause.BOTH_QUIT;
enter(Phase.IDLE, now);
listener.voided(Outcome.LOSS, opponentId);
}
else if (finished && msgDuel != answeredDuel && !blocked && waitsForUs(m))
{
// the other side fights on (or waits for an answer) in a duel that is over here: tell it once
answeredDuel = msgDuel;
EndReason r = closingWord(outcome);
if (r != null)
sendEnd(r);
}
else if (!ours && !blocked && m instanceof SkateDuelHit && msgDuel != 0 && msgDuel != cancelledOrphan
&& ((SkateDuelHit) m).targetMemberId == localId)
{
// someone fights a duel with us that we are not in: tell them once. The seq is past any real one, so the
// receiver holds it until its gap times out
cancelledOrphan = msgDuel;
post(new SkateDuelEnd(msgDuel, Integer.MAX_VALUE - 1, fromId, EndReason.CANCEL.name()), false);
}
}

/** An End to us saying the sender quit: FORFEIT (it took a loss) or CANCEL (it called the duel off). */
private boolean quitWord(SkateDuelEnd e)
{
return e.targetMemberId == localId
&& (EndReason.FORFEIT.name().equals(e.reason) || EndReason.CANCEL.name().equals(e.reason));
}

/** A hit, or a KO waiting for its answer: the sender still thinks the duel is on. */
private static boolean waitsForUs(PartyMessage m)
{
return m instanceof SkateDuelHit
|| m instanceof SkateDuelEnd && EndReason.KO.name().equals(((SkateDuelEnd) m).reason);
}

/** What the other side must hear to end a duel that ended here with {@code result}; null for a draw. */
private static EndReason closingWord(Outcome result)
{
return result == Outcome.WIN ? EndReason.TIMEOUT : result == Outcome.LOSS ? EndReason.FORFEIT
: result == Outcome.CANCELLED ? EndReason.CANCEL : null;
}

/** The opponent went silent or left: this side calls it, and tells the other (if it can still hear). */
private void opponentGone(Cause why, float now)
{
// before the fight nobody wins or loses
Outcome result = phase == Phase.COUNTDOWN ? Outcome.CANCELLED : selfKo ? Outcome.LOSS : Outcome.WIN;
sendEnd(closingWord(result));
finish(result, why, now);
}

private void applyAll(List<PartyMessage> ready, float now)
{
for (PartyMessage m : ready)
{
if (m instanceof SkateDuelHit)
applyHit((SkateDuelHit) m, now);
else
applyEnd((SkateDuelEnd) m, now);
}
}

/** A hit from the opponent, in its seq order; an invalid one is ignored. */
private void applyHit(SkateDuelHit h, float now)
{
if (!inDuel() || h.damage < 1 || h.damage > DuelDamage.MAX)
return;
if (h.selfInflicted)
{
if (h.targetMemberId == opponentId)
{
oppHp = Math.max(0, oppHp - h.damage);
listener.hit(false, h.damage, true);
}
return;
}
if (h.targetMemberId != localId || selfKo)
return;
myHp = Math.max(0, myHp - h.damage);
listener.hit(true, h.damage, false);
if (myHp <= 0)
knockedOut(now);
}

private void applyEnd(SkateDuelEnd e, float now)
{
// unknown names (a newer version) are ignored
EndReason reason = Arrays.stream(EndReason.values()).filter(r -> r.name().equals(e.reason)).findFirst()
.orElse(null);
if (reason == null || e.targetMemberId != localId && e.targetMemberId != 0)
return;
if (phase == Phase.CHALLENGED || !inDuel())
{
if (phase == Phase.CHALLENGED && reason == EndReason.CANCEL)
finish(Outcome.CANCELLED, Cause.CANCELLED, now);
return;
}
switch (reason)
{
case KO:
oppHp = 0;
if (!selfKo)
sendEnd(EndReason.SURVIVED);
finish(selfKo ? Outcome.DRAW : Outcome.WIN, Cause.KO, now);
break;
case SURVIVED:
if (selfKo)
finish(Outcome.LOSS, Cause.KO, now);
break;
case FORFEIT:
finish(Outcome.WIN, Cause.OPPONENT_FORFEIT, now);
break;
case CANCEL:
finish(Outcome.CANCELLED, Cause.CANCELLED, now);
break;
default:
finish(Outcome.LOSS, Cause.TIMED_OUT, now);
break;
}
}

// ---- Internals

private void releaseHits(float now)
{
if (phase != Phase.FIGHT || selfKo)
return;
HitCooldown.Pending p;
while ((p = cooldown.poll(now)) != null)
{
if (oppHp <= 0)
{
// already down as far as we know: their KO is on its way
cooldown.clear();
return;
}
post(new SkateDuelHit(duelId, ++mySeq, opponentId, p.damage, p.comboValue, p.trickCount, false), false);
oppHp = Math.max(0, oppHp - p.damage);
listener.hit(false, p.damage, false);
}
}

private void knockedOut(float now)
{
selfKo = true;
koAt = now;
myHp = 0;
cooldown.clear();
sendEnd(EndReason.KO);
}

/**
* This client quits: a forfeit (a loss) in the fight, called off (no result) in the countdown, before anyone
* could land a hit.
*/
private void forfeit(Cause why, float now)
{
boolean countdown = phase == Phase.COUNTDOWN;
// the duel's last word: the one message that still goes out from a PvP area or instance
out.send(end(countdown ? EndReason.CANCEL : EndReason.FORFEIT), true);
finish(countdown ? Outcome.CANCELLED : Outcome.LOSS, why, now);
}

private void finish(Outcome result, Cause why, float now)
{
if (inDuel())
finishedDuel = duelId;
outcome = result;
cause = why;
cooldown.clear();
enter(result == Outcome.CANCELLED ? Phase.IDLE : Phase.OVER, now);
listener.over(result, why, opponentId);
}

private void startCountdown(float now)
{
myHp = START_HP;
oppHp = START_HP;
selfKo = false;
outcome = null;
cause = null;
cooldown.clear();
notSkatingSince = Float.NaN;
lastHeard = now;
enter(Phase.COUNTDOWN, now);
listener.countdown(opponentId);
}

private void enter(Phase p, float now)
{
phase = p;
phaseStart = now;
}

private void sendEnd(EndReason reason)
{
post(end(reason), false);
}

private SkateDuelEnd end(EndReason reason)
{
return new SkateDuelEnd(duelId, ++mySeq, opponentId, reason.name());
}

/**
* Sends {@code m}, unless in a PvP area or instance (where only a forfeit's last word goes out); {@code lastWord}:
* the last word of this client's part (still sent at shutdown).
*/
private void post(PartyMessage m, boolean lastWord)
{
if (!blocked)
out.send(m, lastWord);
}

private boolean free()
{
return phase == Phase.IDLE || phase == Phase.OVER;
}

private boolean inChallenge()
{
return phase == Phase.CHALLENGING || phase == Phase.CHALLENGED;
}

boolean inDuel()
{
return phase == Phase.COUNTDOWN || phase == Phase.FIGHT;
}

/** Seconds in the current phase. */
public float phaseAge(float now)
{
return now - phaseStart;
}

/** Seconds a challenge (made or received) has left; 0 outside one. */
public float challengeLeft(float now)
{
return inChallenge() ? Math.max(0f, CHALLENGE_EXPIRY - (now - phaseStart)) : 0f;
}

/** Seconds left to get back on before forfeiting; NaN while skating (or outside a duel). */
public float restartLeft(float now)
{
return inDuel() ? Math.max(0f, RESTART_GRACE - (now - notSkatingSince)) : Float.NaN;
}
}
