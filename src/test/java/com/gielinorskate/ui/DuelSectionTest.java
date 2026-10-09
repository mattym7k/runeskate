package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.duel.DuelStateMachine.Phase;
import com.gielinorskate.duel.DuelView;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.Test;

public class DuelSectionTest
{
	private final List<String> asked = new ArrayList<>();
	private final DuelSection section = new DuelSection(new DuelSection.Actions()
	{
		@Override
		public void challenge(long memberId)
		{
			asked.add("challenge " + memberId);
		}

		@Override
		public void accept()
		{
			asked.add("accept");
		}

		@Override
		public void decline()
		{
			asked.add("decline");
		}

		@Override
		public void withdraw()
		{
			asked.add("withdraw");
		}
	});

	private static DuelView view(boolean skating, Phase phase, DuelView.Member... members)
	{
		return new DuelView(true, skating, true, Arrays.asList(members), phase, "Bob", 99, 99, 3, 1, null, false, true);
	}

	@Test
	public void skatingMembersGetChallengeButtons()
	{
		section.display(view(true, Phase.IDLE, new DuelView.Member(5L, "Bob"), new DuelView.Member(6L, "Ann")));
		assertEquals(2, challengeButtons().size());
		challengeButtons().get(1).doClick();
		assertEquals(Collections.singletonList("challenge 6"), asked);
		assertFalse(section.answer.isVisible());
	}

	@Test
	public void challengeButtonsNeedSkating()
	{
		section.display(view(false, Phase.IDLE, new DuelView.Member(5L, "Bob")));
		assertFalse(challengeButtons().get(0).isEnabled());
		section.display(view(true, Phase.IDLE, new DuelView.Member(5L, "Bob")));
		assertTrue(challengeButtons().get(0).isEnabled());
	}

	@Test
	public void aChallengeReceivedShowsAcceptAndDecline()
	{
		section.display(view(true, Phase.CHALLENGED, new DuelView.Member(5L, "Bob")));
		assertTrue(section.answer.isVisible());
		assertTrue(challengeButtons().isEmpty());
		section.accept.doClick();
		section.decline.doClick();
		assertEquals(Arrays.asList("accept", "decline"), asked);
		section.display(view(false, Phase.CHALLENGED));
		assertFalse("accepting needs skating", section.accept.isEnabled());
	}

	@Test
	public void duelsOffShowsNoButtons()
	{
		section.display(new DuelView(false, true, true, Collections.singletonList(new DuelView.Member(5L, "Bob")),
			Phase.IDLE, null, 99, 99, 0, 0, null, false, true));
		assertTrue(challengeButtons().isEmpty());
	}

	/** The challenge buttons showing. */
	private List<JButton> challengeButtons()
	{
		List<JButton> out = new ArrayList<>();
		for (Component row : section.list.getComponents())
		{
			for (Component c : ((JPanel) row).getComponents())
			{
				if (c instanceof JButton)
				{
					out.add((JButton) c);
				}
			}
		}
		return out;
	}
}
