package com.gielinorskate.session;

import com.gielinorskate.SkateChat;
import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.camera.BoardOrbit;
import com.gielinorskate.camera.FootCamera;
import com.gielinorskate.camera.SkateCamera;
import com.gielinorskate.controller.PadPresets;
import com.gielinorskate.duel.DuelEnding;
import com.gielinorskate.duel.DuelService;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.input.BoardKey;
import com.gielinorskate.input.FootControls;
import com.gielinorskate.input.InputController;
import com.gielinorskate.input.LiveStroke;
import com.gielinorskate.input.NearMiss;
import com.gielinorskate.input.StrokeSnapshot;
import com.gielinorskate.leaderboard.LeaderboardService;
import com.gielinorskate.leaderboard.RunService;
import com.gielinorskate.overlay.ControlsCardOverlay;
import com.gielinorskate.overlay.ControlsCardTimer;
import com.gielinorskate.overlay.ManualMeter;
import com.gielinorskate.party.GhostCodec;
import com.gielinorskate.party.GhostFailureGuard;
import com.gielinorskate.party.PartyGhostService;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BoardBounce;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.BoardTransition;
import com.gielinorskate.physics.FootEvent;
import com.gielinorskate.physics.FootInput;
import com.gielinorskate.physics.FootPhysics;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.physics.SkateMode;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkateTuning;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.physics.StandSpot;
import com.gielinorskate.physics.StartHeading;
import com.gielinorskate.physics.TumbleBody;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.render.CelebrationSequence;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.render.LockBlend;
import com.gielinorskate.render.OffBoardPose;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.render.SkatePoseCommand;
import com.gielinorskate.render.SkaterAnimator;
import com.gielinorskate.render.TantrumSequence;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.ui.PanelState;
import com.gielinorskate.render.SkaterRenderer;
import com.gielinorskate.world.BlockerSet;
import com.gielinorskate.world.GridCollisionWorld;
import com.gielinorskate.world.GrindSegment;
import com.gielinorskate.world.HitLog;
import com.gielinorskate.world.SceneCollisionBuilder;
import com.google.gson.Gson;
import java.awt.event.KeyEvent;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.WorldView;
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
	/** Middle-button camera orbit on foot: about one turn per 1,000 pixels of drag. */
	private static final float ORBIT_RADIANS_PER_PIXEL = 0.006f;
	private static final float FORGIVING_SKATER_RADIUS = 8f;
	private static final float FORGIVING_WALL_BAIL_SPEED = 1500f;
	private static final int MAX_STEPS_PER_FRAME = 5;
	/** The core Camera plugin: its enabled flag and its "Vertical camera" setting. */
	private static final String RUNELITE_GROUP = "runelite";
	private static final String CAMERA_PLUGIN_KEY = "cameraplugin";
	private static final String CAMERA_GROUP = "zoom";
	private static final String RELAX_PITCH_KEY = "relaxCameraPitch";

	private final Client client;
	@Inject
	private SkateChat skateChat;
	private final GielinorSkateConfig config;
	private final InputController input;
	private final SkateTuning baseTuning;
	private final SkateInput skateInput = new SkateInput();
	private final SkaterRenderer renderer;
	private final SkaterAnimator animator;
	private final SkateCamera camera;
	/** The board camera turned by hand (button tricks: the right stick), easing back behind the skater when idle. */
	private final BoardOrbit boardOrbit = new BoardOrbit();
	private final ComboScorer scorer;
	private final ScoreClock scoreClock;
	private final PartyGhostService partyGhosts;
	private final ConfigManager configManager;

	private boolean active;
	/** Skating in a PvP area: other players aren't drawn (see SafetyRules.hideOtherPlayers). */
	private boolean hideOtherPlayers;
	/** The skater (not the real character) is inside the Wilderness; updated every frame. */
	private boolean skaterInWilderness;
	private SkatePhysics physics;
	/** Grind segments of the current session (empty when not skating); read by the debug overlay. */
	private volatile List<GrindSegment> grindSegments = Collections.emptyList();
	private volatile boolean showGrinds;
	/** Object and wall-edge collision boxes of the current session (empty when not skating); read by the box view. */
	private volatile List<BlockerSet> blockerSets = Collections.emptyList();
	/** ::skateboxes "Hit: ..." lines already posted (client thread only). */
	private final HitLog hitLog = new HitLog();
	private volatile boolean showBoxes;
	private volatile boolean showGesture;
	/** Owns the controls card's show/auto-fade timing; driven from enter() and onFrame(). */
	private final ControlsCardTimer controlsCard = new ControlsCardTimer();
	/** Times the current manual hold from the outside, since physics exposes no elapsed-time getter. */
	private final ManualMeter manualMeter = new ManualMeter();
	/** "key/keyboardTricks" of the unusable manual key already warned about (one chat warning per setup). */
	private String warnedManualKey;
	/** The key-setting notes last said in chat (see {@link #warnKeyNotes}). */
	private List<String> lastKeyNotes = Collections.emptyList();
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
	/** Why the latest bail happened, for the line under "Bailed"; null before any bail this session. */
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
	/** Sounds, particles and bail jokes (Q3), fed each frame after the scorer. */
	@Inject
	private SkateFeedback feedback;
	/** Skate XP, levels, decks and session goals (Q4), fed each landed combo. */
	@Inject
	private ProgressionService progression;
	/** The timed 2-minute run, fed every result and frame while skating. */
	@Inject
	private RunService runs;
	/** The online leaderboard (does nothing unless turned on). */
	@Inject
	private LeaderboardService leaderboard;
	/** The Skate Duel: landed combos hit the opponent, bails cost HP. */
	@Inject
	private DuelService duel;
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
	/** R as a quick reset: off in a Skate Duel. */
	private final BailRules.ResetGate resetGate = new BailRules.ResetGate();
	/** Knocked off the board by a bail, until standing (or back on with R); null otherwise. */
	private Knockdown knockdown;
	/** The knockdown before the latest step (null when there is none to blend from). */
	private KnockdownPose prevKnockPose;
	private final Random knockRandom = new Random();
	/**
	 * The bail frame's events, trick events and wall hit, held back from the sounds, smoke and bail line until the
	 * body hits the ground; null when given (or none held).
	 */
	private List<SkateEvent> heldBailEvents;
	private List<TrickEvent> heldBailTricks;
	private boolean heldBailHit;
	/** A finished duel's tantrum or celebration: waiting for its moment, or playing. */
	private final DuelEndingDirector endings = new DuelEndingDirector();

	@Inject
	SkateSession(Client client, GielinorSkateConfig config, InputController input, Gson gson, ComboScorer scorer,
		ScoreClock scoreClock, PartyGhostService partyGhosts, ConfigManager configManager)
	{
		this.configManager = configManager;
		this.partyGhosts = partyGhosts;
		this.scorer = scorer;
		this.scoreClock = scoreClock;
		this.client = client;
		this.config = config;
		this.input = input;
		this.baseTuning = loadTuning(gson);
		this.renderer = new SkaterRenderer(client);
		this.animator = new SkaterAnimator(client);
		renderer.setBodyPose(animator.getBodyPose());
		applyBoardSettings();
		renderer.setDrawnReport(animator.getDrawnReport());
		this.camera = new SkateCamera(client);
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
			log.warn("Failed to load skate tuning, using defaults", e);
			return new SkateTuning();
		}
	}

	public boolean isActive()
	{
		return active;
	}

	/** ::skatepose dev command: sets or lists the live animation IDs. Safe to call whether active or not. */
	public void handleSkatePoseCommand(String[] args)
	{
		chat(SkatePoseCommand.apply(animator.getPoses(), args, this::canLoadAnimation));
	}

	/**
	 * ::skateboard runeskate|classic dev command (v2 and v3 also mean runeskate): draws the baked image-texture
	 * board or the classic board for the local skater until the "Board model" setting changes.
	 */
	public void handleSkateboardCommand(String[] args)
	{
		String which = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
		if (which.equals("runeskate") || which.equals("v2") || which.equals("v3"))
		{
			renderer.setBoardDetail(config.boardDetail() == GielinorSkateConfig.BoardDetail.HIGH);
			chat(renderer.useBakedBoard(true)
				? "Board: RuneSkate (baked image texture)" + (active ? "." : ", from your next skate.")
				: "The RuneSkate board couldn't be built (missing resource or cache model); keeping the classic "
				+ "board.");
		}
		else if (which.equals("classic"))
		{
			renderer.useBakedBoard(false);
			chat("Board: classic.");
		}
		else
		{
			chat("Usage: ::skateboard runeskate|classic (now " + (renderer.isBakedBoard() ? "runeskate" : "classic")
				+ ")");
		}
	}

	/** The "Board model" and "Board detail" settings, for the local skater's board. */
	private void applyBoardSettings()
	{
		renderer.setBoardChoice(config.boardModel() == GielinorSkateConfig.BoardType.RUNESKATE,
			config.boardDetail() == GielinorSkateConfig.BoardDetail.HIGH);
	}

	/** ::skategrinds dev command: toggles drawing every grind segment of the current session. */
	public void handleSkateGrindsCommand()
	{
		showGrinds = !showGrinds;
		chat(showGrinds
			? "Grind debug view on (" + (active ? grindSegments.size() + " segments" : "shows while skating") + ")."
			: "Grind debug view off.");
	}

	/** True while skating with the ::skategrinds view toggled on. */
	public boolean isShowingGrinds()
	{
		return (showGrinds || config.showGrindEdges()) && active;
	}

	/** ::skateboxes dev command: toggles drawing the collision boxes near the skater. */
	public void handleSkateBoxesCommand()
	{
		showBoxes = !showBoxes;
		hitLog.reset();
		chat(showBoxes
			? "Hitbox debug view on (red solid, yellow low, green ridden through; what you hit is named in chat"
				+ (active ? "" : "; shows while skating") + ")."
			: "Hitbox debug view off.");
	}

	/** True while skating with the ::skateboxes view toggled on. */
	public boolean isShowingBoxes()
	{
		return showBoxes && active;
	}

	/** With ::skateboxes on, names in chat (once each) the blockers the skater ran or bailed into. */
	private void reportHit()
	{
		if (!physics.hasHit())
		{
			return;
		}
		if (isShowingBoxes())
		{
			String line = hitLog.report(physics.getHitBox(), physics.getHitLabel());
			if (line != null)
			{
				chat(line);
			}
		}
		physics.clearHit();
	}

	/** Read-only object and wall-edge collision boxes of the current session; empty when not skating. */
	public List<BlockerSet> getBlockerSets()
	{
		return blockerSets;
	}

	/** ::skategesture dev command: toggles drawing the last right-mouse stroke for 2 s after each flick. */
	public void handleSkateGestureCommand()
	{
		showGesture = !showGesture;
		chat(showGesture ? "Gesture debug view on." : "Gesture debug view off.");
	}

	/** True while skating with the ::skategesture view toggled on. */
	public boolean isShowingGesture()
	{
		return showGesture && active;
	}

	/** The last completed right-mouse stroke, for the ::skategesture dev overlay; null until the first flick. */
	public StrokeSnapshot getLastStrokeSnapshot()
	{
		return input.getLastStrokeSnapshot();
	}

	/** Opacity in [0, 1] of the controls card (H), 0 when it should not be drawn at all. */
	public float getControlsCardAlpha()
	{
		return active ? controlsCard.alpha(scoreClock.now()) : 0f;
	}

	/** The controller layout the controls card names, read from the settings when they change. */
	private final PadPresets.Cache padPresets = new PadPresets.Cache();

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
		boolean keyboard = config.trickControls().keyboard();
		return ComfortHints.manualKeyLabel(key.toString(), key.getKeyCode(), keyboard);
	}

	/** "Manual 1.4s"-style label while the skater is in a manual, near the trick stack; null otherwise. */
	public String getManualMeterText()
	{
		return manualMeterText;
	}

	/** Skater local {x, y}, or null when not skating. Client thread. */
	public float[] getSkaterPosition()
	{
		SkatePhysics p = physics;
		return p == null ? null : new float[]{p.getX(), p.getY()};
	}

	/** Read-only grind segments of the current skate session; empty when not skating. */
	public List<GrindSegment> getGrindSegments()
	{
		return grindSegments;
	}

	/** An out-of-range sequence ID would otherwise only fail later, inside the client's render path. */
	private boolean canLoadAnimation(int id)
	{
		try
		{
			return client.loadAnimation(id) != null;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	public boolean shouldHide(Renderable r)
	{
		if (!active)
		{
			return false;
		}
		// the real character is replaced by the skater; in PvP areas other players are hidden too
		return r == client.getLocalPlayer() || ((hideOtherPlayers || skaterInWilderness) && r instanceof Player);
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

	/**
	 * Starts skating. Returns null when skating started (or already was), otherwise why not; the reason is also
	 * said in chat.
	 */
	public String enter()
	{
		Player p = client.getLocalPlayer();
		if (active)
		{
			return null;
		}
		if (p == null)
		{
			return "Log in to skate.";
		}
		if (client.getLocalDestinationLocation() != null)
		{
			return refuse("Stand still to start skating.");
		}
		if (!p.getWorldView().isTopLevel())
		{
			return refuse("You can't skate on a boat or moving platform.");
		}

		WorldView wv = client.getTopLevelWorldView();
		WorldPoint here = p.getWorldLocation();
		String reason = SafetyRules.alwaysBlockedReason(wv.isInstance(),
			client.getVarbitValue(VarbitID.BR_INGAME) != 0,
			client.getVarbitValue(VarbitID.DEADMAN_INWILDERNESS) != 0,
			client.getWorldType(), SafetyRules.regionId(here.getX(), here.getY()));
		boolean inWilderness = SafetyRules.inPvpArea(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1,
			client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1);
		boolean pvpWorld = SafetyRules.isOptInPvpWorld(client.getWorldType());
		if (reason == null)
		{
			reason = SafetyRules.blockReason(inWilderness, pvpWorld, isInCombat(p), config.allowPvpAreas());
		}
		if (reason != null)
		{
			return refuse(reason);
		}

		int plane = wv.getPlane();
		world = SceneCollisionBuilder.build(client, wv, plane, config.passThroughVegetation());
		// party ghosts follow the same ground as the local skater here
		partyGhosts.setCollisionWorld(world, wv.getBaseX(), wv.getBaseY(), plane, world.size());
		applyLook(progression.look());
		if (!renderer.spawn(wv, plane))
		{
			return refuse("RuneSkate couldn't build the board model.");
		}

		LocalPoint lp = p.getLocalLocation();
		float heading = Angles.fromJau(p.getOrientation());
		float speedScale = SkateTuning.scaleFromPercent(config.speedScale());
		SkateTuning tuning = baseTuning.scaled(speedScale, SkateTuning.scaleFromPercent(config.popScale()));
		if (config.forgivingCollisions())
		{
			// smaller hitbox around the skater and harder hits needed to bail (scaled with push speed too)
			tuning.skaterRadius = FORGIVING_SKATER_RADIUS;
			tuning.wallBailSpeed = FORGIVING_WALL_BAIL_SPEED * speedScale;
		}
		// a wall right ahead (a bank booth): start facing the most open way instead
		heading = StartHeading.choose(world, lp.getX(), lp.getY(), heading, tuning.skaterRadius, tuning.maxStepUp);
		physics = new SkatePhysics(tuning, world, world.getGrinds(), lp.getX(), lp.getY(), heading);
		this.tuning = tuning;
		foot = null;
		prevFootPose = null;
		boardSwap.reset();
		footTipShown = false;
		knockTipShown = false;
		endings.clear();
		knockdown = null;
		prevKnockPose = null;
		heldBailEvents = null;
		heldBailTricks = null;
		grindSegments = world.getGrinds().segments();
		blockerSets = Arrays.asList(world.getBlockers(), world.getWallBlockers());
		hitLog.reset();
		anchor = p.getWorldLocation();
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
		manualMeterText = null;
		if (config.learnedBasics())
		{
			controlsCard.markLearned();
		}
		controlsCard.onSkateStart(scoreClock.now(), config.showControlsCard());
		scorer.startSession();
		progressedResults = scorer.resultSequence();
		feedback.onSkateStart();
		progression.startSession();
		clickHintShown = false;
		trickHints.reset();
		bailReasonText = null;
		chat("Skate mode on. Press H for controls, Esc to stop.");
		float edgeTiles = world.edgeDistance(lp.getX(), lp.getY()) / GridCollisionWorld.TILE;
		if (ComfortHints.needsRoomTip(edgeTiles))
		{
			chat("You're near the edge of the loaded area (about " + Math.max(0, Math.round(edgeTiles))
				+ " tiles away), where an invisible wall stops you. Walk to a more central spot for more room.");
		}
		lastRefusal = null;
		publishPanel();
		warnIfManualKeyUnusable();
		warnKeyNotes(true);
		return null;
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
		{
			return;
		}
		active = false;
		hideOtherPlayers = false;
		skaterInWilderness = false;
		input.setEnabled(false);
		input.setClickThroughAreas(Collections.emptyList());
		saveWheelZoom();
		camera.exit(pitchRelaxerOn());
		try
		{
			renderer.despawn();
		}
		catch (RuntimeException ex)
		{
			log.warn("Failed to despawn skate renderer", ex);
		}
		try
		{
			animator.stop();
		}
		catch (RuntimeException ex)
		{
			log.warn("Failed to stop skate animator", ex);
		}
		try
		{
			partyGhosts.onSkateEnd();
		}
		catch (RuntimeException ex)
		{
			log.warn("Failed to end party ghosts", ex);
		}
		physics = null;
		world = null;
		foot = null;
		prevFootPose = null;
		offBoardPose = null;
		endings.clear();
		knockdown = null;
		prevKnockPose = null;
		heldBailEvents = null;
		heldBailTricks = null;
		boardSwap.reset();
		idleWarning.reset();
		idleWarningText = null;
		edgeHintAlpha = 0f;
		lastStumbleAt = Float.NEGATIVE_INFINITY;
		prevPose = null;
		grindSegments = Collections.emptyList();
		blockerSets = Collections.emptyList();
		manualMeterText = null;
		// a timed run ends with skating, without a result
		runs.cancel();
		// a combo cut off mid-air or mid-grind must not be awarded in the next session
		scorer.abandonCombo();
		feedback.onSkateEnd();
		progression.flush();
		publishPanel();
		if (message != null)
		{
			chat(message);
		}
	}

	public void onFrame()
	{
		if (!active)
		{
			return;
		}
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
			// a finished duel's tantrum or cheer: taken now, played when the skater can
			endings.offer(duel.takeEnding(), scoreClock.now());
			if (endings.inTantrum() || startEnding())
			{
				tantrumFrame(frameDt);
				return;
			}
			if (knockdown != null)
			{
				knockdownFrame(frameDt);
				return;
			}
			if (offBoardFrame(frameDt))
			{
				return;
			}
			// the side panel and chatbox stay clickable; refreshed each frame as tabs open and close
			input.setClickThroughAreas(ClickThroughAreas.collect(client));
			input.drainInto(skateInput);
			// a winner's cheer ends on any control
			celebrationInput(DuelEndingDirector.anyInput(skateInput, false), frameDt);
			// R is no quick reset in a Skate Duel
			boolean dueling = duel.isDueling();
			skateInput.resetRequested = resetGate.allow(skateInput.resetRequested, dueling);
			if (resetGate.takeHint())
			{
				chat(BailRules.R_DISABLED_HINT);
			}
			NearMiss miss = input.consumeNearMiss();
			if (!skateInput.gestures.isEmpty())
			{
				trickHints.onTrick();
			}
			else if (config.showTrickHints())
			{
				trickHints.offer(miss, scoreClock.now());
			}
			if (input.consumePlainClick() && !clickHintShown)
			{
				clickHintShown = true;
				chat(ComfortHints.CLICK_HINT);
			}
			if (input.consumeControlsToggle())
			{
				controlsCard.toggle(scoreClock.now());
			}

			SkaterState stateBefore = physics.getState();
			accumulator += frameDt;
			int steps = 0;
			while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME)
			{
				// the pose before each step, so the frame can be drawn between the last two steps
				prevPose = RenderPose.of(physics);
				physics.step(STEP, skateInput);
				accumulator -= STEP;
				steps++;
			}
			if (steps == MAX_STEPS_PER_FRAME)
			{
				accumulator = 0f;
			}
			// a controller's stick can pop out of a rolling grab
			input.setRolling(physics.getState() == SkaterState.ROLLING);
			// a pad button with an air action of its own does it in the air
			input.setAirborne(physics.getState() == SkaterState.AIRBORNE);
			// button tricks: a stick manual holds while physics is in it; the grind button steps off with no rail near
			input.setInManual(physics.getState() == SkaterState.MANUAL);
			input.setRailNear(physics.isRailNear());
			// drawn one step behind physics: alpha of the way from the previous step to the latest one
			float alpha = config.smoothMotion() ? accumulator / STEP : 1f;
			// a grind lock eases onto the rail instead of snapping
			RenderPose pose = lockBlend.apply(RenderPose.lerp(prevPose, RenderPose.of(physics), alpha), frameDt);
			boolean hitThisFrame = physics.hasHit();
			reportHit();
			List<SkateEvent> events = physics.drainEvents();
			float scoreTime = scoreClock.now();
			updateControlsCard(events, scoreTime);
			if (events.contains(SkateEvent.BAIL) && physics.getLastBailReason() != null)
			{
				bailReasonText = physics.getLastBailReason().text;
			}
			boolean nolliePop = events.contains(SkateEvent.POP) && physics.isLastPopNollie();
			List<TrickEvent> trickEvents = physics.drainTrickEvents();
			for (TrickEvent e : trickEvents)
			{
				// visible with the client's --debug flag: shows whether gestures produce tricks
				log.debug("Skate trick event {} {} {}s", e.type, e.trick == null ? "-" : e.trick.displayName, e.seconds);
				// a LANDED event carries its own clean flag for the clean bonus
				scorer.accept(e, scoreTime);
			}
			scorer.update(scoreTime, physics.getState() == SkaterState.ROLLING && physics.getActiveHold() == null);
			takeResults();
			if (events.contains(SkateEvent.BAIL))
			{
				duel.onBail();
			}
			if (events.contains(SkateEvent.BAIL) && BailRules.knocksOff(config.afterBail(), dueling))
			{
				// knocked off the board: scored, counted and shared as any bail; the rest is the knockdown's
				runs.tick(scorer.hasCombo());
				updateHints(events, scoreTime);
				manualMeter.update(null, scoreTime);
				manualMeterText = null;
				startKnockdown(events, trickEvents, hitThisFrame, dueling);
				shareKnockdown(events, trickEvents, stateBefore, frameDt, steps * STEP);
				drawKnockdown(frameDt, 0f);
				return;
			}
			runs.tick(scorer.hasCombo());
			updateHints(events, scoreTime);
			feedback.onFrame(physics, events, trickEvents, hitThisFrame, scoreTime, frameDt);

			Trick hold = physics.getActiveHold();
			// the manual being held (a swap between manual and nose manual restarts the meter)
			manualMeter.update(hold == Trick.MANUAL || hold == Trick.NOSE_MANUAL ? hold : null, scoreTime);
			manualMeterText = manualMeter.text(scoreTime);

			// just got on: the renderer eases over from the walker
			renderer.setTransition(boardSwap.getTransition(), boardSwap.getTransitionProgress());
			renderer.update(pose, skateInput.steer, skateInput.crouch, events, nolliePop, frameDt);
			animator.update(pose, physics, skateInput.crouch, events, frameDt);
			paceCelebration();
			String animWarning = animator.takeWarning();
			if (animWarning != null)
			{
				chat(animWarning);
			}
			if (!checkSkaterArea(physics.getX(), physics.getY()))
			{
				return;
			}
			camera.adjustZoom(input.drainZoomNotches());
			// button tricks: the right stick turns the chase camera; idle, it eases back behind the skater
			float turn = -input.drainOrbitPixels() * ORBIT_RADIANS_PER_PIXEL;
			if (turn != 0f)
			{
				boardOrbit.turn(turn);
				camera.orbit(turn);
			}
			boardOrbit.update(frameDt);
			float landedAirtime = events.contains(SkateEvent.LAND) ? physics.getLastAirtime() : 0f;
			camera.update(pose.x, pose.y, pose.h, pose.cameraHeading + boardOrbit.offset(),
				physics.getSpeed(), physics.getState(), landedAirtime, frameDt,
				config.cameraHeight().pitchJau14());
			shareSkater(events, trickEvents, stateBefore, frameDt, steps * STEP);
			offBoardPose = onBoardPose(pose);
		}
		catch (RuntimeException ex)
		{
			log.warn("Skate frame failed", ex);
			exit("RuneSkate hit an error and stopped.");
		}
	}

	/** Shares the skater (on the board, or knocked off as a bailed skater) with the party and draws its ghosts. */
	private void shareSkater(List<SkateEvent> events, List<TrickEvent> trickEvents, SkaterState stateBefore,
		float frameDt, float physicsDt)
	{
		// the real character can't move while skating, so hideOtherPlayers still says whether it is in a PvP area
		SkatePhysics framePhysics = physics;
		boolean inPvpArea = hideOtherPlayers || skaterInWilderness;
		// a ghost error turns ghosts off for this session but never ends local skating
		ghostGuard.run(
			() -> partyGhosts.onSkateFrame(framePhysics, events, trickEvents, stateBefore, inPvpArea, frameDt,
				physicsDt),
			partyGhosts::onGhostFailure,
			e -> log.warn("Party ghosts failed; ghosts are off until the next skate session", e));
	}

	// ---- Duel endings

	/** Seconds of landing the camera kicks with when the tantrum's board hits the ground. */
	private static final float SNAP_CAMERA_KICK = 0.6f;

	/**
	 * Starts a finished duel's ending if one waits and the skater is somewhere it can play (never in a PvP area,
	 * never with the "Duel endings" setting off): the cheer at once, on the board or on foot; true when a tantrum
	 * started (its first frame is to run now).
	 */
	private boolean startEnding()
	{
		boolean enabled = config.duelEndings();
		if (!enabled && endings.celebrating())
		{
			stopCelebration();
		}
		DuelEndingDirector.Where where = endingWhere();
		DuelEnding e = endings.poll(scoreClock.now(), where, enabled, hideOtherPlayers || skaterInWilderness);
		if (e == DuelEnding.TANTRUM)
		{
			startTantrum();
			return true;
		}
		if (e == DuelEnding.CELEBRATE)
		{
			startCelebration(where == DuelEndingDirector.Where.FOOT_GROUND);
		}
		return false;
	}

	private DuelEndingDirector.Where endingWhere()
	{
		if (knockdown != null)
		{
			return DuelEndingDirector.Where.KNOCKED_DOWN;
		}
		if (boardSwap.getMode() == SkateMode.ON_FOOT)
		{
			return foot != null && !foot.isAirborne() ? DuelEndingDirector.Where.FOOT_GROUND
				: DuelEndingDirector.Where.FOOT_AIR;
		}
		SkaterState s = physics.getState();
		return s == SkaterState.ROLLING || s == SkaterState.MANUAL ? DuelEndingDirector.Where.BOARD_GROUND
			: DuelEndingDirector.Where.BOARD_BUSY;
	}

	/**
	 * The loser's tantrum starts: off the board (a combo in progress is banked) or stopped where they stand, the board
	 * in hand. Party members see it on our ghost (sent at once with its time).
	 */
	private void startTantrum()
	{
		stopCelebration();
		if (boardSwap.getMode() == SkateMode.ON_BOARD)
		{
			scorer.bankCombo(scoreClock.now());
			SkatePhysics p = physics;
			foot = new FootPhysics(world, tuning.gravity, tuning.skaterRadius, p.getX(), p.getY(), p.getH(),
				p.getHeading());
			physics = standIn();
		}
		foot.setVelocity(0f, 0f);
		prevFootPose = null;
		manualMeter.update(null, scoreClock.now());
		manualMeterText = null;
		input.setOnFoot(true);
		// the grab starts from the board where it was drawn; its halves are made now, not at the snap
		try
		{
			if (!renderer.prepareTantrum())
			{
				log.debug("The board can't snap; the tantrum plays without its halves");
			}
		}
		catch (RuntimeException ex)
		{
			log.warn("Failed to prepare the snapped board", ex);
		}
		// a fresh board: the old one is about to be in pieces
		boardSwap.freshBoardInHand();
		endings.startTantrum();
		partyGhosts.addEvents(GhostCodec.EV_TANTRUM);
	}

	/**
	 * One tantrum frame: no controls (everything pressed is read and dropped; Esc, checked before, still ends
	 * skating), the body standing where the tantrum started, the board grabbed, stamped, heaved up and slammed down
	 * until it snaps (a crack, dust and a camera kick), then on foot with a fresh board in hand.
	 */
	private void tantrumFrame(float frameDt)
	{
		boardOrbit.reset();
		input.setClickThroughAreas(ClickThroughAreas.collect(client));
		input.drainInto(skateInput);
		skateInput.gestures.clear();
		skateInput.pushPressed = false;
		skateInput.resetRequested = false;
		input.consumeNearMiss();
		input.consumePlainClick();
		input.consumeControlsToggle();
		input.drainFoot(footControls);
		footControls.jumpPressed = false;
		footControls.dropPickupPressed = false;
		input.pollBoardKey(boardKeyEdges);
		boardSwap.tick(frameDt);
		accumulator = 0f;

		boolean snap = endings.advanceTantrum(frameDt);
		boolean over = !endings.inTantrum();
		float t = over ? TantrumSequence.DURATION : endings.tantrumTime();
		FootPhysics f = foot;
		float x = f.getX();
		float y = f.getY();
		float h = f.getH();
		float heading = f.getHeading();
		physics.placeStanding(x, y, h, heading);
		float scoreTime = scoreClock.now();
		List<SkateEvent> noEvents = Collections.emptyList();
		takeResults();
		runs.tick(scorer.hasCombo());
		updateHints(noEvents, scoreTime);
		feedback.tickEffects(scoreTime);
		manualMeterText = null;

		animator.updateTantrum(t, frameDt);
		String warning = animator.takeWarning();
		if (warning != null)
		{
			chat(warning);
		}
		renderer.updateTantrum(t, x, y, h, heading, frameDt);
		if (snap)
		{
			renderer.snapBoard(world, tuning.gravity, x, y, h, heading, knockRandom);
			float[] at = new float[2];
			TantrumSequence.snapPoint(x, y, heading, at);
			feedback.playEndingCue(SkateFeedback.EndingCue.SNAP, at[0], at[1], h, scoreTime);
		}
		if (over)
		{
			// on foot, a fresh board in hand, the controls back
			animator.endTantrum();
			prevFootPose = null;
		}
		offBoardPose = footPose();
		if (!checkSkaterArea(x, y))
		{
			return;
		}
		camera.adjustZoom(input.drainZoomNotches());
		camera.orbit(-input.drainOrbitPixels() * ORBIT_RADIANS_PER_PIXEL);
		camera.update(x, y, h, FootCamera.targetHeading(camera.getYaw(), heading, false), 0f, SkaterState.ROLLING,
			snap ? SNAP_CAMERA_KICK : 0f, frameDt, config.cameraHeight().pitchJau14());
		// party members see a walker standing with the board in hand (and, on this version, the tantrum itself)
		boolean inPvpArea = hideOtherPlayers || skaterInWilderness;
		OffBoardPose stood = footPose();
		List<FootEvent> noFootEvents = Collections.emptyList();
		ghostGuard.run(
			() -> partyGhosts.onFootFrame(stood, 0f, 0f, noFootEvents, inPvpArea, frameDt, 0f),
			partyGhosts::onGhostFailure,
			e -> log.warn("Party ghosts failed; ghosts are off until the next skate session", e));
	}

	/** The winner's celebration starts: the cheer while riding on, or jump for joy on foot, and a sparkle. */
	private void startCelebration(boolean onFoot)
	{
		animator.startCelebration(CelebrationSequence.animation(onFoot));
		endings.startCelebration();
		feedback.playEndingCue(SkateFeedback.EndingCue.SPARKLE, physics.getX(), physics.getY(), physics.getH(),
			scoreClock.now());
		partyGhosts.addEvents(GhostCodec.EV_CELEBRATE);
	}

	/** The celebration goes on by {@code dt}, or ends (on any control, or on time). */
	private void celebrationInput(boolean anyInput, float dt)
	{
		if (endings.celebrating() && !endings.advanceCelebration(dt, anyInput))
		{
			animator.stopEmote();
		}
	}

	/** Paces the celebration's emote to its length (after the frame's animation update). */
	private void paceCelebration()
	{
		if (endings.celebrating())
		{
			animator.paceCelebration(CelebrationSequence.progress(endings.celebrationTime()));
		}
	}

	/** The celebration ends now (a bail, stepping off, the setting turned off). */
	private void stopCelebration()
	{
		if (endings.celebrating())
		{
			endings.advanceCelebration(0f, true);
			animator.stopEmote();
		}
	}

	// ---- Knockdown

	/**
	 * A bail knocks the skater off: the body and the board fly off on their own (see {@link Knockdown}); the bail's
	 * sounds, smoke and line wait for the impact.
	 */
	private void startKnockdown(List<SkateEvent> events, List<TrickEvent> trickEvents, boolean hit, boolean dueling)
	{
		stopCelebration();
		SkatePhysics p = physics;
		Knockdown k = new Knockdown(world, tuning.gravity, tuning.skaterRadius);
		k.start(p.getX(), p.getY(), p.getH(), p.getVelocityX(), p.getVelocityY(), p.getHeading(), p.isBailWall(),
			p.getBailWallNx(), p.getBailWallNy(), dueling, knockRandom);
		knockdown = k;
		prevKnockPose = null;
		heldBailEvents = new ArrayList<>(events);
		heldBailTricks = new ArrayList<>(trickEvents);
		heldBailHit = hit;
		// WASD, Space and F are the on-foot keys from here
		input.setOnFoot(true);
	}

	/** One frame knocked off: the keys (R, a skip, F), the fixed steps, the impact, the drawing; or the end of it. */
	private void knockdownFrame(float frameDt)
	{
		boardOrbit.reset();
		input.setClickThroughAreas(ClickThroughAreas.collect(client));
		input.drainInto(skateInput);
		skateInput.gestures.clear();
		skateInput.pushPressed = false;
		boolean reset = resetGate.allow(skateInput.resetRequested, knockdown.isDueling() || duel.isDueling());
		skateInput.resetRequested = false;
		if (resetGate.takeHint())
		{
			chat(BailRules.R_DISABLED_HINT);
		}
		input.consumeNearMiss();
		if (input.consumePlainClick() && !clickHintShown)
		{
			clickHintShown = true;
			chat(ComfortHints.CLICK_HINT);
		}
		if (input.consumeControlsToggle())
		{
			controlsCard.toggle(scoreClock.now());
		}
		input.drainFoot(footControls);
		FootControls fc = footControls;
		boolean moveHeld = fc.forward || fc.back || fc.left || fc.right;
		boolean jump = fc.jumpPressed;
		fc.jumpPressed = false;
		fc.dropPickupPressed = false;
		input.pollBoardKey(boardKeyEdges);
		boardSwap.tick(frameDt);

		Knockdown.Outcome outcome = knockdown.input(reset, moveHeld, jump, boardKeyEdges.pressed);
		if (outcome == Knockdown.Outcome.BACK_ON_BOARD)
		{
			backOnBoard();
			return;
		}
		accumulator += frameDt;
		int steps = 0;
		boolean impact = false;
		while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME && !knockdown.isDone())
		{
			prevKnockPose = knockPose();
			knockdown.step(STEP);
			impact |= knockdown.takeImpact();
			accumulator -= STEP;
			steps++;
		}
		if (steps == MAX_STEPS_PER_FRAME || knockdown.isDone())
		{
			accumulator = Math.min(accumulator, STEP);
		}
		TumbleBody body = knockdown.getBody();
		physics.placeBailed(body.getX(), body.getY(), body.getH(), body.getVelocityX(), body.getVelocityY(),
			body.getVerticalSpeed());
		float scoreTime = scoreClock.now();
		// at the impact (or once down after a long fall cut short)
		if (impact || knockdown.getPhase() != Knockdown.Phase.TUMBLE)
		{
			giveHeldBail(scoreTime, frameDt);
		}
		if (outcome == Knockdown.Outcome.RECLAIM || knockdown.isDone())
		{
			standUp(outcome == Knockdown.Outcome.RECLAIM);
			return;
		}
		List<SkateEvent> noEvents = Collections.emptyList();
		List<TrickEvent> noTricks = Collections.emptyList();
		takeResults();
		runs.tick(scorer.hasCombo());
		updateHints(noEvents, scoreTime);
		if (heldBailEvents == null)
		{
			// after the impact: the smoke and any delayed sounds play on
			feedback.onFrame(physics, noEvents, noTricks, false, scoreTime, frameDt);
		}
		if (!drawKnockdown(frameDt, impact ? body.getLastAirTime() : 0f))
		{
			return;
		}
		shareKnockdown(noEvents, noTricks, SkaterState.BAILED, frameDt, steps * STEP);
	}

	/**
	 * Shares the knocked-off skater (the body, its stage and lying angle, the board where it is) with the party and
	 * draws its ghosts; a ghost error turns ghosts off for this session but never ends local skating.
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
		boolean inPvpArea = hideOtherPlayers || skaterInWilderness;
		ghostGuard.run(
			() -> partyGhosts.onKnockdownFrame(k, vx, vy, airborne, lie, events, trickEvents, stateBefore, inPvpArea,
				frameDt, physicsDt),
			partyGhosts::onGhostFailure,
			e -> log.warn("Party ghosts failed; ghosts are off until the next skate session", e));
	}

	/** The bail's sounds, smoke and line, held from the bail frame, at the knocked-off body. */
	private void giveHeldBail(float scoreTime, float frameDt)
	{
		if (heldBailEvents == null)
		{
			return;
		}
		List<SkateEvent> events = heldBailEvents;
		List<TrickEvent> tricks = heldBailTricks;
		heldBailEvents = null;
		heldBailTricks = null;
		feedback.onFrame(physics, events, tricks, heldBailHit, scoreTime, frameDt);
	}

	/** The knockdown now (not blended). */
	private KnockdownPose knockPose()
	{
		Knockdown k = knockdown;
		TumbleBody b = k.getBody();
		BoardBounce bb = k.getBoard();
		KnockdownPose.Stage stage;
		float facing = b.getFacing();
		switch (k.getPhase())
		{
			case TUMBLE:
				stage = KnockdownPose.Stage.AIR;
				break;
			case LIE:
				stage = KnockdownPose.Stage.LIE;
				break;
			default:
				stage = KnockdownPose.Stage.GET_UP;
				// turning to face the board while getting up
				float u = k.getPhaseProgress();
				facing = Angles.wrap(facing + Angles.wrap(k.standHeading() - facing) * u * u * (3f - 2f * u));
				break;
		}
		return new KnockdownPose(stage, k.getPhaseProgress(), b.getX(), b.getY(), b.getH(), facing, k.bodyAngle(),
			b.getRoll(), bb.getX(), bb.getY(), bb.getH(), bb.getYaw(), bb.getRoll(), bb.getPitch());
	}

	/**
	 * Draws the knocked-off skater and board, follows them with the camera (a landing kick at the impact) and checks
	 * the area; false when skating ended.
	 */
	private boolean drawKnockdown(float frameDt, float landedAirtime)
	{
		float alpha = config.smoothMotion() ? accumulator / STEP : 1f;
		KnockdownPose pose = KnockdownPose.lerp(prevKnockPose, knockPose(), alpha);
		// the animator first: the body's pose decides how low the renderer draws it
		animator.updateKnockdown(pose, frameDt);
		String warning = animator.takeWarning();
		if (warning != null)
		{
			chat(warning);
		}
		renderer.updateKnockdown(pose, frameDt);
		TumbleBody body = knockdown.getBody();
		offBoardPose = new OffBoardPose(SkateMode.ON_FOOT, BoardState.DROPPED, pose.boardX, pose.boardY, pose.boardH,
			pose.boardYaw, pose.bodyX, pose.bodyY, pose.bodyH, pose.facing, body.getSpeed(), false, body.isAirborne(),
			false, body.getVerticalSpeed(), 0f, BoardTransition.NONE, 1f);
		if (!checkSkaterArea(pose.bodyX, pose.bodyY))
		{
			return false;
		}
		camera.adjustZoom(input.drainZoomNotches());
		camera.orbit(-input.drainOrbitPixels() * ORBIT_RADIANS_PER_PIXEL);
		camera.update(pose.bodyX, pose.bodyY, pose.bodyH, camera.getYaw(), body.getSpeed(),
			body.isAirborne() ? SkaterState.AIRBORNE : SkaterState.ROLLING, landedAirtime, frameDt,
			config.cameraHeight().pitchJau14());
		return true;
	}

	/**
	 * Up on foot where the body lies (the nearest standable spot if that is not one), facing the board, which lies
	 * where it came to rest (or near the skater, if that is out of reach). With {@code reclaim} (the board key while
	 * down) the press also gets the board back: stepped on nearby, else called to hand.
	 */
	private void standUp(boolean reclaim)
	{
		Knockdown k = knockdown;
		TumbleBody b = k.getBody();
		BoardBounce bb = k.getBoard();
		float r = tuning.skaterRadius;
		float[] spot = StandSpot.nearest(world, b.getX(), b.getY(), r, 3 * GridCollisionWorld.TILE);
		boolean moved = spot[0] != b.getX() || spot[1] != b.getY();
		// still in the air (a long fall): the walker falls on from there
		float h = moved ? spot[2] : Math.max(spot[2], b.getH());
		float[] rest = StandSpot.boardRest(world, bb.getX(), bb.getY(), bb.getH(), spot[0], spot[1], spot[2], r);
		float heading = Math.hypot(rest[0] - spot[0], rest[1] - spot[1]) < 1f ? b.getFacing()
			: (float) Math.atan2(rest[0] - spot[0], rest[1] - spot[1]);
		animator.endKnockdown();
		knockdown = null;
		prevKnockPose = null;
		boardSwap.knockedOff(rest[0], rest[1], rest[2], bb.getYaw());
		foot = new FootPhysics(world, tuning.gravity, r, spot[0], spot[1], h, heading);
		physics = standIn();
		prevFootPose = null;
		input.setOnFoot(true);
		giveHeldBail(scoreClock.now(), 0f);
		if (!knockTipShown)
		{
			knockTipShown = true;
			chat("Knocked off! " + getBoardKeyLabel() + " gets your board back (step on it nearby, or it comes to "
				+ "your hands)" + (duel.isDueling() ? "." : ", R gets straight back on. Settings: After a bail."));
		}
		if (reclaim)
		{
			BoardSwap.Action a = boardSwap.onBoardKeyPressed(true, spot[0], spot[1]);
			if (a == BoardSwap.Action.MOUNT_DROPPED)
			{
				mount(boardSwap.getBoardX(), boardSwap.getBoardY(), boardSwap.getBoardHeading(), 0f);
				offBoardPose = onBoardPose(RenderPose.of(physics));
				return;
			}
		}
		offBoardPose = footPose();
	}

	/** R while knocked off: straight back on the board where the body lies (or the nearest standable spot). */
	private void backOnBoard()
	{
		Knockdown k = knockdown;
		TumbleBody b = k.getBody();
		float[] spot = StandSpot.nearest(world, b.getX(), b.getY(), tuning.skaterRadius, 3 * GridCollisionWorld.TILE);
		animator.endKnockdown();
		knockdown = null;
		prevKnockPose = null;
		physics.placeBailed(b.getX(), b.getY(), b.getH(), 0f, 0f, 0f);
		giveHeldBail(scoreClock.now(), 0f);
		boardSwap.remount();
		mount(spot[0], spot[1], b.getFacing(), 0f);
		offBoardPose = onBoardPose(RenderPose.of(physics));
	}

	/** Hands a new combo result to the side panel's stats and to progression. */
	private void takeResults()
	{
		if (stats.update(scorer.resultSequence(), scorer.lastResult(), scorer.lastResultTrickCount()))
		{
			publishPanel();
		}
		if (scorer.resultSequence() != progressedResults)
		{
			progressedResults = scorer.resultSequence();
			if (scorer.lastResult().isLanded())
			{
				// before feedback runs, so a level-up's graphic and bells come this frame
				progression.onComboLanded(scorer.lastSummary());
				runs.onComboLanded(scorer.lastLanded());
				leaderboard.onComboLanded(scorer.lastLanded());
				// the banked value; does nothing outside a duel
				duel.onComboLanded(scorer.lastResult().value, scorer.lastResultTrickCount());
			}
			else if (scorer.lastResult().isBailed())
			{
				runs.onComboBailed();
			}
		}
	}

	/**
	 * Ends skating when the skater (or walker) at local (x, y) is somewhere skating is not allowed, and notes
	 * whether it is in the Wilderness. False when skating ended.
	 */
	private boolean checkSkaterArea(float x, float y)
	{
		WorldPoint skaterTile = WorldPoint.fromLocal(client,
			new LocalPoint(Math.round(x), Math.round(y), client.getTopLevelWorldView()));
		String areaBlock = SafetyRules.skaterAreaBlockReason(skaterTile.getX(), skaterTile.getY(),
			config.allowPvpAreas());
		if (areaBlock != null)
		{
			exit(areaBlock);
			return false;
		}
		skaterInWilderness = SafetyRules.isWildernessTile(skaterTile.getX(), skaterTile.getY());
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
			{
				return false;
			}
			dismount();
			// this press stepped off; it must not also get back on
			boardKeyEdges.pressed = false;
			boardKeyEdges.held = false;
			boardKeyEdges.tapped = false;
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
		stopCelebration();
		scorer.bankCombo(scoreClock.now());
		SkatePhysics p = physics;
		foot = new FootPhysics(world, tuning.gravity, tuning.skaterRadius, p.getX(), p.getY(), p.getH(), p.getHeading());
		// steps off with the roll's momentum, up to a sprint; it runs out within 0.1 s with no key held
		float carried = Math.min(Math.abs(p.getSpeed()), FootPhysics.SPRINT_SPEED);
		float travel = p.getTravelHeading();
		foot.setVelocity((float) Math.sin(travel) * carried, (float) Math.cos(travel) * carried);
		physics = standIn();
		prevFootPose = null;
		manualMeter.update(null, scoreClock.now());
		manualMeterText = null;
		input.setOnFoot(true);
		if (!footTipShown)
		{
			footTipShown = true;
			String key = getBoardKeyLabel();
			chat("On foot: WASD walk, Shift sprint, Space jump, Q / E drop or pick up the board, " + key
				+ " to get back on.");
		}
	}

	/** A standing skater at the walker, for what still reads one on foot (effects, party ghosts, the edge hint). */
	private SkatePhysics standIn()
	{
		SkatePhysics s = new SkatePhysics(tuning, world, world.getGrinds(), foot.getX(), foot.getY(), foot.getHeading());
		s.placeStanding(foot.getX(), foot.getY(), foot.getH(), foot.getHeading());
		return s;
	}

	/** Gets on the board at (x, y), rolling forward at {@code speed}. */
	private void mount(float x, float y, float heading, float speed)
	{
		SkatePhysics p = new SkatePhysics(tuning, world, world.getGrinds(), x, y, heading);
		if (speed > 0f)
		{
			p.setRollingSpeed(speed);
		}
		physics = p;
		foot = null;
		prevFootPose = null;
		prevPose = RenderPose.of(p);
		lockBlend.reset();
		input.setOnFoot(false);
	}

	private void footFrame(float frameDt)
	{
		boardOrbit.reset();
		// the side panel and chatbox stay clickable; refreshed each frame as tabs open and close
		input.setClickThroughAreas(ClickThroughAreas.collect(client));
		// the board's own controls are read (so nothing is left over for later) but do nothing on foot
		input.drainInto(skateInput);
		skateInput.gestures.clear();
		skateInput.pushPressed = false;
		skateInput.resetRequested = false;
		input.consumeNearMiss();
		if (input.consumePlainClick() && !clickHintShown)
		{
			clickHintShown = true;
			chat(ComfortHints.CLICK_HINT);
		}
		if (input.consumeControlsToggle())
		{
			controlsCard.toggle(scoreClock.now());
		}

		input.drainFoot(footControls);
		FootControls fc = footControls;
		// a winner's jump for joy ends on any control
		celebrationInput(DuelEndingDirector.anyInput(fc, boardKeyEdges.pressed), frameDt);
		boolean moving = fc.forward != fc.back || fc.left != fc.right;
		footInput.setCameraRelative(fc.forward, fc.back, fc.left, fc.right, camera.getYaw());
		footInput.sprint = fc.sprint;
		footInput.jumpPressed |= fc.jumpPressed;
		// a jump that will land on the board is the bigger mount hop
		footInput.mountJump = boardSwap.willJumpMount(fc.sprint && moving, foot.getX(), foot.getY());
		boolean dropPickup = fc.dropPickupPressed;
		fc.jumpPressed = false;
		fc.dropPickupPressed = false;

		accumulator += frameDt;
		int steps = 0;
		while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME)
		{
			prevFootPose = footPose();
			foot.step(STEP, footInput);
			accumulator -= STEP;
			steps++;
		}
		if (steps == MAX_STEPS_PER_FRAME)
		{
			accumulator = 0f;
		}
		List<FootEvent> footEvents = foot.drainEvents();
		float x = foot.getX();
		float y = foot.getY();

		BoardSwap.Action action = BoardSwap.Action.NONE;
		if (footEvents.contains(FootEvent.LAND) && !foot.isAirborne())
		{
			action = boardSwap.onLanded(foot.getJumpStartX(), foot.getJumpStartY(), x, y, foot.isJumpedSprinting());
		}
		if (action == BoardSwap.Action.NONE && boardKeyEdges.pressed)
		{
			// never in the air: stepping on mid-jump would drop the skater to the ground below at once
			action = boardSwap.onBoardKeyPressed(!foot.isAirborne(), x, y);
		}
		if (action == BoardSwap.Action.NONE && boardKeyEdges.held)
		{
			action = boardSwap.onBoardKeyHeld();
		}
		if (action == BoardSwap.Action.NONE && dropPickup)
		{
			action = boardSwap.onDropPickup(x, y, world.groundHeight(x, y), foot.getHeading());
		}
		switch (action)
		{
			case MOUNT_CARRIED:
				// dropped under the feet on the move: rolls on at the walker's speed, the way it was going
				mount(x, y, foot.getSpeed() >= BoardSwap.MOVING_SPEED ? foot.getTravelHeading() : foot.getHeading(),
					BoardSwap.stepOnSpeed(foot.getSpeed(), tuning.maxPushSpeed));
				break;
			case MOUNT_DROPPED:
				mount(boardSwap.getBoardX(), boardSwap.getBoardY(), boardSwap.getBoardHeading(), 0f);
				break;
			case JUMP_MOUNT:
				mount(x, y, foot.getTravelHeading(),
					BoardSwap.jumpMountSpeed(foot.getSpeed(), foot.isJumpedSprinting(), tuning.maxPushSpeed));
				break;
			default:
				break;
		}
		if (boardSwap.getMode() == SkateMode.ON_BOARD)
		{
			// got on: the board frame draws from the next frame
			offBoardPose = onBoardPose(RenderPose.of(physics));
			return;
		}

		SkatePhysics stand = physics;
		stand.placeStanding(x, y, foot.getH(), foot.getHeading());
		float scoreTime = scoreClock.now();
		// no scoring on foot; a combo banked on stepping off is shown and counted here
		takeResults();
		// the timed run's clock keeps going on foot
		runs.tick(scorer.hasCombo());
		List<SkateEvent> noEvents = Collections.emptyList();
		List<TrickEvent> noTricks = Collections.emptyList();
		updateHints(noEvents, scoreTime);
		feedback.onFrame(stand, noEvents, noTricks, false, scoreTime, frameDt);
		manualMeterText = null;

		float alpha = config.smoothMotion() ? accumulator / STEP : 1f;
		OffBoardPose pose = OffBoardPose.lerp(prevFootPose, footPose(), alpha);
		offBoardPose = pose;
		renderer.updateOnFoot(pose, frameDt);
		animator.updateOnFoot(pose, frameDt);
		paceCelebration();
		String footAnimWarning = animator.takeWarning();
		if (footAnimWarning != null)
		{
			chat(footAnimWarning);
		}
		if (!checkSkaterArea(x, y))
		{
			return;
		}
		camera.adjustZoom(input.drainZoomNotches());
		// dragging right turns the view like the game's own middle-button drag
		camera.orbit(-input.drainOrbitPixels() * ORBIT_RADIANS_PER_PIXEL);
		float landedAirtime = footEvents.contains(FootEvent.LAND) ? foot.getLastAirTime() : 0f;
		camera.update(pose.walkerX, pose.walkerY, pose.walkerH,
			FootCamera.targetHeading(camera.getYaw(), pose.walkerHeading, moving), foot.getSpeed(),
			foot.isAirborne() ? SkaterState.AIRBORNE : SkaterState.ROLLING, landedAirtime, frameDt,
			config.cameraHeight().pitchJau14());
		// party members see the walker and the board (older plugin versions: a skater standing on its board)
		boolean inPvpArea = hideOtherPlayers || skaterInWilderness;
		float physicsDt = steps * STEP;
		OffBoardPose stepped = footPose();
		float vx = foot.getVelocityX();
		float vy = foot.getVelocityY();
		ghostGuard.run(
			() -> partyGhosts.onFootFrame(stepped, vx, vy, footEvents, inPvpArea, frameDt, physicsDt),
			partyGhosts::onGhostFailure,
			e -> log.warn("Party ghosts failed; ghosts are off until the next skate session", e));
	}

	/** The walker and the board now (not blended). */
	private OffBoardPose footPose()
	{
		FootPhysics f = foot;
		return new OffBoardPose(SkateMode.ON_FOOT, boardSwap.getBoard(), boardSwap.getBoardX(), boardSwap.getBoardY(),
			boardSwap.getBoardH(), boardSwap.getBoardHeading(), f.getX(), f.getY(), f.getH(), f.getHeading(),
			f.getSpeed(), f.isSprinting(), f.isAirborne(), f.isAirborne() && f.isJump(), f.getVerticalVelocity(),
			f.getAirTime(), boardSwap.getTransition(), boardSwap.getTransitionProgress(),
			f.isAirborne() && f.isJump() && f.isMountJump() && boardSwap.getBoard() == BoardState.CARRIED);
	}

	/** The skater on the board, drawn this frame, as the off-board renderer sees it. */
	private OffBoardPose onBoardPose(RenderPose pose)
	{
		SkatePhysics p = physics;
		return new OffBoardPose(SkateMode.ON_BOARD, BoardState.CARRIED, boardSwap.getBoardX(), boardSwap.getBoardY(),
			boardSwap.getBoardH(), boardSwap.getBoardHeading(), pose.x, pose.y, pose.h, pose.heading,
			Math.abs(p.getSpeed()), false, pose.state == SkaterState.AIRBORNE, false, p.getVerticalVelocity(), 0f,
			boardSwap.getTransition(), boardSwap.getTransitionProgress());
	}

	/** On board or on foot (ON_BOARD when not skating; ON_FOOT while knocked off). */
	public SkateMode getMode()
	{
		if (!active)
		{
			return SkateMode.ON_BOARD;
		}
		return knockdown != null ? SkateMode.ON_FOOT : boardSwap.getMode();
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
		{
			controlsCard.onPush(now);
		}
		if (events.contains(SkateEvent.POP))
		{
			controlsCard.onJump(now);
		}
		if (!learnedBefore && controlsCard.learnedBasics() && !config.learnedBasics())
		{
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "learnedBasics", true);
		}
	}

	/** The edge-of-area hint, the "Stumble!" flash and the idle-logout warning (warning only, never prevented). */
	private void updateHints(List<SkateEvent> events, float scoreTime)
	{
		if (events.contains(SkateEvent.STUMBLE))
		{
			lastStumbleAt = scoreTime;
		}
		edgeHintAlpha = ComfortHints.edgeHintAlpha(world.edgeDistance(physics.getX(), physics.getY()) / GridCollisionWorld.TILE);
		// skate keys and right-drags are consumed, so the game does not see them as activity
		int idle = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
		if (idleWarning.update(idle, client.getIdleTimeout()))
		{
			chat(ComfortHints.idleMessage(idle));
		}
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

	/** Why the latest bail happened ("Hit a wall"), shown under "Bailed"; null before any bail. */
	public String getBailReasonText()
	{
		return bailReasonText;
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
		{
			exit("Skate mode ended because your character moved.");
		}
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
		if (note == null || warned.equals(warnedManualKey))
		{
			return;
		}
		warnedManualKey = warned;
		chat(note);
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

		Map<String, Integer> others = new LinkedHashMap<>();
		others.put("Skate mode key", plainToggle);
		others.put("Wheelie (manual) key", config.manualKey().getKeyCode());
		others.put("Lean forward key", config.leanForwardKey().getKeyCode());
		others.put("Lean back key", config.leanBackKey().getKeyCode());
		others.put("Brake key", config.brakeKey().getKeyCode());
		addNote(notes, ComfortHints.boardKeyNote(board.toString(), board.getKeyCode(), keyboardTricks));
		addNote(notes, ComfortHints.boardKeyClashNote(getBoardKeyLabel(), boardCode, others));

		if (config.controllerMode())
		{
			Map<String, Integer> taken = new LinkedHashMap<>();
			taken.put("Wheelie (manual) key", InputController.effectiveManualKey(config.manualKey().getKeyCode(),
				keyboardTricks));
			int brake = config.brakeKey().getKeyCode();
			taken.put("Brake key", InputController.isValidManualKey(brake) ? brake : KeyEvent.VK_UNDEFINED);
			taken.put("Lean forward key", config.leanForwardKey().getKeyCode());
			taken.put("Lean back key", config.leanBackKey().getKeyCode());
			taken.put("Board on/off key", boardCode);
			taken.put("Skate mode key", plainToggle);
			notes.addAll(ComfortHints.padKeyNotes(taken));
			if (PadPresets.customIsBroken(config.controllerPreset(), config.customControllerLayout()))
			{
				notes.add(ComfortHints.BROKEN_CUSTOM_LAYOUT);
			}
		}
		return notes;
	}

	private static void addNote(List<String> notes, String note)
	{
		if (note != null)
		{
			notes.add(note);
		}
	}

	/**
	 * Says the {@link #keyNotes key-setting notes} in chat: always on entering skate mode, and after a key setting
	 * changes while skating only when they changed (so a fix, or an unrelated setting, says nothing).
	 */
	private void warnKeyNotes(boolean always)
	{
		List<String> notes = keyNotes();
		if (!always && notes.equals(lastKeyNotes))
		{
			return;
		}
		List<String> before = lastKeyNotes;
		lastKeyNotes = notes;
		for (String note : notes)
		{
			if (always || !before.contains(note))
			{
				chat(note);
			}
		}
	}

	/** The core Camera plugin's "Vertical camera" (relaxCameraPitch) is on, as that plugin itself applies it. */
	private boolean pitchRelaxerOn()
	{
		Boolean pluginOn = configManager.getConfiguration(RUNELITE_GROUP, CAMERA_PLUGIN_KEY, Boolean.class);
		Boolean relax = configManager.getConfiguration(CAMERA_GROUP, RELAX_PITCH_KEY, Boolean.class);
		// the Camera plugin is on by default: only an explicit false turns it off
		return !Boolean.FALSE.equals(pluginOn) && Boolean.TRUE.equals(relax);
	}

	/** Mouse-wheel zoom while skating is kept: written back to the camera zoom setting. */
	private void saveWheelZoom()
	{
		int zoom = camera.getBaseZoom();
		if (zoom != config.cameraZoom())
		{
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "cameraZoom", zoom);
		}
	}

	/**
	 * A RuneSkate setting changed. While skating, zoom and the input settings (flick sensitivity and button,
	 * mirroring, trick controls, the manual key) apply at once;
	 * settings read only when skating starts say they apply next time. Client thread.
	 */
	public void onConfigChanged(String key)
	{
		if ("boardDetail".equals(key) || "boardModel".equals(key))
		{
			applyBoardSettings();
		}
		if (!active || key == null)
		{
			return;
		}
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
				chat("That setting applies next time you start skating.");
				break;
			default:
				break;
		}
	}

	private void chat(String msg)
	{
		skateChat.send(msg);
	}

	/** The rail a grind lock would catch right now, for the ::skategrinds view; null when not skating or none. */
	public GrindSegment getPredictedGrind()
	{
		SkatePhysics p = physics;
		return active && p != null && knockdown == null && boardSwap.getMode() == SkateMode.ON_BOARD
			? p.getPredictedGrind() : null;
	}
}
