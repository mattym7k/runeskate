package com.gielinorskate.progression;

import java.util.Objects;

/** The designs a board is drawn in: one grip, one deck and one wheels design. Pure, immutable. */
public final class BoardLook
{
	public final BoardDesign grip;
	public final BoardDesign deck;
	public final BoardDesign wheels;

	public BoardLook(BoardDesign grip, BoardDesign deck, BoardDesign wheels)
	{
		this.grip = Objects.requireNonNull(grip);
		this.deck = Objects.requireNonNull(deck);
		this.wheels = Objects.requireNonNull(wheels);
	}

	/** Every part's default design. */
	public static BoardLook defaults(BoardDesigns designs)
	{
		return new BoardLook(designs.defaultFor(DesignPart.GRIP), designs.defaultFor(DesignPart.DECK),
			designs.defaultFor(DesignPart.WHEELS));
	}

	public BoardDesign get(DesignPart part)
	{
		return part == DesignPart.GRIP ? grip : part == DesignPart.DECK ? deck : wheels;
	}

	/** This look with {@code d} on its part. */
	public BoardLook with(BoardDesign d)
	{
		return new BoardLook(d.part == DesignPart.GRIP ? d : grip, d.part == DesignPart.DECK ? d : deck,
			d.part == DesignPart.WHEELS ? d : wheels);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof BoardLook))
			return false;
		BoardLook l = (BoardLook) o;
		return same(grip, l.grip) && same(deck, l.deck) && same(wheels, l.wheels);
	}

	private static boolean same(BoardDesign a, BoardDesign b)
	{
		return a.id.equals(b.id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(grip.id, deck.id, wheels.id);
	}

	@Override
	public String toString()
	{
		return grip.id + "/" + deck.id + "/" + wheels.id;
	}
}
