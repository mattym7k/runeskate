package com.gielinorskate.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import com.gielinorskate.render.DesignColours;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

public class ThumbRenderTest
{
	/** The runtime thumbnail of a shipped design matches the one tools/design_thumbs.py drew for it. */
	@Test
	public void drawsLikeTheBakedThumbnails() throws Exception
	{
		BakedBoardGeometry.Mesh[] high = BakedBoardGeometry.sharedBoard(true);
		for (String id : new String[]{"GRIP_RUNE", "DRAGON", "WHEELS_GUTHIX"})
		{
			DesignPart part = id.startsWith("GRIP_") ? DesignPart.GRIP : id.startsWith("WHEELS_") ? DesignPart.WHEELS
				: DesignPart.DECK;
			BakedBoardGeometry.Mesh mesh = high[part.index];
			BufferedImage got = ThumbRender.thumbnail(part, mesh, DesignColours.colours(id, true, mesh));
			BufferedImage want;
			try (InputStream in = getClass().getResourceAsStream("/com/gielinorskate/designs/thumbs/" + id + ".png"))
			{
				want = ImageIO.read(in);
			}
			assertEquals(want.getWidth(), got.getWidth());
			assertEquals(want.getHeight(), got.getHeight());
			int off = 0;
			int pixels = want.getWidth() * want.getHeight();
			for (int y = 0; y < want.getHeight(); y++)
			{
				for (int x = 0; x < want.getWidth(); x++)
				{
					int a = want.getRGB(x, y);
					int b = got.getRGB(x, y);
					int d = Math.abs((a >>> 24) - (b >>> 24));
					if ((a >>> 24) > 0 && (b >>> 24) > 0)
					{
						for (int s = 0; s <= 16; s += 8)
						{
							d = Math.max(d, Math.abs((a >> s & 255) - (b >> s & 255)));
						}
					}
					if (d > 3)
					{
						off++;
					}
				}
			}
			assertTrue(id + ": " + off + " pixels differ", off <= pixels / 100);
		}
	}
}
