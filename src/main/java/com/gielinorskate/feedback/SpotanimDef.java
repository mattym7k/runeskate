package com.gielinorskate.feedback;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * The parts of a spotanim (graphic) definition needed to draw one as a RuneLiteObject: the API has no spotanim
 * definition lookup, so this decodes the cache entry (config archive {@link #CONFIG_ARCHIVE}). Opcodes follow
 * RuneLite's cache SpotAnimLoader. Pure.
 */
final class SpotanimDef
{
	/** Config index group holding spotanim definitions (RuneLite cache ConfigType.SPOTANIM). */
	static final int CONFIG_ARCHIVE = 13;
	static final int DEFAULT_RESIZE = 128;

	int modelId = -1;
	int animationId = -1;
	int resizeX = DEFAULT_RESIZE;
	int resizeY = DEFAULT_RESIZE;
	int ambient;
	int contrast;
	short[] recolorFrom;
	short[] recolorTo;

	/** Decodes one definition; null for missing, truncated or unknown data (that graphic is then skipped). */
	static SpotanimDef parse(byte[] data)
	{
		if (data == null)
			return null;
		// big-endian reads; running off the end throws BufferUnderflowException
		ByteBuffer in = ByteBuffer.wrap(data);
		SpotanimDef def = new SpotanimDef();
		try
		{
			while (true)
			{
				switch (in.get() & 0xFF)
				{
					case 0:
						return def.modelId >= 0 ? def : null;
					case 1:
						def.modelId = u16(in);
						break;
					case 2:
						def.animationId = u16(in);
						break;
					case 3:
						def.modelId = in.getInt();
						break;
					case 4:
						def.resizeX = u16(in);
						break;
					case 5:
						def.resizeY = u16(in);
						break;
					case 6:
					case 42:
						in.getShort(); // rotation, full recolour
						break;
					case 7:
						def.ambient = in.get() & 0xFF;
						break;
					case 8:
						def.contrast = in.get() & 0xFF;
						break;
					case 9:
						while (in.get() != 0)
						{
							// skip the debug name up to its terminator
						}
						break;
					case 10:
						break; // not clickable
					case 40:
						int n = in.get() & 0xFF;
						def.recolorFrom = new short[n];
						def.recolorTo = new short[n];
						for (int i = 0; i < n; i++)
						{
							def.recolorFrom[i] = in.getShort();
							def.recolorTo[i] = in.getShort();
						}
						break;
					case 41:
						int retextures = in.get() & 0xFF;
						in.position(in.position() + 4 * retextures); // retextures: left as they are
						break;
					default:
						return null;
				}
			}
		}
		catch (BufferUnderflowException | IllegalArgumentException e)
		{
			return null;
		}
	}

	private static int u16(ByteBuffer in)
	{
		return in.getShort() & 0xFFFF;
	}
}
