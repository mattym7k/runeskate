package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;

/**
 * The board under a riding skater, eased frame to frame: its trick roll and pitch, the grab's tweak and lift, the
 * slide's drop, and where board and body go. Shared by the local skater and party ghosts. Client thread.
 */
public final class RidingBoard
{
	/** The board's roll and pitch as drawn (NaN: rebuilt from scratch next frame). */
	public float roll = Float.NaN;
	public float pitch = Float.NaN;
	/** This frame's deck lift (a pitched board raises the middle of the deck) and grab lift. */
	public int deckLift;
	public int lift;
	private float holdPitch;
	/** A grab's board pitch and roll tweak (GrabPose), snapped in and out (PoseSmoothing.GRAB_TAU). */
	private float grabPitch;
	private float grabRoll;
	private float slideDrop;

	/** Back to a board at rest, rebuilt from scratch next frame. */
	public void reset()
	{
		offBoard();
		holdPitch = 0f;
		grabPitch = 0f;
		grabRoll = 0f;
		slideDrop = 0f;
	}

	/** Off the board: back on it, its trick pose is rebuilt from scratch. */
	public void offBoard()
	{
		roll = Float.NaN;
		pitch = Float.NaN;
	}

	/**
	 * One frame riding {@code p} at local ({@code x}, {@code y}): the roll is {@code baseRoll} plus the grab's tweak
	 * plus {@code wobble} (half a turn when bailed); the pitch is {@code popPitch} plus the hold pitch (easing to
	 * {@code holdTarget}), the grab's tweak (from {@code grab}) and {@code extraPitch}. A change under 0.01 (the gap
	 * between deck and soles) keeps the last pose. The board and body placements go to {@code board} and
	 * {@code body}, the board lowered by the slide's drop plus {@code dip}; {@code pose} is told the board as drawn
	 * (a grabbing hand goes to it) and its grab lift read from it. Returns the feet's z (RuneLite, down-negative).
	 */
	public int update(RenderPose p, int x, int y, float baseRoll, float wobble, float popPitch, float holdTarget,
		float extraPitch, Trick grab, float dip, BodyPose pose, float dt, Placement board, Placement body)
	{
		boolean bailed = p.state == SkaterState.BAILED;
		holdPitch = PoseSmoothing.approach(holdPitch, holdTarget, dt, PoseSmoothing.POSE_TAU);
		grabPitch = PoseSmoothing.approach(grabPitch, GrabPose.boardPitch(grab), dt, PoseSmoothing.GRAB_TAU);
		grabRoll = PoseSmoothing.approach(grabRoll, GrabPose.boardRoll(grab), dt, PoseSmoothing.GRAB_TAU);
		float r = bailed ? Angles.PI : baseRoll + grabRoll + wobble;
		float pt = popPitch + holdPitch + grabPitch + extraPitch;
		if (!(Math.abs(r - roll) <= 0.01f && Math.abs(pt - pitch) <= 0.01f))
		{
			roll = r;
			pitch = pt;
		}
		// slides: the deck, not the wheels, sits on the rail (RuneLite z grows downward)
		slideDrop = PoseSmoothing.approach(slideDrop, BoardPlacement.slideDrop(p.state, p.hold), dt,
			PoseSmoothing.POSE_TAU);
		int lower = Math.round(slideDrop + dip);
		// a pitched board turns about its contact truck, which raises the middle of the deck under the feet
		deckLift = Math.round(BoardPlacement.deckLift(pitch));
		// a grab pulls the board up toward the hips with the knees (the body stays put, the feet fold up to it)
		lift = BoardPlacement.grabLift(pose.feetLift, bailed);
		int z = -Math.round(p.h);
		int top = (int) BoardGeometry.BOARD_TOP;
		// a front or back flip turns the board with the skater about the skater's centre of mass (JAU per radian:
		// a relative turn, no south offset)
		int yawOffsetJau = Math.round(p.boardYaw * (1024f / Angles.PI));
		board.set(x, y, bailed ? z - top : z + lower - lift, (Angles.toJau(p.heading) + yawOffsetJau) & 2047, roll,
			pitch);
		// regular stance: the body faces 90 degrees clockwise from the board's nose; a bail keeps the body where the
		// skater is (the fall animation moves it)
		int feetZ = bailed ? z : z - top - BoardPlacement.FOOT_CLEARANCE - deckLift + lower;
		body.set(x, y, feetZ, Angles.toJau(p.heading + Angles.PI / 2), 0f, 0f);
		pose.boardRoll = roll;
		pose.boardPitch = pitch;
		pose.deckLift = deckLift;
		pose.boardLift = lift;
		return feetZ;
	}
}
