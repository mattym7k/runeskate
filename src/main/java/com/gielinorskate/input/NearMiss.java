package com.gielinorskate.input;

/** Why a mouse stroke that looked like a trick attempt did not become one (see {@link GestureRecognizer}). */
public enum NearMiss
{
	/** Wound up and moved toward a flick, but too slowly to count. */
	TOO_SLOW("Flick faster: a quick snap after the pull down", "Flick faster: snap {RS} all the way after the pull down"),
	/** Wound up, then let go after only a short flick. */
	TOO_SHORT("Flick further: a longer snap after the pull down", "Flick further: a longer {RS} snap after the pull down"),
	/** A sideways flick with no pull down (or push up) first. */
	NO_WIND_UP("Pull down first, then flick", "Pull {RS} down first, then flick");

	/** The HUD hint. */
	public final String hint;
	/** The hint in controller mode, with drawn button tokens ("{RS}", see ControllerGlyphs). */
	public final String controllerHint;

	NearMiss(String hint, String controllerHint)
	{
		this.hint = hint;
		this.controllerHint = controllerHint;
	}
}
