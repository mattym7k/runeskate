package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.gielinorskate.tricks.Grabs.Aim;
import org.junit.Test;

public class GrabAimTest
{
	@Test
	public void smallMovesDoNotAim()
	{
		assertNull(GrabAim.classify(0, 0, false));
		assertNull(GrabAim.classify(GrabAim.AIM_PX - 1, 0, false));
		assertNull(GrabAim.classify(10, -12, false));
	}

	@Test
	public void upIsTheNoseAndDownTheTail()
	{
		assertEquals(Aim.NOSE, GrabAim.classify(5, -GrabAim.AIM_PX, false));
		assertEquals(Aim.TAIL, GrabAim.classify(-5, GrabAim.AIM_PX, false));
	}

	@Test
	public void rightIsTheToeSideForRegularStanceAndMirroringSwapsIt()
	{
		// regular stance: the rider faces the right of the screen from behind, so the toes are on the right
		assertEquals(Aim.TOE, GrabAim.classify(GrabAim.AIM_PX, 3, false));
		assertEquals(Aim.HEEL, GrabAim.classify(-GrabAim.AIM_PX, 3, false));
		assertEquals(Aim.HEEL, GrabAim.classify(GrabAim.AIM_PX, 3, true));
		assertEquals(Aim.TOE, GrabAim.classify(-GrabAim.AIM_PX, 3, true));
		// mirroring never touches up and down
		assertEquals(Aim.NOSE, GrabAim.classify(0, -30, true));
	}
}
