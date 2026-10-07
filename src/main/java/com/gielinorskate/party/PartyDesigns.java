package com.gielinorskate.party;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.DesignPart;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * Party members' custom designs on this client: offers and chunks from party members go through the
 * {@link DesignInbox}; a complete picture is decoded and baked at Normal detail in the background, then its colours
 * are registered and the design kept (in memory only, {@link DesignCache} of {@link DesignShare#CACHE_SIZE}) on
 * the client thread, and the ghosts are worked out again ({@code changed}). Nothing is ever written to disk. Every
 * method runs on the client thread.
 */
@Slf4j
public final class PartyDesigns implements GhostHub.MemberDesigns
{
	/** Whether a member id is in the local party now. */
	public interface Members
	{
		boolean isMember(long memberId);
	}

	/** A received picture's Normal-detail corner colours for its part, or null when it can't be baked. */
	public interface Baker
	{
		int[] bake(DesignPart part, BufferedImage picture);
	}

	/** Where a design's colours are drawn from (render.DesignColours in the plugin). */
	public interface Colours
	{
		void register(String id, int[] low);

		void unregister(String id);
	}

	private final Executor background;
	private final Executor clientThread;
	private final Members members;
	private final Baker baker;
	private final Colours colours;
	private final Runnable changed;
	private final DesignInbox inbox = new DesignInbox();
	private final DesignCache<BoardDesign> cache;
	/** "member:hash" being decoded and baked. */
	private final Set<String> baking = new HashSet<>();
	/** Bumped by {@link #clear}: a bake finishing after it is thrown away. */
	private int generation;
	private boolean showOthers = true;

	/**
	 * @param background where pictures are decoded and baked (RuneLite's executor)
	 * @param clientThread where the results are applied
	 * @param changed run (client thread) when a design became ready or was let go: the ghosts' looks change
	 */
	public PartyDesigns(Executor background, Executor clientThread, Members members, Baker baker, Colours colours,
		Runnable changed)
	{
		this.background = background;
		this.clientThread = clientThread;
		this.members = members;
		this.baker = baker;
		this.colours = colours;
		this.changed = changed;
		this.cache = new DesignCache<>(DesignShare.CACHE_SIZE, d -> colours.unregister(d.id));
	}

	/** The id a member's design is drawn under: never a shipped or local custom id. */
	static String designId(long member, String hash)
	{
		return "PARTY_" + Long.toHexString(member) + "_" + hash;
	}

	public void onOffer(long member, SkateDesignOffer offer, float now)
	{
		if (!members.isMember(member))
		{
			log.debug("RuneSkate: design offer from {}, not a party member, dropped", member);
			return;
		}
		if (offer != null && DesignShare.validHash(offer.hash)
			&& (cache.contains(member, ref(offer.hash)) || baking.contains(member + ":" + ref(offer.hash))))
		{
			// already here: its chunks find no offer and are dropped
			return;
		}
		inbox.offer(member, offer, now);
	}

	public void onChunk(long member, SkateDesignChunk chunk, float now)
	{
		if (!members.isMember(member))
		{
			log.debug("RuneSkate: design chunk from {}, not a party member, dropped", member);
			return;
		}
		DesignInbox.Assembled a = inbox.chunk(member, chunk, now);
		if (a == null)
		{
			return;
		}
		String key = member + ":" + a.hash;
		if (!baking.add(key))
		{
			return;
		}
		int gen = generation;
		background.execute(() ->
		{
			int[] low = null;
			try
			{
				// the bytes were checked against the 8 KB cap before this: only now does ImageIO see them
				BufferedImage picture = SharedDesignImage.decode(a.png, a.width, a.height);
				if (picture == null)
				{
					log.debug("RuneSkate: design {} from {} is not a {}x{} PNG, dropped", a.hash, member, a.width,
						a.height);
				}
				else
				{
					low = baker.bake(a.part, picture);
				}
			}
			catch (RuntimeException e)
			{
				log.debug("RuneSkate: design {} from {} could not be baked", a.hash, member, e);
			}
			finally
			{
				// always, even after an Error: the design must not stay "baking" (its offers ignored) for good
				int[] colours = low;
				clientThread.execute(() -> finish(a, gen, colours));
			}
		});
	}

	private void finish(DesignInbox.Assembled a, int gen, int[] low)
	{
		if (gen != generation)
		{
			return;
		}
		baking.remove(a.member + ":" + a.hash);
		if (low == null || !members.isMember(a.member))
		{
			return;
		}
		String id = designId(a.member, a.hash);
		colours.register(id, low);
		cache.put(a.member, a.hash, BoardDesign.custom(id, a.name.isEmpty() ? "Custom" : a.name, a.part, 0));
		changed.run();
	}

	@Override
	public BoardDesign resolve(long member, DesignPart part, String hash)
	{
		if (!showOthers)
		{
			return null;
		}
		BoardDesign d = cache.get(member, hash);
		return d != null && d.part == part ? d : null;
	}

	/** "Show party members' custom designs". */
	public void setShowOthers(boolean show)
	{
		if (show != showOthers)
		{
			showOthers = show;
			changed.run();
		}
	}

	/** Drops pictures incomplete too long; call now and then. */
	public void expire(float now)
	{
		inbox.expire(now);
	}

	/** A member left the party: their designs go. */
	public void forgetMember(long member)
	{
		inbox.forget(member);
		int before = cache.size();
		cache.forget(member);
		if (cache.size() != before)
		{
			changed.run();
		}
	}

	/** The party was left or changed, or the plugin stops: every design goes, and bakes on their way are ignored. */
	public void clear()
	{
		generation++;
		baking.clear();
		inbox.clear();
		int before = cache.size();
		cache.clear();
		if (before > 0)
		{
			changed.run();
		}
	}

	/** Complete designs kept. */
	public int size()
	{
		return cache.size();
	}

	private static String ref(String hash)
	{
		return hash.substring(0, DesignShare.MIN_HASH);
	}
}
