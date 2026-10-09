package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import net.runelite.api.gameval.AnimationID;

/**
* The loser's tantrum after a Skate Duel knockout, as a function of the seconds since it started: grab the board in
* both hands at the chest, stamp the feet with it (the game's own stamp-feet emote), heave it overhead, slam it down
* in front, where it snaps in two ({@link SnappedBoard}), then a huff while the arms drop. The same timing drives the
* local skater and every party ghost of them. Puppet model space throughout: x across the body (the right hand on
* -x), y down with the soles at 0, z out of the back (the body faces -z). Pure.
*/
public final class TantrumSequence
{
public enum Phase
{
GRAB, STOMP, LIFT, SLAM, HUFF, DONE
}

static final float GRAB = 0.3f;
static final float STOMP = 0.9f;
static final float LIFT = 0.4f;
/** The board hits the ground and snaps (after the 0.18 s slam). */
public static final float SNAP_AT = GRAB + STOMP + LIFT + 0.18f;
/** The tantrum is over (after the 0.55 s huff): on foot with a fresh board in hand, the controls back. */
public static final float DURATION = SNAP_AT + 0.55f;
/** Each phase's start and length. */
private static final float[] STARTS = {0f, GRAB, GRAB + STOMP, GRAB + STOMP + LIFT, SNAP_AT, DURATION};
private static final float[] LENGTHS = {GRAB, STOMP, LIFT, 0.18f, 0.55f};
/** The wind-up's emote: the game's stamp-feet (the Stronghold of Security "Stamp" emote). */
public static final int STOMP_ANIMATION = AnimationID.EMOTE_STAMPFEET;

/** Where the hands hold the board: at the chest, overhead, and down on the ground in front. */
static final float GROUND_Z = -62f;
/**
* At the start of each phase (and once done): the hands' hold point (y, z: at the chest, overhead, and down on the
* ground in front, where the hands are at the board's held point, its middle) and the upper body's fold (hunched in
* the stamp, leaning back under the board, folded forward by the slam). Each phase eases to the next phase's:
* linearly, smoothly (the lift and the huff) or faster and faster (the slam).
*/
private static final float[][] KEYS = {{-105f, -24f, 0f}, {-105f, -24f, 0.12f}, {-105f, -24f, 0.12f},
{-228f, -6f, -0.12f}, {CarryPose.HELD_Y, GROUND_Z, 0.55f}, {CarryPose.HELD_Y, GROUND_Z, 0f},
{CarryPose.HELD_Y, GROUND_Z, 0f}};

private TantrumSequence()
{
}

public static Phase phase(float t)
{
int i = 0;
while (i < 5 && t >= STARTS[i + 1])
i++;
return Phase.values()[i];
}

/** 0..1 through the phase {@code t} is in (1 once done). */
public static float progress(float t)
{
int i = phase(t).ordinal();
return i == 5 ? 1f : PushCycle.clamp01((t - STARTS[i]) / LENGTHS[i]);
}

/** The OSRS animation at {@code t}: the stamp-feet during the wind-up, else none (-1). */
public static int animation(float t)
{
return phase(t) == Phase.STOMP ? STOMP_ANIMATION : -1;
}

/** The board is in the hands (not yet snapped). */
public static boolean holding(float t)
{
return t < SNAP_AT;
}

/** 0..1: how far the board has come from where it was to the hands (the grab). */
public static float grabBlend(float t)
{
return PushCycle.smoothstep(0f, GRAB, t);
}

/**
* The procedural body at {@code t}: both hands reaching for the board where it is held (the arms ease in over the
* grab and let go over the first 0.35 s of the huff, the board half the hands' 17-unit spread from each) and the
* upper body's fold. Everything else neutral.
*/
public static void writeBody(float t, BodyPose out)
{
out.neutral();
float[] hold = new float[4];
hands(t, hold);
out.liftX = hold[0];
out.liftY = hold[1];
out.liftZ = hold[2];
out.torsoBend = hold[3];
out.liftSpread = 17f;
Phase ph = phase(t);
out.liftWeight = ph == Phase.GRAB ? smooth(progress(t)) : ph == Phase.HUFF
? 1f - PushCycle.smoothstep(0f, 0.35f, t - SNAP_AT) : ph == Phase.DONE ? 0f : 1f;
}

/** Where the hands hold the board at {@code t} (their middle; x, y, z) and the upper body's fold, into {@code out}. */
static void hands(float t, float[] out)
{
int i = phase(t).ordinal();
float u = progress(t);
float e = i == 2 || i == 4 ? smooth(u) : i == 3 ? u * u : u;
out[0] = 0f;
for (int k = 0; k < 3; k++)
out[k + 1] = KEYS[i][k] + (KEYS[i + 1][k] - KEYS[i][k]) * e;
// the stamping shakes the held board up and down 6 units, 5 times a second
out[1] += i == 1 ? 6f * (float) Math.sin(2 * Math.PI * 5f * (t - GRAB)) : 0f;
}

/**
* The board's held point (the middle of the board, board-model y {@link CarryPose#HELD_Y}) at {@code t} in puppet
* space into {@code out} (x, y, z), and its pitch along its length (the stamping's shake) as {@code out[3]}. The
* board lies across the body (its length along x), deck up.
*/
public static void board(float t, float[] out)
{
hands(t, out);
Phase ph = phase(t);
// 6 units in front of the hands while held; on the ground at the slam's end
float ahead = ph == Phase.SLAM ? 6f * (1f - progress(t)) : ph == Phase.HUFF || ph == Phase.DONE ? 0f : 6f;
out[2] -= ahead;
out[3] = ph == Phase.STOMP ? 0.08f * (float) Math.sin(2 * Math.PI * 5f * (t - GRAB) + 1.3) : 0f;
}

/**
* Where the board object goes for a body with its soles at ({@code x}, {@code y}, up-positive {@code h}) facing
* {@code heading}, holding the board's middle at puppet point {@code p} (x, y, z, pitch from {@link #board}):
* across the body (its long axis, model z, along the body's x: turned a quarter from it), so its nose points to
* the body's left.
*/
public static void place(float x, float y, float h, float heading, float[] p, Placement out)
{
int bodyJau = Angles.toJau(heading);
CarryPose.hold(x, y, h, bodyJau, p[0], p[1], p[2], (bodyJau - 512) & 2047, 0f, p[3], out);
}

/**
* The board object at {@code t} while held by that body (as {@link #place}), eased over the grab from where it
* was ({@code from}, when valid); {@code hold} is scratch of four.
*/
public static void placeHeld(float t, float x, float y, float h, float heading, Placement from, float[] hold,
Placement out)
{
board(t, hold);
place(x, y, h, heading, hold, out);
float grab = grabBlend(t);
if (grab < 1f && from.valid)
SwapBlend.lerp(from, out, grab);
}

/**
* The board's nose heading (0 north, clockwise) while held: a quarter turn from the body's {@code heading}, the
* way {@link #place} turns it. The halves fly apart along it.
*/
public static float boardHeading(float heading)
{
return Angles.wrap(heading - Angles.PI / 2);
}

/** Where the board breaks for that body: the ground point under its middle, local (x, y), into {@code out}. */
public static void snapPoint(float x, float y, float heading, float[] out)
{
double th = Angles.toJau(heading) * Math.PI / 1024.0;
out[0] = x + GROUND_Z * (float) Math.sin(th);
out[1] = y + GROUND_Z * (float) Math.cos(th);
}

private static float smooth(float u)
{
return PushCycle.smoothstep(0f, 1f, u);
}
}
