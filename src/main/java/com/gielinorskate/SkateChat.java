package com.gielinorskate;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.*;

/**
 * Every RuneSkate chat line goes out through here: tagged "[RuneSkate]" in the highlight colour, so no line can be
 * mistaken for a real game message.
 */
@Singleton
public class SkateChat
{
	public static final String PREFIX = "[RuneSkate] ";

	private final ChatMessageManager chatMessageManager;

	@Inject
	SkateChat(ChatMessageManager chatMessageManager)
	{
		this.chatMessageManager = chatMessageManager;
	}

	/** Queues one branded line; null or empty lines are dropped. Safe from any thread. */
	public void send(String line)
	{
		if (line == null || line.isEmpty())
			return;
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(format(line))
			.build());
	}

	/** The line as sent: the highlighted tag, then the text in the normal colour. */
	static String format(String line)
	{
		return new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(PREFIX)
			.append(ChatColorType.NORMAL)
			.append(line)
			.build();
	}
}
