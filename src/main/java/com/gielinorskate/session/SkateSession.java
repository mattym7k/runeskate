package com.gielinorskate.session;

import com.gielinorskate.*;
import com.gielinorskate.camera.*;
import com.gielinorskate.controller.PadPresets;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.input.*;
import com.gielinorskate.overlay.*;
import com.gielinorskate.party.*;
import com.gielinorskate.physics.*;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.render.*;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.ui.PanelState;
import com.gielinorskate.world.*;
import com.google.gson.Gson;
import java.awt.event.KeyEvent;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;

/** Owns one skate-mode session: enter/exit, fixed-step loop, player hiding. Client thread only. */
@Slf4j
@Singleton
public class SkateSession
{
	private static final float STEP = 0.02f;
	private static final int MAX_STEPS_PER_FRAME = 5;

	private final Client client;
	private final GielinorSkateConfig config;
	private final SkateTuning baseTuning;
	private final SkateInput skateInput = new SkateInput();
	private final SkaterRenderer renderer;
	private final SkaterAnimator animator;
	private final SkateCamera camera;
	@Inject
	private SkateChat skateChat;
	@Inject
	private InputController input;
	@Inject
	private ComboScorer scorer;
	@Inject
	private ScoreClock scoreClock;
	@Inject
	private PartyGhostService partyGhosts;
	@Inject
	private ConfigManager configManager;
	/** Sounds, particles and bail jokes (Q3), fed each frame after the scorer. */
	@Inject
	private SkateFeedback feedback;
	/** Skate XP, levels, decks and session goals (Q4), fed each landed combo. */
	@Inject
	private ProgressionService progression;
	/** The board camera turned by hand (button tricks: the right stick), easing back behind the skater when idle. */
	private final BoardOrbit boardOrbit = new BoardOrbit();

	@Getter
	private boolean active;
	/** Skating in a PvP area: other players aren't drawn (see SafetyRules.hideOtherPlayers). */
	private boolean hideOtherPlayers;
	/** The skater (not the real character) is inside the Wilderness; updated every frame. */
	private boolean skaterInWilderness;
	private SkatePhysics physics;
	/** Read-only grind segments of the current skate session (empty when not skating), for the grind edges overlay. */
	@Getter
	private volatile List<GrindSegment> grindSegments = Collections.emptyList();
	/** Owns the controls card's show/auto-fade timing; driven from enter() and onFrame(). */
	private final ControlsCardTimer controlsCard = new ControlsCardTimer();
	/** Times the current manual hold from the outside, since physics exposes no elapsed-time getter. */
	private final ManualMeter manualMeter = new ManualMeter();
	/** "key/keyboardTricks" of the unusable manual key already warned about (one chat warning per setup). */
	private String warnedManualKey;
	/** The key-setting notes last said in chat (see {@link #warnKeyNotes}). */
	private List<String> lastKeyNotes = Collections.emptyList();
	/** "Manual 1.4s"-style label while the skater is in a manual, near the trick stack; null otherwise. */
	@Getter
	private volatile String manualMeterText;
	private WorldPoint anchor;
	/** The collision world of the current session (null when not skating), for the loaded-area edge. */
	private GridCollisionWorld world;
	/** Best combo and tricks landed for the side panel. */
	private final SessionStats stats = new SessionStats();
	/** Why skating last failed to start (cleared when it starts), for the side panel. */
	private String lastRefusal;
	/** The side panel's last state, and who wants new ones (the panel, through invokeLater). Client thread. */
	private PanelState lastPanel;
	private Consumer<PanelState> panelListener;
	/** Near-miss flick hints ("Flick faster"), throttled; read by ScoreOverlay. */
	private final TrickHints trickHints = new TrickHints();
	/** Why the latest bail happened ("Hit a wall"), shown under "Bailed"; null before any bail this session. */
	@Getter
	private volatile String bailReasonText;
	/** The "clicks are paused" tip was already given this session (left clicks are silently consumed). */
	private boolean clickHintShown;
	private final ComfortHints.IdleWarning idleWarning = new ComfortHints.IdleWarning();
	/** HUD hints, read by ComfortHintsOverlay: the idle warning line (null when none), the edge hint opacity. */
	private volatile String idleWarningText;
	private volatile float edgeHintAlpha;
	/** Score-clock time of the latest stumble. */
	private volatile float lastStumbleAt = Float.NEGATIVE_INFINITY;
	private long lastNanos;
	private float accumulator;
	/** Physics pose before the latest step; the frame is drawn between it and the current pose. */
	private RenderPose prevPose;
	private final LockBlend lockBlend = new LockBlend();
	/** Isolates party-ghost errors from local skating; reset each session. */
	private final GhostFailureGuard ghostGuard = new GhostFailureGuard();
	/** The scorer's result sequence already handed to progression. */
	private int progressedResults;
	/** On board or on foot, and where the board is. */
	private final BoardSwap boardSwap = new BoardSwap();
	private final BoardKey.Edges boardKeyEdges = new BoardKey.Edges();
	private final FootControls footControls = new FootControls();
	private final FootInput footInput = new FootInput();
	/** The session's tuning (scaled by the settings), for the skater made on each mount. */
	private SkateTuning tuning;
	/** The walker while on foot, else null. On foot, {@link #physics} is a standing stand-in kept at the walker. */
	private FootPhysics foot;
	/** Off-board pose before the latest foot step (null when there is none to blend from). */
	private OffBoardPose prevFootPose;
	/** What W2's renderer draws of the off-board side, every frame while skating; null when not skating. */
	private volatile OffBoardPose offBoardPose;
	/** The on-foot controls were said this session. */
	private boolean footTipShown;
	/** The knocked-off tip was said this session. */
	private boolean knockTipShown;
	/** Knocked off the board by a bail, until standing (or back on with R); null otherwise. */
	private Knockdown knockdown;
	/** The knockdown before the latest step (null when there is none to blend from). */
	private KnockdownPose prevKnockPose;
	private final Random knockRandom = new Random();
	/**
	 * The bail frame's sounds, smoke and bail line (its events, trick events and wall hit), held back until the body
	 * hits the ground; given the score time and frame time then. Null when given (or none held).
	 */
	private BiConsumer<Float, Float> heldBail;
	/** The controller layout the controls card names, read from the settings when they change. */
	private final PadPresets.Cache padPresets = new PadPresets.Cache();

	@Inject
	SkateSession(Client client, GielinorSkateConfig config, Gson gson)
	{
		this.client = client;
		this.config = config;
		baseTuning = loadTuning(gson);
		renderer = new SkaterRenderer(client);
		animator = new SkaterAnimator(client);
		renderer.setBodyPose(animator.getBodyPose());
		applyBoardSettings();
		renderer.setDrawnReport(animator.getDrawnReport());
		camera = new SkateCamera(client);
	}

	private static SkateTuning loadTuning(Gson gson)
	{
		try (Reader r = new InputStreamReader(
			SkateSession.class.getResourceAsStream("/com/gielinorskate/skate-tuning.json"), StandardCharsets.UTF_8))
		{
			return SkateTuning.load(gson, r);
		}
		catch (Exception e)
		{
			log.warn(Text.get("ss.log.tuning"), e);
			return new SkateTuning();
		}
	}

	/** The "Board detail" setting, for the local skater's board. */
	private void applyBoardSettings()
	{
		renderer.setBoardDetail(config.boardDetail() == GielinorSkateConfig.BoardDetail.HIGH);
	}

	/** True while skating with "Show grindable edges" on. */
	public boolean isShowingGrinds()
	{
		return config.showGrindEdges() && active;
	}

	/** Opacity in [0, 1] of the controls card (H), 0 when it should not be drawn at all. */
	public float getControlsCardAlpha()
	{
		return active ? controlsCard.alpha(scoreClock.now()) : 0f;
	}

	/** What the controls card shows: the settings its wording depends on. */
	public ControlsCardOverlay.Spec getControlsCardSpec()
	{
		GielinorSkateConfig.TrickControls controls = config.trickControls();
		return new ControlsCardOverlay.Spec(getManualKeyLabel(), controls.keyboard(),
			controls.mouse(), config.flickButton().toString().toLowerCase(), config.mirrorFlicks(),
			controlsCard.learnedBasics(), config.controllerMode(), getMode() == SkateMode.ON_FOOT,
			getBoardKeyLabel(), padPresets.get(config.controllerPreset(), config.customControllerLayout()));
	}

	/** The board on/off key in use, for the card, panel and chat: F when the configured one is not usable. */
	public String getBoardKeyLabel()
	{
		Keybind board = config.boardKey();
		return ComfortHints.boardKeyLabel(board.toString(), board.getKeyCode(), config.trickControls().keyboard());
	}

	/** The manual key in use, for the controls card's text: Space when the configured one is not usable. */
	public String getManualKeyLabel()
	{
		Keybind key = config.manualKey();
		return ComfortHints.manualKeyLabel(key.toString(), key.getKeyCode(), config.trickControls().keyboard());
	}

	public boolean shouldHide(Renderable r)
	{
		// the real character is replaced by the skater; in PvP areas other players are hidden too
		return active && (r == client.getLocalPlayer() || (inPvpArea() && r instanceof Player));
	}

	/**
	 * In a PvP area (where nothing is shared and other players are hidden). The real character can't move while
	 * skating, so hideOtherPlayers still says whether it is in one; the skater may have rolled into the Wilderness.
	 */
	private boolean inPvpArea()
	{
		return hideOtherPlayers || skaterInWilderness;
	}

	/** Starts or stops skating; returns why skating could not start (also said in chat), or null. */
	public String toggle()
	{
		if (active)
		{
			exit("Skate mode off.");
			return null;
		}
		return enter();
	}

	/** Starts skating. Returns null when skating started, otherwise why not; the reason is also said in chat. */
	private String enter()
	{
		Player p = client.getLocalPlayer();
		if (p == null)
			return "Log in to skate.";
		if (client.getLocalDestinationLocation() != null)
			return refuse("Stand still to start skating.");
		if (!p.getWorldView().isTopLevel())
			return refuse(Text.get("ss.boat"));

		WorldView wv = client.getTopLevelWorldView();
		WorldPoint here = p.getWorldLocation();
		boolean inWilderness = SafetyRules.inPvpArea(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1,
			client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1);
		boolean pvpWorld = SafetyRules.isOptInPvpWorld(client.getWorldType());
		String reason = SafetyRules.alwaysBlockedReason(wv.isInstance(),
			client.getVarbitValue(VarbitID.BR_INGAME) != 0,
			client.getVarbitValue(VarbitID.DEADMAN_INWILDERNESS) != 0,
			client.getWorldType(), SafetyRules.regionId(here.getX(), here.getY()));
		if (reason == null)
			reason = SafetyRules.blockReason(inWilderness, pvpWorld, isInCombat(p), config.allowPvpAreas());
		if (reason != null)
			return refuse(reason);

		int plane = wv.getPlane();
		world = SceneCollisionBuilder.build(client, wv, plane, config.passThroughVegetation());
		// party ghosts follow the same ground as the local skater here
		partyGhosts.setCollisionWorld(world, wv.getBaseX(), wv.getBaseY(), plane, world.size());
		applyLook(progression.look());
		if (!renderer.spawn(wv, plane))
			return refuse(Text.get("ss.model"));

		LocalPoint lp = p.getLocalLocation();
		float speedScale = SkateTuning.scaleFromPercent(config.speedScale());
		tuning = baseTuning.scaled(speedScale, SkateTuning.scaleFromPercent(config.popScale()));
		if (config.forgivingCollisions())
		{
			// smaller hitbox around the skater (8 rather than the default) and harder hits needed to bail (scaled
			// with push speed too)
			tuning.skaterRadius = 8f;
			tuning.wallBailSpeed = 1500f * speedScale;
		}
		// a wall right ahead (a bank booth): start facing the most open way instead
		float heading = StartHeading.choose(world, lp.getX(), lp.getY(), Angles.fromJau(p.getOrientation()),
			tuning.skaterRadius, tuning.maxStepUp);
		physics = new SkatePhysics(tuning, world, world.getGrinds(), lp.getX(), lp.getY(), heading);
		clearRide();
		footTipShown = knockTipShown = clickHintShown = false;
		grindSegments = world.getGrinds().segments();
		anchor = here;
		active = true;
		ghostGuard.reset();
		hideOtherPlayers = SafetyRules.hideOtherPlayers(inWilderness, pvpWorld);
		animator.start();
		input.setEnabled(true);
		camera.enter(physics.getX(), physics.getY(), physics.getH(), heading, config.cameraZoom());
		boardOrbit.reset();
		lastNanos = System.nanoTime();
		accumulator = 0f;
		prevPose = RenderPose.of(physics);
		lockBlend.reset();
		manualMeter.update(null, scoreClock.now());
		if (config.learnedBasics())
			controlsCard.markLearned();
		controlsCard.onSkateStart(config.showControlsCard());
		scorer.startSession();
		progressedResults = scorer.resultSequence();
		feedback.onSkateStart();
		progression.startSession();
		trickHints.reset();
		bailReasonText = null;
		chat(Text.get("ss.on"));
		float edgeTiles = world.edgeDistance(lp.getX(), lp.getY()) / GridCollisionWorld.TILE;
		if (ComfortHints.needsRoomTip(edgeTiles))
			chat(Text.get("ss.edge", Math.max(0, Math.round(edgeTiles))));
		lastRefusal = null;
		publishPanel();
		warnIfManualKeyUnusable();
		warnKeyNotes(true);
		return null;
	}

	/** Forgets the ride: on the board, no walker, knockdown, held bail or manual meter. */
	private void clearRide()
	{
		foot = null;
		prevFootPose = null;
		knockdown = null;
		prevKnockPose = null;
		heldBail = null;
		manualMeterText = null;
		boardSwap.reset();
	}

	private String refuse(String reason)
	{
		chat(reason);
		lastRefusal = reason;
		publishPanel();
		return reason;
	}

	public void exit(String message)
	{
		if (!active)
			return;
		active = hideOtherPlayers = skaterInWilderness = false;
		input.setEnabled(false);
		input.setClickThroughAreas(Collections.emptyList());
		// mouse-wheel zoom while skating is kept: written back to the camera zoom setting
		int zoom = camera.getBaseZoom();
		if (zoom != config.cameraZoom())
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "cameraZoom", zoom);
		camera.exit(pitchRelaxerOn());
		safely(renderer::despawn, "despawn skate renderer");
		safely(animator::stop, "stop skate animator");
		safely(partyGhosts::onSkateEnd, "end party ghosts");
		physics = null;
		world = null;
		offBoardPose = null;
		clearRide();
		idleWarning.reset();
		idleWarningText = null;
		edgeHintAlpha = 0f;
		lastStumbleAt = Float.NEGATIVE_INFINITY;
		prevPose = null;
		grindSegments = Collections.emptyList();
		// a combo cut off mid-air or mid-grind must not be awarded in the next session
		scorer.abandonCombo();
		feedback.onSkateEnd();
		progression.flush();
		publishPanel();
		chat(message);
	}

	/** Runs {@code r}, logging (not throwing) a failure to {@code what}. */
	private static void safely(Runnable r, String what)
	{
		try
		{
			r.run();
		}
		catch (RuntimeException ex)
		{
			log.warn("Failed to " + what, ex);
		}
	}

	public void onFrame()
	{
		if (!active)
			return;
		long now = System.nanoTime();
		float frameDt = Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;

		if (input.consumeExit())
		{
			exit("Skate mode off.");
			return;
		}

		try
		{
			if (knockdown != null)
				knockdownFrame(frameDt);
			else if (!offBoardFrame(frameDt))
				boardFrame(frameDt);
		}
		catch (RuntimeException ex)
		{
			log.warn("Skate frame failed", ex);
			exit(Text.get("ss.error"));
		}
	}

	private void boardFrame(float frameDt)
	{
		// the side panel and chatbox stay clickable; refreshed each frame as tabs open and close
		input.setClickThroughAreas(ClickThroughAreas.collect(client));
		input.drainInto(skateInput);
		NearMiss miss = input.consumeNearMiss();
		if (!skateInput.gestures.isEmpty())
			trickHints.onTrick();
		else if (config.showTrickHints())
			trickHints.offer(miss, scoreClock.now());
		hintKeys(true);

		SkaterState stateBefore = physics.getState();
		int steps = runSteps(frameDt, () -> true, () ->
		{
			// the pose before each step, so the frame can be drawn between the last two steps
			prevPose = RenderPose.of(physics);
			physics.step(STEP, skateInput);
		});
		if (steps == MAX_STEPS_PER_FRAME)
			accumulator = 0f;
		SkaterState state = physics.getState();
		// a controller's stick can pop out of a rolling grab
		input.setRolling(state == SkaterState.ROLLING);
		// a pad button with an air action of its own does it in the air
		input.setAirborne(state == SkaterState.AIRBORNE);
		// button tricks: a stick manual holds while physics is in it; the grind button steps off with no rail near
		input.setInManual(state == SkaterState.MANUAL);
		input.setRailNear(physics.isRailNear());
		// drawn one step behind physics; a grind lock eases onto the rail instead of snapping
		RenderPose pose = lockBlend.apply(RenderPose.lerp(prevPose, RenderPose.of(physics), blendAlpha()), frameDt);
		boolean hitThisFrame = physics.hasHit();
		physics.clearHit();
		List<SkateEvent> events = physics.drainEvents();
		float scoreTime = scoreClock.now();
		updateControlsCard(events, scoreTime);
		boolean bail = events.contains(SkateEvent.BAIL);
		if (bail && physics.getLastBailReason() != null)
			bailReasonText = physics.getLastBailReason().text;
		boolean nolliePop = events.contains(SkateEvent.POP) && physics.isLastPopNollie();
		List<TrickEvent> trickEvents = physics.drainTrickEvents();
		for (TrickEvent e : trickEvents)
			// a LANDED event carries its own clean flag for the clean bonus
			scorer.accept(e, scoreTime);
		scorer.update(scoreTime, state == SkaterState.ROLLING && physics.getActiveHold() == null);
		tickScore(events, scoreTime);
		float physicsDt = steps * STEP;
		if (bail && BailRules.knocksOff(config.afterBail()))
		{
			// knocked off the board: scored, counted and shared as any bail; the rest is the knockdown's
			clearManualMeter();
			startKnockdown(events, trickEvents, hitThisFrame);
			shareKnockdown(events, trickEvents, stateBefore, frameDt, physicsDt);
			drawKnockdown(frameDt, 0f);
			return;
		}
		feedback.onFrame(physics, events, trickEvents, hitThisFrame, scoreTime, frameDt);

		Trick hold = physics.getActiveHold();
		// the manual being held (a swap between manual and nose manual restarts the meter)
		manualMeter.update(hold == Trick.MANUAL || hold == Trick.NOSE_MANUAL ? hold : null, scoreTime);
		manualMeterText = manualMeter.text(scoreTime);

		// just got on: the renderer eases over from the walker
		renderer.setTransition(boardSwap.getTransition(), boardSwap.getTransitionProgress());
		renderer.update(pose, skateInput.steer, skateInput.crouch, events, nolliePop, frameDt);
		animator.update(pose, physics, events, frameDt);
		chat(animator.takeWarning());
		if (!checkSkaterArea(physics.getX(), physics.getY()))
			return;
		// button tricks: the right stick turns the chase camera; idle, it eases back behind the skater
		boardOrbit.turn(cameraInput());
		boardOrbit.update(frameDt);
		follow(pose.x, pose.y, pose.h, pose.cameraHeading + boardOrbit.offset(), physics.getSpeed(), state,
			events.contains(SkateEvent.LAND) ? physics.getLastAirtime() : 0f, frameDt);
		// the skater on the board; a ghost error turns ghosts off for this session but never ends local skating
		SkatePhysics framePhysics = physics;
		boolean pvp = inPvpArea();
		ghosts(() -> partyGhosts.onSkateFrame(framePhysics, events, trickEvents, stateBefore, pvp, frameDt,
			physicsDt));
		offBoardPose = onBoardPose(pose);
	}

	/**
	 * Runs the fixed physics steps this frame's time allows (at most {@link #MAX_STEPS_PER_FRAME}, and only while
	 * {@code more} holds); returns how many ran.
	 */
	private int runSteps(float frameDt, BooleanSupplier more, Runnable step)
	{
		accumulator += frameDt;
		int steps = 0;
		while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME && more.getAsBoolean())
		{
			step.run();
			accumulator -= STEP;
			steps++;
		}
		return steps;
	}

	/** How far the frame is drawn from the previous step to the latest one (always the latest without smoothing). */
	private float blendAlpha()
	{
		return config.smoothMotion() ? accumulator / STEP : 1f;
	}

	/** Shares a frame with the party; a ghost error turns ghosts off for this session but never ends local skating. */
	private void ghosts(Runnable frame)
	{
		ghostGuard.run(frame, partyGhosts::onGhostFailure,
			e -> log.warn(Text.get("ss.log.ghosts"), e));
	}

	/** The wheel zoom and the camera orbit (a middle-button drag, or the right stick) of this frame; returns the turn. */
	private float cameraInput()
	{
		camera.adjustZoom(input.drainZoomNotches());
		// about one turn per 1,000 pixels of drag; dragging right turns the view like the game's own
		float turn = -input.drainOrbitPixels() * 0.006f;
		camera.orbit(turn);
		return turn;
	}

	/** The camera follows (x, y, h) looking along {@code heading}, kicking with a landing of {@code landedAirtime}. */
	private void follow(float x, float y, float h, float heading, float speed, SkaterState state, float landedAirtime,
		float frameDt)
	{
		camera.update(x, y, h, heading, speed, state, landedAirtime, frameDt, config.cameraHeight().pitchJau14());
	}

	/** A plain click's one-time hint and H's controls card; both are dropped when not {@code live}. */
	private void hintKeys(boolean live)
	{
		if (input.consumePlainClick() && live && !clickHintShown)
		{
			clickHintShown = true;
			chat(Text.get("ch.click"));
		}
		if (input.consumeControlsToggle() && live)
			controlsCard.toggle(scoreClock.now());
	}

	/**
	 * Off the board (on foot or knocked down): the board's controls are read, so nothing is left over
	 * for later, but do nothing. Returns whether R went down.
	 */
	private boolean drainOffBoard()
	{
		boardOrbit.reset();
		// the side panel and chatbox stay clickable; refreshed each frame as tabs open and close
		input.setClickThroughAreas(ClickThroughAreas.collect(client));
		input.drainInto(skateInput);
		boolean reset = skateInput.resetRequested;
		skateInput.gestures.clear();
		skateInput.pushPressed = skateInput.resetRequested = false;
		input.consumeNearMiss();
		return reset;
	}

	/** The new combo results and the HUD hints, each frame. */
	private void tickScore(List<SkateEvent> events, float scoreTime)
	{
		takeResults();
		updateHints(events, scoreTime);
	}

	private void clearManualMeter()
	{
		manualMeter.update(null, scoreClock.now());
		manualMeterText = null;
	}

	// ---- Knockdown

	/**
	 * A bail knocks the skater off: the body and the board fly off on their own (see {@link Knockdown}); the bail's
	 * sounds, smoke and line wait for the impact.
	 */
	private void startKnockdown(List<SkateEvent> events, List<TrickEvent> trickEvents, boolean hit)
	{
		SkatePhysics p = physics;
		knockdown = new Knockdown(world, tuning.gravity, tuning.skaterRadius);
		knockdown.start(p.getX(), p.getY(), p.getH(), p.getVelocityX(), p.getVelocityY(), p.getHeading(),
			p.isBailWall(), p.getBailWallNx(), p.getBailWallNy(), knockRandom);
		prevKnockPose = null;
		List<SkateEvent> heldEvents = new ArrayList<>(events);
		List<TrickEvent> heldTricks = new ArrayList<>(trickEvents);
		heldBail = (scoreTime, frameDt) -> feedback.onFrame(physics, heldEvents, heldTricks, hit, scoreTime, frameDt);
		// WASD, Space and F are the on-foot keys from here
		input.setOnFoot(true);
	}

	/** One frame knocked off: the keys (R, a skip, F), the fixed steps, the impact, the drawing; or the end of it. */
	private void knockdownFrame(float frameDt)
	{
		boolean reset = drainOffBoard();
		hintKeys(true);
		input.drainFoot(footControls);
		FootControls fc = footControls;
		input.pollBoardKey(boardKeyEdges);
		boardSwap.tick(frameDt);

		Knockdown.Outcome outcome = knockdown.input(reset, fc.forward || fc.back || fc.left || fc.right,
			fc.jumpPressed, boardKeyEdges.pressed);
		if (outcome == Knockdown.Outcome.BACK_ON_BOARD)
		{
			// R: straight back on the board where the body lies (or the nearest standable spot)
			TumbleBody b = knockdown.getBody();
			float[] spot = endKnockdown();
			physics.placeBailed(b.getX(), b.getY(), b.getH(), 0f, 0f, 0f);
			giveHeldBail(scoreClock.now(), 0f);
			boardSwap.remount();
			mount(spot[0], spot[1], b.getFacing(), 0f);
			return;
		}
		int steps = runSteps(frameDt, () -> !knockdown.isDone(), () ->
		{
			prevKnockPose = knockPose();
			knockdown.step(STEP);
		});
		boolean impact = knockdown.takeImpact();
		if (steps == MAX_STEPS_PER_FRAME || knockdown.isDone())
			accumulator = Math.min(accumulator, STEP);
		TumbleBody body = knockdown.getBody();
		physics.placeBailed(body.getX(), body.getY(), body.getH(), body.getVelocityX(), body.getVelocityY(),
			body.getVerticalSpeed());
		float scoreTime = scoreClock.now();
		// at the impact (or once down after a long fall cut short)
		if (impact || knockdown.getPhase() != Knockdown.Phase.TUMBLE)
			giveHeldBail(scoreTime, frameDt);
		if (outcome == Knockdown.Outcome.RECLAIM || knockdown.isDone())
		{
			standUp(outcome == Knockdown.Outcome.RECLAIM);
			return;
		}
		tickScore(List.of(), scoreTime);
		if (heldBail == null)
			// after the impact: the smoke and any delayed sounds play on
			feedback.onFrame(physics, List.of(), List.of(), false, scoreTime, frameDt);
		if (drawKnockdown(frameDt, impact ? body.getLastAirTime() : 0f))
			shareKnockdown(List.of(), List.of(), SkaterState.BAILED, frameDt, steps * STEP);
	}

	/**
	 * Shares the knocked-off skater (the body, its stage and lying angle, the board where it is) with the party and
	 * draws its ghosts.
	 */
	private void shareKnockdown(List<SkateEvent> events, List<TrickEvent> trickEvents, SkaterState stateBefore,
		float frameDt, float physicsDt)
	{
		KnockdownPose k = knockPose();
		TumbleBody body = knockdown.getBody();
		float vx = body.getVelocityX();
		float vy = body.getVelocityY();
		boolean airborne = body.isAirborne();
		float lie = body.getLieAngle();
		boolean pvp = inPvpArea();
		ghosts(() -> partyGhosts.onKnockdownFrame(k, vx, vy, airborne, lie, events, trickEvents, stateBefore, pvp,
			frameDt, physicsDt));
	}

	/** The bail's sounds, smoke and line, held from the bail frame, at the knocked-off body. */
	private void giveHeldBail(float scoreTime, float frameDt)
	{
		BiConsumer<Float, Float> bail = heldBail;
		heldBail = null;
		if (bail != null)
			bail.accept(scoreTime, frameDt);
	}

	/** The knockdown now (not blended). */
	private KnockdownPose knockPose()
	{
		Knockdown k = knockdown;
		TumbleBody b = k.getBody();
		BoardBounce bb = k.getBoard();
		Knockdown.Phase phase = k.getPhase();
		float facing = b.getFacing();
		if (phase == Knockdown.Phase.GET_UP || phase == Knockdown.Phase.DONE)
		{
			// turning to face the board while getting up
			float u = k.getPhaseProgress();
			facing = Angles.wrap(facing + Angles.wrap(k.standHeading() - facing) * u * u * (3f - 2f * u));
		}
		KnockdownPose.Stage stage = phase == Knockdown.Phase.TUMBLE ? KnockdownPose.Stage.AIR
			: phase == Knockdown.Phase.LIE ? KnockdownPose.Stage.LIE : KnockdownPose.Stage.GET_UP;
		return new KnockdownPose(stage, k.getPhaseProgress(), b.getX(), b.getY(), b.getH(), facing, k.bodyAngle(),
			b.getRoll(), bb.getX(), bb.getY(), bb.getH(), bb.getYaw(), bb.getRoll(), bb.getPitch());
	}

	/**
	 * Draws the knocked-off skater and board, follows them with the camera (a landing kick at the impact) and checks
	 * the area; false when skating ended.
	 */
	private boolean drawKnockdown(float frameDt, float landedAirtime)
	{
		KnockdownPose pose = KnockdownPose.lerp(prevKnockPose, knockPose(), blendAlpha());
		// the animator first: the body's pose decides how low the renderer draws it
		animator.updateKnockdown(pose, frameDt);
		chat(animator.takeWarning());
		renderer.updateKnockdown(pose);
		TumbleBody body = knockdown.getBody();
		offBoardPose = new OffBoardPose(SkateMode.ON_FOOT, BoardState.DROPPED, pose.boardX, pose.boardY, pose.boardH,
			pose.boardYaw, pose.bodyX, pose.bodyY, pose.bodyH, pose.facing, body.getSpeed(), body.isAirborne(), false,
			body.getVerticalSpeed(), BoardTransition.NONE, 1f, false);
		if (!checkSkaterArea(pose.bodyX, pose.bodyY))
			return false;
		cameraInput();
		follow(pose.bodyX, pose.bodyY, pose.bodyH, camera.getYaw(), body.getSpeed(),
			body.isAirborne() ? SkaterState.AIRBORNE : SkaterState.ROLLING, landedAirtime, frameDt);
		return true;
	}

	/** The knockdown is over: the nearest standable spot to where the body lies. */
	private float[] endKnockdown()
	{
		TumbleBody b = knockdown.getBody();
		float[] spot = StandSpot.nearest(world, b.getX(), b.getY(), tuning.skaterRadius, 3 * GridCollisionWorld.TILE);
		animator.endKnockdown();
		knockdown = null;
		prevKnockPose = null;
		return spot;
	}

	/**
	 * Up on foot where the body lies (the nearest standable spot if that is not one), facing the board, which lies
	 * where it came to rest (or near the skater, if that is out of reach). With {@code reclaim} (the board key while
	 * down) the press also gets the board back: stepped on nearby, else called to hand.
	 */
	private void standUp(boolean reclaim)
	{
		TumbleBody b = knockdown.getBody();
		BoardBounce bb = knockdown.getBoard();
		float r = tuning.skaterRadius;
		float bodyH = b.getH();
		float facing = b.getFacing();
		float[] spot = endKnockdown();
		boolean moved = spot[0] != b.getX() || spot[1] != b.getY();
		// still in the air (a long fall): the walker falls on from there
		float h = moved ? spot[2] : Math.max(spot[2], bodyH);
		float[] rest = StandSpot.boardRest(world, bb.getX(), bb.getY(), bb.getH(), spot[0], spot[1], spot[2], r);
		float heading = Math.hypot(rest[0] - spot[0], rest[1] - spot[1]) < 1f ? facing
			: (float) Math.atan2(rest[0] - spot[0], rest[1] - spot[1]);
		boardSwap.knockedOff(rest[0], rest[1], rest[2], bb.getYaw());
		foot = new FootPhysics(world, tuning.gravity, r, spot[0], spot[1], h, heading);
		physics = standIn();
		prevFootPose = null;
		input.setOnFoot(true);
		giveHeldBail(scoreClock.now(), 0f);
		if (!knockTipShown)
		{
			knockTipShown = true;
			chat(Text.get("ss.knock", getBoardKeyLabel()));
		}
		if (reclaim && boardSwap.onBoardKeyPressed(true, spot[0], spot[1]) == BoardSwap.Action.MOUNT_DROPPED)
		{
			mount(boardSwap.getBoardX(), boardSwap.getBoardY(), boardSwap.getBoardHeading(), 0f);
			return;
		}
		offBoardPose = footPose();
	}

	/** Hands a new combo result to the side panel's stats and to progression. */
	private void takeResults()
	{
		if (stats.update(scorer.resultSequence(), scorer.lastResult(), scorer.lastResultTrickCount()))
			publishPanel();
		if (scorer.resultSequence() == progressedResults)
			return;
		progressedResults = scorer.resultSequence();
		if (scorer.lastResult().isLanded())
		{
			// before feedback runs, so a level-up's graphic and bells come this frame
			progression.onComboLanded(scorer.lastSummary());
		}
	}

	/**
	 * Ends skating when the skater (or walker) at local (x, y) is somewhere skating is not allowed, and notes
	 * whether it is in the Wilderness. False when skating ended.
	 */
	private boolean checkSkaterArea(float x, float y)
	{
		WorldPoint tile = WorldPoint.fromLocal(client,
			new LocalPoint(Math.round(x), Math.round(y), client.getTopLevelWorldView()));
		String areaBlock = SafetyRules.skaterAreaBlockReason(tile.getX(), tile.getY(), config.allowPvpAreas());
		if (areaBlock != null)
		{
			exit(areaBlock);
			return false;
		}
		skaterInWilderness = SafetyRules.isWildernessTile(tile.getX(), tile.getY());
		return true;
	}

	// ---- Off-board mode

	/**
	 * The board key and, on foot, the whole frame. Returns false on the board when the board frame should run
	 * as always; true when this frame was handled on foot (including the frame of stepping off).
	 */
	private boolean offBoardFrame(float frameDt)
	{
		input.pollBoardKey(boardKeyEdges);
		boardSwap.tick(frameDt);
		if (boardSwap.getMode() == SkateMode.ON_BOARD)
		{
			if (!boardKeyEdges.pressed
				|| boardSwap.onBoardKeyPressed(canDismount(), physics.getX(), physics.getY()) != BoardSwap.Action.DISMOUNT)
				return false;
			dismount();
			// this press stepped off; it must not also get back on
			boardKeyEdges.pressed = boardKeyEdges.held = boardKeyEdges.tapped = false;
		}
		footFrame(frameDt);
		return true;
	}

	/** Stepping off works on the ground only: rolling (at any speed) or in a manual. */
	private boolean canDismount()
	{
		SkaterState s = physics.getState();
		return s == SkaterState.ROLLING || s == SkaterState.MANUAL;
	}

	/** Steps off the board with it carried: the combo in progress is banked, the walker starts where the skater was. */
	private void dismount()
	{
		scorer.bankCombo(scoreClock.now());
		SkatePhysics p = physics;
		foot = new FootPhysics(world, tuning.gravity, tuning.skaterRadius, p.getX(), p.getY(), p.getH(), p.getHeading());
		// steps off with the roll's momentum, up to a sprint; it runs out within 0.1 s with no key held
		float carried = Math.min(Math.abs(p.getSpeed()), FootPhysics.SPRINT_SPEED);
		float travel = p.getTravelHeading();
		foot.setVelocity((float) Math.sin(travel) * carried, (float) Math.cos(travel) * carried);
		physics = standIn();
		prevFootPose = null;
		clearManualMeter();
		input.setOnFoot(true);
		if (!footTipShown)
		{
			footTipShown = true;
			chat(Text.get("ss.foot", getBoardKeyLabel()));
		}
	}

	/** A standing skater at the walker, for what still reads one on foot (effects, party ghosts, the edge hint). */
	private SkatePhysics standIn()
	{
		SkatePhysics s = new SkatePhysics(tuning, world, world.getGrinds(), foot.getX(), foot.getY(), foot.getHeading());
		s.placeStanding(foot.getX(), foot.getY(), foot.getH(), foot.getHeading());
		return s;
	}

	/** Gets on the board at (x, y), rolling forward at {@code speed}; the off-board renderer sees it at once. */
	private void mount(float x, float y, float heading, float speed)
	{
		physics = new SkatePhysics(tuning, world, world.getGrinds(), x, y, heading);
		if (speed > 0f)
			physics.setRollingSpeed(speed);
		foot = null;
		prevFootPose = null;
		prevPose = RenderPose.of(physics);
		lockBlend.reset();
		input.setOnFoot(false);
		offBoardPose = onBoardPose(prevPose);
	}

	private void footFrame(float frameDt)
	{
		drainOffBoard();
		hintKeys(true);
		input.drainFoot(footControls);
		FootControls fc = footControls;
		boolean moving = fc.forward != fc.back || fc.left != fc.right;
		footInput.setCameraRelative(fc.forward, fc.back, fc.left, fc.right, camera.getYaw());
		footInput.sprint = fc.sprint;
		footInput.jumpPressed |= fc.jumpPressed;
		// a jump that will land on the board is the bigger mount hop
		footInput.mountJump = boardSwap.willJumpMount(fc.sprint && moving, foot.getX(), foot.getY());

		int steps = runSteps(frameDt, () -> true, () ->
		{
			prevFootPose = footPose();
			foot.step(STEP, footInput);
		});
		if (steps == MAX_STEPS_PER_FRAME)
			accumulator = 0f;
		List<FootEvent> footEvents = foot.drainEvents();
		float x = foot.getX();
		float y = foot.getY();

		BoardSwap.Action action = BoardSwap.Action.NONE;
		if (footEvents.contains(FootEvent.LAND) && !foot.isAirborne())
			action = boardSwap.onLanded(foot.getJumpStartX(), foot.getJumpStartY(), x, y, foot.isJumpedSprinting());
		if (action == BoardSwap.Action.NONE && boardKeyEdges.pressed)
			// never in the air: stepping on mid-jump would drop the skater to the ground below at once
			action = boardSwap.onBoardKeyPressed(!foot.isAirborne(), x, y);
		if (action == BoardSwap.Action.NONE && boardKeyEdges.held)
			action = boardSwap.onBoardKeyHeld();
		if (action == BoardSwap.Action.NONE && fc.dropPickupPressed)
			action = boardSwap.onDropPickup(x, y, world.groundHeight(x, y), foot.getHeading());
		switch (action)
		{
			case MOUNT_CARRIED:
				// dropped under the feet on the move: rolls on at the walker's speed, the way it was going
				mount(x, y, foot.getSpeed() >= BoardSwap.MOVING_SPEED ? foot.getTravelHeading() : foot.getHeading(),
					BoardSwap.stepOnSpeed(foot.getSpeed(), tuning.maxPushSpeed));
				return;
			case MOUNT_DROPPED:
				mount(boardSwap.getBoardX(), boardSwap.getBoardY(), boardSwap.getBoardHeading(), 0f);
				return;
			case JUMP_MOUNT:
				mount(x, y, foot.getTravelHeading(),
					BoardSwap.jumpMountSpeed(foot.getSpeed(), foot.isJumpedSprinting(), tuning.maxPushSpeed));
				return;
			default:
				break;
		}

		physics.placeStanding(x, y, foot.getH(), foot.getHeading());
		float scoreTime = scoreClock.now();
		// no scoring on foot; a combo banked on stepping off is shown and counted here
		tickScore(List.of(), scoreTime);
		feedback.onFrame(physics, List.of(), List.of(), false, scoreTime, frameDt);
		manualMeterText = null;

		OffBoardPose pose = OffBoardPose.lerp(prevFootPose, footPose(), blendAlpha());
		offBoardPose = pose;
		renderer.updateOnFoot(pose, frameDt);
		animator.updateOnFoot(pose, frameDt);
		chat(animator.takeWarning());
		if (!checkSkaterArea(x, y))
			return;
		cameraInput();
		follow(pose.walkerX, pose.walkerY, pose.walkerH,
			FootCamera.targetHeading(camera.getYaw(), pose.walkerHeading, moving), foot.getSpeed(),
			foot.isAirborne() ? SkaterState.AIRBORNE : SkaterState.ROLLING,
			footEvents.contains(FootEvent.LAND) ? foot.getLastAirTime() : 0f, frameDt);
		// party members see the walker and the board (older plugin versions: a skater standing on its board)
		boolean pvp = inPvpArea();
		float physicsDt = steps * STEP;
		OffBoardPose stepped = footPose();
		float vx = foot.getVelocityX();
		float vy = foot.getVelocityY();
		ghosts(() -> partyGhosts.onFootFrame(stepped, vx, vy, footEvents, pvp, frameDt, physicsDt));
	}

	/** The walker and the board now (not blended). */
	private OffBoardPose footPose()
	{
		FootPhysics f = foot;
		return new OffBoardPose(SkateMode.ON_FOOT, boardSwap.getBoard(), boardSwap.getBoardX(), boardSwap.getBoardY(),
			boardSwap.getBoardH(), boardSwap.getBoardHeading(), f.getX(), f.getY(), f.getH(), f.getHeading(),
			f.getSpeed(), f.isAirborne(), f.isAirborne() && f.isJump(), f.getVerticalVelocity(),
			boardSwap.getTransition(), boardSwap.getTransitionProgress(),
			f.isAirborne() && f.isJump() && f.isMountJump() && boardSwap.getBoard() == BoardState.CARRIED);
	}

	/** The skater on the board, drawn this frame, as the off-board renderer sees it. */
	private OffBoardPose onBoardPose(RenderPose pose)
	{
		return new OffBoardPose(SkateMode.ON_BOARD, BoardState.CARRIED, boardSwap.getBoardX(), boardSwap.getBoardY(),
			boardSwap.getBoardH(), boardSwap.getBoardHeading(), pose.x, pose.y, pose.h, pose.heading,
			Math.abs(physics.getSpeed()), pose.state == SkaterState.AIRBORNE, false, physics.getVerticalVelocity(),
			boardSwap.getTransition(), boardSwap.getTransitionProgress(), false);
	}

	/** On board or on foot (ON_BOARD when not skating; ON_FOOT while knocked off). */
	public SkateMode getMode()
	{
		return !active ? SkateMode.ON_BOARD : knockdown != null ? SkateMode.ON_FOOT : boardSwap.getMode();
	}

	/**
	 * The off-board render state of the latest frame: mode, board CARRIED / DROPPED and where it lies, the walker
	 * (position, heading, speed, sprint, air), and the mount / dismount blend. Null when not skating. Client
	 * thread.
	 */
	public OffBoardPose getOffBoardPose()
	{
		return active ? offBoardPose : null;
	}

	/** The first push and jump put the auto-shown basics card away, and are remembered for later sessions. */
	private void updateControlsCard(List<SkateEvent> events, float now)
	{
		boolean learnedBefore = controlsCard.learnedBasics();
		if (events.contains(SkateEvent.PUSH))
			controlsCard.onPush(now);
		if (events.contains(SkateEvent.POP))
			controlsCard.onJump(now);
		if (!learnedBefore && controlsCard.learnedBasics() && !config.learnedBasics())
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "learnedBasics", true);
	}

	/** The edge-of-area hint, the "Stumble!" flash and the idle-logout warning (warning only, never prevented). */
	private void updateHints(List<SkateEvent> events, float scoreTime)
	{
		if (events.contains(SkateEvent.STUMBLE))
			lastStumbleAt = scoreTime;
		edgeHintAlpha = ComfortHints.edgeHintAlpha(world.edgeDistance(physics.getX(), physics.getY()) / GridCollisionWorld.TILE);
		// skate keys and right-drags are consumed, so the game does not see them as activity
		int idle = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
		if (idleWarning.update(idle, client.getIdleTimeout()))
			chat(ComfortHints.idleMessage(idle));
		idleWarningText = idleWarning.isWarning() ? ComfortHints.idleMessage(idle) : null;
	}

	/**
	 * Puts the board in these designs (now if skating, else from the next start), for party members too.
	 * Client thread.
	 */
	public void applyLook(BoardLook look)
	{
		renderer.setLook(look);
		// party members see it on our ghost
		partyGhosts.setLocalLook(look);
	}

	/** Who gets the side panel's state when it changes (called on the client thread). */
	public void setPanelListener(Consumer<PanelState> listener)
	{
		panelListener = listener;
		lastPanel = null;
	}

	/** Sends the side panel its state if it changed. Client thread. */
	public void publishPanel()
	{
		PanelState s = new PanelState(active, lastRefusal, scorer.sessionScore(), stats.bestCombo(),
			stats.tricksLanded());
		Consumer<PanelState> listener = panelListener;
		if (listener != null && !s.equals(lastPanel))
		{
			lastPanel = s;
			listener.accept(s);
		}
	}

	/** The idle-logout warning line for the HUD, or null. */
	public String getIdleWarningText()
	{
		return active ? idleWarningText : null;
	}

	/** The flick stroke for the HUD's live visualizer, or null when it is off or not skating. */
	public LiveStroke getLiveStroke()
	{
		return active && config.showFlickVisualizer() ? input.getLiveStroke() : null;
	}

	/** The near-miss flick hint showing now ("Flick faster"), or null. */
	public String getTrickHintText()
	{
		return active ? trickHints.text(scoreClock.now(), config.controllerMode()) : null;
	}

	/** Opacity of the near-miss flick hint, 0 when hidden. */
	public float getTrickHintAlpha()
	{
		return active ? trickHints.alpha(scoreClock.now()) : 0f;
	}

	/** Opacity of the "Edge of the loaded area" hint, 0 when hidden. */
	public float getEdgeHintAlpha()
	{
		return active ? edgeHintAlpha : 0f;
	}

	/** Opacity of the "Stumble!" flash, 0 when hidden. */
	public float getStumbleFlashAlpha()
	{
		return active ? ComfortHints.stumbleFlashAlpha(scoreClock.now() - lastStumbleAt) : 0f;
	}

	/** The real character must not move while skating (teleports, being moved by the game). */
	public void checkPlayerMoved()
	{
		Player p = client.getLocalPlayer();
		if (active && p != null && !p.getWorldLocation().equals(anchor))
			exit(Text.get("ss.moved"));
	}

	/** Health scale != -1 means the health bar is showing; a non-combat NPC interaction (e.g. a shop) must not block. */
	private static boolean isInCombat(Player p)
	{
		return p.getHealthScale() != -1
			|| (p.getInteracting() instanceof NPC && ((NPC) p.getInteracting()).getCombatLevel() > 0);
	}

	/**
	 * Once per configured key and trick mode: a manual key that collides with a skate control (or, with keyboard
	 * tricks, a trick key) or is no real key is replaced (see {@link InputController#effectiveManualKey}); say so.
	 */
	private void warnIfManualKeyUnusable()
	{
		Keybind key = config.manualKey();
		boolean keyboard = config.trickControls().keyboard();
		String note = ComfortHints.manualKeyNote(key.toString(), key.getKeyCode(), keyboard);
		String warned = key + "/" + keyboard;
		if (note != null && !warned.equals(warnedManualKey))
		{
			warnedManualKey = warned;
			chat(note);
		}
	}

	/**
	 * The key-setting notes for chat: the board on/off key not usable (F stands in), another key setting on the
	 * board key (which wins over it; the precedence is unchanged, this only says so), and in controller mode each
	 * pad key that is off because it is a skate control or another key setting's key. Keys match by key code, as
	 * the input does; a skate mode key with modifiers never clashes with a plain key.
	 */
	private List<String> keyNotes()
	{
		List<String> notes = new ArrayList<>();
		boolean keyboardTricks = config.trickControls().keyboard();
		Keybind toggle = config.toggleKey();
		int plainToggle = toggle.getModifiers() == 0 ? toggle.getKeyCode() : KeyEvent.VK_UNDEFINED;
		Keybind board = config.boardKey();
		int boardCode = InputController.effectiveBoardKey(board.getKeyCode(), keyboardTricks);
		int manual = config.manualKey().getKeyCode();
		int leanForward = config.leanForwardKey().getKeyCode();
		int leanBack = config.leanBackKey().getKeyCode();
		int brake = config.brakeKey().getKeyCode();

		Map<String, Integer> others = new LinkedHashMap<>();
		others.put("Skate mode key", plainToggle);
		others.put("Wheelie (manual) key", manual);
		others.put("Lean forward key", leanForward);
		others.put("Lean back key", leanBack);
		others.put("Brake key", brake);
		notes.add(ComfortHints.boardKeyNote(board.toString(), board.getKeyCode(), keyboardTricks));
		notes.add(ComfortHints.boardKeyClashNote(getBoardKeyLabel(), boardCode, others));
		notes.removeIf(n -> n == null);

		if (config.controllerMode())
		{
			Map<String, Integer> taken = new LinkedHashMap<>();
			taken.put("Wheelie (manual) key", InputController.effectiveManualKey(manual, keyboardTricks));
			taken.put("Brake key", InputController.effectiveBrakeKey(brake));
			taken.put("Lean forward key", leanForward);
			taken.put("Lean back key", leanBack);
			taken.put("Board on/off key", boardCode);
			taken.put("Skate mode key", plainToggle);
			notes.addAll(ComfortHints.padKeyNotes(taken));
			if (PadPresets.customIsBroken(config.controllerPreset(), config.customControllerLayout()))
				notes.add(Text.get("ch.pad.broken"));
		}
		return notes;
	}

	/**
	 * Says the {@link #keyNotes key-setting notes} in chat: always on entering skate mode, and after a key setting
	 * changes while skating only the new ones (so a fix, or an unrelated setting, says nothing).
	 */
	private void warnKeyNotes(boolean always)
	{
		List<String> before = lastKeyNotes;
		lastKeyNotes = keyNotes();
		for (String note : lastKeyNotes)
		{
			if (always || !before.contains(note))
				chat(note);
		}
	}

	/** The core Camera plugin's "Vertical camera" (relaxCameraPitch) is on, as that plugin itself applies it. */
	private boolean pitchRelaxerOn()
	{
		// the Camera plugin (enabled flag "cameraplugin" in the "runelite" group) is on by default: only an
		// explicit false turns it off
		Boolean pluginOn = configManager.getConfiguration("runelite", "cameraplugin", Boolean.class);
		Boolean relax = configManager.getConfiguration("zoom", "relaxCameraPitch", Boolean.class);
		return !Boolean.FALSE.equals(pluginOn) && Boolean.TRUE.equals(relax);
	}

	/**
	 * A RuneSkate setting changed. While skating, zoom and the input settings (flick sensitivity and button,
	 * mirroring, trick controls, the manual key) apply at once;
	 * settings read only when skating starts say they apply next time. Client thread.
	 */
	public void onConfigChanged(String key)
	{
		if ("boardDetail".equals(key))
			applyBoardSettings();
		if (!active || key == null)
			return;
		if ("toggleKey".equals(key))
		{
			// read every key press by the toggle hotkey, but the pad keys step aside for a plain one
			input.reconfigure();
			warnKeyNotes(false);
		}
		switch (SettingsApply.classify(key))
		{
			case LIVE:
				camera.setBaseZoom(config.cameraZoom());
				input.reconfigure();
				warnIfManualKeyUnusable();
				warnKeyNotes(false);
				break;
			case NEXT_SESSION:
				chat(Text.get("ss.next"));
				break;
			default:
				break;
		}
	}

	/** Says {@code msg} in chat (nothing when null). */
	private void chat(String msg)
	{
		if (msg != null)
			skateChat.send(msg);
	}

	/** The rail a grind lock would catch right now, for the grind edges overlay; null when not skating or none. */
	public GrindSegment getPredictedGrind()
	{
		SkatePhysics p = physics;
		return active && p != null && knockdown == null && boardSwap.getMode() == SkateMode.ON_BOARD
			? p.getPredictedGrind() : null;
	}
}
