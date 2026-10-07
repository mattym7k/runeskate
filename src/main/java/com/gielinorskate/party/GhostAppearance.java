package com.gielinorskate.party;

import java.util.Arrays;
import net.runelite.api.gameval.AnimationID;

/**
 * When a ghost's copy of the member's model ({@code MeshSnapshot}) is taken and still fits: the appearance key
 * (gear, colours, gender, an NPC transformation and the idle pose; another key means a new snapshot) and whether
 * the member's real character stands in its plain idle now (read only: nothing about it is changed). Pure.
 */
final class GhostAppearance
{
	private GhostAppearance()
	{
	}

	/** A key for the member's appearance; equal appearances give equal keys. */
	static int key(int[] equipment, int[] colours, int gender, int npcTransform, int idlePose)
	{
		int h = Arrays.hashCode(equipment);
		h = 31 * h + Arrays.hashCode(colours);
		h = 31 * h + gender;
		h = 31 * h + npcTransform;
		return 31 * h + idlePose;
	}

	/**
	 * The real character stands in its plain idle (no action animation, its idle pose playing, not an NPC), so the
	 * frame it is drawn in now is a standing body to copy.
	 */
	static boolean mayTake(int animation, int poseAnimation, int idlePose, int npcTransform)
	{
		return animation == -1 && poseAnimation == idlePose && npcTransform == -1;
	}

	/**
	 * An OSRS animation goes on top of a snapshot taken in this idle pose: only the game's own standing frame, which
	 * is near the model's base pose (a weapon's stance would add its own arm swing to every animation).
	 */
	static boolean layerable(int idlePose)
	{
		return idlePose == AnimationID.HUMAN_READY;
	}
}
