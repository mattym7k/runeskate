package com.gielinorskate.party;

import java.util.Arrays;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
* When the local skater's state goes to the party. While another party member is skating (an audience),
* events go out as they happen, a state snapshot at most once per game tick (0.6 s) and only when something
* changed, and a keep-alive after 10 s without any message. With no audience only an "I'm skating" announce
* goes out, at most once every {@link #ANNOUNCE_INTERVAL} s, so other skaters can discover this one. Never more
* than {@link #MAX_PER_SECOND} messages in any one-second window: a token bucket of 2 tokens in which each spent
* token comes back exactly 1 s after it was spent, so even a burst of events cannot put more than 2 messages
* into any second (events held back coalesce into the next message). Times are seconds on one monotonic clock.
* Pure.
*/
@Accessors(fluent = true)
final class GhostSendPolicy
{
static final int MAX_PER_SECOND = 2;
/** One game tick. */
static final float SNAPSHOT_INTERVAL = 0.6f;
/** Receivers expire a ghost after 15 s of silence, so an idle skater re-sends well before. */
static final float KEEP_ALIVE = 10f;
/** Without an audience: one announce per 5 s, well inside the receivers' 15 s ghost expiry. */
static final float ANNOUNCE_INTERVAL = 5f;
/** The limit's window, seconds. */
static final float WINDOW = 1f;

/** Times of the latest sends, oldest first once full (a ring of MAX_PER_SECOND). */
private final float[] recent = new float[MAX_PER_SECOND];
private int recentNext;
/** When the latest update, stop or duel message went out (negative infinity when none since a reset). */
@Getter
private float lastSend = Float.NEGATIVE_INFINITY;
private int seq;

/** Sequence numbers start just above {@code seqStart}. */
GhostSendPolicy(int seqStart)
{
seq = seqStart;
// no sends yet
Arrays.fill(recent, Float.NEGATIVE_INFINITY);
}

/** A message may go out at {@code now} without breaking the per-second limit. */
boolean hasToken(float now)
{
return freeTokens(now) > 0;
}

/**
* Whether to send the current state now.
*
* @param changed the state differs from the last one sent
* @param event an event (pop, land, trick...) is waiting to go out
*/
boolean shouldSend(float now, boolean changed, boolean event)
{
float since = now - lastSend;
return hasToken(now) && (event || since >= KEEP_ALIVE || changed && since >= SNAPSHOT_INTERVAL);
}

/**
* With nobody else skating: whether the cheap "I'm skating" announce is due. Only with every token free, so a
* stop right after it (skating toggled off) still fits in the limit.
*/
boolean shouldAnnounce(float now)
{
return freeTokens(now) == MAX_PER_SECOND && now - lastSend >= ANNOUNCE_INTERVAL;
}

/** How many messages may go out at {@code now} without breaking the per-second limit. */
int freeTokens(float now)
{
int free = MAX_PER_SECOND;
for (float r : recent)
free -= now - r < WINDOW ? 1 : 0;
return free;
}

/** Records a message (update or stop) sent at {@code now}. */
void recordSend(float now)
{
recordSideSend(now);
lastSend = now;
}

/**
* Records a message that is not part of the skater's own stream (a custom design's offer or chunk) sent at
* {@code now}: it counts in the per-second limit but does not move the snapshot, keep-alive or announce timing.
*/
void recordSideSend(float now)
{
recent[recentNext] = now;
recentNext = (recentNext + 1) % MAX_PER_SECOND;
}

/** Sharing (re)starts: the first snapshot may go out at once. The rate limit is kept. */
void reset()
{
lastSend = Float.NEGATIVE_INFINITY;
}

/** The next sequence number; never repeats while the plugin runs. */
int nextSeq()
{
return ++seq;
}
}
