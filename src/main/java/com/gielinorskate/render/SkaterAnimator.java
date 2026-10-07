package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.AnimationPicker.Action;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * Drives the hidden local player's animations from physics state, and the procedural body pose layered on
 * top of them ({@link SkaterPoseRig} into {@link #getBodyPose()}, which the puppet applies to the mesh).
 * By default the stance animation stays on all the time and every move is procedural, so nothing cuts;
 * an animation set with {@code ::skatepose} still plays. Client thread only.
 */
@Slf4j
public final class SkaterAnimator
{
	private static final int NO_ANIMATION = -1;

	private final Client client;
	private final StancePoses poses = new StancePoses();
	private final PoseGuard guard = new PoseGuard();
	private final SkaterPoseRig rig = new SkaterPoseRig();
	private final SkaterPoseRig.Signals signals = new SkaterPoseRig.Signals();
	private final BodyPose bodyPose = new BodyPose();
	/** Whether the puppet's latest draw had a model: spares fetching the animated model again for the guard. */
	private final DrawnModelReport drawnReport = new DrawnModelReport();
	private final FootBody footBody = new FootBody();
	/** False once a walk or run animation left the player with no model: on foot stands in the idle pose. */
	private boolean footGaitAnimations = true;
	/** Plays the walk or run animation as much faster as the walker is faster than the game's walk or run. */
	private final GaitPlayback gaitPlayback = new GaitPlayback();
	/** The gait animation's definition (frame lengths), loaded once per animation; null when unusable. */
	private Animation gaitAnimation;
	private int gaitAnimationId = Integer.MIN_VALUE;
	/** False once stepping the gait animation threw: it plays at the game's speed from then on. */
	private boolean gaitSpeedUp = true;
	private float lastHeading = Float.NaN;
	private String warning;

	private boolean started;
	/** The player's own idle pose, given back on exit; follows the game's changes while skating. */
	private final IdlePoseTracker ownIdlePose = new IdlePoseTracker();
	private Action currentAction = Action.NONE;
	private float actionTimeLeft;
	private int lastIdlePose = Integer.MIN_VALUE;
	/** The last frame was on foot: the idle pose is a walk / run / own idle, which the board must replace. */
	private boolean idleFromFoot;
	/** The knockdown's lie-down and get-up animations play (false once one left the skater with no model). */
	private boolean knockdownAnimations = true;
	/** The knockdown animation playing now, or NO_ANIMATION. */
	private int knockAnimation = NO_ANIMATION;
	/** The get-up animation's definition, loaded once per animation; null when it can't be scrubbed. */
	private Animation scrubAnimation;
	private int scrubAnimationId = Integer.MIN_VALUE;
	/** A duel ending's emote playing now (the tantrum's stamp, the cheer), or NO_ANIMATION. */
	private int emoteAnimation = NO_ANIMATION;
	/** The duel endings' emotes play (false once one left the skater with no model: the pose alone then). */
	private boolean emoteAnimations = true;

	public SkaterAnimator(Client client)
	{
		this.client = client;
	}

	/** The live animation IDs, mutable by the {@code ::skatepose} dev command. */
	public StancePoses getPoses()
	{
		return poses;
	}

	/** The procedural pose for the puppet, updated every frame. */
	public BodyPose getBodyPose()
	{
		return bodyPose;
	}

	/** Handed to the puppet, which reports whether each draw had a model. */
	public DrawnModelReport getDrawnReport()
	{
		return drawnReport;
	}

	public void start()
	{
		resetBody();
		drawnReport.clear();
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return;
		}
		ownIdlePose.start(p.getIdlePoseAnimation());
		footGaitAnimations = true;
		emoteAnimations = true;
		emoteAnimation = NO_ANIMATION;
		knockdownAnimations = true;
		knockAnimation = NO_ANIMATION;
		setIdlePose(p, poses.stance);
		started = true;
		currentAction = Action.NONE;
		actionTimeLeft = 0f;
		lastIdlePose = Integer.MIN_VALUE;
		idleFromFoot = false;
	}

	/** @param pose the (interpolated) pose drawn this frame */
	public void update(RenderPose pose, SkatePhysics physics, boolean crouching, List<SkateEvent> events, float dt)
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return;
		}
		noteGameIdlePose(p);
		updateBody(pose, physics, events, dt);
		// the next step off starts from standing
		footBody.reset();
		if (idleFromFoot)
		{
			// just got on: back to the stance at once, even airborne (a jump-mount off an edge), where the idle
			// pose is otherwise left alone and the walk or run legs would keep cycling until the landing
			idleFromFoot = false;
			setIdlePose(p, poses.stance);
			lastIdlePose = poses.stance;
		}

		actionTimeLeft = Math.max(0f, actionTimeLeft - dt);
		Action next = AnimationPicker.pick(physics.getState(), events, currentAction, actionTimeLeft);
		if (next != currentAction)
		{
			currentAction = next;
			actionTimeLeft = next == Action.PUSH ? AnimationPicker.PUSH_SECONDS : 0f;
			p.setAnimation(animationFor(next, p));
			p.setAnimationFrame(0);
		}

		SkaterState s = physics.getState();
		if (s == SkaterState.ROLLING || s == SkaterState.MANUAL || s == SkaterState.GRINDING)
		{
			int idlePose;
			if (s == SkaterState.GRINDING)
			{
				idlePose = poses.grind;
			}
			else if (s == SkaterState.MANUAL)
			{
				idlePose = poses.manual;
			}
			else
			{
				idlePose = crouching && poses.crouch != StancePoses.NONE ? poses.crouch : poses.stance;
			}
			if (idlePose != lastIdlePose)
			{
				setIdlePose(p, idlePose);
				lastIdlePose = idlePose;
			}
		}

		if (guard.frame(hasModel(p)))
		{
			// an animation that doesn't work on player models makes the skater vanish: fall back to the defaults
			poses.resetToDefaults();
			setIdlePose(p, poses.stance);
			lastIdlePose = poses.stance;
			p.setAnimation(NO_ANIMATION);
			currentAction = Action.NONE;
			actionTimeLeft = 0f;
			warning = "That animation made your skater disappear, so the skate poses were reset to the defaults.";
		}
	}

	/**
	 * On foot: the player's own OSRS idle, walk or run pose animation by the walker's speed (the real character
	 * stands still, so its idle pose is what plays: the walk cycles in place while the puppet moves), no action
	 * animation, and the procedural body for jumps (a tuck, then the landing squash).
	 */
	public void updateOnFoot(OffBoardPose pose, float dt)
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return;
		}
		if (currentAction != Action.NONE)
		{
			currentAction = Action.NONE;
			actionTimeLeft = 0f;
			p.setAnimation(NO_ANIMATION);
			p.setAnimationFrame(0);
		}
		noteGameIdlePose(p);
		idleFromFoot = true;
		footBody.fill(signals, pose.walkerSpeed, pose.airborne, pose.jumping, pose.verticalSpeed);
		int idlePose = footGaitAnimations ? gaitAnimation(footBody.gait(), p) : realIdlePose(p);
		if (idlePose != lastIdlePose)
		{
			setIdlePose(p, idlePose);
			lastIdlePose = idlePose;
			gaitPlayback.reset();
		}
		if (footGaitAnimations && footBody.gait() != FootBody.Gait.IDLE)
		{
			speedUpGait(p, idlePose, GaitPlayback.rate(footBody.gait(), pose.walkerSpeed), dt);
		}
		rig.update(signals, dt);
		rig.writeTo(bodyPose);
		// getting back on is no carve
		lastHeading = Float.NaN;

		if (guard.frame(hasModel(p)))
		{
			// a walk or run animation that leaves no model: stand in the player's own idle for the session
			footGaitAnimations = false;
			lastIdlePose = realIdlePose(p);
			setIdlePose(p, lastIdlePose);
			warning = "Your walk animation made your character disappear, so it stands still on foot for now.";
		}
	}

	/**
	 * Knocked off the board: the tumble in the air is procedural (the whole body turned about its centre of mass);
	 * once down the OSRS lie-down loop plays, then the get-up animation squeezed into the get-up's time (each falls
	 * back to the procedural pose when set to -1 with {@code ::skatepose}, or for the session when one leaves the
	 * skater with no model). Animations go on the hidden local player only, as every skate animation does.
	 */
	public void updateKnockdown(KnockdownPose k, float dt)
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return;
		}
		noteGameIdlePose(p);
		currentAction = Action.NONE;
		actionTimeLeft = 0f;
		int want = NO_ANIMATION;
		if (knockdownAnimations)
		{
			if (k.stage == KnockdownPose.Stage.LIE)
			{
				want = poses.knockdown;
			}
			else if (k.stage == KnockdownPose.Stage.GET_UP)
			{
				want = poses.getUp;
			}
		}
		if (want < 0)
		{
			want = NO_ANIMATION;
		}
		if (want != knockAnimation)
		{
			knockAnimation = want;
			p.setAnimation(want);
			p.setAnimationFrame(0);
		}
		if (want != NO_ANIMATION && k.stage == KnockdownPose.Stage.GET_UP)
		{
			scrub(p, want, k.progress);
		}
		KnockdownBody.write(k, want != NO_ANIMATION, bodyPose);
		// carving starts afresh once back on
		lastHeading = Float.NaN;

		if (guard.frame(hasModel(p)))
		{
			knockdownAnimations = false;
			knockAnimation = NO_ANIMATION;
			p.setAnimation(NO_ANIMATION);
			warning = "The knocked-down animation made your skater disappear, so a plain fall is used for now.";
		}
	}

	/** The knockdown is over (standing, or back on the board with R): its animation stops, the body starts afresh. */
	public void endKnockdown()
	{
		Player p = client.getLocalPlayer();
		if (p != null && knockAnimation != NO_ANIMATION)
		{
			p.setAnimation(NO_ANIMATION);
			p.setAnimationFrame(0);
		}
		knockAnimation = NO_ANIMATION;
		currentAction = Action.NONE;
		actionTimeLeft = 0f;
		rig.reset();
		bodyPose.neutral();
		lastHeading = Float.NaN;
	}

	/**
	 * A duel tantrum {@code t} seconds in ({@link TantrumSequence}): standing in the player's own idle pose, the
	 * stamp-feet emote squeezed into the wind-up, both arms holding the board (the procedural pose). The emote goes on
	 * the hidden local player only, as every skate animation does.
	 */
	public void updateTantrum(float t, float dt)
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return;
		}
		noteGameIdlePose(p);
		currentAction = Action.NONE;
		actionTimeLeft = 0f;
		// standing: back in the stance once on the board again
		idleFromFoot = true;
		int idle = realIdlePose(p);
		if (idle != lastIdlePose)
		{
			setIdlePose(p, idle);
			lastIdlePose = idle;
		}
		int want = emoteAnimations ? TantrumSequence.animation(t) : NO_ANIMATION;
		if (want < 0)
		{
			want = NO_ANIMATION;
		}
		if (want != emoteAnimation)
		{
			emoteAnimation = want;
			p.setAnimation(want);
			p.setAnimationFrame(0);
		}
		if (want != NO_ANIMATION)
		{
			scrub(p, want, TantrumSequence.progress(t));
		}
		rig.reset();
		footBody.reset();
		TantrumSequence.writeBody(t, bodyPose);
		lastHeading = Float.NaN;
		guardEmote(p);
	}

	/** The tantrum is over (or cut short): its emote stops, the body starts afresh. */
	public void endTantrum()
	{
		stopEmote();
		rig.reset();
		footBody.reset();
		bodyPose.neutral();
		lastHeading = Float.NaN;
	}

	/** The winner's celebration starts: {@code animationId} (the cheer on the board, jump for joy on foot). */
	public void startCelebration(int animationId)
	{
		Player p = client.getLocalPlayer();
		if (p == null || !emoteAnimations)
		{
			return;
		}
		currentAction = Action.NONE;
		actionTimeLeft = 0f;
		emoteAnimation = animationId;
		p.setAnimation(animationId);
		p.setAnimationFrame(0);
	}

	/**
	 * The celebration {@code progress} (0..1) of the way through: its emote is paced to the celebration's length
	 * (left to the game's speed if it can't be). Call after the frame's own update; a trick's animation that took over
	 * is left alone.
	 */
	public void paceCelebration(float progress)
	{
		Player p = client.getLocalPlayer();
		if (p == null || emoteAnimation == NO_ANIMATION)
		{
			return;
		}
		scrub(p, emoteAnimation, progress);
	}

	/** A duel ending's emote stops (skipped or over), unless something else already replaced it. */
	public void stopEmote()
	{
		Player p = client.getLocalPlayer();
		if (p != null && emoteAnimation != NO_ANIMATION && p.getAnimation() == emoteAnimation)
		{
			p.setAnimation(NO_ANIMATION);
			p.setAnimationFrame(0);
		}
		emoteAnimation = NO_ANIMATION;
	}

	/** An emote that leaves the skater with no model is not played again this session. */
	private void guardEmote(Player p)
	{
		if (guard.frame(hasModel(p)))
		{
			emoteAnimations = false;
			emoteAnimation = NO_ANIMATION;
			p.setAnimation(NO_ANIMATION);
			warning = "The duel ending's emote made your skater disappear, so it is left out for now.";
		}
	}

	/** Shows the frame of a classic animation {@code progress} of the way through it (left alone otherwise). */
	private void scrub(Player p, int animationId, float progress)
	{
		try
		{
			if (animationId != scrubAnimationId)
			{
				scrubAnimationId = animationId;
				Animation a = client.loadAnimation(animationId);
				scrubAnimation = a != null && !a.isMayaAnim() ? a : null;
			}
			if (scrubAnimation == null || p.getAnimation() != animationId)
			{
				return;
			}
			int frame = AnimationScrub.frameAt(progress, scrubAnimation.getFrameLengths());
			if (frame >= 0)
			{
				p.setAnimationFrame(frame);
			}
		}
		catch (RuntimeException ex)
		{
			scrubAnimation = null;
			log.debug("Could not pace the get-up animation; it plays at the game's speed", ex);
		}
	}

	/**
	 * The walker is faster than the game's walk and run, so the gait animation is stepped on by the frames the
	 * game's own playback leaves out, keeping the feet from sliding. Only a classic (frame-based) animation that is
	 * the one playing now is touched; anything unexpected leaves it at the game's speed.
	 */
	private void speedUpGait(Player p, int animationId, float rate, float dt)
	{
		if (!gaitSpeedUp || rate <= 1f)
		{
			return;
		}
		try
		{
			if (animationId != gaitAnimationId)
			{
				gaitAnimationId = animationId;
				Animation a = animationId >= 0 ? client.loadAnimation(animationId) : null;
				gaitAnimation = a != null && !a.isMayaAnim() ? a : null;
				gaitPlayback.reset();
			}
			if (gaitAnimation == null || p.getPoseAnimation() != animationId)
			{
				return;
			}
			int frame = gaitPlayback.advance(p.getPoseAnimationFrame(), gaitAnimation.getFrameLengths(), rate, dt);
			if (frame >= 0)
			{
				p.setPoseAnimationFrame(frame);
			}
		}
		catch (RuntimeException ex)
		{
			gaitSpeedUp = false;
			log.warn("Could not speed up the walk animation; it plays at the game's speed from now on", ex);
		}
	}

	/** The player's own pose animation for {@code gait} (the idle one if the game has none for it). */
	private int gaitAnimation(FootBody.Gait gait, Player p)
	{
		int idle = realIdlePose(p);
		switch (gait)
		{
			case RUN:
			{
				int run = p.getRunAnimation();
				if (run >= 0)
				{
					return run;
				}
				int walk = p.getWalkAnimation();
				return walk >= 0 ? walk : idle;
			}
			case WALK:
			{
				int walk = p.getWalkAnimation();
				return walk >= 0 ? walk : idle;
			}
			default:
				return idle;
		}
	}

	/** The idle pose the player had before skating (the skate stance replaced it). */
	private int realIdlePose(Player p)
	{
		return started ? ownIdlePose.restorePose() : p.getIdlePoseAnimation();
	}

	/** Sets the player's idle pose, noting it was ours. */
	private void setIdlePose(Player p, int pose)
	{
		p.setIdlePoseAnimation(pose);
		ownIdlePose.wrote(pose);
	}

	/**
	 * The game may have set a new idle pose (a weapon wielded while skating): it becomes the one given back on
	 * exit, and ours is written again.
	 */
	private void noteGameIdlePose(Player p)
	{
		if (started && ownIdlePose.observe(p.getIdlePoseAnimation()))
		{
			lastIdlePose = Integer.MIN_VALUE;
		}
	}

	/** A message for the player if the animator had to recover, else null. Clears it. */
	public String takeWarning()
	{
		String w = warning;
		warning = null;
		return w;
	}

	public void stop()
	{
		if (started)
		{
			Player p = client.getLocalPlayer();
			if (p != null)
			{
				noteGameIdlePose(p);
				setIdlePose(p, ownIdlePose.restorePose());
				p.setAnimation(NO_ANIMATION);
			}
		}
		started = false;
		resetBody();
		knockAnimation = NO_ANIMATION;
		emoteAnimation = NO_ANIMATION;
		currentAction = Action.NONE;
		actionTimeLeft = 0f;
		lastIdlePose = Integer.MIN_VALUE;
	}

	private void updateBody(RenderPose pose, SkatePhysics physics, List<SkateEvent> events, float dt)
	{
		// carve rate from the drawn (interpolated) heading, so it is smooth between physics steps; a reset
		// teleports the heading, which is not a carve
		float rate = 0f;
		if (!Float.isNaN(lastHeading) && dt > 0f && !events.contains(SkateEvent.RESET))
		{
			rate = Angles.wrap(pose.heading - lastHeading) / dt;
		}
		lastHeading = pose.heading;

		SkaterPoseRig.Signals s = signals;
		s.state = pose.state;
		s.speed = physics.getSpeed();
		s.carveRate = rate;
		s.charge = physics.getPopCharge();
		s.verticalSpeed = physics.getVerticalVelocity();
		s.boardPitch = pose.boardPitch;
		// rolling with a grab key held the body grabs the board on the ground (pose only, the physics' ground grab)
		s.hold = pose.hold != null ? pose.hold : physics.getGroundGrab();
		s.flipping = pose.boardRoll != 0f || pose.boardYaw != 0f;
		s.popped = events.contains(SkateEvent.POP);
		s.rolledOff = events.contains(SkateEvent.ROLL_OFF);
		s.landed = events.contains(SkateEvent.LAND);
		s.bailed = events.contains(SkateEvent.BAIL);
		s.landingSpeed = physics.getLastLandingSpeed();
		s.proceduralBail = poses.bail == StancePoses.NONE;
		s.pushed = events.contains(SkateEvent.PUSH);
		s.proceduralPush = poses.push == StancePoses.NONE;
		// front/back flips: the body turns head over heels by the physics body-flip angle (0 when upright)
		s.bodyFlipAngle = physics.getBodyFlipAngle();
		s.onFoot = false;
		// the grabbing hand: Q the left, E the right
		s.grabHand = physics.isGrabLeftHand() ? 1 : -1;
		rig.update(s, dt);
		rig.writeTo(bodyPose);
	}

	/**
	 * Whether the player has a model, for the guard: the puppet's latest draw when it reported one since the last
	 * check (the model it fetched anyway), else a fetch of our own.
	 */
	private boolean hasModel(Player p)
	{
		return drawnReport.isFresh() ? drawnReport.take() : p.getModel() != null;
	}

	private void resetBody()
	{
		rig.reset();
		bodyPose.neutral();
		footBody.reset();
		lastHeading = Float.NaN;
	}

	private int animationFor(Action action, Player p)
	{
		switch (action)
		{
			case PUSH:
				return poses.push;
			case JUMP:
				return poses.jump;
			case BAIL:
				return poses.bail;
			default:
				return NO_ANIMATION;
		}
	}
}
