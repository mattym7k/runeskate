package com.gielinorskate.feedback;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * Client-side particle bursts: a game graphic (spotanim) drawn once as a RuneLiteObject at a point, then
 * removed. The graphic's model and animation come from its cache definition ({@link SpotanimDef}). At most
 * {@link #MAX_LIVE} are alive at once; the oldest makes way. Nothing here is seen by other players or the
 * server. Client thread.
 */
@Slf4j
final class SpotEffects
{
	static final int MAX_LIVE = 8;
	/** Client animation frame lengths count 20 ms client cycles. */
	private static final float SECONDS_PER_CYCLE = 0.02f;
	private static final float MIN_LIFE = 0.3f;
	/**
	 * Upper bound on a graphic's life: long enough for the level-99 fireworks to play out in full, short enough
	 * that a bad length in the cache cannot keep an object alive for long.
	 */
	static final float MAX_LIFE = 8f;
	/** Life of a graphic without an animation, or with one whose length is unknown. */
	private static final float DEFAULT_LIFE = 0.8f;
	/** A model may still be loading; give up on a graphic after this many tries. */
	private static final int MAX_LOAD_TRIES = 30;
	/**
	 * How the client lights a spotanim's model: its ambient and contrast on top of these bases, with this light
	 * direction (as the game's own graphic definition does).
	 */
	private static final int LIGHT_AMBIENT_BASE = 64;
	private static final int LIGHT_CONTRAST_BASE = 850;
	private static final int LIGHT_X = -30;
	private static final int LIGHT_Y = -50;
	private static final int LIGHT_Z = -30;

	/** A graphic ready to draw. */
	private static final class Loaded
	{
		final Model model;
		final Animation animation;
		final float life;

		Loaded(Model model, Animation animation, float life)
		{
			this.model = model;
			this.animation = animation;
			this.life = life;
		}
	}

	private final Client client;
	private final EffectSlots<RuneLiteObject> live = new EffectSlots<>(MAX_LIVE);
	private final Map<Integer, Loaded> loaded = new HashMap<>();
	/** Load attempts per graphic still loading; a graphic at {@link #MAX_LOAD_TRIES} is never tried again. */
	private final Map<Integer, Integer> tries = new HashMap<>();

	SpotEffects(Client client)
	{
		this.client = client;
	}

	/** Shows graphic {@code spotanimId} at local (x, y), height h (up-positive, as physics). */
	void spawn(int spotanimId, float x, float y, float h, WorldView wv, float now)
	{
		Loaded graphic = load(spotanimId);
		if (graphic == null)
		{
			return;
		}
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
		{
			evicted.setActive(false);
		}
	}

	/** Removes graphics whose time is up. */
	void update(float now)
	{
		for (RuneLiteObject obj : live.expire(now))
		{
			obj.setActive(false);
		}
	}

	int liveCount()
	{
		return live.size();
	}

	void clear()
	{
		for (RuneLiteObject obj : live.clear())
		{
			obj.setActive(false);
		}
	}

	private Loaded load(int spotanimId)
	{
		Loaded cached = loaded.get(spotanimId);
		if (cached != null)
		{
			return cached;
		}
		int attempt = tries.getOrDefault(spotanimId, 0);
		if (attempt >= MAX_LOAD_TRIES)
		{
			return null;
		}
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
		if (def.recolorFrom != null && def.recolorFrom.length > 0)
		{
			data = data.shallowCopy().cloneColors();
			for (int i = 0; i < def.recolorFrom.length; i++)
			{
				data.recolor(def.recolorFrom[i], def.recolorTo[i]);
			}
		}
		if (def.resizeX != SpotanimDef.DEFAULT_RESIZE || def.resizeY != SpotanimDef.DEFAULT_RESIZE)
		{
			data = data.shallowCopy().cloneVertices().scale(def.resizeX, def.resizeY, def.resizeX);
		}
		Model model = data.light(LIGHT_AMBIENT_BASE + def.ambient, LIGHT_CONTRAST_BASE + def.contrast,
			LIGHT_X, LIGHT_Y, LIGHT_Z);
		if (model == null)
		{
			return null;
		}
		Animation animation = def.animationId >= 0 ? client.loadAnimation(def.animationId) : null;
		Loaded graphic = new Loaded(model, animation, lifeOf(animation));
		loaded.put(spotanimId, graphic);
		tries.remove(spotanimId);
		return graphic;
	}

	private static float lifeOf(Animation animation)
	{
		if (animation == null)
		{
			return DEFAULT_LIFE;
		}
		return animation.isMayaAnim()
			? lifeSeconds(true, animation.getDuration(), null)
			: lifeSeconds(false, 0, animation.getFrameLengths());
	}

	/**
	 * How long a graphic lives: the animation's own length (summed frame lengths, or a maya animation's duration,
	 * both in 20 ms client cycles), bounded to [{@link #MIN_LIFE}, {@link #MAX_LIFE}]; {@link #DEFAULT_LIFE} when
	 * the length is unknown. Pure.
	 */
	static float lifeSeconds(boolean maya, int mayaDuration, int[] frameLengths)
	{
		long cycles = 0;
		if (maya)
		{
			cycles = mayaDuration;
		}
		else if (frameLengths != null)
		{
			for (int f : frameLengths)
			{
				cycles += f;
			}
		}
		if ((maya && mayaDuration <= 0) || (!maya && (frameLengths == null || frameLengths.length == 0)))
		{
			return DEFAULT_LIFE;
		}
		return Math.max(MIN_LIFE, Math.min(MAX_LIFE, cycles * SECONDS_PER_CYCLE));
	}
}
