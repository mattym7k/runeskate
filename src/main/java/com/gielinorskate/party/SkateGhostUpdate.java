package com.gielinorskate.party;

import java.util.Objects;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * One skater state for the sender's RuneLite party, Gson-serialised by RuneLite's party client. Only skate-pose
 * data plus world and plane: no names, account or inventory data (the member is identified by the party itself).
 * Short field names keep the JSON small; see {@link GhostCodec} for the units.
 */
public class SkateGhostUpdate extends PartyMemberMessage
{
	/** World number. */
	int w;
	/** Plane. */
	int p;
	/** Absolute position: world tile * 128 + sub-tile (local units). */
	int x;
	int y;
	/** Up-positive height, local units. */
	int h;
	/** Heading * 1000 (radians, 0 = north, clockwise). */
	int hd;
	/** Heading turn rate * 1000 (rad/s, + = clockwise), so receivers carve smoothly between updates. */
	int tw;
	/** Velocity, local units per second (vh up-positive). */
	int vx;
	int vy;
	int vh;
	/**
	 * SkaterState name. Enums go by name, not ordinal, so members on plugin versions with more (or reordered)
	 * states or tricks never see the wrong one: an unknown name is ignored on decode.
	 */
	String st;
	/** Held trick (grab, manual, grind) name, or null (left out of the JSON). */
	String hold;
	/** Front / back flip angle of the whole skater * 1000 (radians, + = frontflip), and its rate * 1000. */
	int bf;
	int bw;
	/** Bitmask of GhostCodec.EV_* events since the previous update, 0 when none. */
	int ev;
	/** Flip in progress (or, on a TRICK event with no flip, the event's trick) name, or null. */
	String tr;
	/** Flip time into tr's nominal duration * 1000. */
	int ft;
	/** Increasing per sender, so stale or out-of-order updates are dropped. */
	int seq;
	/**
	 * The sender's deck design (progression.BoardDesign wire name: the id without DECK_; the old ladder's decks
	 * keep their old names, so older versions still draw them), or null (left out) for the default deck. In every
	 * update. Receivers check it against their own designs: an unknown name draws the default deck. A shared custom
	 * deck goes as "C:" and its picture's 8-hex hash (GhostCodec.CUSTOM_REF), drawn once the picture has arrived.
	 */
	String dk;
	/**
	 * The sender's grip and wheels designs as "GRIP.WHEELS" wire names (or "C:" references, as for dk), or null
	 * (left out). Not in every update:
	 * it rides along when the designs change and every few seconds after (GhostHub.LOOK_REFRESH_SECONDS), never
	 * with a trick name (tr) or a dropped board's place (bx...), so the biggest updates stay as small as before. Receivers keep the last ones they got
	 * (the defaults before any, and from older versions, which never send it). Not part of {@link #sameState}.
	 */
	String gw;
	/**
	 * Off the board: the board's state (BoardState name, CARRIED or DROPPED), and x / y / h / hd then describe the
	 * walker. Null (left out of the JSON) on the board: older plugin versions never send it and ignore it, so to
	 * them a walker is a skater standing on its board.
	 */
	String ob;
	/** A DROPPED board: absolute position, up-positive height and nose heading * 1000; null (left out) otherwise. */
	Integer bx;
	Integer by;
	Integer bh;
	Integer bd;

	/**
	 * Pop charge in ninths (1..9) while crouching to pop on the board, else null (left out): the ghost crouches with
	 * it. Older versions never send it and ignore it.
	 */
	Integer cr;
	/**
	 * Knocked off the board: the stage (1 tumbling, 2 lying, 3 getting up) plus 10 times the lying angle's code
	 * (GhostCodec.knockWire), with x / y / h / hd the body (its lowest point and facing), vx / vy its flight and ob /
	 * bx... the board DROPPED where it is; null (left out) otherwise. Older versions ignore it and draw a walker by
	 * its dropped board.
	 */
	Integer kd;

	/**
	 * The Skate Duel version the sender takes challenges with, or null (left out) when it takes none: older
	 * plugin versions never send it and ignore it, so they are never offered a duel.
	 */
	Integer dv;

	/**
	 * The timeline (GhostTrajectory): the sender's send time, when each event in ev happened, and its last few
	 * positions, as compact text; null (left out) when there is no room for it. Older versions never send it and
	 * ignore it; receivers then dead-reckon as before. Not part of {@link #sameState}.
	 */
	String tj;

	/** True when everything but seq and ev is equal: nothing changed worth a snapshot. */
	boolean sameState(SkateGhostUpdate o)
	{
		return o != null && w == o.w && p == o.p && x == o.x && y == o.y && h == o.h && hd == o.hd && tw == o.tw && vx == o.vx
			&& vy == o.vy && vh == o.vh && Objects.equals(st, o.st) && Objects.equals(hold, o.hold)
			&& Objects.equals(tr, o.tr) && ft == o.ft && bf == o.bf && bw == o.bw && Objects.equals(dk, o.dk)
			&& Objects.equals(ob, o.ob) && Objects.equals(bx, o.bx) && Objects.equals(by, o.by)
			&& Objects.equals(bh, o.bh) && Objects.equals(bd, o.bd) && Objects.equals(dv, o.dv)
			&& Objects.equals(cr, o.cr) && Objects.equals(kd, o.kd);
	}
}
