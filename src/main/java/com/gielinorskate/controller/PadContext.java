package com.gielinorskate.controller;

/** Where the skater is when a pad button is read: its binding can differ between them. */
public enum PadContext
{
	/** On the board, on the ground (rolling, grinding, a manual or bailed). */
	BOARD("On the board"),
	/** On the board in the air. A binding with no air action uses its board action. */
	AIR("In the air"),
	/** Off the board, walking. */
	FOOT("On foot");

	public final String label;

	PadContext(String label)
	{
		this.label = label;
	}
}
