package com.gielinorskate.controller;

import com.gielinorskate.GielinorSkateConfig.ControllerPreset;

/** The preset in use from the settings. */
public final class PadPresets
{
	private PadPresets()
	{
	}

	/** The layout of {@code preset}; Custom reads {@code customCode}, and a missing or broken one plays as Skate 3. */
	public static PadPreset resolve(ControllerPreset preset, String customCode)
	{
		if (preset == ControllerPreset.THAW)
		{
			return PadPreset.thaw();
		}
		if (preset == ControllerPreset.CUSTOM)
		{
			LayoutCode.Result r = LayoutCode.decode(customCode);
			if (r.ok())
			{
				return r.preset;
			}
		}
		return PadPreset.skate3();
	}

	/** Remembers the last layout read, so a per-frame caller does not decode the same code again. */
	public static final class Cache
	{
		private ControllerPreset lastPreset;
		private String lastCode;
		private PadPreset last = PadPreset.skate3();

		public synchronized PadPreset get(ControllerPreset preset, String customCode)
		{
			if (preset != lastPreset || !java.util.Objects.equals(customCode, lastCode))
			{
				lastPreset = preset;
				lastCode = customCode;
				last = resolve(preset, customCode);
			}
			return last;
		}
	}

	/** True when Custom is chosen but its saved layout can't be read (it plays as Skate 3). */
	public static boolean customIsBroken(ControllerPreset preset, String customCode)
	{
		return preset == ControllerPreset.CUSTOM && !LayoutCode.decode(customCode).ok();
	}
}
