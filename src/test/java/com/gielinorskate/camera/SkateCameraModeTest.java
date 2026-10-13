package com.gielinorskate.camera;

import static org.junit.Assert.*;

import com.gielinorskate.physics.SkaterState;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import org.junit.Test;

/** The free camera can be switched off under the skater (by the game or another plugin): never an error. */
public class SkateCameraModeTest
{
	/** A client whose camera mode sticks only while {@code accepts}, and whose focal point needs the free camera. */
	private static final class FakeClient
	{
		int mode;
		boolean accepts = true;

		Client client()
		{
			return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) ->
				{
					switch (method.getName())
					{
						case "getCameraMode":
							return mode;
						case "setCameraMode":
							if (accepts)
								mode = (int) args[0];
							return null;
						case "setCameraFocalPointX":
						case "setCameraFocalPointY":
						case "setCameraFocalPointZ":
							if (mode != 1)
								throw new IllegalArgumentException("must be in free camera mode");
							return null;
					}
					Class<?> r = method.getReturnType();
					if (r == boolean.class)
						return false;
					if (r == int.class)
						return 0;
					if (r == long.class)
						return 0L;
					if (r == float.class)
						return 0f;
					if (r == double.class)
						return 0d;
					return null;
				});
		}
	}

	private static boolean frame(SkateCamera camera)
	{
		return camera.update(0, 0, 0, 0, 0, SkaterState.ROLLING, 0, 1 / 60f, 400);
	}

	@Test
	public void freeCameraTurnedOffIsTurnedBackOn()
	{
		FakeClient fake = new FakeClient();
		SkateCamera camera = new SkateCamera(fake.client());
		camera.enter(0, 0, 0, 0, 800);
		assertEquals(1, fake.mode);
		fake.mode = 0;
		assertTrue(frame(camera));
		assertEquals(1, fake.mode);
	}

	@Test
	public void freeCameraThatWontStayOnStopsWithoutAnError()
	{
		FakeClient fake = new FakeClient();
		SkateCamera camera = new SkateCamera(fake.client());
		camera.enter(0, 0, 0, 0, 800);
		fake.mode = 0;
		fake.accepts = false;
		boolean ok = true;
		int frames = 0;
		while (ok && frames < 600)
		{
			ok = frame(camera);
			frames++;
		}
		assertFalse("gives up after a while", ok);
		assertTrue("not on the first frame", frames > 30);
	}
}
