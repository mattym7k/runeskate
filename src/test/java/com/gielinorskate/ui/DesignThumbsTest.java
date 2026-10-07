package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
import java.util.Collections;
import org.junit.Test;

public class DesignThumbsTest
{
	@Test
	public void deletedCustomDesignsThumbnailsGo()
	{
		DesignThumbs thumbs = new DesignThumbs();
		BufferedImage a = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		BufferedImage b = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		thumbs.put("CUSTOM_0000000A", a);
		thumbs.put("CUSTOM_0000000B", b);
		thumbs.locked("CUSTOM_0000000A");
		thumbs.get("GRIP_RUNE");
		assertEquals(4, thumbs.size());
		// only B is left: A's thumbnails go, the shipped ones stay
		thumbs.retainCustom(Collections.singleton("CUSTOM_0000000B"));
		assertEquals(2, thumbs.size());
		assertSame(b, thumbs.get("CUSTOM_0000000B"));
	}
}
