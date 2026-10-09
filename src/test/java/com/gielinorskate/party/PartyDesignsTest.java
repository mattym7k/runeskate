package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.DesignPart;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.client.party.messages.PartyMessage;
import org.junit.Test;

public class PartyDesignsTest
{
	private final ArrayDeque<Runnable> executor = new ArrayDeque<>();
	private final ArrayDeque<Runnable> clientThread = new ArrayDeque<>();
	private final Map<String, int[]> colours = new HashMap<>();
	private int bakes;

	private static DesignShare.Outgoing deck() throws Exception
	{
		BufferedImage img = new BufferedImage(35, 96, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 96; y++)
		{
			for (int x = 0; x < 35; x++)
			{
				img.setRGB(x, y, y < 48 ? 0xC02020 : 0x2020C0);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return DesignShare.outgoing("CUSTOM_0A1B2C3E", "Mine",
			new SharedDesignImage.Encoded(DesignPart.DECK, 35, 96, out.toByteArray()));
	}

	/** Runs the background work the way an executor does (a task's Error ends only that task), then the client's. */
	private void pump()
	{
		while (!executor.isEmpty())
		{
			try
			{
				executor.poll().run();
			}
			catch (Error e)
			{
				// the executor's thread swallows it
			}
		}
		while (!clientThread.isEmpty())
		{
			clientThread.poll().run();
		}
	}

	private static void feed(PartyDesigns designs, DesignShare.Outgoing d, float now)
	{
		for (PartyMessage m : d.messages())
		{
			if (m instanceof SkateDesignOffer)
			{
				designs.onOffer(7L, (SkateDesignOffer) m, now);
			}
			else
			{
				designs.onChunk(7L, (SkateDesignChunk) m, now);
			}
		}
	}

	@Test
	public void aBakeEndingInAnErrorDoesNotBlockTheDesignForever() throws Exception
	{
		PartyDesigns designs = new PartyDesigns(executor::add, clientThread::add, id -> true, (part, picture) ->
		{
			if (bakes++ == 0)
			{
				throw new OutOfMemoryError("test");
			}
			return new int[]{1, 2, 3};
		}, colours::put, colours::remove, () -> { });
		designs.setShowOthers(true);
		DesignShare.Outgoing d = deck();
		feed(designs, d, 0f);
		pump();
		assertEquals(1, bakes);
		assertEquals(0, designs.size());
		// sent again: taken and baked this time
		feed(designs, d, 5f);
		pump();
		assertEquals(2, bakes);
		assertEquals(1, designs.size());
		assertNotNull(designs.resolve(7L, DesignPart.DECK, d.hash));
	}
}
