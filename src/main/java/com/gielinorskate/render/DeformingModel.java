package com.gielinorskate.render;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Model;

/**
 * An actor's current (animated) model with a procedural {@link BodyPose} applied to its vertices when
 * {@link BufferProbe} proves this frame that the model is the client's per-request animation buffer (so the
 * edit never reaches the cached base model). Otherwise, or after any exception (which switches deformation
 * off for this instance), the plain model is returned. Shared by the local puppet and party ghost bodies.
 * Client thread only; no allocation per frame.
 */
@Slf4j
public final class DeformingModel
{
	static final BufferProbe.Mesh<Model> MESH = new BufferProbe.Mesh<Model>()
	{
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

	private final BufferProbe.Source<Model> source;
	private final BufferProbe.Probe<Model> probe = new BufferProbe.Probe<>();
	private final BodyPose pose;
	private boolean disabled;
	/** Reads the drawn mesh's hand for the carried board (local puppet only); null when none or after a failure. */
	private HandAnchor hand;
	/** Told whether each draw had a model (local puppet only); null when none. */
	private DrawnModelReport drawnReport;

	/** {@code source} returns the actor's model on each call (e.g. {@code player.getModel()}), or null. */
	public DeformingModel(Supplier<Model> source, BodyPose pose)
	{
		this(source, pose, null);
	}

	/** As above, and every drawn model's hand is read into {@code hand} (while it is active). */
	public DeformingModel(Supplier<Model> source, BodyPose pose, HandAnchor hand)
	{
		this.source = source::get;
		this.pose = pose;
		this.hand = hand;
	}

	/** Every later {@link #get()} reports whether it had a model to {@code report} (null: none). */
	public void setDrawnReport(DrawnModelReport report)
	{
		this.drawnReport = report;
	}

	/** False once deformation failed and was switched off. */
	public boolean canDeform()
	{
		return !disabled;
	}

	/** The model to draw now. */
	public Model get()
	{
		Model m;
		if (disabled)
		{
			m = source.get();
		}
		else
		{
			try
			{
				m = BufferProbe.drawable(source, MESH, pose, probe);
			}
			catch (RuntimeException ex)
			{
				disabled = true;
				log.warn("Skater mesh deformation failed, drawing the plain animation from now on", ex);
				m = source.get();
			}
		}
		readHand(m);
		DrawnModelReport r = drawnReport;
		if (r != null)
		{
			r.report(m != null);
		}
		return m;
	}

	/** Reads (never writes) the drawn mesh's hand. A failure stops the reading; the board then hangs at its fixed spot. */
	private void readHand(Model m)
	{
		HandAnchor h = hand;
		if (h == null || m == null || !h.isActive())
		{
			return;
		}
		try
		{
			h.sample(m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(), m.getVerticesCount());
		}
		catch (RuntimeException ex)
		{
			hand = null;
			log.warn("Could not read the skater's hand; the carried board stays at its fixed spot from now on", ex);
		}
	}
}
