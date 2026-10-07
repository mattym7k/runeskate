package com.gielinorskate.party;

import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.CollisionWorld;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.physics.TumbleBody;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.Deck;
import com.gielinorskate.render.BakedBoardModel;
import com.gielinorskate.render.BoardController;
import com.gielinorskate.render.BoardGeometry;
import com.gielinorskate.render.BoardModel;
import com.gielinorskate.render.BoardPlacement;
import com.gielinorskate.render.BoardPoser;
import com.gielinorskate.render.BodyPose;
import com.gielinorskate.render.CarryPose;
import com.gielinorskate.render.CelebrationSequence;
import com.gielinorskate.render.FootBody;
import com.gielinorskate.render.GaitPlayback;
import com.gielinorskate.render.GrabPose;
import com.gielinorskate.render.HandAnchor;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.render.OsrsColor;
import com.gielinorskate.render.Placement;
import com.gielinorskate.render.PoseSmoothing;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.render.SnappedBoard;
import com.gielinorskate.render.SwapBlend;
import com.gielinorskate.render.TantrumSequence;
import com.gielinorskate.tricks.Trick;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.LongFunction;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * Draws party ghosts: per visible ghost its own board (mesh included) and, when the member's real character is in the scene, a body
 * drawn from our own copy of that character's model (else a board-only ghost), animated as the local skater is
 * (GhostBodyController: the riding stance, walk and run, the knockdown, with the procedural pose on top) without
 * touching the character itself. Placement mirrors the local skater's renderer. The board
 * is the RuneSkate (baked) board at Normal detail in the member's designs, one object per part like the local
 * board, or the classic board when that is the "Board model" setting (or the baked one can't be built).
 * Client thread only.
 */
final class GhostRenderer
{
	/** JAU per radian, as in the local renderer (a relative turn, no south offset). */
	private static final float JAU_PER_RADIAN = 1024f / Angles.PI;
	/** Gap between deck and soles, as for the local skater. */
	private static final float POSE_EPSILON = 0.01f;
	/** Seconds between searches of the scene's players for a ghost's real character. */
	private static final float LOOKUP_INTERVAL = 1f;
	/** Label height above the feet, a little over a character's height. */
	private static final int LABEL_HEIGHT = 240;
	/** Getting on or off, or a board dropped or picked up, eases over this long (BoardSwap.TRANSITION_SECONDS). */
	static final float SWAP_SECONDS = 0.25f;
	/** A board further than this from where it was drawn (a call-back) appears instead of flying there. */
	static final float BOARD_MOVE_REACH = 2 * 128f;

	/** Most objects a ghost's board is drawn as (the baked board's parts). */
	private static final int MAX_BOARD_PARTS = 4;
	/** Seconds (time constant): a knocked-off ghost's board eases toward each new place instead of jumping there. */
	static final float KNOCK_BOARD_TAU = 0.08f;

	private static final class Ghost
	{
		/** The ghost's body pose: the body is deformed by it and the board reads its front / back flip, when drawn. */
		final BodyPose pose = new BodyPose();
		final GhostRig rig = new GhostRig();
		/** One object per board part, all placed and posed the same; the first {@link #boardCount} are used. */
		final BoardController[] boards = new BoardController[MAX_BOARD_PARTS];
		int boardCount;
		/** The ghost's own board mesh (never shared: posing rewrites it), baked or classic, and its designs. */
		BakedBoardModel baked;
		BoardModel classic;
		BoardLook look;
		final GhostBodyController body;
		boolean bodyRegistered;
		float lastRoll = Float.NaN;
		float lastPitch = Float.NaN;
		float holdPitch;
		/** A grab's board pitch and roll tweak (GrabPose), snapped in and out (PoseSmoothing.GRAB_TAU). */
		float grabPitch;
		float grabRoll;
		float slideDrop;
		float nextLookup;
		/** The board object is registered (it is not while it lies outside this scene). */
		boolean boardRegistered;
		/** This frame's placements, the ones drawn last frame, and the get-on / get-off blend between. */
		final Placement bodyT = new Placement();
		final Placement boardT = new Placement();
		final Placement drawnBody = new Placement();
		final Placement drawnBoard = new Placement();
		final SwapBlend swap = new SwapBlend();
		float swapTime;
		boolean drawnOnce;
		/** The board's state last drawn: null on the board. */
		BoardState lastOff;
		/** This frame's board flip pivot and whether the board turns with a flip (for a blended re-pose). */
		float pivotY;
		boolean flipAllowed;
		int feetZ;
		/** The last frame was a knockdown (its board eases between updates). */
		boolean knocked;
		/** Duel endings already taken from the predictor, and when the playing ones started (our clock; NaN: none). */
		int tantrumsSeen;
		int celebrationsSeen;
		float tantrumAt = Float.NaN;
		float celebrateAt = Float.NaN;
		/** Where the board was drawn as the tantrum began (the grab starts there), and its hold this frame. */
		final Placement tantrumFrom = new Placement();
		final float[] tantrumHold = new float[4];
		/** The tantrum's board halves (made from this ghost's own board), and whether it snapped yet. */
		SnappedBoard halves;
		boolean snapped;

		Ghost(Client client)
		{
			body = new GhostBodyController(client, pose);
		}
	}

	private final Client client;
	private final Map<Long, Ghost> drawn = new HashMap<>();
	/** Ghosts get the baked board ("Board model": RuneSkate); a change rebuilds them. */
	private boolean bakedBoards = true;
	/** A board mesh could not be built (cache model unavailable): not retried every frame. */
	private boolean boardModelFailed;
	/** The last update's show flag (for ::skateghosts). */
	private boolean lastShow;
	private volatile List<GhostLabel> labels = Collections.emptyList();
	/** This frame's drawn ghosts and their distances from the camera's focus, for the detail budget (reused). */
	private Ghost[] budgetGhosts = new Ghost[4];
	private float[] budgetDistance = new float[4];
	private int[] budgetRank = new int[4];
	/** The local skater's collision world and the scene base it is in (tantrum halves bounce on it), or null. */
	private CollisionWorld world;
	private int worldBaseX;
	private int worldBaseY;
	private float gravity = GhostPredictor.GRAVITY;
	/** Plays a duel ending's crack or sparkle at a ghost (sounds and particles, by their settings); null: none. */
	private EndingCues cues;
	private final Random random = new Random();

	/** Where a ghost's duel ending cues go. */
	interface EndingCues
	{
		void play(SkateFeedback.EndingCue cue, float x, float y, float h, float now);
	}

	GhostRenderer(Client client)
	{
		this.client = client;
	}

	/**
	 * The ground the halves of a ghost's snapped board bounce on: the local skater's collision world (local units of
	 * the scene based at {@code baseX}, {@code baseY}) with its gravity, or null (flat ground where it breaks).
	 */
	void setCollisionWorld(CollisionWorld world, int baseX, int baseY, float gravity)
	{
		this.world = world;
		this.worldBaseX = baseX;
		this.worldBaseY = baseY;
		if (gravity > 0f)
		{
			this.gravity = gravity;
		}
	}

	/** Who plays the duel endings' cues at ghosts. */
	void setEndingCues(EndingCues cues)
	{
		this.cues = cues;
	}

	/** This frame's labels, for the overlay. */
	List<GhostLabel> labels()
	{
		return labels;
	}

	/**
	 * The "Board model" setting: the baked RuneSkate board (true) or the classic one for every ghost. Ghosts drawn
	 * now are rebuilt with it.
	 */
	void setBakedBoards(boolean baked)
	{
		if (baked != bakedBoards)
		{
			bakedBoards = baked;
			removeAll();
		}
	}

	/**
	 * Draws {@code ghosts} for this frame, or nothing when {@code show} is false.
	 *
	 * @param names display name of a party member by ID, or null
	 * @param ground the ground ghosts on this plane follow (null: none)
	 */
	void update(Map<Long, GhostPredictor> ghosts, LongFunction<String> names, boolean show, float now, float dt,
		GhostPredictor.Ground ground)
	{
		WorldView wv = client.getTopLevelWorldView();
		lastShow = show;
		if (!show || wv == null || ghosts.isEmpty() || boardModelFailed)
		{
			// keeps boardModelFailed: no retry every frame within the session
			removeAll();
			return;
		}
		int world = client.getWorld();
		int plane = wv.getPlane();
		List<GhostLabel> out = new ArrayList<>(ghosts.size());
		Set<Long> seen = new HashSet<>();
		int budgetCount = 0;
		// the focal point's Z is the scene's y (its Y is the height)
		float focusX = client.getCameraFocalPointX();
		float focusY = client.getCameraFocalPointZ();
		for (Map.Entry<Long, GhostPredictor> e : ghosts.entrySet())
		{
			GhostPredictor predictor = e.getValue();
			if (!GhostVisibility.sameSpace(world, plane, predictor.current()))
			{
				continue;
			}
			RenderPose p = predictor.pose(now, ground);
			int lx = Math.round(GhostCodec.toLocal(p.x, wv.getBaseX()));
			int ly = Math.round(GhostCodec.toLocal(p.y, wv.getBaseY()));
			if (!GhostVisibility.inScene(lx, ly, wv.getSizeX(), wv.getSizeY()))
			{
				continue;
			}
			long id = e.getKey();
			Ghost g = drawn.get(id);
			if (g == null)
			{
				// each ghost has its own board mesh, built on spawn and dropped with the ghost: posing rewrites the
				// model data, so ghosts sharing one would draw each other's flips and pitch (the baked board's
				// merged templates are shared: a spawn only copies them)
				g = new Ghost(client);
				if (!buildBoard(g, predictor.look()))
				{
					boardModelFailed = true;
					continue;
				}
				g.nextLookup = now;
				// endings from before it was drawn are not played
				g.tantrumsSeen = predictor.tantrumCount();
				g.celebrationsSeen = predictor.celebrateCount();
				showBoard(g, true);
				drawn.put(id, g);
			}
			seen.add(id);
			if (!g.look.equals(predictor.look()))
			{
				// the member picked other designs: only the colours change
				recolour(g, predictor.look());
			}
			String name = names.apply(id);
			takeEndings(g, predictor, p, lx, ly, now);
			int feetZ = place(g, wv, plane, p, lx, ly, predictor, now, dt);
			if (g.halves != null)
			{
				g.halves.update(dt);
			}
			updateBody(g, wv, name, now);
			animate(g, predictor, p, now, dt);
			if (budgetCount == budgetGhosts.length)
			{
				budgetGhosts = Arrays.copyOf(budgetGhosts, budgetCount * 2);
				budgetDistance = Arrays.copyOf(budgetDistance, budgetCount * 2);
				budgetRank = Arrays.copyOf(budgetRank, budgetCount * 2);
			}
			budgetGhosts[budgetCount] = g;
			budgetDistance[budgetCount] = GhostDetail.focusDistance(lx, ly, focusX, focusY);
			budgetCount++;

			Trick trick = predictor.labelTrick();
			float alpha = trick == null ? 0f : GhostVisibility.labelAlpha(predictor.labelAge(now));
			// always one per drawn ghost: a duel draws its HP bar and hitsplats at it even without a name
			out.add(new GhostLabel(id, lx, ly, feetZ - LABEL_HEIGHT, name, alpha > 0f ? trick.displayName : null,
				alpha));
		}
		// the nearest few get the full body; the rest play the animation only
		GhostDetail.rank(budgetDistance, budgetCount, budgetRank);
		for (int i = 0; i < budgetCount; i++)
		{
			GhostBodyController body = budgetGhosts[i].body;
			body.setFull(GhostDetail.full(budgetRank[i], budgetDistance[i]), budgetRank[i], budgetDistance[i]);
			body.logChanges();
			budgetGhosts[i] = null;
		}
		for (Iterator<Map.Entry<Long, Ghost>> it = drawn.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Long, Ghost> e = it.next();
			if (!seen.contains(e.getKey()))
			{
				remove(e.getValue());
				it.remove();
			}
		}
		labels = out;
	}

	/** Builds a ghost's board in {@code look}'s designs: baked if wanted and possible, else classic. */
	private boolean buildBoard(Ghost g, BoardLook look)
	{
		g.look = look;
		BoardPoser poser = null;
		if (bakedBoards)
		{
			g.baked = BakedBoardModel.create(client, client.getTextureProvider() != null
				? client.getTextureProvider().getBrightness() : OsrsColor.DEFAULT_BRIGHTNESS, false, look);
			poser = g.baked;
		}
		if (poser == null)
		{
			g.classic = BoardModel.create(client, Deck.fromName(look.deck.id).palette());
			poser = g.classic;
		}
		if (poser == null)
		{
			return false;
		}
		g.boardCount = Math.max(1, Math.min(MAX_BOARD_PARTS, poser.partCount()));
		for (int i = 0; i < g.boardCount; i++)
		{
			g.boards[i] = new BoardController(i);
			g.boards[i].setBoard(poser, g.pose);
		}
		return true;
	}

	/** The member's new designs on its ghost's board: colours only, lit again at the next pose. */
	private static void recolour(Ghost g, BoardLook look)
	{
		g.look = look;
		if (g.baked != null)
		{
			g.baked.setLook(look);
		}
		else if (g.classic != null)
		{
			g.classic.setPalette(Deck.fromName(look.deck.id).palette());
		}
		for (int i = 0; i < g.boardCount; i++)
		{
			g.boards[i].repaint();
		}
	}

	/** Every part of the ghost's board in the same pose. */
	private static void poseBoard(Ghost g, float roll, float pitch, float pivotY, boolean flipAllowed)
	{
		for (int i = 0; i < g.boardCount; i++)
		{
			g.boards[i].setPose(roll, pitch, pivotY, flipAllowed);
		}
	}

	/**
	 * Positions board and body, on the board or on foot, easing over 0.25 s when the member gets on or off, or
	 * drops or picks up the board nearby; returns the feet's z (RuneLite, down-negative).
	 */
	private int place(Ghost g, WorldView wv, int plane, RenderPose p, int x, int y, GhostPredictor predictor,
		float now, float dt)
	{
		GhostState latest = predictor.current();
		BoardState off = latest == null ? null : latest.offBoard;
		boolean boardVisible = true;
		boolean knocked = latest != null && latest.knockStage != null;
		float tantrumAge = now - g.tantrumAt;
		// the member shares a walker standing with the board in hand while throwing it
		boolean tantrum = !knocked && off != null && tantrumAge >= 0f && tantrumAge < TantrumSequence.DURATION;
		if (knocked)
		{
			boardVisible = placeKnockdown(g, wv, p, x, y, latest, predictor, now, dt);
		}
		else if (tantrum)
		{
			boardVisible = placeTantrum(g, wv, plane, p, x, y, predictor, now, dt, tantrumAge);
		}
		else if (off == null)
		{
			placeOnBoard(g, p, x, y, predictor, now, dt);
		}
		else
		{
			boardVisible = placeOnFoot(g, wv, p, x, y, latest, predictor, now, dt);
		}
		g.knocked = knocked;
		// getting on or off eases from what was drawn; so does a board dropped or picked up within reach (a
		// knockdown starts where the bail is, as the local skater's does: no blend into it)
		boolean swapped = (off == null) != (g.lastOff == null);
		if (knocked || tantrum)
		{
			// (the tantrum's grab takes the board from where it was drawn itself)
			g.swap.cancel();
		}
		else if (g.drawnOnce && swapped)
		{
			g.swap.begin(g.drawnBody, g.boardRegistered ? g.drawnBoard : null, off == null);
			g.swapTime = 0f;
		}
		else if (g.drawnOnce && off != g.lastOff && g.boardRegistered && boardVisible
			&& Math.hypot(g.boardT.x - g.drawnBoard.x, g.boardT.y - g.drawnBoard.y) <= BOARD_MOVE_REACH)
		{
			g.swap.begin(null, g.drawnBoard, false);
			g.swapTime = 0f;
		}
		g.lastOff = off;
		if (g.swap.isActive())
		{
			g.swapTime += dt;
			g.swap.apply(g.swapTime / SWAP_SECONDS, g.bodyT, g.boardT);
			// the board turns between hand and wheels with the rest of the blend
			poseBoard(g, g.boardT.roll, g.boardT.pitch, g.pivotY, g.flipAllowed);
		}
		showBoard(g, boardVisible);

		// every part of the board in exactly the same place
		for (int i = 0; i < g.boardCount; i++)
		{
			BoardController b = g.boards[i];
			b.setWorldView(wv.getId());
			b.setLevel(plane);
			b.setX(Math.round(g.boardT.x));
			b.setY(Math.round(g.boardT.y));
			b.setZ(Math.round(g.boardT.z));
			b.setOrientation(g.boardT.orientation());
		}
		g.body.setWorldView(wv.getId());
		g.body.setLevel(plane);
		g.body.setX(Math.round(g.bodyT.x));
		g.body.setY(Math.round(g.bodyT.y));
		g.body.setZ(Math.round(g.bodyT.z));
		g.body.setOrientation(g.bodyT.orientation());
		g.drawnBody.copyFrom(g.bodyT);
		g.drawnBoard.copyFrom(g.boardT);
		g.drawnOnce = true;
		return g.feetZ;
	}

	/**
	 * A ghost on foot: upright, facing the way it walks (walking or running on our copy of the member's model, see
	 * {@link #animate}), with a jump tuck; the board in the right hand as the walk swings it (read off the drawn body),
	 * or lying where it was dropped (false when that is outside this scene: the board is not drawn).
	 */
	private boolean placeOnFoot(Ghost g, WorldView wv, RenderPose p, int x, int y, GhostState latest,
		GhostPredictor predictor, float now, float dt)
	{
		g.rig.updateOnFoot(predictor, p, now, dt, g.pose);
		int z = -Math.round(p.h);
		g.bodyT.set(x, y, z, Angles.toJau(p.heading), 0f, 0f);
		g.feetZ = z;
		boolean visible = true;
		if (latest.offBoard == BoardState.DROPPED)
		{
			float bx = GhostCodec.toLocal(latest.boardX, wv.getBaseX());
			float by = GhostCodec.toLocal(latest.boardY, wv.getBaseY());
			visible = GhostVisibility.inScene(bx, by, wv.getSizeX(), wv.getSizeY());
			g.boardT.set(Math.round(bx), Math.round(by), -Math.round(latest.boardH), Angles.toJau(latest.boardHeading),
				0f, 0f);
		}
		else
		{
			// in the hand as the walk or run swings it (the fixed spot until the hand is read)
			HandAnchor hand = g.body.hand();
			hand.setActive(true);
			hand.step(dt);
			CarryPose.placeAt(x, y, p.h, p.heading, predictor.speed(), hand.x(), hand.y(), hand.z(), g.boardT);
		}
		if (latest.offBoard == BoardState.DROPPED)
		{
			g.body.hand().setActive(false);
		}
		// back on the board its trick pose is rebuilt from scratch
		g.lastRoll = Float.NaN;
		g.lastPitch = Float.NaN;
		g.pose.boardLift = 0f;
		g.pivotY = BoardPlacement.boardFlipPivotY(0);
		g.flipAllowed = false;
		poseBoard(g, g.boardT.roll, g.boardT.pitch, g.pivotY, false);
		return visible;
	}

	/**
	 * A knocked-off ghost: the body tumbling onto its lying angle, lying, and getting up (the OSRS lie-down and
	 * get-up when our animation shows, else the procedural fall), its lowest point on the ground, facing along the
	 * tumble; the board where it was sent, eased between updates (false when that is outside this scene).
	 */
	private boolean placeKnockdown(Ghost g, WorldView wv, RenderPose p, int x, int y, GhostState latest,
		GhostPredictor predictor, float now, float dt)
	{
		KnockdownPose.Stage stage = latest.knockStage;
		float age = predictor.knockStageAge(now);
		float angle = GhostKnockdown.angle(stage, latest.knockLie, age);
		KnockdownPose k = new KnockdownPose(stage, GhostKnockdown.progress(stage, age), p.x, p.y, p.h, p.heading,
			angle, 0f, latest.boardX, latest.boardY, latest.boardH, latest.boardHeading, 0f, 0f);
		g.rig.knockdown(k, stage != KnockdownPose.Stage.AIR && g.body.isLayered(), g.pose);
		float down = TumbleBody.groundOffset(g.pose.flip);
		int z = -Math.round(p.h + down);
		g.bodyT.set(x, y, z, Angles.toJau(p.heading + Angles.PI / 2), 0f, 0f);
		g.feetZ = -Math.round(p.h);
		g.body.hand().setActive(false);
		float bx = GhostCodec.toLocal(latest.boardX, wv.getBaseX());
		float by = GhostCodec.toLocal(latest.boardY, wv.getBaseY());
		boolean visible = GhostVisibility.inScene(bx, by, wv.getSizeX(), wv.getSizeY());
		float bz = -latest.boardH;
		if (g.knocked && g.drawnOnce
			&& Math.hypot(bx - g.drawnBoard.x, by - g.drawnBoard.y) <= BOARD_MOVE_REACH)
		{
			float f = 1f - (float) Math.exp(-dt / KNOCK_BOARD_TAU);
			bx = g.drawnBoard.x + (bx - g.drawnBoard.x) * f;
			by = g.drawnBoard.y + (by - g.drawnBoard.y) * f;
			bz = g.drawnBoard.z + (bz - g.drawnBoard.z) * f;
		}
		g.boardT.set(Math.round(bx), Math.round(by), Math.round(bz), Angles.toJau(latest.boardHeading), 0f, 0f);
		// back on the board its trick pose is rebuilt from scratch
		g.lastRoll = Float.NaN;
		g.lastPitch = Float.NaN;
		g.pose.boardLift = 0f;
		g.pivotY = BoardPlacement.boardFlipPivotY(0);
		g.flipAllowed = false;
		poseBoard(g, 0f, 0f, g.pivotY, false);
		return visible;
	}

	/**
	 * A duel tantrum on a ghost {@code t} seconds in ({@link TantrumSequence}), the local skater's own: the body
	 * standing where the member stands (both arms holding the board, the fold of the slam: the body pose), the
	 * ghost's own board grabbed from where it was drawn, stamped, heaved up and slammed down in front, where it snaps
	 * into halves made from that board (with the crack and dust); false once snapped (the board is not drawn until
	 * the member's fresh one is in hand).
	 */
	private boolean placeTantrum(Ghost g, WorldView wv, int plane, RenderPose p, int x, int y,
		GhostPredictor predictor, float now, float dt, float t)
	{
		g.rig.updateOnFoot(predictor, p, now, dt, g.pose);
		TantrumSequence.writeBody(t, g.pose);
		int z = -Math.round(p.h);
		g.bodyT.set(x, y, z, Angles.toJau(p.heading), 0f, 0f);
		g.feetZ = z;
		g.body.hand().setActive(false);
		boolean holding = TantrumSequence.holding(t);
		if (holding)
		{
			TantrumSequence.board(t, g.tantrumHold);
			TantrumSequence.place(x, y, p.h, p.heading, g.tantrumHold, g.boardT);
			float grab = TantrumSequence.grabBlend(t);
			if (grab < 1f && g.tantrumFrom.valid)
			{
				SwapBlend.lerp(g.tantrumFrom, g.boardT, grab);
			}
		}
		else if (!g.snapped)
		{
			g.snapped = true;
			snap(g, wv, plane, x, y, p.h, p.heading, now);
		}
		// back on the board its trick pose is rebuilt from scratch
		g.lastRoll = Float.NaN;
		g.lastPitch = Float.NaN;
		g.pose.boardLift = 0f;
		g.pivotY = BoardPlacement.boardFlipPivotY(0);
		g.flipAllowed = false;
		poseBoard(g, g.boardT.roll, g.boardT.pitch, g.pivotY, false);
		return holding;
	}

	/** The ghost's tantrum board breaks in front of a body at local ({@code x}, {@code y}), feet at {@code h}. */
	private void snap(Ghost g, WorldView wv, int plane, float x, float y, float h, float heading, float now)
	{
		float[] at = new float[2];
		TantrumSequence.snapPoint(x, y, heading, at);
		// the local collision world when it covers this scene, else flat ground at the feet
		CollisionWorld w = world != null && worldBaseX == wv.getBaseX() && worldBaseY == wv.getBaseY() ? world : null;
		float ground = w == null ? h : w.groundHeight(at[0], at[1]);
		if (Float.isNaN(ground) || Float.isInfinite(ground) || Math.abs(ground - h) > 128f)
		{
			ground = h;
		}
		if (g.halves != null)
		{
			g.halves.snap(w, gravity, at[0], at[1], ground, TantrumSequence.boardHeading(heading), wv.getId(), plane,
				random);
		}
		if (cues != null)
		{
			cues.play(SkateFeedback.EndingCue.SNAP, at[0], at[1], ground, now);
		}
	}

	/**
	 * Takes a duel ending the predictor reached since the last frame: a tantrum (still early enough to show its slam)
	 * gets its halves made from the ghost's own board now; a celebration sparkles at the ghost.
	 */
	private void takeEndings(Ghost g, GhostPredictor predictor, RenderPose p, int x, int y, float now)
	{
		if (predictor.tantrumCount() != g.tantrumsSeen)
		{
			g.tantrumsSeen = predictor.tantrumCount();
			float age = predictor.tantrumAge(now);
			if (age >= 0f && age < TantrumSequence.SNAP_AT)
			{
				g.tantrumAt = now - age;
				g.celebrateAt = Float.NaN;
				g.snapped = false;
				g.tantrumFrom.copyFrom(g.drawnBoard);
				if (!g.boardRegistered || !g.drawnOnce
					|| Math.hypot(g.drawnBoard.x - x, g.drawnBoard.y - y) > BOARD_MOVE_REACH)
				{
					// far away (or not drawn): the board is simply in the hands
					g.tantrumFrom.invalidate();
				}
				if (g.halves == null)
				{
					g.halves = new SnappedBoard(client);
				}
				try
				{
					g.halves.prepare(g.baked != null ? g.baked : g.classic);
				}
				catch (RuntimeException ex)
				{
					// no halves: the board just goes at the slam
					g.halves.despawn();
				}
			}
		}
		if (predictor.celebrateCount() != g.celebrationsSeen)
		{
			g.celebrationsSeen = predictor.celebrateCount();
			float age = predictor.celebrateAge(now);
			if (age >= 0f && CelebrationSequence.playing(age))
			{
				g.celebrateAt = now - age;
				if (cues != null)
				{
					cues.play(SkateFeedback.EndingCue.SPARKLE, x, y, p.h, now);
				}
			}
		}
	}

	/**
	 * The ghost body's OSRS animation this frame: the riding stance on the board, walk or run on foot (at the
	 * walker's pace), lying and getting up when knocked down; a duel ending's emote over it (the tantrum's stamp on
	 * foot; the cheer or jump for joy on the ground, cut short once the member is off it: they skipped it).
	 */
	private static void animate(Ghost g, GhostPredictor predictor, RenderPose p, float now, float dt)
	{
		GhostState latest = predictor.current();
		KnockdownPose.Stage knock = latest == null ? null : latest.knockStage;
		boolean onFoot = latest != null && latest.offBoard != null;
		FootBody.Gait gait = g.rig.gait();
		GhostAnim a = GhostAnim.pick(onFoot, gait, knock);
		float rate = a == GhostAnim.WALK || a == GhostAnim.RUN ? GaitPlayback.rate(gait, predictor.speed()) : 1f;
		float progress = knock == null ? 0f : GhostKnockdown.progress(knock, predictor.knockStageAge(now));
		float tantrumAge = now - g.tantrumAt;
		float celebrateAge = now - g.celebrateAt;
		boolean grounded = onFoot ? p.state != SkaterState.AIRBORNE
			: p.state == SkaterState.ROLLING || p.state == SkaterState.MANUAL;
		if (knock != null || !grounded)
		{
			g.celebrateAt = Float.NaN;
			celebrateAge = Float.NaN;
		}
		GhostAnim ending = knock != null ? null : GhostAnim.ending(onFoot ? tantrumAge : Float.NaN, celebrateAge,
			onFoot);
		if (ending != null)
		{
			a = ending;
			rate = 1f;
			progress = GhostAnim.endingProgress(ending, tantrumAge, celebrateAge);
		}
		g.body.animate(a, rate, progress, dt);
	}

	/** Shows or hides a ghost's board (hidden when it lies outside this scene). */
	private void showBoard(Ghost g, boolean show)
	{
		if (show == g.boardRegistered)
		{
			return;
		}
		g.boardRegistered = show;
		for (int i = 0; i < g.boardCount; i++)
		{
			if (show)
			{
				client.registerRuneLiteObject(g.boards[i]);
			}
			else
			{
				client.removeRuneLiteObject(g.boards[i]);
			}
		}
	}

	/** A ghost on its board, placed as the local skater is. */
	private void placeOnBoard(Ghost g, RenderPose p, int x, int y, GhostPredictor predictor, float now, float dt)
	{
		boolean bailed = p.state == SkaterState.BAILED;
		// manual and grind pitch ease in and out; an impossible's end-over-end pitch is drawn as predicted
		// a grab tweaks the board toward the hand (GrabPose), as quickly as the hand reaches for it
		float holdPitchFor = SkatePhysics.boardPitchFor(p.state, p.hold);
		g.holdPitch = PoseSmoothing.approach(g.holdPitch, holdPitchFor, dt, PoseSmoothing.POSE_TAU);
		// a rolling grab (a grab hold while ROLLING) keeps the board flat on the ground
		Trick tweak = p.state == SkaterState.ROLLING ? null : p.hold;
		g.grabPitch = PoseSmoothing.approach(g.grabPitch, GrabPose.boardPitch(tweak), dt, PoseSmoothing.GRAB_TAU);
		g.grabRoll = PoseSmoothing.approach(g.grabRoll, GrabPose.boardRoll(tweak), dt, PoseSmoothing.GRAB_TAU);
		float roll = bailed ? Angles.PI : p.boardRoll + g.grabRoll;
		float pitch = PoseSmoothing.popPitch(predictor.sincePop(now), predictor.isPopNollie()) + g.holdPitch
			+ g.grabPitch + (p.boardPitch - holdPitchFor);
		if (Float.isNaN(g.lastRoll) || Math.abs(roll - g.lastRoll) > POSE_EPSILON
			|| Float.isNaN(g.lastPitch) || Math.abs(pitch - g.lastPitch) > POSE_EPSILON)
		{
			g.lastRoll = roll;
			g.lastPitch = pitch;
		}
		// the body's lean, crouch, push, landing squash, bail fall and front / back flip; the board turns with the
		// skater's flip about the centre of mass (both read the pose when drawn)
		g.rig.update(predictor, p, now, dt, g.pose);
		g.body.hand().setActive(false);
		g.slideDrop = PoseSmoothing.approach(g.slideDrop, BoardPlacement.slideDrop(p.state, p.hold), dt,
			PoseSmoothing.POSE_TAU);
		int lower = Math.round(g.slideDrop);
		int deckLift = Math.round(BoardPlacement.deckLift(g.lastPitch));
		int z = -Math.round(p.h);
		int top = (int) BoardGeometry.BOARD_TOP;
		// a grab pulls the board up toward the hips with the knees (the body stays put)
		int lift = BoardPlacement.grabLift(g.pose.feetLift, bailed);
		poseBoard(g, g.lastRoll, g.lastPitch, BoardPlacement.boardFlipPivotY(deckLift - lift), !bailed);
		// a grabbing hand goes to the board as drawn
		g.pose.boardRoll = g.lastRoll;
		g.pose.boardPitch = g.lastPitch;
		g.pose.deckLift = deckLift;
		g.pose.boardLift = lift;

		int yawOffsetJau = Math.round(p.boardYaw * JAU_PER_RADIAN);
		g.boardT.set(x, y, bailed ? z - top : z + lower - lift, (Angles.toJau(p.heading) + yawOffsetJau) & 2047,
			g.lastRoll, g.lastPitch);

		int feetZ = bailed ? z : z - top - BoardPlacement.FOOT_CLEARANCE - deckLift + lower;
		// regular stance: the body faces 90 degrees clockwise from the board's nose
		g.bodyT.set(x, y, feetZ, Angles.toJau(p.heading + Angles.PI / 2), 0f, 0f);
		g.feetZ = feetZ;
		g.pivotY = BoardPlacement.boardFlipPivotY(deckLift - lift);
		g.flipAllowed = !bailed;
	}

	/** Finds the member's real character in the scene now and then; no character means a board-only ghost. */
	private void updateBody(Ghost g, WorldView wv, String name, float now)
	{
		if (now >= g.nextLookup)
		{
			g.nextLookup = now + LOOKUP_INTERVAL;
			Player found = null;
			if (name != null)
			{
				for (Player pl : wv.players())
				{
					if (pl != null && GhostVisibility.sameName(pl.getName(), name))
					{
						found = pl;
						break;
					}
				}
			}
			g.body.setPlayer(found);
			g.body.setName(name);
			if (found == null)
			{
				g.body.noBody(name == null ? GhostBodyStatus.Body.NO_NAME : GhostBodyStatus.Body.NOT_IN_SCENE);
			}
		}
		boolean want = g.body.getPlayer() != null;
		if (want && !g.bodyRegistered)
		{
			client.registerRuneLiteObject(g.body);
			g.bodyRegistered = true;
		}
		else if (!want && g.bodyRegistered)
		{
			client.removeRuneLiteObject(g.body);
			g.bodyRegistered = false;
		}
	}

	/**
	 * The ::skateghosts lines: one per ghost the party knows of, drawn or not, with what its body is drawn as and
	 * why. Client thread; built only when asked.
	 */
	List<String> statusLines(Map<Long, GhostPredictor> ghosts, LongFunction<String> names)
	{
		List<String> out = new ArrayList<>(ghosts.size());
		WorldView wv = client.getTopLevelWorldView();
		for (Map.Entry<Long, GhostPredictor> e : ghosts.entrySet())
		{
			String name = names.apply(e.getKey());
			Ghost g = drawn.get(e.getKey());
			if (g != null)
			{
				out.add(g.body.status().line(name) + "; " + e.getValue().playbackStatus());
				continue;
			}
			GhostState latest = e.getValue().latest();
			boolean sameSpace = wv != null && GhostVisibility.sameSpace(client.getWorld(), wv.getPlane(), latest);
			boolean inScene = wv != null && latest != null && GhostVisibility.inScene(
				GhostCodec.toLocal(latest.x, wv.getBaseX()), GhostCodec.toLocal(latest.y, wv.getBaseY()),
				wv.getSizeX(), wv.getSizeY());
			out.add(GhostBodyStatus.notDrawn(name, lastShow, sameSpace, inScene, boardModelFailed) + "; "
				+ e.getValue().playbackStatus());
		}
		return out;
	}

	/** A player left the scene: no ghost may keep drawing its model. */
	void forgetPlayer(Player player)
	{
		for (Ghost g : drawn.values())
		{
			if (g.body.getPlayer() == player)
			{
				g.body.setPlayer(null);
				g.nextLookup = 0f;
				if (g.bodyRegistered)
				{
					client.removeRuneLiteObject(g.body);
					g.bodyRegistered = false;
				}
			}
		}
	}

	/**
	 * Skate end, scene lost, shutdown or ghost failure: removes every ghost, and a later session (or scene) gets
	 * a fresh try at building the board model.
	 */
	void despawnAll()
	{
		boardModelFailed = false;
		removeAll();
	}

	private void removeAll()
	{
		if (drawn.isEmpty())
		{
			labels = Collections.emptyList();
			return;
		}
		for (Ghost g : drawn.values())
		{
			remove(g);
		}
		drawn.clear();
		labels = Collections.emptyList();
	}

	private void remove(Ghost g)
	{
		if (g.halves != null)
		{
			g.halves.despawn();
		}
		showBoard(g, false);
		if (g.bodyRegistered)
		{
			client.removeRuneLiteObject(g.body);
			g.bodyRegistered = false;
		}
		g.body.setPlayer(null);
	}

	/**
	 * The ground of the loaded scene at absolute coordinates, from its tile heights (up-positive; NaN outside
	 * the scene). Client thread.
	 */
	static GhostPredictor.Ground sceneGround(Client client, WorldView wv)
	{
		int baseX = wv.getBaseX();
		int baseY = wv.getBaseY();
		int sizeX = wv.getSizeX();
		int sizeY = wv.getSizeY();
		int plane = wv.getPlane();
		return (x, y) ->
		{
			float lx = GhostCodec.toLocal(x, baseX);
			float ly = GhostCodec.toLocal(y, baseY);
			if (!GhostVisibility.inScene(lx, ly, sizeX, sizeY))
			{
				return Float.NaN;
			}
			// RuneLite heights grow downward
			return -Perspective.getTileHeight(client, new LocalPoint(Math.round(lx), Math.round(ly), wv), plane);
		};
	}
}
