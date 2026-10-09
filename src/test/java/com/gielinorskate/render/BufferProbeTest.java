package com.gielinorskate.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class BufferProbeTest
{
	/** A model with vertex arrays (longer than the count, like the client's shared buffer). */
	static final class FakeModel
	{
		int count;
		float[] xs = new float[8];
		float[] ys = new float[8];
		float[] zs = new float[8];
		int deformedCalls;
	}

	static final BufferProbe.Mesh<FakeModel> MESH = new BufferProbe.Mesh<FakeModel>()
	{
		@Override
		public int count(FakeModel m)
		{
			return m.count;
		}

		@Override
		public float[] xs(FakeModel m)
		{
			return m.xs;
		}

		@Override
		public float[] ys(FakeModel m)
		{
			return m.ys;
		}

		@Override
		public float[] zs(FakeModel m)
		{
			return m.zs;
		}

		@Override
		public void deformed(FakeModel m)
		{
			m.deformedCalls++;
		}
	};

	private static FakeModel base()
	{
		FakeModel m = new FakeModel();
		m.count = 3;
		float[] y = {0f, -90f, -200f};
		for (int i = 0; i < 3; i++)
		{
			m.xs[i] = 4f * i;
			m.ys[i] = y[i];
			m.zs[i] = -i;
		}
		return m;
	}

	/** The client's shared animation buffer: every request copies the base into the same object. */
	static final class SharedBuffer implements BufferProbe.Source<FakeModel>
	{
		final FakeModel base = base();
		final FakeModel shared = new FakeModel();
		int calls;

		@Override
		public FakeModel get()
		{
			calls++;
			shared.count = base.count;
			System.arraycopy(base.xs, 0, shared.xs, 0, base.count);
			System.arraycopy(base.ys, 0, shared.ys, 0, base.count);
			System.arraycopy(base.zs, 0, shared.zs, 0, base.count);
			return shared;
		}
	}

	private static BodyPose leaning()
	{
		BodyPose p = new BodyPose();
		p.roll = -0.3f;
		p.legScale = 0.8f;
		return p;
	}

	@Test
	public void sharedBufferIsDeformedAndTheBaseNeverChanges()
	{
		SharedBuffer src = new SharedBuffer();
		FakeModel untouched = base();
		FakeModel first = BufferProbe.drawable(src, MESH, leaning(), new BufferProbe.Probe<>());
		assertSame(src.shared, first);
		assertEquals(1, first.deformedCalls);
		assertNotEquals(-200f, first.ys[2], 1f);
		float[] firstYs = first.ys.clone();
		// frame after frame the result is the same: nothing piles up
		for (int i = 0; i < 5; i++)
		{
			BufferProbe.drawable(src, MESH, leaning(), new BufferProbe.Probe<>());
		}
		assertArrayEquals(firstYs, src.shared.ys, 1e-4f);
		assertArrayEquals(untouched.xs, src.base.xs, 0f);
		assertArrayEquals(untouched.ys, src.base.ys, 0f);
		assertArrayEquals(untouched.zs, src.base.zs, 0f);
	}

	@Test
	public void persistentModelIsDrawnPlainAndRestoredExactly()
	{
		FakeModel cached = base();
		FakeModel copy = base();
		BufferProbe.Source<FakeModel> src = () -> cached;
		for (int i = 0; i < 5; i++)
		{
			assertSame(cached, BufferProbe.drawable(src, MESH, leaning(), new BufferProbe.Probe<>()));
		}
		assertEquals(0, cached.deformedCalls);
		assertArrayEquals(copy.xs, cached.xs, 0f);
		assertArrayEquals(copy.ys, cached.ys, 0f);
		assertArrayEquals(copy.zs, cached.zs, 0f);
	}

	@Test
	public void aNewModelPerRequestIsDrawnPlain()
	{
		FakeModel[] made = new FakeModel[2];
		int[] n = {0};
		BufferProbe.Source<FakeModel> src = () -> made[n[0]++ % 2] = base();
		FakeModel out = BufferProbe.drawable(src, MESH, leaning(), new BufferProbe.Probe<>());
		assertSame(made[1], out);
		assertEquals(0, out.deformedCalls);
		assertArrayEquals(base().ys, out.ys, 0f);
		// the probed first model got its sentinel back
		assertArrayEquals(base().xs, made[0].xs, 0f);
	}

	@Test
	public void neutralPoseAsksOnceAndDrawsPlain()
	{
		SharedBuffer src = new SharedBuffer();
		FakeModel out = BufferProbe.drawable(src, MESH, new BodyPose(), new BufferProbe.Probe<>());
		assertEquals(1, src.calls);
		assertEquals(0, out.deformedCalls);
		assertArrayEquals(base().ys, out.ys, 0f);
	}

	@Test
	public void nullAndEmptyModelsPassThrough()
	{
		assertEquals(null, BufferProbe.drawable(() -> null, MESH, leaning(), new BufferProbe.Probe<>()));
		FakeModel empty = new FakeModel();
		assertSame(empty, BufferProbe.drawable(() -> empty, MESH, leaning(), new BufferProbe.Probe<>()));
	}

	@Test
	public void sentinelIsRestoredWhenTheSecondRequestThrows()
	{
		FakeModel cached = base();
		int[] n = {0};
		BufferProbe.Source<FakeModel> src = () ->
		{
			if (n[0]++ > 0)
			{
				throw new IllegalStateException("boom");
			}
			return cached;
		};
		try
		{
			BufferProbe.drawable(src, MESH, leaning(), new BufferProbe.Probe<>());
			fail();
		}
		catch (IllegalStateException expected)
		{
			assertTrue(true);
		}
		assertArrayEquals(base().xs, cached.xs, 0f);
	}
}
