package com.gielinorskate.party;

import com.gielinorskate.render.AnimClock;
import com.gielinorskate.render.BodyPose;
import com.gielinorskate.render.BufferProbe;
import com.gielinorskate.render.HandAnchor;
import com.gielinorskate.render.LayeredBody;
import com.gielinorskate.render.MeshSnapshot;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.RuneLiteObjectController;

/**
 * A party ghost's body, drawn as the local skater's is (the riding stance, walk and run, the lie-down and get-up,
 * with the procedural {@link BodyPose} on top) without ever touching the member's real character on this client:
 * its animations, frames and model are only read. Each draw takes the member's model as the client hands it out
 * and, only when that is provably the per-request buffer the client rebuilds on every request ({@link LayeredBody}),
 * draws our own {@link MeshSnapshot} of the member standing (taken once while their character stands in its plain
 * idle, again when their appearance changes) in it, with our OSRS animation applied by the client to that canvas
 * ({@code Client.applyTransformations}: it animates the model it is given, not an actor) and the pose deformed in.
 * Without that proof the member's model is drawn as is; any failure falls back for this ghost (the layer alone, or
 * the whole body: then the member's model is drawn plain). Client thread only; no allocation per frame.
 */
@Slf4j
final class GhostBodyController extends RuneLiteObjectController
{
	/** The body as animated, no procedural pose (a far ghost's, see GhostDetail). */
	private static final BodyPose NEUTRAL = new BodyPose();

	private static final LayeredBody.Mesh<Model> MESH = new LayeredBody.Mesh<Model>()
	{
		@Override
		public int faces(Model m)
		{
			return m.getFaceCount();
		}

		@Override
		public int count(Model m)
		{
			return m.getVerticesCount();
		}

		@Override
		public float[] xs(Model m)
		{
			return m.getVerticesX();
		}

		@Override
		public float[] ys(Model m)
		{
			return m.getVerticesY();
		}

		@Override
		public float[] zs(Model m)
		{
			return m.getVerticesZ();
		}

		@Override
		public void deformed(Model m)
		{
			m.calculateBoundsCylinder();
		}
	};

	private final Client client;
	private final BodyPose pose;
	private final MeshSnapshot snapshot = new MeshSnapshot();
	private final BufferProbe.Probe<Model> probe = new BufferProbe.Probe<>();
	/** Reads the drawn body's right hand while the board is carried. */
	private final HandAnchor hand = new HandAnchor();
	private final AnimClock clock = new AnimClock();
	private final LayeredBody.Layer<Model> layer = this::applyLayer;
	private final BufferProbe.Source<Model> source = () ->
	{
		Player q = this.player;
		return q == null ? null : q.getModel();
	};
	private Player player;
	/** The member's display name, for the state-change log. */
	private String name;
	/** What the body is drawn as and why (::skateghosts), and its signature last logged. */
	private final GhostBodyStatus status = new GhostBodyStatus();
	private final LayeredBody.Report report = new LayeredBody.Report();
	private int loggedSignature;
	/** Why no OSRS animation is layered, as of the last {@link #animate}. */
	private GhostBodyStatus.Layer layerWhy = GhostBodyStatus.Layer.STANDING;
	private String bodyError;
	private String layerError;

	/** The animation playing now and its frame (-1: none), set each frame by {@link #animate}. */
	private GhostAnim anim = GhostAnim.NONE;
	private int animId = -1;
	private Animation animation;
	/** Its frame lengths: a classic animation's own, or one tick per frame for a Maya one (AnimClock.tickFrames). */
	private int[] lengths;
	private int frame = -1;
	/** The procedural pose is drawn (the ghost is near enough, GhostDetail). */
	private boolean full = true;
	/** The last draw had our animation on it. */
	private boolean layered;
	/** The OSRS layer failed once: this ghost plays none from then on. */
	private boolean layerFailed;
	/** A Maya animation failed once: this ghost plays only classic ones from then on (walk, run, the get-up). */
	private boolean mayaFailed;
	/** The body failed once: this ghost is drawn as the member's plain model from then on. */
	private boolean failed;

	GhostBodyController(Client client, BodyPose pose)
	{
		this.client = client;
		this.pose = pose;
	}

	void setPlayer(Player player)
	{
		if (player != this.player)
		{
			snapshot.clear();
		}
		this.player = player;
	}

	Player getPlayer()
	{
		return player;
	}

	void setName(String name)
	{
		this.name = name;
	}

	/** No body is drawn now (no member's character to draw from), for {@code why}. */
	void noBody(GhostBodyStatus.Body why)
	{
		status.noBody(why);
	}

	GhostBodyStatus status()
	{
		return status;
	}

	/** Logs (debug) the status when what is drawn changed since the last log. Once a frame; cheap when unchanged. */
	void logChanges()
	{
		if (status.changed(loggedSignature))
		{
			loggedSignature = status.signature();
			if (log.isDebugEnabled())
			{
				log.debug("Party ghost body: {}", status.line(name));
			}
		}
	}

	HandAnchor hand()
	{
		return hand;
	}

	/** The procedural pose is drawn (near) or only the animation (far); the rank and distance it was decided by. */
	void setFull(boolean full, int rank, float distance)
	{
		this.full = full;
		status.detail(full, rank, distance);
	}

	/** The last draw showed our OSRS animation (a lie-down or get-up then needs no procedural fall). */
	boolean isLayered()
	{
		return layered;
	}

	/**
	 * This frame's animation: {@code a} at {@code rate} times the game's pace for a looping one, else at
	 * {@code progress} (0..1) through it. Client thread, before the draw.
	 */
	void animate(GhostAnim a, float rate, float progress, float dt)
	{
		Player p = player;
		int id = p == null || layerFailed ? -1 : a.id(p.getWalkAnimation(), p.getRunAnimation());
		if (a != anim || id != animId)
		{
			anim = a;
			animId = id;
			animation = null;
			clock.reset();
		}
		if (id < 0)
		{
			frame = -1;
			layerWhy = layerFailed ? GhostBodyStatus.Layer.FAILED : GhostBodyStatus.Layer.STANDING;
			status.layer(a, id, layerWhy, layerError);
			return;
		}
		if (animation == null)
		{
			try
			{
				Animation loaded = client.loadAnimation(id);
				if (loaded != null && loaded.isMayaAnim() && mayaFailed)
				{
					animation = null;
					frame = -1;
					layerWhy = GhostBodyStatus.Layer.MAYA_FAILED;
					status.layer(a, id, layerWhy, layerError);
					return;
				}
				animation = loaded;
				// a Maya (skeletal) animation's frame is the tick within its duration
				lengths = loaded == null ? null
					: loaded.isMayaAnim() ? AnimClock.tickFrames(loaded.getDuration()) : loaded.getFrameLengths();
				layerWhy = loaded == null ? GhostBodyStatus.Layer.NOT_LOADED : GhostBodyStatus.Layer.OK;
			}
			catch (RuntimeException ex)
			{
				failLayer(ex);
				frame = -1;
				status.layer(a, id, layerWhy, layerError);
				return;
			}
		}
		Animation an = animation;
		if (an == null)
		{
			frame = -1;
			status.layer(a, id, layerWhy, layerError);
			return;
		}
		frame = a.loops() ? clock.loop(lengths, rate, dt) : clock.seek(lengths, progress);
		layerWhy = frame < 0 ? GhostBodyStatus.Layer.NO_FRAMES : GhostBodyStatus.Layer.OK;
		status.layer(a, id, layerWhy, layerError);
	}

	@Override
	public Model getModel()
	{
		Player p = player;
		if (p == null)
		{
			return null;
		}
		if (failed)
		{
			layered = false;
			status.drawn(true, bodyError, BufferProbe.Fail.NONE, LayeredBody.Outcome.NOT_PROVEN);
			return p.getModel();
		}
		try
		{
			return draw(p);
		}
		catch (RuntimeException ex)
		{
			failed = true;
			layered = false;
			bodyError = describe(ex);
			status.drawn(true, bodyError, BufferProbe.Fail.NONE, LayeredBody.Outcome.NOT_PROVEN);
			log.warn("A party ghost's body failed; it is drawn as the member's plain character from now on", ex);
			return p.getModel();
		}
	}

	private Model draw(Player p)
	{
		PlayerComposition c = p.getPlayerComposition();
		int idle = p.getIdlePoseAnimation();
		int npc = c == null ? -1 : c.getTransformedNpcId();
		int key = c == null ? 0 : GhostAppearance.key(c.getEquipmentIds(), c.getColors(), c.getGender(), npc, idle);
		int actorAnim = p.getAnimation();
		int actorPose = p.getPoseAnimation();
		boolean mayTake = c != null && GhostAppearance.mayTake(actorAnim, actorPose, idle, npc);
		boolean layerable = GhostAppearance.layerable(idle);
		boolean useLayer = !layerFailed && frame >= 0 && animation != null && layerable;
		layered = false;
		Model m = LayeredBody.drawable(source, MESH, snapshot, key, mayTake, useLayer ? layer : null,
			full ? pose : NEUTRAL, probe, report);
		status.idle(actorAnim, actorPose, idle, npc);
		status.drawn(false, null, probe.fail(), report.outcome());
		if (!layerable && layerWhy == GhostBodyStatus.Layer.OK)
		{
			status.layer(anim, animId, GhostBodyStatus.Layer.WEAPON_IDLE, null);
		}
		else if (layerFailed)
		{
			status.layer(anim, animId, GhostBodyStatus.Layer.FAILED, layerError);
		}
		readHand(m);
		return m;
	}

	/** Our animation on the canvas (the snapshot in the proven per-request buffer); null when not applied. */
	private Model applyLayer(Model canvas)
	{
		Animation a = animation;
		int f = frame;
		if (a == null || f < 0 || layerFailed)
		{
			return null;
		}
		try
		{
			Model r = client.applyTransformations(canvas, a, f, null, 0);
			layered = r == canvas;
			return r;
		}
		catch (RuntimeException ex)
		{
			if (a.isMayaAnim())
			{
				// only the skeletal kind failed: the classic animations (walk, run, the get-up) still play
				mayaFailed = true;
				layerError = describe(ex);
				animation = null;
				frame = -1;
				layerWhy = GhostBodyStatus.Layer.MAYA_FAILED;
				log.warn("Could not play a Maya animation on a party ghost; it plays only classic ones from now on",
					ex);
				return null;
			}
			failLayer(ex);
			return null;
		}
	}

	private void failLayer(RuntimeException ex)
	{
		layerFailed = true;
		layerWhy = GhostBodyStatus.Layer.FAILED;
		layerError = describe(ex);
		animation = null;
		frame = -1;
		log.warn("Could not animate a party ghost; it keeps the procedural pose only from now on", ex);
	}

	/** An error in a few words, for the status line. */
	private static String describe(RuntimeException ex)
	{
		String m = ex.getMessage();
		return m == null ? ex.getClass().getSimpleName() : ex.getClass().getSimpleName() + ": " + m;
	}

	/** Reads (never writes) the drawn body's hand; a failure stops the reading (the board then hangs at its spot). */
	private void readHand(Model m)
	{
		if (m == null || !hand.isActive())
		{
			return;
		}
		try
		{
			hand.sample(m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(), m.getVerticesCount());
		}
		catch (RuntimeException ex)
		{
			hand.setActive(false);
			log.debug("Could not read a party ghost's hand", ex);
		}
	}
}
