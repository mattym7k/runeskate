package com.gielinorskate.party;

import com.gielinorskate.render.BufferProbe;
import com.gielinorskate.render.LayeredBody;
import net.runelite.api.gameval.AnimationID;

/**
 * What a party ghost's body is drawn as now and, for every fallback, exactly why: the ::skateghosts line, and a
 * signature to log state changes by. Written by the renderer and the body controller as they go (enum and int
 * fields only: no allocation per frame); the text is built only when asked for. Pure.
 */
final class GhostBodyStatus
{
	/** How the body is drawn. */
	enum Mode
	{
		NONE("none"),
		/** The member's character as the client hands it out, or our snapshot without pose or animation. */
		STATIC("static"),
		/** Our snapshot (or their live frame) with the procedural pose. */
		PROCEDURAL("procedural"),
		/** Our snapshot animated, no procedural pose (a far ghost). */
		ANIM("anim"),
		ANIM_PROCEDURAL("anim+procedural");

		final String label;

		Mode(String label)
		{
			this.label = label;
		}
	}

	/** Why the body is not drawn from our snapshot (OK: it is). */
	enum Body
	{
		OK,
		NO_NAME,
		NOT_IN_SCENE,
		FAILED,
		NOT_PROVEN,
		NO_SNAPSHOT
	}

	/** Why no OSRS animation goes on the snapshot (OK: one does, or is asked for). */
	enum Layer
	{
		OK,
		STANDING,
		FAILED,
		WEAPON_IDLE,
		/** A Maya (skeletal) animation failed on this ghost: only classic ones play. */
		MAYA_FAILED,
		NOT_LOADED,
		NO_FRAMES
	}

	private boolean hasBody;
	private Body body = Body.NO_NAME;
	private boolean failed;
	private String error;
	private BufferProbe.Fail fail = BufferProbe.Fail.NONE;
	private LayeredBody.Outcome outcome = LayeredBody.Outcome.NOT_PROVEN;
	private GhostAnim anim = GhostAnim.NONE;
	private int animId = -1;
	private Layer layer = Layer.STANDING;
	private String layerError;
	private boolean full;
	private int rank;
	private float distance;
	private int actorAnim = -1;
	private int actorPose = -1;
	private int idlePose = -1;
	private int npc = -1;

	/** How a body is drawn: from whether there is one, whether it failed, the draw's path and the detail level. */
	static Mode mode(boolean hasBody, boolean failed, LayeredBody.Outcome outcome, boolean full)
	{
		if (!hasBody)
		{
			return Mode.NONE;
		}
		if (failed)
		{
			return Mode.STATIC;
		}
		switch (outcome)
		{
			case LAYERED:
				return full ? Mode.ANIM_PROCEDURAL : Mode.ANIM;
			case LIVE:
			case SNAPSHOT:
			case LAYER_SKIPPED:
			case NOT_IN_PLACE:
				return full ? Mode.PROCEDURAL : Mode.STATIC;
			default:
				return Mode.STATIC;
		}
	}

	/** The detail budget's verdict: full (the procedural pose) or not, the rank by nearness and the distance. */
	void detail(boolean full, int rank, float distance)
	{
		this.full = full;
		this.rank = rank;
		this.distance = distance;
	}

	/** No body is drawn, for {@code why} (NO_NAME or NOT_IN_SCENE). */
	void noBody(Body why)
	{
		hasBody = false;
		body = why;
	}

	/** A body was drawn: failed for good ({@code error} then says why), else the probe's verdict and the path. */
	void drawn(boolean failed, String error, BufferProbe.Fail fail, LayeredBody.Outcome outcome)
	{
		hasBody = true;
		this.failed = failed;
		this.error = error;
		this.fail = fail;
		this.outcome = outcome;
		if (failed)
		{
			body = Body.FAILED;
		}
		else if (outcome == LayeredBody.Outcome.NOT_PROVEN)
		{
			body = Body.NOT_PROVEN;
		}
		else if (outcome == LayeredBody.Outcome.LIVE)
		{
			body = Body.NO_SNAPSHOT;
		}
		else
		{
			body = Body.OK;
		}
	}

	/** The animation the ghost wants and why it is not layered (OK when it is asked for). */
	void layer(GhostAnim anim, int animId, Layer layer, String error)
	{
		this.anim = anim;
		this.animId = animId;
		this.layer = layer;
		this.layerError = error;
	}

	/** The member's real character as read (never changed): its action and pose animation, idle pose, NPC form. */
	void idle(int animation, int pose, int idle, int npcTransform)
	{
		actorAnim = animation;
		actorPose = pose;
		idlePose = idle;
		npc = npcTransform;
	}

	Mode mode()
	{
		return mode(hasBody, failed, outcome, full);
	}

	/** Equal while nothing a player would see changed (not the distance or rank). No allocation. */
	int signature()
	{
		int h = mode().ordinal();
		h = 31 * h + body.ordinal();
		h = 31 * h + fail.ordinal();
		h = 31 * h + outcome.ordinal();
		h = 31 * h + layer.ordinal();
		h = 31 * h + anim.ordinal();
		return 31 * h + (full ? 1 : 0);
	}

	boolean changed(int signature)
	{
		return signature() != signature;
	}

	/** The one-line ::skateghosts status, the name escaped for the chatbox. */
	String line(String name)
	{
		StringBuilder b = new StringBuilder(160);
		b.append(name == null ? "(unknown)" : escape(name)).append(": ");
		b.append(Math.round(distance / 128f)).append(" tiles, ");
		if (full)
		{
			b.append("full detail (nearest #").append(rank + 1).append(')');
		}
		else
		{
			b.append("animation only (nearest #").append(rank + 1).append(": only the nearest ")
				.append(GhostDetail.MAX_FULL).append(" within ").append(Math.round(GhostDetail.FULL_RANGE / 128f))
				.append(" tiles get the pose)");
		}
		Mode mode = mode();
		b.append(", body ").append(mode.label);
		if (mode == Mode.ANIM || mode == Mode.ANIM_PROCEDURAL)
		{
			b.append(" (").append(anim.name()).append(')');
		}
		int start = b.length();
		bodyReason(b);
		if (hasBody && !failed && body == Body.OK)
		{
			layerReason(b);
		}
		if (b.length() > start)
		{
			b.insert(start, ": ");
		}
		return b.toString();
	}

	private void bodyReason(StringBuilder b)
	{
		switch (body)
		{
			case NO_NAME:
				b.append("the member's name is not known yet");
				break;
			case NOT_IN_SCENE:
				b.append("their real character is not in your player list (it stays where they started skating;"
					+ " the game only shows players near your own character)");
				break;
			case FAILED:
				b.append("off after an error (").append(error).append(')');
				break;
			case NOT_PROVEN:
				b.append("not proven a per-request buffer (").append(failText(fail)).append(')');
				break;
			case NO_SNAPSHOT:
				if (npc != -1)
				{
					b.append("no snapshot yet: their character is transformed into NPC ").append(npc);
				}
				else
				{
					b.append("no snapshot yet: their character is not in plain idle (anim ").append(actorAnim)
						.append(", pose ").append(actorPose).append(", idle ").append(idlePose).append(')');
				}
				break;
			default:
				break;
		}
	}

	private void layerReason(StringBuilder b)
	{
		if (anim == GhostAnim.NONE)
		{
			return;
		}
		switch (outcome)
		{
			case LAYER_SKIPPED:
				if (layer == Layer.OK)
				{
					b.append("anim not applied");
					return;
				}
				break;
			case NOT_IN_PLACE:
				b.append("anim off: the client animated a copy, not our canvas");
				return;
			case CANVAS_CHANGED:
				b.append("anim off: the client replaced our canvas's vertices (drawn as it left them)");
				return;
			default:
				break;
		}
		switch (layer)
		{
			case FAILED:
				b.append("anim off after an error (").append(layerError).append(')');
				break;
			case WEAPON_IDLE:
				b.append("anim off: their idle stance ").append(idlePose).append(" is not the plain ")
					.append(AnimationID.HUMAN_READY).append(" (a weapon's stance would twist every animation)");
				break;
			case MAYA_FAILED:
				b.append("anim off: Maya animation ").append(animId).append(" failed (").append(layerError)
					.append("); classic ones still play");
				break;
			case NOT_LOADED:
				b.append("anim off: animation ").append(animId).append(" did not load");
				break;
			case NO_FRAMES:
				b.append("anim off: animation ").append(animId).append(" has no frames");
				break;
			default:
				break;
		}
	}

	private static String failText(BufferProbe.Fail fail)
	{
		switch (fail)
		{
			case NO_MODEL:
				return "their character has no model";
			case BAD_MESH:
				return "an empty mesh";
			case NOT_REBUILT:
				return "a cached model, not rebuilt per request";
			case OTHER_OBJECT:
				return "a new model each request, e.g. a spot anim on them";
			case BAD_ARRAYS:
				return "vertex arrays shorter than the count";
			default:
				return "unknown";
		}
	}

	/**
	 * The ::skateghosts line of a ghost that is not drawn: hidden ({@code shown} false), no board model, another
	 * world or plane, or outside the loaded scene.
	 */
	static String notDrawn(String name, boolean shown, boolean sameSpace, boolean inScene, boolean boardFailed)
	{
		String who = name == null ? "(unknown)" : escape(name);
		if (!shown)
		{
			return who + ": not drawn: ghosts are hidden now (Show party skaters off, a PvP area or an instance)";
		}
		if (boardFailed)
		{
			return who + ": not drawn: the board model could not be built";
		}
		if (!sameSpace)
		{
			return who + ": not drawn: on another world or plane";
		}
		if (!inScene)
		{
			return who + ": not drawn: outside your loaded scene";
		}
		return who + ": not drawn yet";
	}

	/** Chatbox tags in a name shown as text. */
	static String escape(String s)
	{
		StringBuilder b = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '<')
			{
				b.append("<lt>");
			}
			else if (c == '>')
			{
				b.append("<gt>");
			}
			else
			{
				b.append(c);
			}
		}
		return b.toString();
	}
}
