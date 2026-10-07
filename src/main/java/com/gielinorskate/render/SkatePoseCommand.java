package com.gielinorskate.render;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Pure parser/handler for the {@code ::skatepose} dev command. No client dependency, so bad input
 * (unknown slot, non-numeric id, wrong argument count) returns a usage message instead of throwing.
 */
public final class SkatePoseCommand
{
	private static final List<String> SLOTS = Arrays.asList("stance", "crouch", "push", "jump", "bail", "grind", "manual",
		"knockdown", "getup");

	static final String USAGE =
		"Usage: ::skatepose [stance|crouch|push|jump|bail|grind|manual|knockdown|getup] <animationId>";

	private SkatePoseCommand()
	{
	}

	/** Applies {@code args} to {@code poses} (if valid) and returns the chat message to show. */
	public static String apply(StancePoses poses, String[] args)
	{
		return apply(poses, args, id -> true);
	}

	/**
	 * As {@link #apply(StancePoses, String[])}, also rejecting any ID other than -1 (no animation /
	 * walk animation) that {@code loadable} says the client cannot load.
	 */
	public static String apply(StancePoses poses, String[] args, IntPredicate loadable)
	{
		if (args.length == 0)
		{
			return list(poses);
		}
		if (args.length != 2)
		{
			return USAGE;
		}
		String slot = args[0].toLowerCase(Locale.ROOT);
		int id;
		try
		{
			id = Integer.parseInt(args[1]);
		}
		catch (NumberFormatException e)
		{
			return USAGE;
		}
		if (id < -1)
		{
			return USAGE;
		}
		if (!SLOTS.contains(slot))
		{
			return USAGE;
		}
		if (id != -1 && !loadable.test(id))
		{
			return "skatepose: animation " + id + " does not exist";
		}
		switch (slot)
		{
			case "stance":
				poses.stance = id;
				break;
			case "crouch":
				poses.crouch = id;
				break;
			case "push":
				poses.push = id;
				break;
			case "jump":
				poses.jump = id;
				break;
			case "bail":
				poses.bail = id;
				break;
			case "grind":
				poses.grind = id;
				break;
			case "manual":
				poses.manual = id;
				break;
			case "knockdown":
				poses.knockdown = id;
				break;
			case "getup":
				poses.getUp = id;
				break;
			default:
				return USAGE;
		}
		return "skatepose: " + slot + " set to " + id;
	}

	private static String list(StancePoses poses)
	{
		return "skatepose: stance=" + poses.stance + " crouch=" + poses.crouch + " push=" + poses.push
			+ " jump=" + poses.jump + " bail=" + poses.bail + " grind=" + poses.grind + " manual=" + poses.manual + " knockdown=" + poses.knockdown
			+ " getup=" + poses.getUp;
	}
}
