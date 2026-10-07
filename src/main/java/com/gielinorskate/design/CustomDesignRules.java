package com.gielinorskate.design;

import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.DesignPart;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/** The limits on players' own designs: how many, their ids and names, and which images are taken. Pure. */
public final class CustomDesignRules
{
	/** Most custom designs kept (all parts together). */
	public static final int MAX_DESIGNS = 50;
	public static final int MAX_NAME = 24;
	/** Largest image accepted: on either side, and its file. */
	public static final int MAX_SIDE = 4096;
	public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
	/** The copy kept for editing again (and baked from) is at most this long on its long side. */
	public static final int STORED_MAX_SIDE = 1024;
	/** Image files the chooser offers. */
	public static final String[] EXTENSIONS = {"png", "jpg", "jpeg", "gif", "bmp"};

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _'\\-]+");
	private static final Pattern ID = Pattern.compile(Pattern.quote(BoardDesigns.CUSTOM_PREFIX) + "[0-9A-F]{8}");

	private CustomDesignRules()
	{
	}

	/** A new id: CUSTOM_ and 8 random hex digits. */
	public static String newId(Random random)
	{
		return BoardDesigns.CUSTOM_PREFIX + String.format(Locale.ROOT, "%08X", random.nextInt());
	}

	public static boolean isId(String id)
	{
		return id != null && ID.matcher(id).matches();
	}

	/** The name the editor starts with: "My grip", "My deck", "My wheels". */
	public static String defaultName(DesignPart part)
	{
		return "My " + part.key;
	}

	/** Why {@code name} can't be a design's name, or null when it can (it is trimmed first). */
	public static String nameProblem(String name)
	{
		String n = name == null ? "" : name.trim();
		if (n.isEmpty())
		{
			return "Give the design a name.";
		}
		if (n.length() > MAX_NAME)
		{
			return "Names are at most " + MAX_NAME + " characters.";
		}
		if (!NAME.matcher(n).matches())
		{
			return "Use letters, digits, spaces and - _ ' only.";
		}
		return null;
	}

	/** Why a file of {@code bytes} can't be used, or null when its size is fine. */
	public static String fileProblem(long bytes)
	{
		if (bytes > MAX_FILE_BYTES)
		{
			return "That image file is over " + MAX_FILE_BYTES / (1024 * 1024) + " MB. Pick a smaller one.";
		}
		if (bytes <= 0)
		{
			return "That file is empty.";
		}
		return null;
	}

	/** Why an image of this size can't be used, or null when it can. */
	public static String imageProblem(int width, int height)
	{
		if (width > MAX_SIDE || height > MAX_SIDE)
		{
			return "That image is " + width + " x " + height + " pixels. Images can be at most " + MAX_SIDE
				+ " pixels on each side.";
		}
		if (width < 1 || height < 1)
		{
			return "That image is empty.";
		}
		return null;
	}

	/** {width, height} of an image of this size once downscaled to fit {@link #STORED_MAX_SIDE} (never enlarged). */
	public static int[] storedSize(int width, int height)
	{
		int longSide = Math.max(width, height);
		if (longSide <= STORED_MAX_SIDE)
		{
			return new int[]{width, height};
		}
		double f = STORED_MAX_SIDE / (double) longSide;
		return new int[]{Math.max(1, (int) Math.round(width * f)), Math.max(1, (int) Math.round(height * f))};
	}

	/** True if a file name has one of the {@link #EXTENSIONS}. */
	public static boolean isImageFile(String fileName)
	{
		int dot = fileName.lastIndexOf('.');
		if (dot < 0)
		{
			return false;
		}
		String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
		for (String e : EXTENSIONS)
		{
			if (e.equals(ext))
			{
				return true;
			}
		}
		return false;
	}
}
