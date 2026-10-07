package com.gielinorskate.duel;

import com.gielinorskate.duel.DuelStateMachine.Phase;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** What the side panel's "Skate Duel" section shows. Immutable; made on the client thread, shown on the EDT. */
public final class DuelView
{
	/** A party member skating with duels on, on this world. */
	public static final class Member
	{
		public final long id;
		public final String name;

		public Member(long id, String name)
		{
			this.id = id;
			this.name = DuelLines.name(name);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Member && ((Member) o).id == id && ((Member) o).name.equals(name);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(id, name);
		}
	}

	/** "Allow duel challenges" is on. */
	public final boolean allowed;
	public final boolean skating;
	public final boolean inParty;
	public final List<Member> members;
	public final Phase phase;
	public final String opponent;
	public final int myHp;
	public final int oppHp;
	public final int wins;
	public final int losses;
	/** The latest result's chat line while it shows (phase OVER), else null. */
	public final String result;
	/** In a PvP area or instance: no duels there. */
	public final boolean blocked;
	/** "Share my skater with my party" is on (duels need it). */
	public final boolean sharing;

	public DuelView(boolean allowed, boolean skating, boolean inParty, List<Member> members, Phase phase,
		String opponent, int myHp, int oppHp, int wins, int losses, String result)
	{
		this(allowed, skating, inParty, members, phase, opponent, myHp, oppHp, wins, losses, result, false, true);
	}

	public DuelView(boolean allowed, boolean skating, boolean inParty, List<Member> members, Phase phase,
		String opponent, int myHp, int oppHp, int wins, int losses, String result, boolean blocked, boolean sharing)
	{
		this.blocked = blocked;
		this.sharing = sharing;
		this.allowed = allowed;
		this.skating = skating;
		this.inParty = inParty;
		this.members = Collections.unmodifiableList(members);
		this.phase = phase;
		this.opponent = DuelLines.name(opponent);
		this.myHp = myHp;
		this.oppHp = oppHp;
		this.wins = wins;
		this.losses = losses;
		this.result = result;
	}

	/**
	 * Challenge buttons work: duels on, party sharing on, skating, in a party, not in a PvP area or instance and no
	 * duel or challenge going.
	 */
	public boolean canChallenge()
	{
		return allowed && skating && inParty && !blocked && sharing && (phase == Phase.IDLE || phase == Phase.OVER);
	}

	/** The status line. */
	public String status()
	{
		if (!allowed)
		{
			return "Duels are off. Turn on \"Allow duel challenges\" in the plugin's settings (Play together section).";
		}
		if (!sharing)
		{
			return "Duels need \"Share my skater with my party\" on (Play together section of the plugin's settings).";
		}
		if (!inParty)
		{
			return "Join a RuneLite party to duel party members who are skating.";
		}
		switch (phase)
		{
			case CHALLENGING:
				return "Waiting for " + opponent + " to answer...";
			case CHALLENGED:
				return opponent + " challenges you to a Skate Duel!" + (skating ? "" : " Start skating to accept.");
			case COUNTDOWN:
				return "Get ready: Skate Duel against " + opponent + "!";
			case FIGHT:
				return "Duelling " + opponent + ": you " + myHp + " HP, them " + oppHp + " HP.";
			default:
				break;
		}
		if (result != null)
		{
			return result;
		}
		if (blocked)
		{
			return "No Skate Duels in PvP areas or instances.";
		}
		if (members.isEmpty())
		{
			return "No party members on this world are skating with duels on.";
		}
		return skating ? "Challenge a skater (bragging rights only: no stakes)." : "Start skating to challenge someone.";
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof DuelView))
		{
			return false;
		}
		DuelView v = (DuelView) o;
		return allowed == v.allowed && skating == v.skating && inParty == v.inParty && members.equals(v.members)
			&& phase == v.phase && opponent.equals(v.opponent) && myHp == v.myHp && oppHp == v.oppHp
			&& wins == v.wins && losses == v.losses && Objects.equals(result, v.result) && blocked == v.blocked
			&& sharing == v.sharing;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(allowed, skating, inParty, members, phase, opponent, myHp, oppHp, wins, losses, result,
			blocked, sharing);
	}
}
