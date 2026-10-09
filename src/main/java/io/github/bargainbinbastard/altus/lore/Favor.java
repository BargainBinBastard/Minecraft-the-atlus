package io.github.bargainbinbastard.altus.lore;

import io.github.bargainbinbastard.altus.dream.AltusAttachments;
import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Rites;
import io.github.bargainbinbastard.altus.history.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Favor with the gods, who a player follows, and the gifts of discipleship. */
public final class Favor {
    private Favor() {}

    public static Devotion of(ServerPlayer sp) {
        return sp.getData(AltusAttachments.DEVOTION);
    }

    public static int favor(ServerPlayer sp, int god) {
        return of(sp).favorOf(god);
    }

    /** 0 none, 1 Follower, 2 Disciple, 3 Exalted: with the player's patron. */
    public static int tier(ServerPlayer sp) {
        Devotion d = of(sp);
        if (d.patron < 0) return 0;
        int f = d.favorOf(d.patron);
        return f >= Rites.EXALTED ? 3 : f >= Rites.DISCIPLE ? 2 : f >= Rites.FOLLOWER ? 1 : 0;
    }

    public static boolean follows(ServerPlayer sp, int god) {
        return of(sp).patron == god && tier(sp) >= 1;
    }

    public static String tierName(int t) {
        return switch (t) {
            case 1 -> "Follower";
            case 2 -> "Disciple";
            case 3 -> "Exalted";
            default -> "";
        };
    }

    public static void add(ServerPlayer sp, int god, int amount) {
        if (amount == 0) return;
        set(sp, god, favor(sp, god) + amount);
    }

    public static void set(ServerPlayer sp, int god, int value) {
        Devotion d = of(sp);
        int oldPatron = d.patron, oldTier = tier(sp);
        d.favor.put(god, Math.max(-500, Math.min(1000, value)));
        // The patron is the god the player stands best with, once that is enough to follow it. Ties keep the current patron.
        int best = -1, bestF = Rites.FOLLOWER - 1;
        for (var e : d.favor.entrySet())
            if (e.getValue() > bestF || (e.getValue() == bestF && e.getKey() == oldPatron && bestF >= Rites.FOLLOWER)) {
                best = e.getKey();
                bestF = e.getValue();
            }
        if (oldPatron >= 0 && best != oldPatron && d.favorOf(oldPatron) == bestF) best = oldPatron;
        d.patron = best;
        History h = WorldHistory.get(sp.server);
        int newTier = tier(sp);
        if (best != oldPatron)
            tell(sp, best < 0 ? "You no longer follow any god." : "You now follow " + h.name(best) + ".");
        if (best >= 0 && newTier != oldTier && (best == oldPatron || newTier > 0)) {
            String gift = Rites.giftOf(h.god(best));
            if (newTier >= 2 && oldTier < 2)
                tell(sp, "You are now a Disciple of " + h.name(best) + ". It grants you its gift: " + giftName(gift)
                        + ". Do nothing it cannot abide.");
            else if (newTier == 1 && oldTier == 0)
                tell(sp, "You are now a Follower of " + h.name(best) + ". Its guardians will let you pass.");
            else if (newTier < oldTier)
                tell(sp, Text.cap(h.name(best)) + " thinks less of you. You are " + (newTier == 0 ? "no longer its follower." : "now only its " + tierName(newTier) + "."));
        }
        applyGift(sp);
    }

    /** Keeps the disciple's gift applied (it is lost on death or to milk), and removes it when it no longer applies. */
    public static void applyGift(ServerPlayer sp) {
        Devotion d = of(sp);
        String wanted = tier(sp) >= 2 ? Rites.giftOf(WorldHistory.get(sp.server).god(d.patron)) : "";
        if (!d.gift.equals(wanted)) {
            if (!d.gift.isEmpty()) {
                MobEffectInstance cur = sp.getEffect(effectFor(d.gift));
                if (cur != null && cur.isInfiniteDuration()) sp.removeEffect(effectFor(d.gift));
            }
            d.gift = wanted;
        }
        if (!wanted.isEmpty()) {
            MobEffectInstance cur = sp.getEffect(effectFor(wanted));
            if (cur == null || !cur.isInfiniteDuration())
                sp.addEffect(new MobEffectInstance(effectFor(wanted), MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
        }
    }

    public static Holder<MobEffect> effectFor(String gift) {
        return switch (gift) {
            case "haste" -> MobEffects.DIG_SPEED;
            case "water_breathing" -> MobEffects.WATER_BREATHING;
            case "health_boost" -> MobEffects.HEALTH_BOOST;
            case "speed" -> MobEffects.MOVEMENT_SPEED;
            case "night_vision" -> MobEffects.NIGHT_VISION;
            case "resistance" -> MobEffects.DAMAGE_RESISTANCE;
            case "strength" -> MobEffects.DAMAGE_BOOST;
            case "slow_falling" -> MobEffects.SLOW_FALLING;
            case "jump_boost" -> MobEffects.JUMP;
            case "fire_resistance" -> MobEffects.FIRE_RESISTANCE;
            default -> MobEffects.LUCK;
        };
    }

    public static String giftName(String gift) {
        return switch (gift) {
            case "haste" -> "Haste";
            case "water_breathing" -> "Water Breathing";
            case "health_boost" -> "Health Boost";
            case "speed" -> "Speed";
            case "night_vision" -> "Night Vision";
            case "resistance" -> "Resistance";
            case "strength" -> "Strength";
            case "slow_falling" -> "Slow Falling";
            case "jump_boost" -> "Jump Boost";
            case "fire_resistance" -> "Fire Resistance";
            default -> "Luck";
        };
    }

    /** What the player is told when they ask the altar where they stand. */
    public static Component status(ServerPlayer sp) {
        Devotion d = of(sp);
        History h = WorldHistory.get(sp.server);
        StringBuilder b = new StringBuilder();
        if (d.patron >= 0) {
            int f = d.favorOf(d.patron);
            int t = tier(sp);
            b.append("You are a ").append(tierName(t)).append(" of ").append(h.name(d.patron)).append(" (favor ").append(f).append(")");
            if (t < 2) b.append("; its Disciples have ").append(Rites.DISCIPLE);
            b.append(".");
        } else b.append("You follow no god. Offer what a god loves here, crouching, and it may notice you.");
        StringBuilder others = new StringBuilder();
        Knowledge k = Memories.knowledge(sp);
        for (var e : d.favor.entrySet())
            if (e.getKey() != d.patron && e.getValue() != 0 && k.understandingOf(e.getKey()) > 0)
                others.append(others.length() == 0 ? "" : ", ").append(h.name(e.getKey())).append(" ").append(e.getValue());
        if (others.length() > 0) b.append(" Others: ").append(others).append(".");
        return Component.literal(b.toString()).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    static void tell(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }
}
