package io.github.bargainbinbastard.altus.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.bargainbinbastard.altus.history.History;
import io.github.bargainbinbastard.altus.history.Lore;
import io.github.bargainbinbastard.altus.net.TomeSyncPayload;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side of the Tome: saving edits and writing memories into it. The client is never trusted. */
public final class TomeService {
    private TomeService() {}

    public static final int OFFHAND_SLOT = 40;

    /** The Tome in a hotbar or offhand slot, or null. */
    public static ItemStack tomeAt(ServerPlayer sp, int slot) {
        if (!(Inventory.isHotbarSlot(slot) || slot == OFFHAND_SLOT)) return null;
        ItemStack st = sp.getInventory().getItem(slot);
        return st.is(AltusRegistry.TOME.get()) ? st : null;
    }

    public static TomeContents contents(ItemStack st) {
        return st.getOrDefault(AltusRegistry.TOME_CONTENTS.get(), TomeContents.EMPTY);
    }

    public static void save(ServerPlayer sp, int slot, List<String> pages) {
        ItemStack st = tomeAt(sp, slot);
        if (st == null) return;
        List<String> clean = TomeContents.sanitize(pages);
        st.set(AltusRegistry.TOME_CONTENTS.get(), new TomeContents(clean, TomeContents.reconcile(contents(st).records(), clean)));
    }

    /** Writes a held memory into a Tome. Returns the page it landed on, or -1 if nothing was written. */
    public static int write(ServerPlayer sp, int slot, int page, String testimonyId, List<String> clientPages) {
        ItemStack st = tomeAt(sp, slot);
        if (st == null) return -1;
        HeldMemories held = Memories.held(sp);
        HeldMemories.Memory m = held.find(testimonyId);
        if (m == null || m.pending()) return -1;
        History h = WorldHistory.get(sp.server);
        Lore.Testimony t = Lore.render(h, testimonyId);
        if (t == null) return -1;

        List<String> pages = new ArrayList<>(TomeContents.sanitize(clientPages));
        List<TomeRecord> records = new ArrayList<>(TomeContents.reconcile(contents(st).records(), pages));
        long day = sp.server.overworld().getDayTime() / 24000L + 1;
        String entry = t.title + "\n" + t.text + "\n(" + t.attribution + " Day " + day + ".)";
        int target = place(pages, Math.max(0, Math.min(page, pages.size() - 1)), entry);
        if (target < 0) {
            sp.displayClientMessage(Component.literal("This Tome is full.").withStyle(ChatFormatting.RED), true);
            return -1;
        }
        records.add(new TomeRecord(UUID.randomUUID().toString(), testimonyId, sp.getUUID().toString(), entry));
        st.set(AltusRegistry.TOME_CONTENTS.get(), new TomeContents(pages, records));

        held.remove(testimonyId);
        Knowledge k = Memories.knowledge(sp);
        k.written.add(testimonyId);
        k.understanding.merge(t.subject, 1, Integer::sum);
        Memories.sync(sp);
        Memories.updateEffect(sp);
        if (sp.connection != null && sp.connection.hasChannel(TomeSyncPayload.TYPE))
            PacketDistributor.sendToPlayer(sp, new TomeSyncPayload(slot, pages, target));
        sp.displayClientMessage(Component.literal("You write it down: " + t.title + ".").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return target;
    }

    /** Puts an entry on the given page if it fits, otherwise on the next page with room. */
    static int place(List<String> pages, int page, String entry) {
        for (int p = page; p < TomeContents.MAX_PAGES; p++) {
            if (p >= pages.size()) pages.add("");
            String cur = pages.get(p);
            String next = cur.isBlank() ? entry : cur + "\n\n" + entry;
            if (next.length() <= TomeContents.MAX_PAGE_CHARS) {
                pages.set(p, next);
                return p;
            }
        }
        return -1;
    }
}
