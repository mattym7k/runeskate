package com.gielinorskate.ui;

import java.util.Objects;

/** What the side panel shows that comes from the client thread: skating or not, the last refusal, the stats. */
public final class PanelState
{
	public final boolean active;
	/** Why skating could not start last time (cleared when it starts), or null. */
	public final String refusal;
	public final int total;
	public final int bestCombo;
	public final int tricksLanded;

	public PanelState(boolean active, String refusal, int total, int bestCombo, int tricksLanded)
	{
		this.active = active;
		this.refusal = refusal;
		this.total = total;
		this.bestCombo = bestCombo;
		this.tricksLanded = tricksLanded;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof PanelState))
		{
			return false;
		}
		PanelState s = (PanelState) o;
		return active == s.active && total == s.total && bestCombo == s.bestCombo && tricksLanded == s.tricksLanded
			&& Objects.equals(refusal, s.refusal);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(active, refusal, total, bestCombo, tricksLanded);
	}
}
