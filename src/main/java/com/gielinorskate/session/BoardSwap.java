package com.gielinorskate.session;

import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.BoardTransition;
import com.gielinorskate.physics.SkateMode;
import com.gielinorskate.world.GridCollisionWorld;

/**
 * On board or on foot, and where the board is: the rules for getting off, getting on, dropping, picking up,
 * calling the board back and jump-mounting. Pure: the session applies the physics side of each
 * {@link Action}. Client thread only.
 */
public final class BoardSwap
{
	/** The board can be stepped on or picked up from within 1.5 tiles. */
	public static final float PICKUP_RADIUS = 1.5f * GridCollisionWorld.TILE;
	/** A jump starting or landing within a tile of the dropped board lands on it. */
	public static final float JUMP_MOUNT_RADIUS = GridCollisionWorld.TILE;
	/** Mount and dismount blends last this long (drawn by the renderer). */
	public static final float TRANSITION_SECONDS = 0.25f;

	/** What the session must do. */
	public enum Action
	{
		NONE,
		/** Step off the board and pick it up (now ON_FOOT, CARRIED). */
		DISMOUNT,
		/** Drop the carried board under the feet and hop on (now ON_BOARD, rolling on at the walker's speed). */
		MOUNT_CARRIED,
		/** Step onto the dropped board where it lies (now ON_BOARD, speed 0). */
		MOUNT_DROPPED,
		/** A jump landed on the board (now ON_BOARD, rolling at {@link #jumpMountSpeed}). */
		JUMP_MOUNT,
		/** The far dropped board came back to hand (now CARRIED). */
		CALL_BACK,
		/** The carried board was put down at the feet (now DROPPED). */
		DROP,
		/** The dropped board was picked up (now CARRIED). */
		PICK_UP
	}

	private SkateMode mode = SkateMode.ON_BOARD;
	private BoardState board = BoardState.CARRIED;
	private float boardX;
	private float boardY;
	private float boardH;
	private float boardHeading;
	private BoardTransition transition = BoardTransition.NONE;
	private float transitionAge;

	/** A new session: on the board. */
	public void reset()
	{
		mode = SkateMode.ON_BOARD;
		board = BoardState.CARRIED;
		transition = BoardTransition.NONE;
		transitionAge = 0f;
	}

	/** Advances the transition blend. */
	public void tick(float dt)
	{
		if (transition != BoardTransition.NONE)
		{
			transitionAge += dt;
		}
	}

	/**
	 * The board key went down.
	 *
	 * On foot a single press always gets the board back: stepped on when the dropped board is within reach,
	 * called back to hand (CARRIED) when it is further away, even mid-jump.
	 *
	 * @param grounded on the ground: on the board rolling or in a manual, on foot not in a jump or fall (the board
	 * is never stepped on or off in the air)
	 * @param wx walker (or skater) position
	 */
	public Action onBoardKeyPressed(boolean grounded, float wx, float wy)
	{
		if (mode == SkateMode.ON_FOOT && board == BoardState.DROPPED && !inReach(wx, wy))
		{
			board = BoardState.CARRIED;
			return Action.CALL_BACK;
		}
		if (!grounded)
		{
			return Action.NONE;
		}
		if (mode == SkateMode.ON_BOARD)
		{
			mode = SkateMode.ON_FOOT;
			board = BoardState.CARRIED;
			startTransition(BoardTransition.DISMOUNT);
			return Action.DISMOUNT;
		}
		if (board == BoardState.CARRIED)
		{
			mount();
			return Action.MOUNT_CARRIED;
		}
		mount();
		return Action.MOUNT_DROPPED;
	}

	/**
	 * The board key was held {@link com.gielinorskate.input.BoardKey#HOLD_MS}: no longer needed (a tap calls the
	 * board back), but a hold that started mid-jump next to the board still gets it back to hand.
	 */
	public Action onBoardKeyHeld()
	{
		if (mode == SkateMode.ON_FOOT && board == BoardState.DROPPED)
		{
			board = BoardState.CARRIED;
			return Action.CALL_BACK;
		}
		return Action.NONE;
	}

	/**
	 * Q or E on foot: drops the carried board at the feet, or picks the dropped one up when within reach.
	 *
	 * @param wh ground height at the feet, where a dropped board lies
	 */
	public Action onDropPickup(float wx, float wy, float wh, float heading)
	{
		if (mode != SkateMode.ON_FOOT)
		{
			return Action.NONE;
		}
		if (board == BoardState.CARRIED)
		{
			board = BoardState.DROPPED;
			boardX = wx;
			boardY = wy;
			boardH = wh;
			boardHeading = heading;
			return Action.DROP;
		}
		if (inReach(wx, wy))
		{
			board = BoardState.CARRIED;
			return Action.PICK_UP;
		}
		return Action.NONE;
	}

	/**
	 * A jump (not a fall) landed on foot. It lands on the board when it started or landed within
	 * {@link #JUMP_MOUNT_RADIUS} of the dropped board, or when it took off sprinting with the board carried.
	 *
	 * @param startX take-off point, NaN for a fall
	 */
	public Action onLanded(float startX, float startY, float landX, float landY, boolean jumpedSprinting)
	{
		if (mode != SkateMode.ON_FOOT || Float.isNaN(startX) || Float.isNaN(startY))
		{
			return Action.NONE;
		}
		boolean mounts = board == BoardState.CARRIED
			? jumpedSprinting
			: within(startX, startY, JUMP_MOUNT_RADIUS) || within(landX, landY, JUMP_MOUNT_RADIUS);
		if (!mounts)
		{
			return Action.NONE;
		}
		mount();
		return Action.JUMP_MOUNT;
	}

	/** A sprinting jump-mount lands rolling at least this fraction of the push top speed (Skate 3's run-and-jump-on). */
	public static final float SPRINT_MOUNT_FRACTION = 0.65f;
	/** A walking jump-mount lands rolling at least this fraction of the push top speed. */
	public static final float MOVING_MOUNT_FRACTION = 0.35f;
	/** Slower than this (u/s) a jump-mount is a standing hop on: it gets no boost. */
	public static final float MOVING_SPEED = 40f;

	/**
	 * The speed a jump-mount rolls away at: the jump's horizontal speed, raised to a good share of the push top
	 * speed when moving ({@link #SPRINT_MOUNT_FRACTION} from a sprint, else {@link #MOVING_MOUNT_FRACTION}): the
	 * jump on is the push. Never more than the push top speed.
	 *
	 * @param sprinted sprinting as the jump took off
	 */
	public static float jumpMountSpeed(float horizontalSpeed, boolean sprinted, float maxPushSpeed)
	{
		float v = Math.max(0f, horizontalSpeed);
		if (v >= MOVING_SPEED)
		{
			v = Math.max(v, (sprinted ? SPRINT_MOUNT_FRACTION : MOVING_MOUNT_FRACTION) * maxPushSpeed);
		}
		return Math.min(v, maxPushSpeed);
	}

	/** The speed stepping onto the carried board rolls away at: the walker's own, up to the push top speed. */
	public static float stepOnSpeed(float horizontalSpeed, float maxPushSpeed)
	{
		return Math.max(0f, Math.min(horizontalSpeed, maxPushSpeed));
	}

	/**
	 * Whether a jump taking off now at (wx, wy) is one {@link #onLanded} will put on the board (sprinting with the
	 * board carried, or from within {@link #JUMP_MOUNT_RADIUS} of the dropped board): it gets the bigger mount hop.
	 */
	public boolean willJumpMount(boolean sprinting, float wx, float wy)
	{
		if (mode != SkateMode.ON_FOOT)
		{
			return false;
		}
		return board == BoardState.CARRIED ? sprinting : within(wx, wy, JUMP_MOUNT_RADIUS);
	}

	/**
	 * A bail knocked the skater off: on foot, with the board lying where it came to rest, without a mount or
	 * dismount blend (the knockdown drew the way there).
	 */
	public void knockedOff(float bx, float by, float bh, float heading)
	{
		mode = SkateMode.ON_FOOT;
		board = BoardState.DROPPED;
		boardX = bx;
		boardY = by;
		boardH = bh;
		boardHeading = heading;
		transition = BoardTransition.NONE;
		transitionAge = 0f;
	}

	/** A duel tantrum: on foot with a fresh board in hand (the old one is in pieces), without a blend. */
	public void freshBoardInHand()
	{
		mode = SkateMode.ON_FOOT;
		board = BoardState.CARRIED;
		transition = BoardTransition.NONE;
		transitionAge = 0f;
	}

	/** R during a knockdown: straight back on the board (blended from where the body lies). */
	public void remount()
	{
		mount();
	}

	/** The dropped board is within {@link #PICKUP_RADIUS} of (x, y). */
	public boolean inReach(float x, float y)
	{
		return board == BoardState.DROPPED && within(x, y, PICKUP_RADIUS);
	}

	private boolean within(float x, float y, float r)
	{
		return Math.hypot(x - boardX, y - boardY) <= r;
	}

	private void mount()
	{
		mode = SkateMode.ON_BOARD;
		board = BoardState.CARRIED;
		startTransition(BoardTransition.MOUNT);
	}

	private void startTransition(BoardTransition t)
	{
		transition = t;
		transitionAge = 0f;
	}

	public SkateMode getMode()
	{
		return mode;
	}

	public BoardState getBoard()
	{
		return board;
	}

	public float getBoardX()
	{
		return boardX;
	}

	public float getBoardY()
	{
		return boardY;
	}

	public float getBoardH()
	{
		return boardH;
	}

	public float getBoardHeading()
	{
		return boardHeading;
	}

	public BoardTransition getTransition()
	{
		return transition;
	}

	/** 0 at the start of the latest mount or dismount, 1 once it is over (and when there was none). */
	public float getTransitionProgress()
	{
		return transition == BoardTransition.NONE ? 1f : Math.min(1f, transitionAge / TRANSITION_SECONDS);
	}
}
