package com.gielinorskate.feedback;

import java.util.*;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;

/**
* Client-side particle bursts: a game graphic (spotanim) drawn once as a RuneLiteObject at a point, then
* removed. The graphic's model and animation come from its cache definition ({@link SpotanimDef}). At most
* 8 are alive at once; the oldest makes way. Nothing here is seen by other players or the
* server. Client thread.
*/
@Slf4j
final class SpotEffects
{
/**
* Upper bound on a graphic's life: long enough for the level-99 fireworks to play out in full, short enough
* that a bad length in the cache cannot keep an object alive for long.
*/
static final float MAX_LIFE = 8f;
/** Life of a graphic without an animation, or with one whose length is unknown. */
private static final float DEFAULT_LIFE = 0.8f;
/** A model may still be loading; give up on a graphic after this many tries. */
private static final int MAX_LOAD_TRIES = 30;

/** A graphic ready to draw. */
@AllArgsConstructor
private static final class Loaded
{
final Model model;
final Animation animation;
final float life;
}

@Inject
private Client client;
private final EffectSlots<RuneLiteObject> live = new EffectSlots<>(8);
private final Map<Integer, Loaded> loaded = new HashMap<>();
/** Load attempts per graphic still loading; a graphic at {@link #MAX_LOAD_TRIES} is never tried again. */
private final Map<Integer, Integer> tries = new HashMap<>();

/** Shows graphic {@code spotanimId} at local (x, y), height h (up-positive, as physics). */
void spawn(int spotanimId, float x, float y, float h, WorldView wv, float now)
{
Loaded graphic = load(spotanimId);
if (graphic == null)
return;
RuneLiteObject obj = client.createRuneLiteObject();
obj.setModel(graphic.model);
if (graphic.animation != null)
{
obj.setAnimation(graphic.animation);
obj.setShouldLoop(false);
}
obj.setLocation(new LocalPoint(Math.round(x), Math.round(y), wv), wv.getPlane());
// setLocation puts it on the tile's ground; a rail or a ledge can be higher (RuneLite z grows downward)
obj.setZ(-Math.round(h));
obj.setActive(true);
RuneLiteObject evicted = live.add(obj, now, graphic.life);
if (evicted != null)
evicted.setActive(false);
}

/** Removes graphics whose time is up. */
void update(float now)
{
live.expire(now).forEach(obj -> obj.setActive(false));
}

int liveCount()
{
return live.size();
}

void clear()
{
live.clear().forEach(obj -> obj.setActive(false));
}

private Loaded load(int spotanimId)
{
Loaded cached = loaded.get(spotanimId);
if (cached != null)
return cached;
int attempt = tries.getOrDefault(spotanimId, 0);
if (attempt >= MAX_LOAD_TRIES)
return null;
tries.put(spotanimId, attempt + 1);

IndexDataBase config = client.getIndexConfig();
SpotanimDef def = SpotanimDef.parse(config == null ? null : config.loadData(SpotanimDef.CONFIG_ARCHIVE, spotanimId));
if (def == null)
{
tries.put(spotanimId, MAX_LOAD_TRIES);
log.debug("Skate effect {}: no readable graphic definition", spotanimId);
return null;
}
ModelData data = client.loadModelData(def.modelId);
if (data == null)
{
return null; // still loading: try again next time
}
// copies first: the loaded model data may be shared with the game's own use of it
if (def.recolorFrom != null)
{
data = data.shallowCopy().cloneColors();
for (int i = 0; i < def.recolorFrom.length; i++)
data.recolor(def.recolorFrom[i], def.recolorTo[i]);
}
if (def.resizeX != SpotanimDef.DEFAULT_RESIZE || def.resizeY != SpotanimDef.DEFAULT_RESIZE)
data = data.shallowCopy().cloneVertices().scale(def.resizeX, def.resizeY, def.resizeX);
// lit as the client lights a spotanim's model: its ambient and contrast on top of these bases, with this
// light direction (as the game's own graphic definition does)
Model model = data.light(64 + def.ambient, 850 + def.contrast, -30, -50, -30);
if (model == null)
return null;
Animation animation = def.animationId >= 0 ? client.loadAnimation(def.animationId) : null;
Loaded graphic = new Loaded(model, animation, animation == null ? DEFAULT_LIFE : animation.isMayaAnim()
? lifeSeconds(true, animation.getDuration(), null) : lifeSeconds(false, 0, animation.getFrameLengths()));
loaded.put(spotanimId, graphic);
tries.remove(spotanimId);
return graphic;
}

/**
* How long a graphic lives: the animation's own length (summed frame lengths, or a maya animation's duration,
* both in 20 ms client cycles), bounded to [0.3 s, {@link #MAX_LIFE}]; {@link #DEFAULT_LIFE} when
* the length is unknown. Pure.
*/
static float lifeSeconds(boolean maya, int mayaDuration, int[] frameLengths)
{
if (maya ? mayaDuration <= 0 : frameLengths == null || frameLengths.length == 0)
return DEFAULT_LIFE;
long cycles = maya ? mayaDuration : Arrays.stream(frameLengths).asLongStream().sum();
return Math.max(0.3f, Math.min(MAX_LIFE, cycles * 0.02f));
}
}
