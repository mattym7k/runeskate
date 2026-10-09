package com.gielinorskate.ui;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/** What the side panel shows that comes from the client thread: skating or not, the last refusal, the stats. */
@AllArgsConstructor
@EqualsAndHashCode
public final class PanelState
{
public final boolean active;
/** Why skating could not start last time (cleared when it starts), or null. */
public final String refusal;
public final int total;
public final int bestCombo;
public final int tricksLanded;
}
