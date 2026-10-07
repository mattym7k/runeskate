package com.gielinorskate.duel;

import com.gielinorskate.duel.DuelStateMachine.Cause;
import com.gielinorskate.duel.DuelStateMachine.Outcome;
import net.runelite.client.util.Text;

/** The Skate Duel's chat lines and banner text. Pure. */
public final class DuelLines
{
	public static final String WIN_PREFIX = "You have defeated ";
	/** The game's "Oh dear, you are dead!", for skaters. */
	public static final String LOSS = "Oh dear, you've been out-skated!";
	public static final String DRAW = "Double KO! It's a draw.";

	private DuelLines()
	{
	}

	/** A display name, or a stand-in when the party has none. */
	public static String name(String name)
	{
		return name == null || name.isEmpty() ? "your opponent" : name;
	}

	/**
	 * A display name for a chat line (or the stand-in), with its tags escaped: a name holding {@code <col>} or
	 * {@code <img>} shows as text in the chatbox instead of being drawn.
	 */
	public static String chatName(String name)
	{
		return Text.escapeJagex(name(name));
	}

	public static String challenged(String from)
	{
		return name(from) + " challenges you to a Skate Duel! Accept or decline in the RuneSkate side panel within "
			+ Math.round(DuelStateMachine.CHALLENGE_EXPIRY) + " seconds.";
	}

	public static String challengeSent(String to)
	{
		return "You challenge " + name(to) + " to a Skate Duel.";
	}

	public static String declined(String by)
	{
		return name(by) + " declined your Skate Duel.";
	}

	public static String expired(String other, boolean mine)
	{
		return mine ? "Your Skate Duel challenge to " + name(other) + " expired."
			: "The Skate Duel challenge from " + name(other) + " expired.";
	}

	public static String countdown(String opponent)
	{
		return "Skate Duel against " + name(opponent) + ": land combos to hit, bails cost you "
			+ DuelStateMachine.BAIL_DAMAGE + " HP. Get ready!";
	}

	/** The big banner line at the end. */
	public static String banner(Outcome outcome, String opponent)
	{
		switch (outcome)
		{
			case WIN:
				return WIN_PREFIX + name(opponent) + "!";
			case LOSS:
				return LOSS;
			case DRAW:
				return DRAW;
			default:
				return "Skate Duel called off.";
		}
	}

	/** The smaller line under the banner (why), or null when the banner says it all. */
	public static String reason(Outcome outcome, Cause cause, String opponent)
	{
		switch (cause)
		{
			case STOPPED_SKATING:
				return "You forfeit: you stopped skating for too long.";
			case LEFT:
				return outcome == Outcome.CANCELLED ? "You left the party." : "You forfeit: you left the party.";
			case BLOCKED:
				return outcome == Outcome.CANCELLED ? "No duels in PvP areas or instances."
					: "You forfeit: no duels in PvP areas or instances.";
			case HOPPED:
				return outcome == Outcome.CANCELLED ? "Duels are on one world, and you hopped."
					: "You forfeit: duels are on one world, and you hopped.";
			case DUELS_OFF:
				return outcome == Outcome.CANCELLED ? "You turned duels off." : "You forfeit: you turned duels off.";
			case SHARING_OFF:
				return outcome == Outcome.CANCELLED ? "Duels need \"Share my skater with my party\" on."
					: "You forfeit: duels need \"Share my skater with my party\" on.";
			case TIMED_OUT:
				return "You forfeit: " + name(opponent) + " stopped hearing from you.";
			case SHUTDOWN:
				return outcome == Outcome.CANCELLED ? null : "You forfeit.";
			case BOTH_QUIT:
				return "You both quit at once: it counts for nobody.";
			case OPPONENT_FORFEIT:
				return name(opponent) + " forfeits.";
			case OPPONENT_SILENT:
				return name(opponent) + " stopped responding.";
			case OPPONENT_LEFT:
				return name(opponent) + " left the party.";
			case CANCELLED:
				return "The challenge was taken back or had expired.";
			default:
				return null;
		}
	}

	/** The chat line for a finished (or called-off) duel. */
	public static String over(Outcome outcome, Cause cause, String opponent)
	{
		String why = reason(outcome, cause, opponent);
		String head = outcome == Outcome.CANCELLED ? "The Skate Duel with " + name(opponent) + " was called off."
			: banner(outcome, opponent);
		return why == null ? head : head + " " + why;
	}

	/** The panel's record line. */
	public static String record(int wins, int losses)
	{
		return "Record: " + wins + " W / " + losses + " L";
	}
}
