package com.gielinorskate.physics;

import com.gielinorskate.input.GestureRecognizer;
import com.gielinorskate.input.MousePath;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.TrickEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives a {@link SkatePhysics} from a realistic {@link MousePath} the way the client does: the
 * recognizer gets every drag event, and each frame (at {@code fps}) the held state and queued gestures
 * are copied into the input as InputController.drainInto does, then whole 20 ms physics steps run.
 */
final class GestureRig
{
	static final float STEP = 0.02f;

	final SkatePhysics physics;
	final SkateInput in = new SkateInput();
	final GestureRecognizer recognizer = new GestureRecognizer();
	final List<SkateEvent> events = new ArrayList<>();
	final List<TrickEvent> trickEvents = new ArrayList<>();
	float peak;

	GestureRig(SkatePhysics physics)
	{
		this.physics = physics;
	}

	/** Plays `path` (pressed at its first sample, never released) and keeps running until `totalMs`. */
	GestureRig play(MousePath path, long totalMs, double fps)
	{
		List<MousePath.Sample> s = path.samples;
		recognizer.begin(s.get(0).x, s.get(0).y, s.get(0).ms);
		int idx = 1;
		double frameMs = 1000.0 / fps;
		double nextFrame = frameMs;
		double acc = 0;
		for (long now = 0; now <= totalMs; now++)
		{
			while (idx < s.size() && s.get(idx).ms <= now)
			{
				MousePath.Sample m = s.get(idx++);
				recognizer.move(m.x, m.y, m.ms);
			}
			if (now >= nextFrame)
			{
				nextFrame += frameMs;
				recognizer.tick(now);
				in.crouch = recognizer.isCrouching();
				in.charge = recognizer.isCharging();
				Gesture g;
				while ((g = recognizer.poll()) != null)
				{
					in.gestures.add(g);
				}
				acc += frameMs / 1000.0;
				while (acc >= STEP)
				{
					physics.step(STEP, in);
					acc -= STEP;
					peak = Math.max(peak, physics.getH());
				}
				events.addAll(physics.drainEvents());
				trickEvents.addAll(physics.drainTrickEvents());
			}
		}
		return this;
	}
}
