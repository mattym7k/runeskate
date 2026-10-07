package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.AnimationPicker.Action;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AnimationPickerTest
{
	@Test
	public void bailBeatsPopInSameEventList()
	{
		List<SkateEvent> events = Arrays.asList(SkateEvent.POP, SkateEvent.BAIL);
		Action result = AnimationPicker.pick(SkaterState.AIRBORNE, events, Action.NONE, 0f);
		assertEquals(Action.BAIL, result);
	}

	@Test
	public void pushOnlyWhileRolling()
	{
		List<SkateEvent> events = Collections.singletonList(SkateEvent.PUSH);
		assertEquals(Action.PUSH, AnimationPicker.pick(SkaterState.ROLLING, events, Action.NONE, 0f));
		assertEquals(Action.NONE,
			AnimationPicker.pick(SkaterState.AIRBORNE, events, Action.NONE, 0f));
	}

	@Test
	public void pushExpiresAfterItsTimeRunsOut()
	{
		List<SkateEvent> noEvents = Collections.emptyList();
		assertEquals(Action.PUSH,
			AnimationPicker.pick(SkaterState.ROLLING, noEvents, Action.PUSH, 0.1f));
		assertEquals(Action.NONE,
			AnimationPicker.pick(SkaterState.ROLLING, noEvents, Action.PUSH, 0f));
	}

	@Test
	public void jumpPersistsUntilLand()
	{
		List<SkateEvent> popEvent = Collections.singletonList(SkateEvent.POP);
		assertEquals(Action.JUMP,
			AnimationPicker.pick(SkaterState.AIRBORNE, popEvent, Action.NONE, 0f));

		List<SkateEvent> noEvents = Collections.emptyList();
		assertEquals(Action.JUMP,
			AnimationPicker.pick(SkaterState.AIRBORNE, noEvents, Action.JUMP, 0f));
	}

	@Test
	public void landGoesToNone()
	{
		List<SkateEvent> events = Collections.singletonList(SkateEvent.LAND);
		assertEquals(Action.NONE,
			AnimationPicker.pick(SkaterState.ROLLING, events, Action.JUMP, 0f));
	}

	@Test
	public void rollOffTriggersJump()
	{
		List<SkateEvent> events = Collections.singletonList(SkateEvent.ROLL_OFF);
		assertEquals(Action.JUMP,
			AnimationPicker.pick(SkaterState.AIRBORNE, events, Action.NONE, 0f));
	}

	@Test
	public void resetGoesToNone()
	{
		List<SkateEvent> events = Collections.singletonList(SkateEvent.RESET);
		assertEquals(Action.NONE,
			AnimationPicker.pick(SkaterState.ROLLING, events, Action.BAIL, 0f));
	}

	@Test
	public void noEventsKeepsCurrentWhenNotPushOrExpired()
	{
		List<SkateEvent> noEvents = Collections.emptyList();
		assertEquals(Action.JUMP,
			AnimationPicker.pick(SkaterState.AIRBORNE, noEvents, Action.JUMP, 0f));
		assertEquals(Action.NONE,
			AnimationPicker.pick(SkaterState.ROLLING, noEvents, Action.NONE, 0f));
	}

	@Test
	public void manualAndGrindingKeepTheirStanceWithNoOverridingEvents()
	{
		// no PUSH/JUMP/BAIL action overlay while in a manual or a grind: SkaterAnimator keeps
		// playing the manual/grind idle stance for these states (see StancePoses)
		List<SkateEvent> noEvents = Collections.emptyList();
		assertEquals(Action.NONE, AnimationPicker.pick(SkaterState.MANUAL, noEvents, Action.NONE, 0f));
		assertEquals(Action.NONE, AnimationPicker.pick(SkaterState.GRINDING, noEvents, Action.NONE, 0f));
	}

	@Test
	public void bailDuringManualOrGrindStillBails()
	{
		List<SkateEvent> events = Collections.singletonList(SkateEvent.BAIL);
		assertEquals(Action.BAIL, AnimationPicker.pick(SkaterState.MANUAL, events, Action.NONE, 0f));
		assertEquals(Action.BAIL, AnimationPicker.pick(SkaterState.GRINDING, events, Action.NONE, 0f));
	}

	@Test
	public void grabHoldsTheJumpAction()
	{
		// grabs only happen while AIRBORNE; the jump action (held, via JUMP persisting until LAND)
		// doubles as the grab pose, per the brief ("Grab = jump animation, held")
		List<SkateEvent> popEvent = Collections.singletonList(SkateEvent.POP);
		Action afterPop = AnimationPicker.pick(SkaterState.AIRBORNE, popEvent, Action.NONE, 0f);
		assertEquals(Action.JUMP, afterPop);

		List<SkateEvent> noEvents = Collections.emptyList();
		assertEquals(Action.JUMP, AnimationPicker.pick(SkaterState.AIRBORNE, noEvents, afterPop, 0f));
	}
}
