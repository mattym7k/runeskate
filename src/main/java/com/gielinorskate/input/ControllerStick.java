package com.gielinorskate.input;

/**
* Controller mode's right stick, read from button-free mouse movement. AntiMicroX's "Mouse (Normal)" stick output
* (cursor mode, the shipped profile) moves the cursor at a speed set by the tilt, so a still cursor means a centred
* stick and the cursor path is the stick's motion added up over time:
* <ul>
* <li>a <b>stroke</b> starts with the first cursor move after the cursor has been still, and ends once it has not
* moved for {@link #STOP_MS} (the stick is back at centre). The stroke's path goes to the same
* {@link GestureRecognizer} as a right-drag, so flicks, their sensitivity and their near-miss hints are shared;</li>
* <li>a stroke that stays slow (a small tilt, under {@link #FAST_SPEED}) and keeps going mostly up or down for
* {@link #MANUAL_MS} is a <b>manual</b> (up) or a <b>nose manual</b> (down) instead. It is dropped from the
* recogniser with no flick and no hint, and lasts until the stroke ends. A fast motion during a manual ends it and
* starts a fresh stroke, so a flick pops out of the manual;</li>
* <li>while a grab key is held the stick aims the grab ({@link #blockedMove}) and is never a flick, and that
* motion stays blocked after the grab is let go until the cursor has been still.</li>
* </ul>
* Thread-safe: moves arrive on the AWT thread, ticks on the client thread. Times share the MouseEvent clock.
*/
final class ControllerStick
{
/** The manual a held stick tilt asks for. */
enum Manual
{
NONE,
/** Small tilt up, held. */
MANUAL,
/** Small tilt down, held. */
NOSE
}

/**
* No cursor move for this long means the stick is back at centre. AntiMicroX sends a move every 5 ms or so
* while tilted (a tiny tilt can take ~20 ms per pixel); a flick's pass through the dead zone takes ~10-30 ms.
*/
static final long STOP_MS = 80;
/** A slow tilt held this long, without a fast motion, is a manual. */
static final long MANUAL_MS = 250;
/**
* Cursor speed, px/ms over {@link #SPEED_SPAN_MS}, from which a stroke counts as fast (a flick's wind-up or
* snap, never a manual). With the profile's speed 60 and quadratic curve, full tilt is ~1.2 px/ms and this is
* a bit over half tilt; flicks themselves must still reach the recogniser's own 0.5 px/ms.
*/
static final float FAST_SPEED = 0.35f;
static final long SPEED_SPAN_MS = 24;
/** A manual's net travel since the stroke began must be at least this, px, and mostly vertical. */
static final float MANUAL_MIN_PX = 8f;
/** Mostly vertical: |dy| at least this many times |dx|. */
static final float MANUAL_VERTICAL_RATIO = 2f;
private static final int HISTORY = 16;

private final GestureRecognizer gesture;

/** Flicks on (mouse tricks); manuals are read either way. */
private boolean flicks = true;
/** The cursor's latest position and move time; known once the first move arrives. */
private boolean known;
private int lastX;
private int lastY;
private long lastMoveMs;
/** A stroke is in progress (the stick is off centre). */
private boolean active;
/** The recogniser was begun for this stroke (cleared once it is a manual). */
private boolean feeding;
/** A grab key went down: motion is blocked until the cursor has been still for STOP_MS. */
private boolean blocked;
/** The stroke has moved fast at some point: a flick, never a manual. */
private boolean fast;
private long strokeStartMs;
private int strokeStartX;
private int strokeStartY;
private Manual manual = Manual.NONE;
private final Samples history = new Samples(HISTORY);

ControllerStick(GestureRecognizer gesture)
{
this.gesture = gesture;
}

/** Mouse tricks on or off: off, strokes are only read for manuals. */
synchronized void setFlicks(boolean on)
{
flicks = on;
}

/** A button-free mouse move with no grab key held. */
synchronized void move(int x, int y, long ms)
{
boolean resting = !known || ms - lastMoveMs >= STOP_MS;
if (blocked)
{
if (!resting)
{
// still the motion that was going on when the grab key went down
remember(x, y, ms);
return;
}
blocked = false;
}
if (active && resting)
endStroke();
if (!active)
startStroke(known ? lastX : x, known ? lastY : y, ms);
remember(x, y, ms);
// only samples of this stroke and its run of moves count; none older: not fast
boolean fastNow = history.speed(x, y, ms, SPEED_SPAN_MS, HISTORY, strokeStartMs, 0f) >= FAST_SPEED;
if (fastNow && manual != Manual.NONE)
{
// a flick out of the manual: the manual ends and a fresh stroke starts at the sample before this one
manual = Manual.NONE;
boolean before = history.count >= 2;
int i = history.ago(1);
startStroke(before ? (int) history.x[i] : lastX, before ? (int) history.y[i] : lastY,
before ? history.ms[i] : ms);
}
fast |= fastNow;
if (feeding)
gesture.move(x, y, ms);
checkManual(ms);
}

/** A button-free mouse move while a grab key is held: it aims the grab and is never a flick. */
synchronized void blockedMove(int x, int y, long ms)
{
block(ms);
remember(x, y, ms);
}

/** A grab key went down (or is held): the stroke in progress is dropped, and motion is blocked until still. */
synchronized void block(long ms)
{
if (active)
{
if (feeding)
gesture.cancel();
active = false;
feeding = false;
manual = Manual.NONE;
}
blocked = true;
if (!known || ms > lastMoveMs)
lastMoveMs = ms;
}

/** Lets time pass with no move: the stroke ends once the cursor has been still for STOP_MS. */
synchronized void tick(long now)
{
if (!active)
return;
if (now - lastMoveMs >= STOP_MS)
{
endStroke();
return;
}
checkManual(now);
}

/**
* Forgets the stroke without touching the recogniser (skating toggled, focus lost, or a flick button press
* that starts a right-drag of its own).
*/
synchronized void reset()
{
active = false;
feeding = false;
blocked = false;
manual = Manual.NONE;
known = false;
history.count = 0;
}

synchronized Manual manual()
{
return manual;
}

/** A stick stroke is in progress. */
synchronized boolean isActive()
{
return active;
}

/**
* True while the recogniser's wind-up (crouch, charge) should not show: a stick stroke that has only been slow
* so far may still turn out to be a manual.
*/
synchronized boolean hidesWindUp()
{
return active && feeding && !fast;
}

private void startStroke(int x, int y, long ms)
{
active = true;
fast = false;
manual = Manual.NONE;
strokeStartMs = ms;
strokeStartX = x;
strokeStartY = y;
feeding = flicks;
if (feeding)
gesture.begin(x, y, ms);
}

private void endStroke()
{
if (feeding)
gesture.end();
active = false;
feeding = false;
manual = Manual.NONE;
}

private void checkManual(long now)
{
if (!active || fast || manual != Manual.NONE || now - strokeStartMs < MANUAL_MS)
return;
float dx = lastX - strokeStartX;
float dy = lastY - strokeStartY;
if (Math.abs(dy) < MANUAL_MIN_PX || Math.abs(dy) < MANUAL_VERTICAL_RATIO * Math.abs(dx))
return;
manual = dy < 0 ? Manual.MANUAL : Manual.NOSE;
if (feeding)
{
gesture.cancel();
feeding = false;
}
}

private void remember(int x, int y, long ms)
{
known = true;
lastX = x;
lastY = y;
lastMoveMs = ms;
history.add(x, y, ms);
}
}
