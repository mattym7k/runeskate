package com.gielinorskate.render;

import com.gielinorskate.physics.*;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;

/**
* A snapped board's two halves ({@link BakedBoardModel#half}) flying apart from where it broke, each bounced, spun
* and settled by its own {@link BoardBounce} against the collision world, then lying still until {@link #LIE_SECONDS}
* after the snap, then sinking out of sight over {@link #SINK_SECONDS} and removed. Cosmetic objects only
* (RuneLiteObjects of our own). Used for the local skater's tantrum and for party ghosts'. Client thread.
*/
@RequiredArgsConstructor
public final class SnappedBoard
{
/** The halves lie this long after the snap (flight included), then sink away. */
public static final float LIE_SECONDS = 3f;
public static final float SINK_SECONDS = 0.5f;
/** Physics step for the halves. */
private static final float STEP = 0.02f;

private final Client client;
private final BakedBoardModel[] halves = new BakedBoardModel[2];
private final BoardBounce[] sims = new BoardBounce[2];
/** Each half's objects, one per part (the baked board has at most four). */
private final BoardController[][] objects = new BoardController[2][];
private float age;
private float accumulator;
/** The halves are out (flying, lying or sinking). */
@Getter
private boolean active;

/**
* Copies the board's two halves now (before the snap, so the snap itself costs nothing). False when the board
* can't snap (there is none): nothing will be drawn.
*/
public boolean prepare(BakedBoardModel board)
{
despawn();
if (board == null)
return false;
halves[0] = board.half(true);
halves[1] = board.half(false);
return true;
}

/**
* A tantrum's slammed board breaks in front of a body at local ({@code x}, {@code y}, up-positive {@code h})
* facing {@code heading}, on the ground there (a step or a kerb in front of the feet; {@code h} when that is
* over 128 units off or unknown), the break point into {@code at}: the halves fly apart along the board.
* {@code world} null: a flat ground. Returns the ground at the break.
*/
public float snap(CollisionWorld world, float gravity, float x, float y, float h, float heading, int worldViewId,
int plane, Random random, float[] at)
{
TantrumSequence.snapPoint(x, y, heading, at);
float ground = world == null ? h : world.groundHeight(at[0], at[1]);
if (!(Math.abs(ground - h) <= 128f))
ground = h;
if (halves[0] == null)
return ground;
CollisionWorld w = world != null ? world : flat(ground);
float yaw = TantrumSequence.boardHeading(heading);
float sx = (float) Math.sin(yaw);
float sy = (float) Math.cos(yaw);
for (int k = 0; k < 2; k++)
{
// the nose half goes the way the nose points, the tail half the other way, thrown at 650 u/s along the
// board away from the break (before BoardBounce's carry share)
float dir = k == 0 ? 1f : -1f;
sims[k] = new BoardBounce(w, gravity);
sims[k].start(at[0] + sx * dir * 20f, at[1] + sy * dir * 20f, ground, sx * dir * 650f, sy * dir * 650f, yaw,
random);
objects[k] = new BoardController[halves[k].partCount()];
for (int i = 0; i < objects[k].length; i++)
{
BoardController c = new BoardController(i);
c.setBoard(halves[k], null);
c.setWorldView(worldViewId);
c.setLevel(plane);
objects[k][i] = c;
}
}
age = 0f;
accumulator = 0f;
active = true;
place();
for (BoardController[] half : objects)
{
for (BoardController c : half)
client.registerRuneLiteObject(c);
}
return ground;
}

/** On by {@code dt}: the halves fly, lie and sink; removed at the end. */
public void update(float dt)
{
if (!active)
return;
age += Math.max(0f, dt);
if (age >= LIE_SECONDS + SINK_SECONDS)
{
despawn();
return;
}
accumulator += Math.max(0f, Math.min(0.1f, dt));
while (accumulator >= STEP)
{
sims[0].step(STEP);
sims[1].step(STEP);
accumulator -= STEP;
}
place();
}

private void place()
{
float sink = sinkDepth(age);
for (int k = 0; k < 2; k++)
{
BoardBounce b = sims[k];
// the half's centre is its model origin: that far above where it rests (RuneLite z grows downward)
int z = -Math.round(b.getH() + halves[k].bottom() - sink);
for (BoardController c : objects[k])
{
c.setX(Math.round(b.getX()));
c.setY(Math.round(b.getY()));
c.setZ(z);
c.setOrientation(Angles.toJau(b.getYaw()));
c.setPose(b.getRoll(), b.getPitch(), 0f, false);
}
}
}

/** Removes the halves (and forgets them): nothing of this board is drawn any more. */
public void despawn()
{
for (int k = 0; k < 2; k++)
{
if (active)
{
for (BoardController c : objects[k])
client.removeRuneLiteObject(c);
}
halves[k] = null;
sims[k] = null;
objects[k] = null;
}
active = false;
}

/** How far (units) the halves have sunk {@code age} seconds after the snap: none until they have lain. Pure. */
public static float sinkDepth(float age)
{
float u = Math.min(1f, (age - LIE_SECONDS) / SINK_SECONDS);
// 40 units deep (well below a board's height) when removed
return age > LIE_SECONDS ? 40f * u * u : 0f;
}

/** Flat ground at {@code h}, nothing in the way (a party ghost's halves outside the local collision world). */
static CollisionWorld flat(float h)
{
return new CollisionWorld()
{
@Override
public float groundHeight(float x, float y)
{
return h;
}

@Override
public float terrainHeight(float x, float y)
{
return h;
}

@Override
public float blockerTop(float x0, float y0, float x1, float y1)
{
return Float.NEGATIVE_INFINITY;
}
};
}
}
