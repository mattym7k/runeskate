package com.gielinorskate.party;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import java.util.Arrays;

/**
* An update's timeline ({@link SkateGhostUpdate#tj}): the sender's send time, how long before it each of the
* update's events happened, and up to {@link #MAX_SAMPLES} earlier positions of the skater (newest first), so
* receivers can play the skater back on its own clock and between updates instead of guessing. Text, in
* {@link GhostWire}'s alphabet:
* <ol>
* <li>a header character: bit 0 the event times are there, bits 1..3 the sample count, bits 4..5 the format
* version (0; another version is ignored as a whole);</li>
* <li>the send time: milliseconds of the sender's monotonic clock modulo 2^18 (about 262 s), 3 characters;</li>
* <li>with bit 0: per set bit of the update's ev, lowest first, how long before the send time it happened, in
* {@link #TICK}s;</li>
* <li>per sample, newest first, each relative to the one before it (the first to the update's own x / y / h /
* hd): time back in TICKs, x, y and h in whole units, heading in {@link #HEADING_UNIT}s the short way round,
* and the state's code ({@link #STATE_CODES}).</li>
* </ol>
* Each delta is taken from the value the receiver will rebuild, so rounding never adds up along the chain. Pure.
*/
final class GhostTrajectory
{
static final int TIME_DIGITS = 3;
static final int TIME_MOD = 1 << (6 * TIME_DIGITS);
/** Seconds per unit of event and sample times. */
static final float TICK = 0.01f;
/** Radians per unit of a sample's heading. */
static final float HEADING_UNIT = 0.01f;
/** EV_* bits that can carry a time (ev's low bits). */
static final int EVENT_BITS = 16;
/** A sample's state on the wire; never reordered (a code past the end reads as ROLLING). */
static final SkaterState[] STATE_CODES = {SkaterState.ROLLING, SkaterState.AIRBORNE, SkaterState.BAILED,
SkaterState.MANUAL, SkaterState.GRINDING};
private static final int VERSION = 0;

/** The sender's send time, ms modulo {@link #TIME_MOD}. */
int timeMs;
/** Per EV bit, seconds before the send time it happened; NaN when not sent (sending: null for none). */
float[] eventAgo;
int count;
/** Per sample (newest first): seconds before the send time, absolute position, heading and state. */
final float[] ago;
final float[] x;
final float[] y;
final float[] h;
final float[] heading;
final SkaterState[] state;

/** Room for {@code capacity} samples. */
GhostTrajectory(int capacity)
{
ago = new float[capacity];
x = new float[capacity];
y = new float[capacity];
h = new float[capacity];
heading = new float[capacity];
state = new SkaterState[capacity];
}

/** The send time on the wire for {@code now} seconds of the sender's clock. */
static int timeMs(float now)
{
return (int) Math.floorMod(Math.round((double) now * 1000.0), (long) TIME_MOD);
}

/**
* The timeline text: the send time; with the event times (by EV bit), the times of {@code ev}'s
* events; then the first {@code count} samples (newest first) relative to the update's own position
* ({@code refX}, {@code refY}, {@code refH}, heading {@code refHd} / 1000).
*/
String encode(int ev, int refX, int refY, int refH, int refHd)
{
// max samples
int n = Math.max(0, Math.min(7, count));
StringBuilder sb = new StringBuilder(8 + n * 8);
int header = (eventAgo != null ? 1 : 0) | n << 1 | VERSION << 4;
sb.append(GhostWire.ALPHABET.charAt(header));
GhostWire.putFixed(sb, Math.floorMod(timeMs, TIME_MOD), TIME_DIGITS);
if (eventAgo != null)
{
for (int bit = 0; bit < EVENT_BITS; bit++)
{
if ((ev & (1 << bit)) != 0)
{
float a = eventAgo[bit];
int ticks = Float.isNaN(a) ? 0 : Math.round(a / TICK);
// Event times further back than this (a minute) are clamped.
GhostWire.putUnsigned(sb, Math.max(0, Math.min(6000, ticks)));
}
}
}
int prevTicks = 0;
int px = refX;
int py = refY;
int ph = refH;
float pHeading = refHd / 1000f;
for (int i = 0; i < n; i++)
{
int ticks = Math.max(prevTicks, Math.round(ago[i] / TICK));
GhostWire.putUnsigned(sb, ticks - prevTicks);
prevTicks = ticks;
int ix = Math.round(x[i]);
int iy = Math.round(y[i]);
int ih = Math.round(h[i]);
GhostWire.putSigned(sb, ix - px);
GhostWire.putSigned(sb, iy - py);
GhostWire.putSigned(sb, ih - ph);
px = ix;
py = iy;
ph = ih;
int dHeading = Math.round(Angles.wrap(heading[i] - pHeading) / HEADING_UNIT);
GhostWire.putSigned(sb, dHeading);
pHeading += dHeading * HEADING_UNIT;
GhostWire.putUnsigned(sb, Math.max(0, Arrays.asList(STATE_CODES).indexOf(state[i])));
}
return sb.toString();
}

/**
* The timeline of an update with events {@code ev} at ({@code refX}, {@code refY}, {@code refH}, {@code refHd}),
* or null when {@code tj} is missing, junk or of another format version.
*/
static GhostTrajectory decode(String tj, int ev, int refX, int refY, int refH, int refHd)
{
if (tj == null || tj.isEmpty() || tj.length() > GhostWire.MAX_UPDATE_CHARS)
return null;
GhostWire.Reader r = new GhostWire.Reader(tj);
int header = r.fixed(1);
if (!r.ok() || header >> 4 != VERSION)
return null;
int n = (header >> 1) & 7;
int time = r.fixed(TIME_DIGITS);
float[] eventAgo = new float[EVENT_BITS];
Arrays.fill(eventAgo, Float.NaN);
if ((header & 1) != 0)
{
for (int bit = 0; bit < EVENT_BITS; bit++)
{
if ((ev & (1 << bit)) != 0)
eventAgo[bit] = r.unsigned() * TICK;
}
}
GhostTrajectory out = new GhostTrajectory(n);
out.timeMs = time;
out.eventAgo = eventAgo;
out.count = n;
int ticks = 0;
int px = refX;
int py = refY;
int ph = refH;
float pHeading = refHd / 1000f;
for (int i = 0; i < n; i++)
{
ticks += r.unsigned();
px += r.signed();
py += r.signed();
ph += r.signed();
pHeading += r.signed() * HEADING_UNIT;
int c = r.unsigned();
out.ago[i] = ticks * TICK;
out.x[i] = px;
out.y[i] = py;
out.h[i] = ph;
out.heading[i] = Angles.wrap(pHeading);
out.state[i] = c >= 0 && c < STATE_CODES.length ? STATE_CODES[c] : SkaterState.ROLLING;
}
return r.ok() ? out : null;
}
}
