package asd.itamio.worldshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Popup screen for OPs to edit an item's display name, icon, buy price, and sell price.
 * Uses ScreenManager.PopupScreen for clean screen transitions.
 * Opens GuiIconPicker as a fully separate screen (no overlap with this edit page).
 */
public class GuiEditItem extends ScreenManager.PopupScreen {
    private final int categoryIndex;
    private final String itemId;
    private final ItemStack originalItem;

    private EditBox nameField;
    private EditBox buyPriceField;
    private EditBox sellPriceField;
    private ItemStack selectedIcon;

    /** Fully opaque background to prevent visual overlap with parent screen. */
    private static final int BG_COLOR = 0xFF1A1A1A;
    /** Spacing between bottom of a widget and the next widget group. */
    private static final int GAP_BELOW = 10;
    /** Spacing between a label and its associated EditBox. */
    private static final int GAP_TO_EDITBOX = 3;

    public GuiEditItem(Screen parent, int categoryIndex, ItemStack item) {
        super(parent, Component.literal("Edit " + item.getHoverName().getString()));
        this.categoryIndex = categoryIndex;
        this.originalItem = item.copy();
        this.itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        this.selectedIcon = item.copy();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int y = 25;

        // --- Display Name ---
        this.nameField = new EditBox(this.font, centerX - 100, y + 13, 200, 20, Component.literal("Display Name"));
        this.nameField.setValue(originalItem.getHoverName().getString());
        this.nameField.setMaxLength(60);
        this.nameField.setFocused(true);
        this.addRenderableWidget(this.nameField);
        y += 13 + 20 + GAP_BELOW;

        // --- Buy Price ---
        this.buyPriceField = new EditBox(this.font, centerX - 100, y + 13, 200, 20, Component.literal("Buy Price"));
        this.buyPriceField.setValue(String.format("%.2f", WorldShop.getPriceEngine().getBuyPrice(originalItem)));
        this.buyPriceField.setMaxLength(15);
        this.buyPriceField.setFilter(s -> s.matches("\\d*\\.?\\d*"));
        this.addRenderableWidget(this.buyPriceField);
        y += 13 + 20 + GAP_BELOW;

        // --- Sell Price ---
        this.sellPriceField = new EditBox(this.font, centerX - 100, y + 13, 200, 20, Component.literal("Sell Price"));
        this.sellPriceField.setValue(String.format("%.2f", WorldShop.getPriceEngine().getSellPrice(originalItem)));
        this.sellPriceField.setMaxLength(15);
        this.sellPriceField.setFilter(s -> s.matches("\\d*\\.?\\d*"));
        this.addRenderableWidget(this.sellPriceField);
        y += 13 + 20 + GAP_BELOW;

        // --- Set Icon button (opens a SEPARATE full-screen GuiIconPicker) ---
        this.addRenderableWidget(Button.builder(Component.literal("\u00a77Set Icon"), btn -> {
            // Open icon picker as a completely separate screen — the edit page is fully hidden
            ScreenManager.open(new GuiIconPicker(this, selectedIcon, chosenIcon -> {
                this.selectedIcon = chosenIcon.copy();
            }));
        }).bounds(centerX - 100, y, 90, 20).build());
        y += 20 + GAP_BELOW;

        // --- Confirm and Cancel ---
        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aSave"), btn -> {
            String newName = nameField.getValue().trim();
            String buyStr = buyPriceField.getValue().trim();
            String sellStr = sellPriceField.getValue().trim();
            Double buyP = buyStr.isEmpty() ? null : Double.parseDouble(buyStr);
            Double sellP = sellStr.isEmpty() ? null : Double.parseDouble(sellStr);
            String iconId = BuiltInRegistries.ITEM.getKey(selectedIcon.getItem()).toString();
            String displayName = newName.equalsIgnoreCase(originalItem.getHoverName().getString()) ? null : newName;
            sendEditItem(categoryIndex, itemId, displayName, iconId, buyP, sellP);
            closeToParent();
        }).bounds(centerX - 105, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7cCancel"), btn -> {
            closeToParent();
        }).bounds(centerX + 5, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // Fully opaque background
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);

        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, "\u00a76\u00a7lEdit " + originalItem.getHoverName().getString(), centerX, 8, 0xFFFFFF);

        int y = 25;

        // Labels above each EditBox — positions match init()
        guiGraphics.drawCenteredString(this.font, "\u00a77Display Name:", centerX, y, 0xAAAAAA);
        y += 13 + 20 + GAP_BELOW;

        guiGraphics.drawCenteredString(this.font, "\u00a77Buy Price (0 = recipe-based):", centerX, y, 0xAAAAAA);
        y += 13 + 20 + GAP_BELOW;

        guiGraphics.drawCenteredString(this.font, "\u00a77Sell Price (0 = recipe-based):", centerX, y, 0xAAAAAA);
        y += 13 + 20 + GAP_BELOW;

        // Set Icon button is at this y position
        y += 20 + GAP_BELOW; // past the button

        // Icon previews below the Set Icon button
        guiGraphics.drawCenteredString(this.font, "\u00a77Selected Icon:", centerX, y, 0xAAAAAA);
        drawSlot(guiGraphics, centerX - 12, y + 10, 24, 24);
        guiGraphics.renderItem(selectedIcon, centerX - 10, y + 12);

        guiGraphics.drawCenteredString(this.font, "\u00a77Original Item:", centerX + 60, y, 0xAAAAAA);
        drawSlot(guiGraphics, centerX + 48, y + 10, 24, 24);
        guiGraphics.renderItem(originalItem, centerX + 50, y + 12);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (this.nameField != null) this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.buyPriceField != null) this.buyPriceField.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.sellPriceField != null) this.sellPriceField.mouseClicked(mouseX, mouseY, mouseButton);

        for (var widget : this.children()) {
            if (widget instanceof Button btn && btn.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            closeToParent();
            return true;
        }
        if (this.nameField != null && this.nameField.isFocused()) {
            return this.nameField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.buyPriceField != null && this.buyPriceField.isFocused()) {
            return this.buyPriceField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.sellPriceField != null && this.sellPriceField.isFocused()) {
            return this.sellPriceField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.nameField != null && this.nameField.isFocused()) {
            return this.nameField.charTyped(codePoint, modifiers);
        }
        if (this.buyPriceField != null && this.buyPriceField.isFocused()) {
            return this.buyPriceField.charTyped(codePoint, modifiers);
        }
        if (this.sellPriceField != null && this.sellPriceField.isFocused()) {
            return this.sellPriceField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void sendEditItem(int categoryIndex, String itemId, String displayName, String iconId, Double buyPrice, Double sellPrice) {
        ClientPlayNetworking.send(
                ShopPacket.PACKET_ID,
                ShopPacket.writeDirect(ShopPacket.editItem(categoryIndex, itemId, displayName != null ? displayName : "", iconId, buyPrice != null ? buyPrice : 0.0, sellPrice != null ? sellPrice : 0.0))
        );
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, -1438366652);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, -1439485133);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
