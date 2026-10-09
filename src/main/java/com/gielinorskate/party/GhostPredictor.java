package com.gielinorskate.party;

import com.gielinorskate.physics.*;
import com.gielinorskate.progression.*;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.tricks.Trick;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
* One party member's ghost: its updates played back on the member's own clock ({@link GhostTimeline}), the events
* they carry counted as the playback reaches them, and the latest update with its arrival (for expiry and stale
* updates). Positions are absolute (world tile * 128 + sub-tile); the returned {@link RenderPose} uses them as its
* x and y. Pure: no client dependency.
*/
@Accessors(fluent = true)
final class GhostPredictor extends GhostTimeline
{
/** A change of what is drawn larger than 3 tiles is a teleport (or a lost update): snap instead of sliding. */
static final float SNAP_DISTANCE = 3 * 128f;
/** The skater's gravity (skate-tuning.json "gravity", u/s^2). */
static final float GRAVITY = 2000f;

/** Height (up-positive) of the receiver's ground at an absolute (x, y), or NaN when unknown. */
interface Ground
{
float heightAt(float x, float y);
}

/** The latest authoritative state, or null before the first. */
@Getter
private GhostState latest;
private float latestTime;
/** The trick (or grab, manual, grind) of the latest TRICK event, or null; see {@link #labelAge}. */
@Getter
private Trick labelTrick;
private float labelTime;
private float popTime = Float.NaN;
/** The latest pop was off the nose. */
@Getter
private boolean popNollie;
/** The member's board designs (from their updates; each part's default when unknown). */
@Getter
private BoardLook look = BoardLook.defaults(BoardDesigns.bundled());
/** The Skate Duel version the member takes challenges with (from their latest update); 0 for none. */
@Getter
private int duelVersion;
/** Pops, landings, bails and pushes played so far, so a renderer can tell new ones each frame. */
@Getter
private int popCount;
@Getter
private int landCount;
@Getter
private int bailCount;
@Getter
private int pushCount;
/** When the current knockdown stage was first seen. */
private float knockSince;
/** Duel endings seen so far (a new one: the ghost plays it) and when the latest of each was played (NaN: none). */
@Getter
private int tantrumCount;
private float tantrumAt = Float.NaN;
@Getter
private int celebrateCount;
private float celebrateAt = Float.NaN;
/** The knockdown stage of the update last played. */
private KnockdownPose.Stage playedKnock;
/** The latest deck and grip-and-wheels fields (dk, gw), so the look can be worked out again. */
String deckWire;
String lookWire;

/**
* Takes in a new authoritative state received at {@code now}, with its timeline {@code tj} (null when it did not
* fit: placed by its arrival). Returns false when it is stale (not newer than the last one accepted; with a
* timeline, its positions still fill in the timeline: they say where the member was), or when it has no timeline
* and none came before (nothing to place it on yet).
*/
boolean accept(GhostState s, GhostTrajectory tj, float now, Ground ground)
{
// An update with an old sequence number is stale, unless the member has been silent this long: then the
// sender has restarted and counts again from a lower number.
boolean newer = latest == null || s.seq > latest.seq || now - latestTime >= 2f;
if (tj == null && !newer || !receive(s, tj, now, ground))
return false;
if (newer)
{
latest = s;
latestTime = now;
}
return newer;
}

@Override
void event(int bits, Trick trick, boolean nollie, float at)
{
if ((bits & GhostCodec.EV_TRICK) != 0 && trick != null)
{
labelTrick = trick;
labelTime = at;
}
if ((bits & GhostCodec.EV_POP) != 0)
{
popTime = at;
popNollie = nollie;
popCount++;
}
landCount += (bits & GhostCodec.EV_LAND) != 0 ? 1 : 0;
bailCount += (bits & GhostCodec.EV_BAIL) != 0 ? 1 : 0;
pushCount += (bits & GhostCodec.EV_PUSH) != 0 ? 1 : 0;
if ((bits & GhostCodec.EV_TANTRUM) != 0)
{
tantrumCount++;
tantrumAt = at;
}
if ((bits & GhostCodec.EV_CELEBRATE) != 0)
{
celebrateCount++;
celebrateAt = at;
}
}

@Override
void keyChanged(GhostState key, float at)
{
if (key.knockStage != null && key.knockStage != playedKnock)
knockSince = at;
playedKnock = key.knockStage;
}

/** The front / back flip angle of the whole skater at {@code now}, radians, + = frontflip; raw (whole turns kept). */
float bodyFlip(float now)
{
advance(now, lastGround);
return bodyFlip();
}

/** The heading turn rate (rad/s) at {@code now}, 0 when bailed. A flip's own body turn is not included. */
float turnRate(float now)
{
advance(now, lastGround);
// the curve's own turn rate: smooth through the samples, so the carve lean is too
return state() == SkaterState.BAILED ? 0f : oturn;
}

void setDuelVersion(int version)
{
duelVersion = Math.max(0, version);
}

/** The look worked out again from the latest deck and grip-and-wheels fields. */
void relook(BoardDesigns designs, BiFunction<DesignPart, String, BoardDesign> custom)
{
look = GhostCodec.decodeLook(deckWire, lookWire, look, designs, custom);
}

/** The state being drawn: the update the playback has reached (its board, knockdown, hold...), else the latest. */
GhostState current()
{
GhostState c = playing();
return c != null ? c : latest;
}

/** No message for {@link #EXPIRY} seconds. */
boolean expired(float now)
{
// No message for this long: the member is gone (keep-alives come every 10 s).
return latest == null || now - latestTime > 15f;
}

/**
* The ghost's pose at {@code now} ({@code ground} may be null: no ground clamp or following). Position, height
* and heading from the timeline's curves; the board flip from when it started on the member's clock (so it starts
* at the pop on the path), the hold and pitch from the update being played.
*/
RenderPose pose(float now, Ground ground)
{
advance(now, ground);
GhostState key = playing();
SkaterState state = state();
Trick hold = key == null ? null : key.hold;
float roll = 0f;
float yaw = 0f;
float pitch = SkatePhysics.boardPitchFor(state, hold);
Trick t = flipTrick();
if (t != null)
{
float u = flipTime() / t.duration;
float e = SkatePhysics.flipEase(u);
roll = t.rollTurns * Angles.TWO_PI * e;
pitch += t.pitchTurns * Angles.TWO_PI * e;
// relative to the body, whose bigspin turn is in the played-back heading already
yaw = t.yawTurns * Angles.TWO_PI * e - GhostCodec.bodySpin(t, u);
}
return new RenderPose(ox, oy, oh, ohd, ohd, roll, yaw, pitch, state, hold);
}

/** Seconds since {@link #labelTrick} was set. */
float labelAge(float now)
{
return now - labelTime;
}

/** Seconds since the latest pop, or Float.MAX_VALUE when none was seen. */
float sincePop(float now)
{
return Float.isNaN(popTime) ? Float.MAX_VALUE : now - popTime;
}

/** Seconds since the latest tantrum was played (it may be a moment ago: a late update); NaN before any. */
float tantrumAge(float now)
{
return now - tantrumAt;
}

/** Seconds since the latest celebration was played; NaN before any. */
float celebrateAge(float now)
{
return now - celebrateAt;
}

/** The pop charge (0..1) of the update drawn; 0 when none. */
float charge()
{
GhostState s = current();
return s == null ? 0f : s.charge;
}

/** Seconds since the current knockdown stage was first seen. */
float knockStageAge(float now)
{
return Math.max(0f, now - knockSince);
}

/** Ground speed as played back, u/s (the curve's own; unsigned: the wire has no fakie). */
float speed()
{
return (float) Math.hypot(ovx, ovy);
}

/** Vertical speed (up > 0) at {@code now}, as played back. */
float verticalSpeed(float now)
{
advance(now, lastGround);
return ovh;
}

/** A board flip (roll, shove-it or impossible) is still turning at {@code now}. */
boolean flipping(float now)
{
advance(now, lastGround);
return flipTrick() != null;
}
}
