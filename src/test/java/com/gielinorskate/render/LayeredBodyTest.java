package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LayeredBodyTest
{
	/** A model with vertex arrays (longer than the count, like the client's shared buffer) and a face count. */
	static final class Fake
	{
		int count;
		int faces = 2;
		float[] xs = new float[8];
		float[] ys = new float[8];
		float[] zs = new float[8];
		int deformedCalls;
	}

	static final LayeredBody.Mesh<Fake> MESH = new LayeredBody.Mesh<Fake>()
	{
		@Override
		public int count(Fake m)
		{
			return m.count;
		}

		@Override
		public int faces(Fake m)
		{
			return m.faces;
		}

		@Override
		public float[] xs(Fake m)
		{
			return m.xs;
		}

		@Override
		public float[] ys(Fake m)
		{
			return m.ys;
		}

		@Override
		public float[] zs(Fake m)
		{
			return m.zs;
		}

		@Override
		public void deformed(Fake m)
		{
			m.deformedCalls++;
		}
	};

	/** The member's real character as the client hands it out: its idle frame rebuilt into one shared buffer. */
	static final class Member implements BufferProbe.Source<Fake>
	{
		final Fake base = new Fake();
		final Fake shared = new Fake();
		/** Added to every x of the idle frame (the real character's own animation moving). */
		float idleOffset;
		boolean persistent;

		Member()
		{
			base.count = 3;
			float[] y = {0f, -90f, -200f};
			for (int i = 0; i < 3; i++)
			{
				base.xs[i] = 4f * i;
				base.ys[i] = y[i];
				base.zs[i] = -i;
			}
		}

		@Override
		public Fake get()
		{
			if (persistent)
			{
				return base;
			}
			shared.count = base.count;
			shared.faces = base.faces;
			for (int i = 0; i < base.count; i++)
			{
				shared.xs[i] = base.xs[i] + idleOffset;
				shared.ys[i] = base.ys[i];
				shared.zs[i] = base.zs[i];
			}
			return shared;
		}
	}

	/** An OSRS animation applied in place: every vertex 100 units up. */
	static final LayeredBody.Layer<Fake> LIFT = m ->
	{
		for (int i = 0; i < m.count; i++)
		{
			m.ys[i] -= 100f;
		}
		return m;
	};

	private final MeshSnapshot snap = new MeshSnapshot();
	private final BufferProbe.Probe<Fake> probe = new BufferProbe.Probe<>();
	private final BodyPose neutral = new BodyPose();

	private Fake draw(Member m, int key, boolean mayTake, LayeredBody.Layer<Fake> layer, BodyPose pose)
	{
		return LayeredBody.drawable(m, MESH, snap, key, mayTake, layer, pose, probe);
	}

	@Test
	public void theFirstIdleFrameIsSnapshottedAndDrawnFromThen()
	{
		Member m = new Member();
		Fake f = draw(m, 7, true, null, neutral);
		assertSame(m.shared, f);
		assertTrue(snap.matches(3, 2, 7));
		// the real character moves on; the ghost keeps the snapshot
		m.idleOffset = 30f;
		f = draw(m, 7, false, null, neutral);
		assertEquals(0f, f.xs[0], 0f);
		assertEquals(8f, f.xs[2], 0f);
		// and the real character's own model was never edited
		assertEquals(0f, m.base.xs[0], 0f);
	}

	@Test
	public void theLayerAnimatesTheSnapshotNotTheLiveFrame()
	{
		Member m = new Member();
		draw(m, 7, true, null, neutral);
		m.idleOffset = 30f;
		Fake f = draw(m, 7, false, LIFT, neutral);
		assertEquals(-100f, f.ys[0], 0f);
		assertEquals(0f, f.xs[0], 0f);
		assertEquals(0f, m.base.ys[0], 0f);
	}

	@Test
	public void noSnapshotYetDrawsTheLiveFrameDeformedAsBefore()
	{
		Member m = new Member();
		BodyPose lean = new BodyPose();
		lean.legScale = 0.7f;
		Fake f = draw(m, 7, false, LIFT, lean);
		assertSame(m.shared, f);
		assertFalse((snap.count >= 0));
		// not lifted: no layer without a snapshot of the plain frame
		assertTrue("not lifted: " + f.ys[2], f.ys[2] > -250f);
		assertEquals(1, f.deformedCalls);
	}

	@Test
	public void anAppearanceChangeWaitsForANewSnapshot()
	{
		Member m = new Member();
		draw(m, 7, true, null, neutral);
		m.idleOffset = 30f;
		// new gear (another key): the old snapshot is not drawn on it
		Fake f = draw(m, 8, false, LIFT, neutral);
		assertEquals(30f, f.xs[0], 0f);
		assertTrue(snap.matches(3, 2, 7));
		draw(m, 8, true, null, neutral);
		assertTrue(snap.matches(3, 2, 8));
	}

	@Test
	public void aDifferentVertexOrFaceCountIsNeverCopiedOnto()
	{
		Member m = new Member();
		draw(m, 7, true, null, neutral);
		m.base.faces = 3;
		m.idleOffset = 30f;
		Fake f = draw(m, 7, false, null, neutral);
		assertEquals(30f, f.xs[0], 0f);
		m.base.faces = 2;
		m.base.count = 2;
		f = draw(m, 7, false, null, neutral);
		assertEquals(30f, f.xs[0], 0f);
	}

	@Test
	public void aPersistentModelIsDrawnAsIsAndNeverSnapshotted()
	{
		Member m = new Member();
		m.persistent = true;
		Fake f = draw(m, 7, true, LIFT, neutral);
		assertSame(m.base, f);
		assertFalse((snap.count >= 0));
		assertEquals(0f, m.base.xs[0], 0f);
		assertEquals(0f, m.base.ys[0], 0f);
	}

	@Test
	public void aLayerThatGivesNothingBackLeavesThePlainSnapshot()
	{
		Member m = new Member();
		draw(m, 7, true, null, neutral);
		// a layer that half-wrote the buffer, then failed
		LayeredBody.Layer<Fake> broken = b ->
		{
			b.xs[0] = 999f;
			return null;
		};
		Fake f = draw(m, 7, false, broken, neutral);
		assertEquals(0f, f.xs[0], 0f);
		// another buffer handed back is not drawn: the snapshot in the proven one is
		Fake other = new Fake();
		other.count = 3;
		f = draw(m, 7, false, b -> other, neutral);
		assertSame(m.shared, f);
		assertEquals(0f, f.xs[0], 0f);
	}

	@Test
	public void thePoseDeformsTheLayeredSnapshot()
	{
		Member m = new Member();
		draw(m, 7, true, null, neutral);
		BodyPose squat = new BodyPose();
		squat.legScale = 0.7f;
		Fake f = draw(m, 7, false, null, squat);
		assertNotEquals(-200f, f.ys[2], 0.01f);
		assertTrue(f.deformedCalls > 0);
		// the snapshot itself is untouched by the deform
		f = draw(m, 7, false, null, neutral);
		assertEquals(-200f, f.ys[2], 0f);
	}

	@Test
	public void snapshotGrowsOnlyWhenNeeded()
	{
		MeshSnapshot s = new MeshSnapshot();
		float[] a = {1f, 2f, 3f, 4f};
		s.take(a, a, a, 4, 1, 1);
		float[] out = new float[4];
		s.copyInto(out, new float[4], new float[4]);
		assertEquals(4f, out[3], 0f);
		s.take(a, a, a, 2, 1, 2);
		assertTrue(s.matches(2, 1, 2));
		assertEquals(4, s.xs.length);
		s.clear();
		assertFalse((s.count >= 0));
	}
}
