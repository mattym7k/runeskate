package com.gielinorskate.input;

import com.gielinorskate.Text;
import java.util.List;

/** Why a mouse stroke that looked like a trick attempt did not become one (see {@link GestureRecognizer}). */
public enum NearMiss
{
	/** Wound up and moved toward a flick, but too slowly to count. */
	TOO_SLOW,
	/** Wound up, then let go after only a short flick. */
	TOO_SHORT,
	/** A sideways flick with no pull down (or push up) first. */
	NO_WIND_UP;

	/** The HUD hint. */
	public final String hint;
	/** The hint in controller mode, with drawn button tokens ("{RS}", see ControllerGlyphs). */
	public final String controllerHint;

	/** Both hints, from text/overlay.properties ("miss." + name: the HUD line, then the controller one). */
	NearMiss()
	{
		List<String> lines = Text.lines("miss." + name());
		hint = lines.get(0);
		controllerHint = lines.get(1);
	}
}
