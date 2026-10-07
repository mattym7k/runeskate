package com.gielinorskate.render;

import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkaterState;
import java.util.List;

/** Pure decision logic for which skater animation to play. No client dependency. */
public final class AnimationPicker
{
	public static final float PUSH_SECONDS = 0.45f;

	public enum Action
	{
		NONE,
		PUSH,
		JUMP,
		BAIL
	}

	private AnimationPicker()
	{
	}

	public static Action pick(SkaterState state, List<SkateEvent> events, Action current, float actionTimeLeft)
	{
		if (events.contains(SkateEvent.BAIL))
		{
			return Action.BAIL;
		}
		if (events.contains(SkateEvent.POP) || events.contains(SkateEvent.ROLL_OFF))
		{
			return Action.JUMP;
		}
		if (events.contains(SkateEvent.LAND) || events.contains(SkateEvent.RESET))
		{
			return Action.NONE;
		}
		if (events.contains(SkateEvent.PUSH) && state == SkaterState.ROLLING)
		{
			return Action.PUSH;
		}
		if (current == Action.PUSH && actionTimeLeft <= 0f)
		{
			return Action.NONE;
		}
		return current;
	}
}
