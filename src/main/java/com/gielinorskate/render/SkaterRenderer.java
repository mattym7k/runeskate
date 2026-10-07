package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.BoardTransition;
import com.gielinorskate.physics.CollisionWorld;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.physics.TumbleBody;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.Deck;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.runelite.api.Client;
import net.runelite.api.WorldView;

/** Positions the puppet and board from physics state each frame. Client thread only. */
public final class SkaterRenderer
{
	/** From docs/spike-results.md. */
	private static final int BOARD_YAW_OFFSET = 0;
	/** JAU units per radian, matching Angles.toJau's scale (no south-offset: this is a relative turn). */
	private static final float JAU_PER_RADIAN = 1024f / Angles.PI;
	private static final float CARVE_LEAN = 0.15f;
	private static final float POSE_EPSILON = 0.01f;

	private final Client client;
	private PuppetController puppet;
	/** The board's first (for the classic board, only) object; the same as {@code boards[0]}. */
	private BoardController board;
	/**
	 * One object per part of the board drawn (the baked board has up to four), all placed identically each frame;
	 * the first {@link #boardCount} are in use.
	 */
	private final BoardController[] boards = new BoardController[MAX_BOARD_PARTS];
	private int boardCount;
	private static final int MAX_BOARD_PARTS = 4;
	private int worldViewId;
	private int plane;
	private BoardModel boardModel;
	/**
	 * The baked (RuneSkate) board in high and in low ("Normal") detail, each built on first use and kept; drawn
	 * instead of the classic when baked. A detail that failed to build isn't tried again.
	 */
	private BakedBoardModel bakedHigh;
	private BakedBoardModel bakedNormal;
	private boolean bakedHighFailed;
	private boolean bakedNormalFailed;
	private boolean baked;
	private boolean highDetail;
	/** The chosen designs, for the next board built and applied to the current one. */
	private BoardLook look = BoardLook.defaults(BoardDesigns.bundled());
	/** The classic board's colours: the chosen deck's old ladder colours (Classic's for any other deck). */
	private short[] palette = Deck.DEFAULT.palette();
	/** The procedural body pose the puppet draws with (neutral until set). */
	private BodyPose bodyPose = new BodyPose();
	/** Told by the puppet whether each draw had a model (null until set). */
	private DrawnModelReport drawnReport;
	private float lastRoll = Float.NaN;
	private float lastPitch = Float.NaN;
	/** Seconds since the last pop (large when none recently). */
	private float sincePop;
	/** Seconds since the last stumble (large when none recently): the board wobbles for a moment. */
	private float sinceStumble = Float.MAX_VALUE;
	private boolean popNollie;
	private float lean;
	private float holdPitch;
	/** A grab's board pitch and roll tweak (GrabPose), snapped in and out (PoseSmoothing.GRAB_TAU). */
	private float grabPitch;
	private float grabRoll;
	private float slideDrop;
	private float chargeDip;
	/** The board object is registered. */
	private boolean boardShown;

	/** A dropped or picked-up board moves between hand and ground over this long (BoardSwap.TRANSITION_SECONDS). */
	static final float BOARD_MOVE_SECONDS = 0.25f;
	/** Further than this (a call-back) the board appears in the hand instead of flying there. */
	static final float BOARD_MOVE_REACH = 2 * 128f;
	/** This frame's body and board placements, and the ones drawn last frame (a blend starts from those). */
	private final Placement bodyTarget = new Placement();
	private final Placement boardTarget = new Placement();
	private final Placement drawnBody = new Placement();
	private final Placement drawnBoard = new Placement();
	private final Placement feetBoard = new Placement();
	/** The walker's right hand read off the drawn mesh: the carried board follows it through the walk cycle. */
	private final HandAnchor hand = new HandAnchor();
	private final SwapBlend swap = new SwapBlend();
	private final SwapBlend boardMove = new SwapBlend();
	private float boardMoveTime;
	private BoardTransition transition = BoardTransition.NONE;
	private float transitionProgress = 1f;
	private BoardTransition lastTransition = BoardTransition.NONE;
	private float lastProgress = 1f;
	/** The board's state last drawn on foot; null on the board. */
	private BoardState lastBoardState;
	/** A duel tantrum's snapped board: its two halves, flying and lying on whatever the skater does next. */
	private final SnappedBoard halves;
	/** Where the board was drawn when the tantrum began: the grab takes it from there to the hands. */
	private final Placement tantrumFrom = new Placement();
	private final float[] tantrumHold = new float[4];

	public SkaterRenderer(Client client)
	{
		this.client = client;
		this.halves = new SnappedBoard(client);
	}

	public void setBodyPose(BodyPose bodyPose)
	{
		this.bodyPose = bodyPose;
	}

	/** The puppet reports each draw's model presence here, for the animator's missing-model guard. */
	public void setDrawnReport(DrawnModelReport drawnReport)
	{
		this.drawnReport = drawnReport;
	}

	/**
	 * Draws the board in {@code look}'s designs, now if skating (only the colours change: the parts are lit again
	 * at their next pose) and on every board built after. The classic board takes the deck's old ladder colours.
	 * Client thread.
	 */
	public void setLook(BoardLook look)
	{
		this.look = look;
		this.palette = Deck.fromName(look.deck.id).palette();
		if (boardModel != null)
		{
			boardModel.setPalette(palette);
		}
		for (BakedBoardModel m : new BakedBoardModel[]{bakedHigh, bakedNormal})
		{
			if (m != null)
			{
				m.setLook(look);
			}
		}
		for (int i = 0; i < boardCount; i++)
		{
			if (boards[i] != null)
			{
				boards[i].repaint();
			}
		}
	}

	/**
	 * Draws the baked (RuneSkate) board instead of the classic one (or back), now if skating and on every spawn
	 * after. Returns false (and stays classic) if it can't be built. Client thread.
	 */
	public boolean useBakedBoard(boolean on)
	{
		if (on && bakedModel() == null)
		{
			baked = false;
			return false;
		}
		baked = on;
		if (board != null && boardModel != null)
		{
			setPoser(currentPoser());
		}
		return true;
	}

	/**
	 * The "Board model" setting: the baked board (built when first drawn; the classic one is drawn if it can't be)
	 * or the classic one, at this detail. Applied now if skating. Client thread.
	 */
	public void setBoardChoice(boolean bakedBoard, boolean high)
	{
		if (bakedBoard == baked && high == highDetail)
		{
			return;
		}
		baked = bakedBoard;
		highDetail = high;
		if (board != null && boardModel != null)
		{
			setPoser(currentPoser());
		}
	}

	/**
	 * The baked board's detail for the local skater: high (each part fills the renderers' per-model budget) or
	 * normal (the low variant party ghosts use too). Applied now if the baked board is drawn. Client thread.
	 */
	public void setBoardDetail(boolean high)
	{
		if (high == highDetail)
		{
			return;
		}
		highDetail = high;
		if (baked && board != null && boardModel != null)
		{
			if (bakedModel() == null)
			{
				baked = false;
			}
			setPoser(currentPoser());
		}
	}

	/** The baked board at the chosen detail, built on first use; null if it can't be built. */
	private BakedBoardModel bakedModel()
	{
		BakedBoardModel m = highDetail ? bakedHigh : bakedNormal;
		if (m == null && !(highDetail ? bakedHighFailed : bakedNormalFailed))
		{
			m = BakedBoardModel.create(client, client.getTextureProvider() != null
				? client.getTextureProvider().getBrightness() : OsrsColor.DEFAULT_BRIGHTNESS, highDetail, look);
			if (highDetail)
			{
				bakedHigh = m;
				bakedHighFailed = m == null;
			}
			else
			{
				bakedNormal = m;
				bakedNormalFailed = m == null;
			}
		}
		else if (m != null)
		{
			m.setLook(look);
		}
		return m;
	}

	private BoardPoser currentPoser()
	{
		BakedBoardModel b = baked ? bakedModel() : null;
		return b != null ? b : boardModel;
	}

	/**
	 * Draws the board with {@code poser}: one object per part, the extra ones created, placed where the board was
	 * last drawn and shown (or hidden and dropped) to match.
	 */
	private void setPoser(BoardPoser poser)
	{
		int n = Math.max(1, Math.min(MAX_BOARD_PARTS, poser.partCount()));
		for (int i = 0; i < n; i++)
		{
			if (boards[i] == null)
			{
				BoardController c = new BoardController(i);
				c.setWorldView(worldViewId);
				c.setLevel(plane);
				if (drawnBoard.valid)
				{
					place(c, drawnBoard);
				}
				boards[i] = c;
			}
			boards[i].setBoard(poser, bodyPose);
			if (boardShown && i >= boardCount)
			{
				client.registerRuneLiteObject(boards[i]);
			}
		}
		for (int i = n; i < boardCount; i++)
		{
			if (boardShown)
			{
				client.removeRuneLiteObject(boards[i]);
			}
		}
		for (int i = n; i < MAX_BOARD_PARTS; i++)
		{
			boards[i] = null;
		}
		boardCount = n;
	}

	private static void place(BoardController c, Placement p)
	{
		c.setX(Math.round(p.x));
		c.setY(Math.round(p.y));
		c.setZ(Math.round(p.z));
		c.setOrientation(p.orientation());
	}

	public boolean isBakedBoard()
	{
		return baked;
	}

	public boolean spawn(WorldView wv, int plane)
	{
		boardModel = BoardModel.create(client, palette);
		if (boardModel == null)
		{
			return false;
		}
		hand.setActive(false);
		puppet = new PuppetController(client, bodyPose, hand);
		puppet.setDrawnReport(drawnReport);
		worldViewId = wv.getId();
		this.plane = plane;
		Arrays.fill(boards, null);
		board = new BoardController(0);
		boards[0] = board;
		boardCount = 1;
		boardShown = false;
		puppet.setWorldView(wv.getId());
		puppet.setLevel(plane);
		board.setWorldView(wv.getId());
		board.setLevel(plane);
		lastRoll = Float.NaN;
		lastPitch = Float.NaN;
		sincePop = Float.MAX_VALUE;
		sinceStumble = Float.MAX_VALUE;
		popNollie = false;
		lean = 0f;
		holdPitch = 0f;
		grabPitch = 0f;
		grabRoll = 0f;
		slideDrop = 0f;
		chargeDip = 0f;
		drawnBody.invalidate();
		drawnBoard.invalidate();
		swap.cancel();
		boardMove.cancel();
		transition = BoardTransition.NONE;
		transitionProgress = 1f;
		lastTransition = BoardTransition.NONE;
		lastProgress = 1f;
		lastBoardState = null;
		setPoser(currentPoser());
		client.registerRuneLiteObject(puppet);
		showBoard(true);
		return true;
	}

	/**
	 * Draws {@code p}, the (possibly interpolated) pose of this frame.
	 *
	 * @param nolliePop a pop on this frame was a nollie (popped off the nose)
	 */
	public void update(RenderPose p, float steer, boolean crouching, List<SkateEvent> events, boolean nolliePop, float dt)
	{
		if (puppet == null)
		{
			return;
		}
		showBoard(true);
		if (events.contains(SkateEvent.POP))
		{
			sincePop = 0f;
			popNollie = nolliePop;
		}
		else if (sincePop < Float.MAX_VALUE)
		{
			sincePop += dt;
		}
		if (events.contains(SkateEvent.STUMBLE))
		{
			sinceStumble = 0f;
		}
		else if (sinceStumble < Float.MAX_VALUE)
		{
			sinceStumble += dt;
		}

		int x = Math.round(p.x);
		int y = Math.round(p.y);
		int z = -Math.round(p.h);
		boolean bailed = p.state == SkaterState.BAILED;

		// trick board pose: carve lean only while rolling on the ground, plus the physics-driven
		// flip roll / nose-pop pitch / manual-and-grind pitch; the mesh is rebuilt only when roll or
		// pitch actually changes (epsilon logic) or the body flip turns (read by the board when drawn).
		// Lean and hold pitch ease in and out; the pop pitch follows its own rise-and-fall curve.
		boolean carving = p.state == SkaterState.ROLLING;
		lean = PoseSmoothing.approach(lean, carving ? -steer * CARVE_LEAN : 0f, dt, PoseSmoothing.POSE_TAU);
		holdPitch = PoseSmoothing.approach(holdPitch, p.boardPitch, dt, PoseSmoothing.POSE_TAU);
		// a grab tweaks the board toward the hand (GrabPose), as quickly as the hand reaches for it
		grabPitch = PoseSmoothing.approach(grabPitch, GrabPose.boardPitch(p.hold), dt, PoseSmoothing.GRAB_TAU);
		grabRoll = PoseSmoothing.approach(grabRoll, GrabPose.boardRoll(p.hold), dt, PoseSmoothing.GRAB_TAU);
		// a stumble off a wall wobbles the board for a moment
		float roll = bailed ? Angles.PI : lean + p.boardRoll + grabRoll + PoseSmoothing.stumbleWobble(sinceStumble);
		float pitch = PoseSmoothing.popPitch(sincePop, popNollie) + holdPitch + grabPitch;
		if (Float.isNaN(lastRoll) || Math.abs(roll - lastRoll) > POSE_EPSILON
			|| Float.isNaN(lastPitch) || Math.abs(pitch - lastPitch) > POSE_EPSILON)
		{
			lastRoll = roll;
			lastPitch = pitch;
		}
		// slides: the deck, not the wheels, sits on the rail; crouching to charge a pop dips the board
		// (RuneLite z grows downward)
		slideDrop = PoseSmoothing.approach(slideDrop, BoardPlacement.slideDrop(p.state, p.hold), dt,
			PoseSmoothing.POSE_TAU);
		boolean charging = crouching && (p.state == SkaterState.ROLLING || p.state == SkaterState.MANUAL);
		chargeDip = PoseSmoothing.approach(chargeDip, charging ? PoseSmoothing.CHARGE_DIP : 0f, dt,
			PoseSmoothing.POSE_TAU);
		int lower = Math.round(slideDrop + chargeDip);
		// a pitched board turns about its contact truck, which raises the middle of the deck under the feet
		int deckLift = Math.round(BoardPlacement.deckLift(lastPitch));
		// a front or back flip turns the board with the skater about the skater's centre of mass
		int yawOffsetJau = Math.round(p.boardYaw * JAU_PER_RADIAN);
		// a grab pulls the board up toward the hips with the knees (the body stays put, the feet fold up to it)
		int lift = BoardPlacement.grabLift(bodyPose.feetLift, bailed);
		boardTarget.set(x, y, bailed ? z - (int) BoardGeometry.BOARD_TOP : z + lower - lift,
			(Angles.toJau(p.heading) + BOARD_YAW_OFFSET + yawOffsetJau) & 2047, lastRoll, lastPitch);
		// regular stance: body faces 90 degrees clockwise from the board's nose
		// a bail keeps the body where the skater is: the fall animation moves it. (It was moved 64 units along
		// world x, whichever way the skater faced, a sideways jump at the start and end of every bail.)
		bodyTarget.set(x, y, bailed ? z : z - (int) BoardGeometry.BOARD_TOP - BoardPlacement.FOOT_CLEARANCE - deckLift + lower,
			Angles.toJau(p.heading + Angles.PI / 2), 0f, 0f);
		// just got on: eased over from the walker and the carried board (no blend otherwise)
		lastBoardState = null;
		hand.setActive(false);
		boardMove.cancel();
		blendSwap();
		for (int i = 0; i < boardCount; i++)
		{
			// the skater's centre of mass is that much lower against a lifted board
			boards[i].setPose(boardTarget.roll, boardTarget.pitch, BoardPlacement.boardFlipPivotY(deckLift - lift),
				!bailed && puppet.canDeform());
		}
		// a grabbing hand goes to the board as drawn (its tweak, a flip's roll, the lift)
		bodyPose.boardRoll = lastRoll;
		bodyPose.boardPitch = lastPitch;
		bodyPose.deckLift = deckLift;
		bodyPose.boardLift = lift;
		draw();
		halves.update(dt);
	}

	/**
	 * The latest mount or dismount and how far through it is (0..1), read by both {@link #update} and
	 * {@link #updateOnFoot}: a new one is blended from what was drawn just before.
	 */
	public void setTransition(BoardTransition transition, float progress)
	{
		this.transition = transition;
		this.transitionProgress = progress;
	}

	/**
	 * On foot: the puppet stands at the walker facing the way it walks (its OSRS walk, run and idle animations are
	 * the animator's); the board is in the right hand while carried (CarryPose) and lies flat where it was left when
	 * dropped. Getting off eases the body down and round and the board up into the hand; dropping or picking up
	 * the board nearby moves it there over the same 0.25 s.
	 */
	public void updateOnFoot(OffBoardPose p, float dt)
	{
		if (puppet == null)
		{
			return;
		}
		setTransition(p.transition, p.transitionProgress);
		showBoard(true);
		bodyTarget.set(Math.round(p.walkerX), Math.round(p.walkerY), -Math.round(p.walkerH),
			Angles.toJau(p.walkerHeading), 0f, 0f);
		if (p.board == BoardState.DROPPED)
		{
			hand.setActive(false);
			boardTarget.set(Math.round(p.boardX), Math.round(p.boardY), -Math.round(p.boardH),
				(Angles.toJau(p.boardHeading) + BOARD_YAW_OFFSET) & 2047, 0f, 0f);
		}
		else
		{
			// in the hand as the walk or run animation swings it (the fixed spot until the hand is read)
			hand.setActive(true);
			hand.step(dt);
			CarryPose.placeAt(p.walkerX, p.walkerY, p.walkerH, p.walkerHeading, p.walkerSpeed, hand.x(), hand.y(),
				hand.z(), boardTarget);
			if (p.mountJump && p.airborne)
			{
				// the hop onto the board: it comes out of the hand and under the feet on the way down
				float k = CarryPose.mountDrop(p.verticalSpeed);
				if (k > 0f)
				{
					CarryPose.underFeet(p.walkerX, p.walkerY, p.walkerH, p.walkerHeading, feetBoard);
					SwapBlend.lerp(boardTarget, feetBoard, k);
					boardTarget.copyFrom(feetBoard);
				}
			}
		}
		blendSwap();
		blendBoardMove(p.board, dt);
		// back on the board, its trick pose is rebuilt from scratch
		lastRoll = Float.NaN;
		lastPitch = Float.NaN;
		for (int i = 0; i < boardCount; i++)
		{
			boards[i].setPose(boardTarget.roll, boardTarget.pitch, BoardPlacement.boardFlipPivotY(0), false);
		}
		bodyPose.boardRoll = 0f;
		bodyPose.boardPitch = 0f;
		bodyPose.deckLift = 0f;
		bodyPose.boardLift = 0f;
		draw();
		halves.update(dt);
	}

	/**
	 * Knocked off the board: the body where the knockdown puts it, facing along its tumble (the tumble itself, the
	 * lie-down and the get-up are the body pose's, written by the animator first: its flip angle decides how far the
	 * body comes down so its lowest point stays on the ground), and the board on its own, spinning and flipping
	 * until it rests. A mount or dismount blend in progress is dropped; getting back on with R blends from here.
	 */
	public void updateKnockdown(KnockdownPose k, float dt)
	{
		if (puppet == null)
		{
			return;
		}
		showBoard(true);
		hand.setActive(false);
		boardMove.cancel();
		swap.cancel();
		lastBoardState = null;
		lastTransition = transition;
		lastProgress = transitionProgress;
		float down = TumbleBody.groundOffset(bodyPose.flip);
		bodyTarget.set(Math.round(k.bodyX), Math.round(k.bodyY), -Math.round(k.bodyH + down),
			Angles.toJau(k.facing + Angles.PI / 2), 0f, 0f);
		boardTarget.set(Math.round(k.boardX), Math.round(k.boardY),
			-Math.round(k.boardH + KnockdownPose.boardLift(k.boardRoll)),
			(Angles.toJau(k.boardYaw) + BOARD_YAW_OFFSET) & 2047, k.boardRoll, k.boardPitch);
		// back on the board, its trick pose is rebuilt from scratch
		lastRoll = Float.NaN;
		lastPitch = Float.NaN;
		for (int i = 0; i < boardCount; i++)
		{
			boards[i].setPose(boardTarget.roll, boardTarget.pitch, BoardPlacement.boardFlipPivotY(0), false);
		}
		bodyPose.boardRoll = 0f;
		bodyPose.boardPitch = 0f;
		bodyPose.deckLift = 0f;
		bodyPose.boardLift = 0f;
		draw();
		halves.update(dt);
	}

	/**
	 * A duel tantrum starts: the board's two halves are copied now (in its designs as they are), so the snap costs
	 * nothing, and the grab starts from where the board is drawn. False when the board can't snap (it then just
	 * disappears at the slam). Client thread.
	 */
	public boolean prepareTantrum()
	{
		tantrumFrom.copyFrom(drawnBoard);
		if (!boardShown || !drawnBody.valid
			|| Math.hypot(drawnBoard.x - drawnBody.x, drawnBoard.y - drawnBody.y) > BOARD_MOVE_REACH)
		{
			// lying far away (or not drawn): the board is simply in the hands
			tantrumFrom.invalidate();
		}
		BoardPoser poser = boardCount > 0 && boards[0] != null ? currentPoser() : null;
		return halves.prepare(poser);
	}

	/**
	 * The tantrum {@code t} seconds in ({@link TantrumSequence}): the body standing at the walker's feet ({@code x},
	 * {@code y}, up-positive {@code h}) facing {@code heading} (its arms and fold are the body pose's, written by the
	 * animator); the board taken from where it was into both hands, stamped, heaved overhead and slammed down in
	 * front; gone once it snapped (its halves are drawn on their own).
	 */
	public void updateTantrum(float t, float x, float y, float h, float heading, float dt)
	{
		if (puppet == null)
		{
			return;
		}
		hand.setActive(false);
		boardMove.cancel();
		swap.cancel();
		lastBoardState = null;
		lastTransition = transition;
		lastProgress = transitionProgress;
		bodyTarget.set(Math.round(x), Math.round(y), -Math.round(h), Angles.toJau(heading), 0f, 0f);
		boolean holding = TantrumSequence.holding(t);
		showBoard(holding);
		if (holding)
		{
			TantrumSequence.board(t, tantrumHold);
			TantrumSequence.place(x, y, h, heading, tantrumHold, boardTarget);
			float grab = TantrumSequence.grabBlend(t);
			if (grab < 1f && tantrumFrom.valid)
			{
				SwapBlend.lerp(tantrumFrom, boardTarget, grab);
			}
		}
		// back on the board its trick pose is rebuilt from scratch
		lastRoll = Float.NaN;
		lastPitch = Float.NaN;
		for (int i = 0; i < boardCount; i++)
		{
			boards[i].setPose(boardTarget.roll, boardTarget.pitch, BoardPlacement.boardFlipPivotY(0), false);
		}
		bodyPose.boardRoll = 0f;
		bodyPose.boardPitch = 0f;
		bodyPose.deckLift = 0f;
		bodyPose.boardLift = 0f;
		draw();
		halves.update(dt);
	}

	/**
	 * The slammed board breaks in front of the body at the walker's feet ({@code x}, {@code y}, ground {@code h})
	 * facing {@code heading}: its halves fly apart along it, bounced by {@code world}.
	 */
	public void snapBoard(CollisionWorld world, float gravity, float x, float y, float h, float heading,
		Random random)
	{
		float[] at = new float[2];
		TantrumSequence.snapPoint(x, y, heading, at);
		// on the ground where it lands (a step or a kerb in front of the feet)
		float ground = world == null ? h : world.groundHeight(at[0], at[1]);
		if (Float.isNaN(ground) || Float.isInfinite(ground) || Math.abs(ground - h) > 128f)
		{
			ground = h;
		}
		halves.snap(world, gravity, at[0], at[1], ground, TantrumSequence.boardHeading(heading), worldViewId, plane,
			random);
	}

	/** The tantrum ended early (skating stopped): its halves go, nothing is left lying. */
	public void clearHalves()
	{
		halves.despawn();
	}

	/** A new mount or dismount starts easing from the last drawn placements; one in progress eases on. */
	private void blendSwap()
	{
		if (SwapBlend.starts(lastTransition, lastProgress, transition, transitionProgress))
		{
			swap.begin(drawnBody, drawnBoard, transition == BoardTransition.MOUNT);
			boardMove.cancel();
		}
		lastTransition = transition;
		lastProgress = transitionProgress;
		swap.apply(transitionProgress, bodyTarget, boardTarget);
	}

	/** On foot, the board dropped or picked up within reach moves between hand and ground instead of jumping. */
	private void blendBoardMove(BoardState state, float dt)
	{
		if (lastBoardState != null && state != lastBoardState && !swap.isActive() && drawnBoard.valid
			&& Math.hypot(boardTarget.x - drawnBoard.x, boardTarget.y - drawnBoard.y) <= BOARD_MOVE_REACH)
		{
			boardMove.begin(null, drawnBoard, false);
			boardMoveTime = 0f;
		}
		lastBoardState = state;
		if (boardMove.isActive())
		{
			boardMoveTime += dt;
			boardMove.apply(boardMoveTime / BOARD_MOVE_SECONDS, null, boardTarget);
		}
	}

	/** Moves the body and board to this frame's placements and remembers them for the next blend. */
	private void draw()
	{
		// every part of the board in exactly the same place
		for (int i = 0; i < boardCount; i++)
		{
			place(boards[i], boardTarget);
		}
		puppet.setX(Math.round(bodyTarget.x));
		puppet.setY(Math.round(bodyTarget.y));
		puppet.setZ(Math.round(bodyTarget.z));
		puppet.setOrientation(bodyTarget.orientation());
		drawnBody.copyFrom(bodyTarget);
		drawnBoard.copyFrom(boardTarget);
	}

	/** Shows or hides the board object (hidden while carried on foot). */
	private void showBoard(boolean show)
	{
		if (show == boardShown || board == null)
		{
			return;
		}
		boardShown = show;
		for (int i = 0; i < boardCount; i++)
		{
			if (show)
			{
				client.registerRuneLiteObject(boards[i]);
			}
			else
			{
				client.removeRuneLiteObject(boards[i]);
			}
		}
	}

	public void despawn()
	{
		halves.despawn();
		if (puppet != null)
		{
			client.removeRuneLiteObject(puppet);
			if (boardShown)
			{
				for (int i = 0; i < boardCount; i++)
				{
					client.removeRuneLiteObject(boards[i]);
				}
			}
		}
		puppet = null;
		board = null;
		Arrays.fill(boards, null);
		boardCount = 0;
		boardModel = null;
		boardShown = false;
	}
}
