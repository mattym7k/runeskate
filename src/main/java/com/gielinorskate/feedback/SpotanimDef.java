package com.gielinorskate.feedback;

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
		{
			return null;
		}
		Reader in = new Reader(data);
		SpotanimDef def = new SpotanimDef();
		try
		{
			while (true)
			{
				int opcode = in.u8();
				switch (opcode)
				{
					case 0:
						return def.modelId >= 0 ? def : null;
					case 1:
						def.modelId = in.u16();
						break;
					case 2:
						def.animationId = in.u16();
						break;
					case 3:
						def.modelId = in.i32();
						break;
					case 4:
						def.resizeX = in.u16();
						break;
					case 5:
						def.resizeY = in.u16();
						break;
					case 6:
						in.u16(); // rotation
						break;
					case 7:
						def.ambient = in.u8();
						break;
					case 8:
						def.contrast = in.u8();
						break;
					case 9:
						in.skipString(); // debug name
						break;
					case 10:
						break; // not clickable
					case 40:
					{
						int n = in.u8();
						def.recolorFrom = new short[n];
						def.recolorTo = new short[n];
						for (int i = 0; i < n; i++)
						{
							def.recolorFrom[i] = (short) in.u16();
							def.recolorTo[i] = (short) in.u16();
						}
						break;
					}
					case 41:
					{
						int n = in.u8();
						for (int i = 0; i < n; i++)
						{
							in.u16();
							in.u16(); // retextures: left as they are
						}
						break;
					}
					case 42:
						in.u16(); // full recolour
						break;
					default:
						return null;
				}
			}
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			return null;
		}
	}

	/** Big-endian reads over a byte array; running off the end throws ArrayIndexOutOfBoundsException. */
	private static final class Reader
	{
		private final byte[] b;
		private int pos;

		Reader(byte[] b)
		{
			this.b = b;
		}

		int u8()
		{
			return b[pos++] & 0xFF;
		}

		int u16()
		{
			return (u8() << 8) | u8();
		}

		int i32()
		{
			return (u16() << 16) | u16();
		}

		void skipString()
		{
			while (b[pos++] != 0)
			{
				// skip to the terminator
			}
		}
	}
}
