package com.gielinorskate.render;

/**
 * Decides, every frame, whether the model the client hands out may be deformed in place, and does it.
 * Pure (generic over the model type) so the safety logic is unit-tested without a client.
 *
 * <p>A model may be edited only when it is a buffer the client rebuilds on every request (the shared
 * animation buffer of an animated actor): then the edit lasts until the next rebuild and never reaches the
 * cached base model it was built from. The cached base model itself (returned when nothing is animating)
 * must never be edited, since edits there would persist and pile up frame after frame.
 *
 * <p>Test: take the model, add {@link #SENTINEL} to its vertex 0 x, request the model again. If the
 * second request overwrote the sentinel and returned the same object, the buffer is rebuilt per request
 * (proved for this frame), so the second result is deformed. Otherwise the sentinel is put back at once
 * (nothing else ran in between: same thread, no draw) and the second result is drawn as is.
 */
public final class BufferProbe
{
	/** Added to vertex 0's x as the probe: no rebuild of a real model lands exactly on it. */
	static final float SENTINEL = 1031f;

	public interface Source<M>
	{
		M get();
	}

	public interface Mesh<M>
	{
		int count(M m);

		float[] xs(M m);

		float[] ys(M m);

		float[] zs(M m);

		/** Called after {@code m} was deformed (to refresh bounds). */
		void deformed(M m);
	}

	private BufferProbe()
	{
	}

	/** Why {@link #prove} did not prove a model (for the ::skateghosts status). */
	public enum Fail
	{
		NONE,
		/** No model was handed out. */
		NO_MODEL,
		/** No vertices, or vertex 0 is not a number. */
		BAD_MESH,
		/** The second request did not rebuild the first model: a cached (persistent) model. */
		NOT_REBUILT,
		/** Rebuilt, but the second request handed out another object (a merged model, e.g. with a spot anim). */
		OTHER_OBJECT,
		/** The vertex arrays do not cover the vertex count. */
		BAD_ARRAYS
	}

	/** The result of {@link #prove}: the model to draw, whether it is provably a per-request buffer, and why not. */
	public static final class Probe<M>
	{
		M drawn;
		boolean proven;
		Fail fail = Fail.NONE;

		public Fail fail()
		{
			return fail;
		}
	}

	/** The model to draw this frame, deformed by {@code pose} when that is provably safe. */
	static <M> M drawable(Source<M> source, Mesh<M> mesh, BodyPose pose)
	{
		return drawable(source, mesh, pose, new Probe<>());
	}

	/** As above, with {@code probe} as scratch (no allocation). */
	static <M> M drawable(Source<M> source, Mesh<M> mesh, BodyPose pose, Probe<M> probe)
	{
		if (pose.isNeutral())
		{
			return source.get();
		}
		if (!prove(source, mesh, probe))
		{
			return probe.drawn;
		}
		M b = probe.drawn;
		MeshDeformer.deform(mesh.xs(b), mesh.ys(b), mesh.zs(b), mesh.count(b), pose);
		mesh.deformed(b);
		return b;
	}

	/**
	 * Requests the model twice with the probe in between. True when the second model is provably the buffer the
	 * client rebuilds on every request (the same object, the probe overwritten) with vertex arrays covering its
	 * count: then {@code out.drawn} may be edited in place for this frame. Otherwise {@code out.drawn} is the model
	 * to draw as is (null when there is none) and nothing was left changed.
	 */
	static <M> boolean prove(Source<M> source, Mesh<M> mesh, Probe<M> out)
	{
		out.proven = false;
		out.fail = Fail.NO_MODEL;
		M a = source.get();
		out.drawn = a;
		if (a == null)
		{
			return false;
		}
		int n = mesh.count(a);
		float[] ax = mesh.xs(a);
		if (n <= 0 || ax == null || ax.length < n || Float.isNaN(ax[0]) || Float.isInfinite(ax[0]))
		{
			out.fail = Fail.BAD_MESH;
			return false;
		}
		float orig = ax[0];
		float mark = orig + SENTINEL;
		ax[0] = mark;
		M b;
		try
		{
			b = source.get();
		}
		catch (RuntimeException ex)
		{
			if (ax[0] == mark)
			{
				ax[0] = orig;
			}
			throw ex;
		}
		boolean rebuilt = ax[0] != mark;
		if (!rebuilt)
		{
			// a persistent model: undo the probe before anything can see it
			ax[0] = orig;
		}
		out.drawn = b != null ? b : a;
		if (!rebuilt || b != a)
		{
			out.fail = !rebuilt ? Fail.NOT_REBUILT : Fail.OTHER_OBJECT;
			return false;
		}
		int m = mesh.count(b);
		float[] xs = mesh.xs(b);
		float[] ys = mesh.ys(b);
		float[] zs = mesh.zs(b);
		if (m <= 0 || xs == null || ys == null || zs == null || xs.length < m || ys.length < m || zs.length < m)
		{
			out.fail = Fail.BAD_ARRAYS;
			return false;
		}
		out.fail = Fail.NONE;
		out.proven = true;
		return true;
	}
}
