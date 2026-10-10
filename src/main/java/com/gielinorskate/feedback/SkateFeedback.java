package com.gielinorskate.feedback;

import com.gielinorskate.SkateChat;
import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.physics.*;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.TrickEvent;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Sounds, particles and bail jokes for one skate session, driven once a frame by the session after it has
 * drained the physics events and fed the scorer. Each part follows its setting. Everything is client-side: it
 * plays local sounds, draws local objects and posts local chat lines. A failure here turns feedback off for the
 * session but never stops skating. Client thread.
 */
@Slf4j
@Singleton
public class SkateFeedback
{
	@Inject
	private Client client;
	@Inject
	private SkateChat skateChat;
	@Inject
	private GielinorSkateConfig config;
	@Inject
	private ComboScorer scorer;
	@Inject
	private SkateSounds sounds;
	@Inject
	private SpotEffects spots;
	private final SoundPicker soundPicker = new SoundPicker();
	private final EffectPicker effectPicker = new EffectPicker();
	private final BailLines bailLines = new BailLines();
	private final DelayedCues delayed = new DelayedCues();

	private int seenResults;
	private boolean failed;
	/** A level reached since the last frame, for its graphic over the skater; 0 when none. */
	private int pendingLevelUp;

	public void onSkateStart()
	{
		seenResults = scorer.resultSequence();
		failed = false;
		pendingLevelUp = 0;
		delayed.clear();
	}

	/**
	 * A Skating level was reached: next frame the level-up graphic shows over the skater (the 99 one at 99) and
	 * the bells ring, each following its setting.
	 */
	public void onLevelUp(int level, float now)
	{
		pendingLevelUp = level;
		delayed.levelUp(now);
	}

	/** A session goal was completed: the combo-landed chime, just after the landing's own. */
	public void onGoalComplete(float now)
	{
		delayed.goalComplete(now);
	}

	/**
	 * @param hitThisFrame the skater ran into a blocker this frame (read before the hit is cleared), which makes
	 *     a bail on this frame a wall bail
	 * @param now score-clock seconds
	 */
	public void onFrame(SkatePhysics physics, List<SkateEvent> events, List<TrickEvent> trickEvents,
		boolean hitThisFrame, float now, float dt)
	{
		if (failed)
			return;
		try
		{
			int sequence = scorer.resultSequence();
			ComboScorer.Result newResult = sequence != seenResults ? scorer.lastResult() : null;
			seenResults = sequence;
			boolean grinding = physics.getState() == SkaterState.GRINDING;

			// pickers run every frame (their repeat timers must see the whole grind), the output only when enabled
			// (both pickers return a fresh list)
			List<SoundPicker.Cue> cues = soundPicker.pick(events, trickEvents, grinding, physics.getLastLandingSpeed(),
				newResult, dt);
			cues.addAll(delayed.due(now));
			if (config.soundEffects())
				sounds.play(cues, now, config.soundVolume());

			List<Integer> graphics = effectPicker.pick(events, grinding, physics.getLastAirtime(), newResult, dt);
			if (pendingLevelUp > 0)
			{
				graphics.add(DelayedCues.levelUpGraphic(pendingLevelUp));
				pendingLevelUp = 0;
			}
			if (config.particleEffects())
			{
				for (int id : graphics)
					spots.spawn(id, physics.getX(), physics.getY(), physics.getH(), client.getTopLevelWorldView(), now);
				spots.update(now);
			}
			else if (spots.liveCount() > 0)
				spots.clear();

			if (newResult != null && newResult.isBailed() && config.funnyBailMessages())
			{
				String line = bailLines.onBail(now, hitThisFrame, newResult.value);
				if (line != null)
					skateChat.send(line);
			}
		}
		catch (RuntimeException e)
		{
			failed = true;
			log.warn("Skate feedback failed; sounds and effects are off until the next skate session", e);
			safely("clear skate effects", spots::clear);
		}
	}

	/** Removes any particles still showing. */
	public void onSkateEnd()
	{
		pendingLevelUp = 0;
		delayed.clear();
		safely("clear skate effects", spots::clear);
	}

	/** Runs {@code action}, logging a failure instead of passing it on. */
	private static void safely(String what, Runnable action)
	{
		try
		{
			action.run();
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to " + what, e);
		}
	}
}
