package io.github.bargainbinbastard.altus.client;

import java.util.ArrayList;
import java.util.List;

import io.github.bargainbinbastard.altus.lore.TomeContents;
import io.github.bargainbinbastard.altus.lore.TomeService;
import io.github.bargainbinbastard.altus.net.HeldView;
import io.github.bargainbinbastard.altus.net.TomeSavePayload;
import io.github.bargainbinbastard.altus.net.TomeSyncPayload;
import io.github.bargainbinbastard.altus.net.TomeWritePayload;
import io.github.bargainbinbastard.altus.registry.AltusRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Tome: an editable page with page turning, and a button that lists the memories you hold so
 * you can write one down. Writing happens on the server, which sends the updated pages back.
 */
public class TomeScreen extends Screen {
    private static final int W = 260, H = 200;

    private final InteractionHand hand;
    private int slot;
    private List<String> pages;
    private int page;
    private boolean picking;
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
            page = 0;
            seenTomeVersion = ClientLore.tomeVersion.get();
            seenHeldVersion = ClientLore.heldVersion.get();
        }
        left = (width - W) / 2;
        top = (height - H) / 2;
        editor = new MultiLineEditBox(font, left, top + 16, W, H - 48, Component.literal("Write here..."), Component.literal("Page"));
        editor.setCharacterLimit(TomeContents.MAX_PAGE_CHARS);
        editor.setValue(pages.get(page));
        editor.setValueListener(v -> {
            if (pages != null && page < pages.size()) pages.set(page, v);
        });
        addRenderableWidget(editor);
        int by = top + H - 26;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + 24, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Write a memory"), b -> setPicking(!picking)).bounds(left + 50, by, 120, 20).build());
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

    private void setPicking(boolean on) {
        picking = on;
        rebuildPicker();
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
        if (!picking) {
            addRenderableWidget(editor);
            return;
        }
        int y = top + 20;
        for (HeldView v : writable()) {
            if (y > top + H - 50) break;
            int mins = (ClientLore.secondsLeft(v) + 59) / 60;
            String label = v.title() + "  (" + mins + "m)";
            if (font.width(label) > W - 10) label = font.plainSubstrByWidth(label, W - 20) + "...";
            Button b = Button.builder(Component.literal(label), btn -> writeMemory(v.id())).bounds(left, y, W, 20).build();
            pickButtons.add(addRenderableWidget(b));
            y += 22;
        }
    }

    private void writeMemory(String id) {
        pages.set(page, editor.getValue());
        PacketDistributor.sendToServer(new TomeWritePayload(slot, page, id, new ArrayList<>(pages)));
        awaitingTicks = 40;
        setPicking(false);
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
                pages = new ArrayList<>(t.pages());
                if (pages.isEmpty()) pages.add("");
                page = Math.max(0, Math.min(t.page(), pages.size() - 1));
                editor.setValue(pages.get(page));
            }
        }
        int hv = ClientLore.heldVersion.get();
        if (hv != seenHeldVersion) {
            seenHeldVersion = hv;
            if (picking) rebuildPicker();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        String head = picking ? "Which memory will you write down?"
                : awaitingTicks > 0 ? "Writing..." : "Page " + (page + 1) + " of " + pages.size();
        g.drawString(font, head, left, top + 3, 0xE0D8C0);
        if (picking && writable().isEmpty())
            g.drawString(font, "You hold no memories to write.", left, top + 24, 0xA0A0A0);
    }

    @Override
    public void removed() {
        if (editor != null && pages != null && page < pages.size()) pages.set(page, editor.getValue());
        // If a write is still on its way back, the server already has the newer pages: don't overwrite them.
        if (pages != null && awaitingTicks == 0) PacketDistributor.sendToServer(new TomeSavePayload(slot, new ArrayList<>(pages)));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
