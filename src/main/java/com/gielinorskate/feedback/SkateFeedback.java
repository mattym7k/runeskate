package com.gielinorskate.feedback;

import com.gielinorskate.SkateChat;
import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.physics.*;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.SpotanimID;

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

/** Skate Duel sounds: the game's own sound effects. */
@AllArgsConstructor
public enum DuelSound
{
CHALLENGE(SoundEffectID.TOWN_CRIER_SHOUT_SQUEAK),
COUNT(SoundEffectID.UI_BOOP),
FIGHT(SoundEffectID.TOWN_CRIER_BELL_DING),
HIT(SoundEffectID.TAKE_DAMAGE_SPLAT),
WIN(SoundEffectID.GE_ADD_OFFER_DINGALING),
LOSE(SoundEffectID.PRAYER_DEPLETE_TWINKLE);

final int id;
}

/**
* Plays a Skate Duel sound, skating or not, following the sound settings like every skate sound. A failure is
* logged and never reaches the duel. Client thread.
*/
public void playDuelSound(DuelSound sound, float now)
{
safely("play a duel sound", () -> playSound(sound.id, now));
}

private void playSound(int id, float now)
{
if (config.soundEffects())
sounds.play(Collections.singletonList(new SoundPicker.Cue(id, 1f)), now, config.soundVolume());
}

/** The duel endings' cues, at a point: the board snapping (a crack and dust), the winner's sparkle. */
@AllArgsConstructor
public enum EndingCue
{
/** The tantrum's board breaking on the ground: a sharp wooden chop and a puff of dust and splinters. */
SNAP(SoundEffectID.TREE_CHOP, new int[]{SpotanimID.EMOTE_DUSTSTAMP_SPOT, SpotanimID.SMALL_SMOKEPUFF}),
/** The celebration: the level-up fireworks' sparkle (no sound: the duel's win jingle just played). */
SPARKLE(-1, new int[]{SpotanimID.LEVELUP_ANIM});

final int sound;
final int[] graphics;
}

/**
* Plays a duel ending's cue at local (x, y), up-positive height h, for the local skater or a party ghost: its
* sound and its graphics, each following its setting like every skate sound and particle. A failure is logged and
* never reaches the caller. Client thread.
*/
public void playEndingCue(EndingCue cue, float x, float y, float h, float now)
{
safely("play a duel ending cue", () ->
{
if (cue.sound >= 0)
playSound(cue.sound, now);
if (config.particleEffects())
{
for (int id : cue.graphics)
spots.spawn(id, x, y, h, client.getTopLevelWorldView(), now);
}
});
}

/**
* Removes graphics whose time is up, for frames that play no other feedback (the tantrum: no skate sounds, but its
* dust must still go away). Client thread.
*/
public void tickEffects(float now)
{
safely("update skate effects", () -> spots.update(now));
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
