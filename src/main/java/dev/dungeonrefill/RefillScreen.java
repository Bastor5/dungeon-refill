package dev.dungeonrefill;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class RefillScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int PANEL_WIDTH = 300;

    private final Screen parent;
    private int scroll;
    private int visibleRows;
    private String newId = "";
    private String newAmount = "16";

    public RefillScreen(Screen parent) {
        super(Component.literal("Dungeon Refill"));
        this.parent = parent;
    }

    private RefillConfig config() {
        return DungeonRefill.get().config();
    }

    @Override
    protected void init() {
        RefillConfig config = config();
        List<RefillItem> items = config.items;
        int left = (width - PANEL_WIDTH) / 2;
        int top = 26;

        addRenderableWidget(Button.builder(autoLabel(), b -> {
            config.autoRefill = !config.autoRefill;
            b.setMessage(autoLabel());
        }).bounds(left, top, PANEL_WIDTH, 20)
                .tooltip(Tooltip.create(Component.literal("Refill when Mort says the run has started"))).build());

        int listTop = top + 36;
        int bottomArea = 72;
        visibleRows = Math.max(1, (height - listTop - bottomArea) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, items.size() - visibleRows));

        for (int i = 0; i < visibleRows && scroll + i < items.size(); i++) {
            RefillItem item = items.get(scroll + i);
            int y = listTop + i * ROW_HEIGHT;

            addRenderableWidget(Button.builder(toggleLabel(item), b -> {
                item.enabled = !item.enabled;
                b.setMessage(toggleLabel(item));
            }).bounds(left, y, 36, 20).build());

            StringWidget label = new StringWidget(left + 42, y + 6, 160, 10, Component.literal(item.name), font);
            label.setTooltip(Tooltip.create(Component.literal(item.id)));
            addRenderableWidget(label);

            EditBox amount = new EditBox(font, left + 208, y, 50, 20, Component.literal(item.name + " amount"));
            amount.setMaxLength(4);
            amount.setValue(Integer.toString(item.amount));
            amount.setResponder(text -> {
                Integer value = parse(text);
                if (value != null) item.amount = RefillConfig.clampAmount(value);
            });
            addRenderableWidget(amount);

            if (item.custom) {
                addRenderableWidget(Button.builder(Component.literal("X").withStyle(ChatFormatting.RED), b -> {
                    config.items.remove(item);
                    rebuildWidgets();
                }).bounds(left + PANEL_WIDTH - 36, y, 36, 20)
                        .tooltip(Tooltip.create(Component.literal("Remove " + item.name))).build());
            }
        }

        if (items.size() > visibleRows) {
            Button up = addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
                scroll--;
                rebuildWidgets();
            }).bounds(left + PANEL_WIDTH + 4, listTop, 20, 20).build());
            up.active = scroll > 0;
            Button down = addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                scroll++;
                rebuildWidgets();
            }).bounds(left + PANEL_WIDTH + 4, listTop + (visibleRows - 1) * ROW_HEIGHT, 20, 20).build());
            down.active = scroll + visibleRows < items.size();
        }

        // Add a custom item by its SkyBlock ID
        int addY = height - 52;
        EditBox idBox = new EditBox(font, left, addY, 180, 20, Component.literal("SkyBlock item ID"));
        idBox.setMaxLength(64);
        idBox.setHint(Component.literal("SkyBlock ID, e.g. DUNGEON_STONE").withStyle(ChatFormatting.DARK_GRAY));
        idBox.setValue(newId);
        idBox.setResponder(text -> newId = text);
        addRenderableWidget(idBox);

        EditBox newAmountBox = new EditBox(font, left + 186, addY, 50, 20, Component.literal("New item amount"));
        newAmountBox.setMaxLength(4);
        newAmountBox.setValue(newAmount);
        newAmountBox.setResponder(text -> newAmount = text);
        addRenderableWidget(newAmountBox);

        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addItem()).bounds(left + 242, addY, 58, 20).build());

        int buttonsY = height - 26;
        int third = (PANEL_WIDTH - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Refill now"), b -> {
            config.save();
            DungeonRefill.get().startRefill();
            onClose();
        }).bounds(left, buttonsY, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"), b -> {
            config.items = RefillConfig.defaultItems();
            scroll = 0;
            rebuildWidgets();
        }).bounds(left + third + 4, buttonsY, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + 2 * (third + 4), buttonsY, PANEL_WIDTH - 2 * (third + 4), 20).build());
    }

    private void addItem() {
        String id = RefillConfig.normalizeId(newId);
        Integer amount = parse(newAmount);
        if (id.isEmpty() || amount == null) return;
        RefillItem existing = config().items.stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
        if (existing != null) {
            existing.amount = RefillConfig.clampAmount(amount);
            existing.enabled = true;
        } else {
            config().items.add(new RefillItem(id, RefillConfig.prettyName(id), RefillConfig.clampAmount(amount), true, true));
            scroll = config().items.size();
        }
        newId = "";
        rebuildWidgets();
    }

    private static Integer parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Component autoLabel() {
        return Component.literal("Auto refill on run start: ")
                .append(config().autoRefill ? Component.literal("ON").withStyle(ChatFormatting.GREEN) : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private static Component toggleLabel(RefillItem item) {
        return item.enabled ? Component.literal("ON").withStyle(ChatFormatting.GREEN) : Component.literal("OFF").withStyle(ChatFormatting.RED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, config().items.size() - visibleRows);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = (width - PANEL_WIDTH) / 2;
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.text(font, Component.literal("Item").withStyle(ChatFormatting.GRAY), left + 42, 51, 0xFFFFFFFF);
        graphics.text(font, Component.literal("Amount").withStyle(ChatFormatting.GRAY), left + 208, 51, 0xFFFFFFFF);
        graphics.text(font, Component.literal("Add item (SkyBlock ID, amount):").withStyle(ChatFormatting.GRAY), left, height - 63, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        config().save();
        minecraft.gui.setScreen(parent);
    }
}
