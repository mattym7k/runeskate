package com.gielinorskate.render;

/**
 * A party ghost's body, drawn without ever touching the member's real character: the member's model is requested
 * from the client, and only when {@link BufferProbe} proves this frame that it is the per-request buffer the client
 * rebuilds on every request (so nothing written to it lasts past the draw, nor reaches the member's own model) is it
 * used as a canvas. Our {@link MeshSnapshot} of the member's standing frame is copied over it, an OSRS animation
 * of our choosing ({@link Layer}: the client animating that canvas, never the member's character) goes on top, then
 * the procedural {@link BodyPose}. Without a matching snapshot the frame is only deformed, as before; without the
 * proof it is drawn as is. Pure (generic over the model type), so the safety logic is tested without a client.
 */
public final class LayeredBody
{
	/** A model's vertices and faces. */
	public interface Mesh<M> extends BufferProbe.Mesh<M>
	{
		int faces(M m);
	}

	/**
	 * Animates the canvas in place; returns it (the same object, animated), or null when nothing was applied (the
	 * canvas may then be half written: it is overwritten again).
	 */
	public interface Layer<M>
	{
		M apply(M canvas);
	}

	/** Which way a draw went (for the ::skateghosts status). */
	public enum Outcome
	{
		/** The model was not proven a per-request buffer: drawn as the client handed it out. */
		NOT_PROVEN,
		/** No snapshot of this appearance (and none could be taken): the live frame, deformed, no layer. */
		LIVE,
		/** The snapshot, deformed; no layer was asked for. */
		SNAPSHOT,
		/** The snapshot with the layer applied in place, deformed. */
		LAYERED,
		/** The layer declined (gave null): the plain snapshot, deformed. */
		LAYER_SKIPPED,
		/** The layer gave back another model than the canvas: the plain snapshot, deformed. */
		NOT_IN_PLACE,
		/** The layer replaced the canvas's vertex arrays: drawn as the client left it. */
		CANVAS_CHANGED
	}

	/** Where {@link #drawable} says which way the draw went; one per caller, reused. */
	public static final class Report
	{
		Outcome outcome = Outcome.NOT_PROVEN;

		public Outcome outcome()
		{
			return outcome;
		}
	}

	private LayeredBody()
	{
	}

	/**
	 * The model to draw this frame.
	 *
	 * @param key the member's appearance now: a snapshot taken under another key is not used
	 * @param mayTake the member's character stands in its plain idle now: a missing or stale snapshot may be taken
	 * @param layer the OSRS animation on top of the snapshot, or null for none
	 * @param report told which way the draw went
	 */
	public static <M> M drawable(BufferProbe.Source<M> source, Mesh<M> mesh, MeshSnapshot snap, int key,
		boolean mayTake, Layer<M> layer, BodyPose pose, BufferProbe.Probe<M> probe, Report report)
	{
		report.outcome = Outcome.NOT_PROVEN;
		if (!BufferProbe.prove(source, mesh, probe))
		{
			return probe.drawn;
		}
		M b = probe.drawn;
		int n = mesh.count(b);
		int faces = mesh.faces(b);
		float[] xs = mesh.xs(b);
		float[] ys = mesh.ys(b);
		float[] zs = mesh.zs(b);
		if (snap.matches(n, faces, key))
		{
			snap.copyInto(xs, ys, zs);
		}
		else if (mayTake)
		{
			snap.take(xs, ys, zs, n, faces, key);
		}
		else
		{
			// no snapshot of this appearance yet: the live frame, deformed (no layer: it would double the animation)
			report.outcome = Outcome.LIVE;
			deform(mesh, b, pose);
			return b;
		}
		report.outcome = Outcome.SNAPSHOT;
		if (layer != null)
		{
			M l = layer.apply(b);
			if (mesh.count(b) != n || mesh.xs(b) != xs || mesh.ys(b) != ys || mesh.zs(b) != zs)
			{
				// the canvas is no longer the mesh we copied into: drawn as the client left it
				report.outcome = Outcome.CANVAS_CHANGED;
				return b;
			}
			if (l != b)
			{
				// not animated in place: the plain snapshot (the canvas may have been half written)
				report.outcome = l == null ? Outcome.LAYER_SKIPPED : Outcome.NOT_IN_PLACE;
				snap.copyInto(xs, ys, zs);
			}
			else
			{
				report.outcome = Outcome.LAYERED;
			}
		}
		deform(mesh, b, pose);
		return b;
	}

	private static <M> void deform(Mesh<M> mesh, M b, BodyPose pose)
	{
		if (!pose.isNeutral())
		{
			MeshDeformer.deform(mesh.xs(b), mesh.ys(b), mesh.zs(b), mesh.count(b), pose);
		}
		mesh.deformed(b);
	}
}
