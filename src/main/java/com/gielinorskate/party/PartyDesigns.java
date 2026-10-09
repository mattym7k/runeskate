package com.gielinorskate.party;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.DesignPart;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.*;
import lombok.RequiredArgsConstructor;

/**
* Party members' custom designs on this client: offers and chunks from party members go through the
* {@link DesignInbox}; a complete picture is decoded and baked at Normal detail in the background, then its colours
* are registered and the design kept (in memory only, {@link DesignCache} of {@link DesignShare#CACHE_SIZE}) on
* the client thread, and the ghosts are worked out again ({@code changed}). Nothing is ever written to disk. Every
* method runs on the client thread.
*/
@RequiredArgsConstructor
final class PartyDesigns
{
/** Where pictures are decoded and baked (RuneLite's executor), and where the results are applied. */
private final Executor background;
private final Executor clientThread;
/** Whether a member id is in the local party now. */
private final LongPredicate members;
/** A received picture's Normal-detail corner colours for its part, or null when it can't be baked. */
private final BiFunction<DesignPart, BufferedImage, int[]> baker;
/** Where a design's colours are drawn from (render.DesignColours in the plugin), and let go. */
private final BiConsumer<String, int[]> register;
private final Consumer<String> unregister;
/** Run (client thread) when a design became ready or was let go: the ghosts' looks change. */
private final Runnable changed;
private final DesignInbox inbox = new DesignInbox();
private final DesignCache<BoardDesign> cache = new DesignCache<>(DesignShare.CACHE_SIZE, this::letGo);
/** "member:hash" being decoded and baked. */
private final Set<String> baking = new HashSet<>();
/** Bumped by {@link #clear}: a bake finishing after it is thrown away. */
private int generation;
/** "Show party members' custom designs": off until the setting says otherwise. */
private boolean showOthers;

private void letGo(BoardDesign d)
{
unregister.accept(d.id);
}

/** The id a member's design is drawn under: never a shipped or local custom id. */
static String designId(long member, String hash)
{
return "PARTY_" + Long.toHexString(member) + "_" + hash;
}

void onOffer(long member, SkateDesignOffer offer, float now)
{
// only from party members; one already here is ignored: its chunks find no offer and are dropped
if (members.test(member) && (offer == null || !DesignShare.validHash(offer.hash)
|| !cache.contains(member, ref(offer.hash)) && !baking.contains(member + ":" + ref(offer.hash))))
inbox.offer(member, offer, now);
}

void onChunk(long member, SkateDesignChunk chunk, float now)
{
DesignInbox.Assembled a = members.test(member) ? inbox.chunk(member, chunk, now) : null;
if (a == null || !baking.add(member + ":" + a.hash))
return;
int gen = generation;
background.execute(() ->
{
int[] low = null;
try
{
// the bytes were checked against the 8 KB cap before this: only now does ImageIO see them
BufferedImage picture = SharedDesignImage.decode(a.png, a.width, a.height);
low = picture == null ? null : baker.apply(a.part, picture);
}
catch (RuntimeException e)
{
// not a PNG of its size, or it could not be baked: dropped
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
return;
baking.remove(a.member + ":" + a.hash);
if (low != null && members.test(a.member))
{
String id = designId(a.member, a.hash);
register.accept(id, low);
cache.put(a.member, a.hash, BoardDesign.custom(id, a.name.isEmpty() ? "Custom" : a.name, a.part, 0));
changed.run();
}
}

/** Member {@code member}'s complete design for {@code part} with this hash, or null (draw the default). */
BoardDesign resolve(long member, DesignPart part, String hash)
{
BoardDesign d = showOthers ? cache.get(member, hash) : null;
return d != null && d.part == part ? d : null;
}

/** "Show party members' custom designs". */
void setShowOthers(boolean show)
{
if (show != showOthers)
{
showOthers = show;
changed.run();
}
}

/** Drops pictures incomplete too long; call now and then. */
void expire(float now)
{
inbox.expire(now);
}

/** A member left the party: their designs go. */
void forgetMember(long member)
{
inbox.forget(member);
if (cache.forget(member))
changed.run();
}

/** The party was left or changed, or the plugin stops: every design goes, and bakes on their way are ignored. */
void clear()
{
generation++;
baking.clear();
inbox.clear();
if (cache.clear())
changed.run();
}

/** Complete designs kept. */
int size()
{
return cache.size();
}

private static String ref(String hash)
{
return hash.substring(0, DesignShare.MIN_HASH);
}
}
