package com.gielinorskate.feedback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SpotanimDefTest
{
	private static byte[] bytes(int... values)
	{
		byte[] b = new byte[values.length];
		for (int i = 0; i < values.length; i++)
		{
			b[i] = (byte) values[i];
		}
		return b;
	}

	@Test
	public void readsModelAnimationSizeLightAndRecolours()
	{
		SpotanimDef def = SpotanimDef.parse(bytes(
			1, 0x12, 0x34,          // model 0x1234
			2, 0x03, 0xE8,          // animation 1000
			4, 0x00, 0x40,          // resize x 64
			5, 0x01, 0x00,          // resize y 256
			6, 0x00, 0x5A,          // rotation (skipped)
			7, 30,                  // ambient
			8, 200,                 // contrast
			9, 'p', 'u', 'f', 'f', 0, // debug name (skipped)
			10,                     // not clickable
			40, 1, 0x00, 0x10, 0xFF, 0xFE, // recolour 16 -> -2
			41, 1, 0, 1, 0, 2,      // retexture (skipped)
			42, 0, 9,               // full recolour (skipped)
			0));
		assertEquals(0x1234, def.modelId);
		assertEquals(1000, def.animationId);
		assertEquals(64, def.resizeX);
		assertEquals(256, def.resizeY);
		assertEquals(30, def.ambient);
		assertEquals(200, def.contrast);
		assertArrayEquals(new short[]{16}, def.recolorFrom);
		assertArrayEquals(new short[]{(short) 0xFFFE}, def.recolorTo);
	}

	@Test
	public void bigModelIdsUseTheIntOpcode()
	{
		SpotanimDef def = SpotanimDef.parse(bytes(3, 0x00, 0x01, 0x86, 0xA0, 0)); // 100000
		assertEquals(100_000, def.modelId);
		assertEquals(-1, def.animationId);
		assertEquals(128, def.resizeX);
	}

	@Test
	public void badDataIsSkippedNotThrown()
	{
		assertNull(SpotanimDef.parse(null));
		assertNull("truncated", SpotanimDef.parse(bytes(1, 0x12)));
		assertNull("no terminator", SpotanimDef.parse(bytes(1, 0x12, 0x34)));
		assertNull("unknown opcode", SpotanimDef.parse(bytes(1, 0, 5, 99, 0)));
		assertNull("no model", SpotanimDef.parse(bytes(2, 0, 5, 0)));
	}
}
