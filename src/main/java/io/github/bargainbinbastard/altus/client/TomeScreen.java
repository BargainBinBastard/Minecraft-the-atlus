package io.github.bargainbinbastard.altus.client;

import java.util.ArrayList;
import java.util.List;

import io.github.bargainbinbastard.altus.lore.TomeContents;
import io.github.bargainbinbastard.altus.lore.TomeService;
import io.github.bargainbinbastard.altus.net.HeldView;
import io.github.bargainbinbastard.altus.net.RiteListPayload;
import io.github.bargainbinbastard.altus.net.RitePerformPayload;
import io.github.bargainbinbastard.altus.net.RiteView;
import io.github.bargainbinbastard.altus.net.TomeSavePayload;
import io.github.bargainbinbastard.altus.net.TomeSyncPayload;
import io.github.bargainbinbastard.altus.net.TomeWritePayload;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Tome: an editable page with page turning, a list of the memories you hold so you can write
 * one down, and a list of the rites its entries can perform at an altar. Writing and rites happen
 * on the server, which sends the results back.
 */
public class TomeScreen extends Screen {
    private static final int W = 260, H = 200;

    private final InteractionHand hand;
    private int slot;
    private List<String> pages;
    private String title;
    private EditBox titleBox;
    private int page;
    /** What the screen shows: the page, the memories to write, or the Tome's rites. */
    private static final int PAGE = 0, MEMORIES = 1, RITES = 2;
    private int mode = PAGE;
    private int listOffset;
    private int seenRiteVersion;
    /** Set after asking the server to write a memory, until it answers (or a couple of seconds pass). */
    private int awaitingTicks;
    private int seenTomeVersion;
    private int seenHeldVersion;
    private MultiLineEditBox editor;
    private final List<Button> pickButtons = new ArrayList<>();
    private int left, top;

    public TomeScreen(InteractionHand hand) {
        super(Component.literal("Tome"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        Player p = minecraft.player;
        slot = hand == InteractionHand.MAIN_HAND ? p.getInventory().selected : TomeService.OFFHAND_SLOT;
        if (pages == null) {
            ItemStack st = p.getItemInHand(hand);
            TomeContents c = st.getOrDefault(AltusRegistry.TOME_CONTENTS.get(), TomeContents.EMPTY);
            pages = new ArrayList<>(c.pages());
            if (pages.isEmpty()) pages.add("");
            title = c.title();
            page = 0;
            seenTomeVersion = ClientLore.tomeVersion.get();
            seenHeldVersion = ClientLore.heldVersion.get();
            seenRiteVersion = ClientLore.riteVersion.get();
        }
        left = (width - W) / 2;
        top = (height - H) / 2;
        titleBox = new EditBox(font, left, top, W - 96, 16, Component.literal("Title"));
        titleBox.setMaxLength(TomeContents.MAX_TITLE);
        titleBox.setHint(Component.literal("Untitled Tome"));
        titleBox.setValue(title == null ? "" : title);
        titleBox.setResponder(v -> title = v);
        addRenderableWidget(titleBox);
        editor = new MultiLineEditBox(font, left, top + 20, W, H - 52, Component.literal("Write here..."), Component.literal("Page"));
        editor.setCharacterLimit(TomeContents.MAX_PAGE_CHARS);
        editor.setValue(pages.get(page));
        editor.setValueListener(v -> {
            if (pages != null && page < pages.size()) pages.set(page, v);
        });
        addRenderableWidget(editor);
        int by = top + H - 26;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + 24, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Write"), b -> setMode(mode == MEMORIES ? PAGE : MEMORIES))
                .tooltip(Tooltip.create(Component.literal("Write down a memory you hold"))).bounds(left + 50, by, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rites"), b -> setMode(mode == RITES ? PAGE : RITES))
                .tooltip(Tooltip.create(Component.literal("Perform the rite of an entry, near an altar"))).bounds(left + 118, by, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + W - 60, by, 60, 20).build());
        rebuildPicker();
    }

    private void turn(int d) {
        pages.set(page, editor.getValue());
        int next = page + d;
        if (next < 0) return;
        if (next >= pages.size()) {
            if (pages.size() >= TomeContents.MAX_PAGES) return;
            pages.add("");
        }
        page = next;
        editor.setValue(pages.get(page));
    }

    private void setMode(int m) {
        mode = m;
        listOffset = 0;
        rebuildPicker();
    }

    private List<RiteView> rites() {
        RiteListPayload r = ClientLore.lastRites;
        return r != null && r.slot() == slot ? r.rites() : List.of();
    }

    private static final int ROWS = 5;

    private String fit(String label) {
        return font.width(label) > W - 10 ? font.plainSubstrByWidth(label, W - 20) + "..." : label;
    }

    private List<HeldView> writable() {
        List<HeldView> out = new ArrayList<>();
        for (HeldView v : ClientLore.held) if (!v.pending()) out.add(v);
        return out;
    }

    private void rebuildPicker() {
        for (Button b : pickButtons) removeWidget(b);
        pickButtons.clear();
        // The text box ignores its visibility when clicked, so take it off the screen entirely while
        // the memory list is showing; otherwise it swallows clicks meant for the list.
        removeWidget(editor);
        if (mode == PAGE) {
            addRenderableWidget(editor);
            return;
        }
        int total = mode == MEMORIES ? writable().size() : rites().size();
        listOffset = Math.max(0, Math.min(listOffset, Math.max(0, total - ROWS)));
        int y = top + 34;
        if (mode == MEMORIES) {
            List<HeldView> list = writable();
            for (int i = listOffset; i < list.size() && i < listOffset + ROWS; i++) {
                HeldView v = list.get(i);
                int mins = (ClientLore.secondsLeft(v) + 59) / 60;
                Button b = Button.builder(Component.literal(fit(v.title() + "  (" + mins + "m)")), btn -> writeMemory(v.id()))
                        .bounds(left, y, W, 20).build();
                pickButtons.add(addRenderableWidget(b));
                y += 22;
            }
        } else {
            List<RiteView> list = rites();
            for (int i = listOffset; i < list.size() && i < listOffset + ROWS; i++) {
                RiteView v = list.get(i);
                String effect = v.effect().contains(":") ? v.effect().substring(0, v.effect().indexOf(':')) : v.effect();
                String label = (v.ready() ? "" : "(" + v.note() + ") ") + effect + ", from " + v.title();
                Button b = Button.builder(Component.literal(fit(label)), btn -> performRite(v.entryId()))
                        .tooltip(Tooltip.create(Component.literal(v.effect() + "\nFrom: " + v.title() + "\nOfferings: " + v.needs()
                                + (v.ready() ? "" : "\n(" + v.note() + ")"))))
                        .bounds(left, y, W, 20).build();
                b.active = v.ready();
                pickButtons.add(addRenderableWidget(b));
                y += 22;
            }
        }
        if (total > ROWS) {
            Button up = Button.builder(Component.literal("^"), btn -> {
                listOffset = Math.max(0, listOffset - ROWS);
                rebuildPicker();
            }).bounds(left + W - 44, top + H - 50, 20, 18).build();
            Button down = Button.builder(Component.literal("v"), btn -> {
                listOffset += ROWS;
                rebuildPicker();
            }).bounds(left + W - 20, top + H - 50, 20, 18).build();
            up.active = listOffset > 0;
            down.active = listOffset + ROWS < total;
            pickButtons.add(addRenderableWidget(up));
            pickButtons.add(addRenderableWidget(down));
        }
    }

    private void performRite(String entryId) {
        PacketDistributor.sendToServer(new RitePerformPayload(slot, entryId));
        setMode(PAGE);
    }

    private void writeMemory(String id) {
        pages.set(page, editor.getValue());
        PacketDistributor.sendToServer(new TomeWritePayload(slot, page, id, new ArrayList<>(pages)));
        awaitingTicks = 40;
        setMode(PAGE);
    }

    @Override
    public void tick() {
        super.tick();
        if (awaitingTicks > 0) awaitingTicks--;
        int tv = ClientLore.tomeVersion.get();
        if (tv != seenTomeVersion) {
            seenTomeVersion = tv;
            awaitingTicks = 0;
            TomeSyncPayload t = ClientLore.lastTome;
            if (t != null && t.slot() == slot) {
                title = t.title();
                if (titleBox != null) titleBox.setValue(title);
                pages = new ArrayList<>(t.pages());
                if (pages.isEmpty()) pages.add("");
                page = Math.max(0, Math.min(t.page(), pages.size() - 1));
                editor.setValue(pages.get(page));
            }
        }
        int hv = ClientLore.heldVersion.get();
        if (hv != seenHeldVersion) {
            seenHeldVersion = hv;
            if (mode == MEMORIES) rebuildPicker();
        }
        int rv = ClientLore.riteVersion.get();
        if (rv != seenRiteVersion) {
            seenRiteVersion = rv;
            if (mode == RITES) rebuildPicker();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        String head = mode == MEMORIES ? "Pick a memory:" : mode == RITES ? "Pick a rite:"
                : awaitingTicks > 0 ? "Writing..." : "Page " + (page + 1) + " of " + pages.size();
        g.drawString(font, head, left + W - 90, top + 4, 0xE0D8C0);
        if (mode == MEMORIES && writable().isEmpty())
            g.drawString(font, "You hold no memories to write.", left, top + 36, 0xA0A0A0);
        if (mode == RITES && rites().isEmpty())
            g.drawString(font, "Nothing written here from memory can be a rite yet.", left, top + 36, 0xA0A0A0);
        if (mode == RITES && !rites().isEmpty())
            g.drawString(font, "Hover for offerings. Stand by an altar.", left, top + H - 46, 0x9090A0);
    }

    @Override
    public void removed() {
        if (editor != null && pages != null && page < pages.size()) pages.set(page, editor.getValue());
        // If a write is still on its way back, the server already has the newer pages: don't overwrite them.
        if (pages != null && awaitingTicks == 0)
            PacketDistributor.sendToServer(new TomeSavePayload(slot, title == null ? "" : title, new ArrayList<>(pages)));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
