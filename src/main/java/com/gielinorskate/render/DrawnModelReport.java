package com.gielinorskate.render;

/**
 * Whether the puppet's last draw had a model, handed from the puppet (which fetches the player's model at
 * draw time anyway) to {@link SkaterAnimator}'s {@link PoseGuard}, so the animator need not build the animated
 * model a third time each frame. A report is used once; with none since the last check (the puppet was not
 * drawn) the animator fetches the model itself. Client thread only.
 */
public final class DrawnModelReport
{
	private boolean fresh;
	private boolean hadModel;

	/** The puppet drew; {@code hasModel} is whether it had a model to draw. */
	void report(boolean hasModel)
	{
		hadModel = hasModel;
		fresh = true;
	}

	/** True when a draw was reported since the last {@link #take()} or {@link #clear()}. */
	boolean isFresh()
	{
		return fresh;
	}

	/** The latest report, consuming it. Only meaningful when {@link #isFresh()}. */
	boolean take()
	{
		fresh = false;
		return hadModel;
	}

	/** Forgets any report (a new session starts). */
	void clear()
	{
		fresh = false;
		hadModel = false;
	}
}
