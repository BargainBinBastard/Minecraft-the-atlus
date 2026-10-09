package io.github.bargainbinbastard.altus.lore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.bargainbinbastard.altus.dream.AltusDimension;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Lore;
import io.github.bargainbinbastard.altus.history.Rites;
import io.github.bargainbinbastard.altus.history.Sites;
import io.github.bargainbinbastard.altus.history.Text;
import io.github.bargainbinbastard.altus.net.RiteListPayload;
import io.github.bargainbinbastard.altus.net.RiteView;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Rites: every entry written in a Tome from a memory can be performed near an altar, with one
 * offering for each god it names. Its effect depends on how deep the entry's lore is. A rite built
 * on a falsehood misfires.
 */
public final class RiteService {
    private RiteService() {}

    public static final int ALTAR_RANGE = 6;

    static long day(ServerPlayer sp) {
        return sp.server.overworld().getDayTime() / 24000L;
    }

    /** One offering per named god that will accept one: the like ids, in order (repeats allowed). */
    public static List<String> offerings(History h, String testimonyId) {
        List<String> out = new ArrayList<>();
        for (int g : Rites.namedGods(h, testimonyId)) {
            String like = Rites.offeringLike(h.god(g));
            if (like != null) out.add(like);
        }
        return out;
    }

    public static List<RiteView> views(ServerPlayer sp, ItemStack tome) {
        History h = WorldHistory.get(sp.server);
        Sites sites = WorldHistory.sites(sp.server);
        Devotion d = Favor.of(sp);
        long today = day(sp);
        List<RiteView> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TomeRecord r : TomeService.contents(tome).records()) {
            if (!seen.add(r.testimonyId())) continue;
            Lore.Testimony t = Lore.render(h, sites, r.testimonyId());
            Rites.Effect e = Rites.effectOf(h, sites, r.testimonyId());
            if (t == null || e == null) continue;
            List<String> names = new ArrayList<>();
            for (String like : offerings(h, r.testimonyId())) names.add(LikeMatch.name(like));
            boolean used = d.riteDay.getOrDefault(r.testimonyId(), -1L) == today;
            out.add(new RiteView(r.entryId(), t.title, e.title + ": " + e.description,
                    names.isEmpty() ? "nothing" : String.join(", ", names), !used, used ? "performed today" : ""));
        }
        return out;
    }

    public static void sync(ServerPlayer sp, int slot) {
        ItemStack tome = TomeService.tomeAt(sp, slot);
        if (tome == null || sp.connection == null || !sp.connection.hasChannel(RiteListPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(sp, new RiteListPayload(slot, views(sp, tome)));
    }

    static BlockPos altarNear(ServerPlayer sp) {
        BlockPos c = sp.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ALTAR_RANGE, -3, -ALTAR_RANGE), c.offset(ALTAR_RANGE, 3, ALTAR_RANGE)))
            if (sp.level().getBlockState(p).is(AltusRegistry.ALTAR.get())) return p.immutable();
        return null;
    }

    /** Performs the rite of an entry. Returns true if the rite happened (even if it misfired). */
    public static boolean perform(ServerPlayer sp, int slot, String entryId) {
        ItemStack tome = TomeService.tomeAt(sp, slot);
        if (tome == null) return false;
        TomeRecord rec = null;
        for (TomeRecord r : TomeService.contents(tome).records()) if (r.entryId().equals(entryId)) rec = r;
        if (rec == null) return false;
        String tid = rec.testimonyId();
        History h = WorldHistory.get(sp.server);
        Sites sites = WorldHistory.sites(sp.server);
        Rites.Effect effect = Rites.effectOf(h, sites, tid);
        if (effect == null) return false;
        if (AltusDimension.isAltus(sp.level())) {
            say(sp, "Rites belong to the waking world.");
            return false;
        }
        BlockPos altar = altarNear(sp);
        if (altar == null) {
            say(sp, "A rite must be performed at an altar.");
            return false;
        }
        Devotion d = Favor.of(sp);
        long today = day(sp);
        if (d.riteDay.getOrDefault(tid, -1L) == today) {
            say(sp, "You have already performed this rite today.");
            return false;
        }
        List<String> needed = offerings(h, tid);
        Map<Integer, Integer> take = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String like : needed) {
            boolean found = false;
            for (int i = 0; i < sp.getInventory().items.size() && !found; i++) {
                ItemStack st = sp.getInventory().items.get(i);
                if (like.equals(LikeMatch.of(st)) && st.getCount() > take.getOrDefault(i, 0)) {
                    take.merge(i, 1, Integer::sum);
                    found = true;
                }
            }
            if (!found) missing.add(LikeMatch.name(like));
        }
        if (!missing.isEmpty()) {
            say(sp, "The rite needs an offering of " + String.join(", ", missing) + ".");
            return false;
        }
        take.forEach((i, n) -> sp.getInventory().items.get(i).shrink(n));
        d.riteDay.put(tid, today);
        ServerLevel level = sp.serverLevel();
        List<Integer> named = Rites.namedGods(h, tid);

        if (Rites.isFalse(h, tid)) {
            for (int g : named) Favor.add(sp, g, -8);
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, altar.getX() + 0.5, altar.getY() + 1.2, altar.getZ() + 0.5, 30, 0.3, 0.4, 0.3, 0.02);
            level.playSound(null, altar, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.6f);
            tell(sp, "The rite curdles. Something in what you wrote is false, and the gods it names are displeased.", ChatFormatting.DARK_RED);
            sync(sp, slot);
            return true;
        }

        int first = named.isEmpty() ? -1 : named.get(0);
        String result = switch (effect) {
            case QUICKEN_FIELDS -> {
                int n = 0;
                BlockPos c = sp.blockPosition();
                for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -3, -8), c.offset(8, 3, 8))) {
                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() instanceof CropBlock crop && !crop.isMaxAge(st)) {
                        level.setBlock(p, crop.getStateForAge(crop.getMaxAge()), 2);
                        n++;
                    }
                }
                yield n == 0 ? "The rite finds nothing growing nearby to quicken." : n + " crops ripen at once.";
            }
            case MEND_TOOLS -> {
                int n = 0;
                List<ItemStack> all = new ArrayList<>(sp.getInventory().items);
                all.addAll(sp.getInventory().armor);
                all.addAll(sp.getInventory().offhand);
                for (ItemStack st : all)
                    if (st.isDamageableItem() && st.isDamaged()) {
                        st.setDamageValue(Math.max(0, st.getDamageValue() - st.getMaxDamage() / 3));
                        n++;
                    }
                yield n == 0 ? "You carry nothing worn to mend." : "Your worn things are partly mended.";
            }
            case FAVOR -> {
                if (first < 0) yield "No god hears you.";
                Favor.add(sp, first, 15);
                yield Text.cap(h.name(first)) + " is pleased with you.";
            }
            case LONG_DREAM -> {
                d.longDream = true;
                yield "Your next dream will last half again as long.";
            }
            case WARD_OF_NIGHT -> {
                Wards.get(sp.server).add(level, altar, sp.server.overworld().getGameTime() + 24000);
                yield "For a day, no monster will rise within " + Wards.RADIUS + " blocks of this altar.";
            }
            case CALL_OF_THE_GOD -> {
                int g = -1;
                for (int x : named) if (h.god(x).alive) {
                    g = x;
                    break;
                }
                if (g < 0) yield "The dead do not call.";
                d.callFocus = g;
                yield "Tonight you will dream toward " + h.name(g) + ".";
            }
            case BORROWED_GIFT -> {
                if (first < 0) yield "No god lends you anything.";
                String gift = Rites.giftOf(h.god(first));
                sp.addEffect(new MobEffectInstance(Favor.effectFor(gift), 24000, 0, true, true, true));
                yield "For a day you carry the gift of " + h.name(first) + ": " + Favor.giftName(gift) + ".";
            }
            case BATTLE_FURY -> {
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 12000, 1));
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 0));
                yield "Fury fills you.";
            }
            case UNSEAL -> {
                if (first < 0) yield "Nothing is unsealed.";
                d.unseal = first;
                yield "On your next dream, the doors of " + h.name(first) + " will open as though you knew it well.";
            }
            case SEERS_WHISPER -> whisper(sp, h, sites);
        };
        level.sendParticles(ParticleTypes.ENCHANT, altar.getX() + 0.5, altar.getY() + 1.5, altar.getZ() + 0.5, 60, 0.4, 0.6, 0.4, 0.5);
        level.playSound(null, altar, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 0.8f);
        tell(sp, "You perform the rite of " + effect.title + ". " + result, ChatFormatting.LIGHT_PURPLE);
        sync(sp, slot);
        return true;
    }

    /** Points toward a piece of lore the player has neither found nor written, preferring gods they know. */
    static String whisper(ServerPlayer sp, History h, Sites sites) {
        Knowledge k = Memories.knowledge(sp);
        HeldMemories held = Memories.held(sp);
        List<String[]> known = new ArrayList<>(), any = new ArrayList<>();
        for (String[] s : AltusWorld.loreSpots()) {
            if (k.written.contains(s[0]) || held.find(s[0]) != null) continue;
            any.add(s);
            if (k.understandingOf(Integer.parseInt(s[1])) > 0) known.add(s);
        }
        List<String[]> pool = known.isEmpty() ? any : known;
        if (pool.isEmpty()) return "The rite has nothing left to whisper to you.";
        String[] s = pool.get(sp.getRandom().nextInt(pool.size()));
        Lore.Testimony t = Lore.render(h, sites, s[0]);
        return "A whisper: in the " + s[2] + " of the sanctum of " + h.name(Integer.parseInt(s[1])) + " lies \"" + t.title + "\".";
    }

    static void say(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    static void tell(ServerPlayer sp, String msg, ChatFormatting color) {
        sp.displayClientMessage(Component.literal(msg).withStyle(color), false);
    }
}
