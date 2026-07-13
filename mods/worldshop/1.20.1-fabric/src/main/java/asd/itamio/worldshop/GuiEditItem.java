package asd.itamio.worldshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Popup screen for OPs to edit an item's display name, icon, buy price, and sell price.
 */
public class GuiEditItem extends Screen {
    private final Screen parent;
    private final int categoryIndex;
    private final String itemId;
    private final ItemStack originalItem;

    private EditBox nameField;
    private EditBox buyPriceField;
    private EditBox sellPriceField;
    private EditBox searchField;
    private String searchText = "";
    private List<ItemStack> searchResults = new ArrayList<>();
    private int searchScroll = 0;
    private boolean showingIconPicker = false;
    private ItemStack selectedIcon;

    private static final int COLS = 7;
    private static final int SLOT_SIZE = 20;

    public GuiEditItem(Screen parent, int categoryIndex, ItemStack item) {
        super(Component.literal("Edit " + item.getHoverName().getString()));
        this.parent = parent;
        this.categoryIndex = categoryIndex;
        this.originalItem = item.copy();
        this.itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        this.selectedIcon = item.copy();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        if (showingIconPicker) {
            // Icon picker mode
            this.searchField = new EditBox(this.font, centerX - 120, 30, 240, 16, Component.literal("Search icon..."));
            this.searchField.setMaxLength(40);
            this.searchField.setFocused(true);
            this.searchField.setResponder(this::onSearchChanged);
            this.addRenderableWidget(this.searchField);

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack to Edit"), btn -> {
                showingIconPicker = false;
                init();
            }).bounds(centerX - 50, this.height - 25, 100, 20).build());
        } else {
            // Edit mode
            int y = 25;

            // Display name
            this.nameField = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Display Name"));
            this.nameField.setValue(originalItem.getHoverName().getString());
            this.nameField.setMaxLength(60);
            this.nameField.setFocused(true);
            this.addRenderableWidget(this.nameField);

            // Buy price
            double buyPrice = WorldShop.getPriceEngine().getBuyPrice(originalItem);
            this.buyPriceField = new EditBox(this.font, centerX - 100, y + 28, 200, 20, Component.literal("Buy Price"));
            this.buyPriceField.setValue(String.format("%.2f", buyPrice));
            this.buyPriceField.setMaxLength(15);
            this.buyPriceField.setFilter(s -> s.matches("\\d*\\.?\\d*"));
            this.addRenderableWidget(this.buyPriceField);

            // Sell price
            double sellPrice = WorldShop.getPriceEngine().getSellPrice(originalItem);
            this.sellPriceField = new EditBox(this.font, centerX - 100, y + 56, 200, 20, Component.literal("Sell Price"));
            this.sellPriceField.setValue(String.format("%.2f", sellPrice));
            this.sellPriceField.setMaxLength(15);
            this.sellPriceField.setFilter(s -> s.matches("\\d*\\.?\\d*"));
            this.addRenderableWidget(this.sellPriceField);

            // Icon button
            this.addRenderableWidget(Button.builder(Component.literal("\u00a77Set Icon"), btn -> {
                showingIconPicker = true;
                init();
            }).bounds(centerX - 100, y + 82, 90, 20).build());

            // Show current icon preview
            // We'll draw it in render()

            // Confirm and Cancel
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7aSave"), btn -> {
                String newName = nameField.getValue().trim();
                String buyStr = buyPriceField.getValue().trim();
                String sellStr = sellPriceField.getValue().trim();
                Double buyP = buyStr.isEmpty() ? null : Double.parseDouble(buyStr);
                Double sellP = sellStr.isEmpty() ? null : Double.parseDouble(sellStr);
                String iconId = BuiltInRegistries.ITEM.getKey(selectedIcon.getItem()).toString();
                // Only send name if it changed
                String displayName = newName.equalsIgnoreCase(originalItem.getHoverName().getString()) ? null : newName;
                sendEditItem(categoryIndex, itemId, displayName, iconId, buyP, sellP);
                Minecraft.getInstance().setScreen(parent);
            }).bounds(centerX - 105, this.height - 30, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cCancel"), btn -> {
                Minecraft.getInstance().setScreen(parent);
            }).bounds(centerX + 5, this.height - 30, 100, 20).build());
        }
    }

    private void onSearchChanged(String text) {
        this.searchText = text.toLowerCase().trim();
        this.searchScroll = 0;
        searchResults.clear();
        if (searchText.isEmpty()) return;

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString().toLowerCase();
            String id = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();
            if (name.contains(searchText) || id.contains(searchText)) {
                searchResults.add(stack);
                if (searchResults.size() >= 200) break;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, -1437247880);

        int centerX = this.width / 2;

        if (showingIconPicker) {
            // Icon picker mode
            guiGraphics.drawCenteredString(this.font, "\u00a76\u00a7lSelect Icon", centerX, 8, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, "\u00a77Current icon:", centerX, 52, 0xAAAAAA);
            guiGraphics.renderItem(selectedIcon, centerX - 8, 62);

            int startY = 90;
            int availableWidth = COLS * (SLOT_SIZE + 2);
            int guiLeft = (this.width - availableWidth) / 2;

            for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int x = guiLeft + col * (SLOT_SIZE + 2);
                int y = startY + row * (SLOT_SIZE + 2);
                int index = i + searchScroll * COLS;
                ItemStack stack = searchResults.get(index);
                drawSlot(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
                guiGraphics.renderItem(stack, x + 2, y + 2);
            }

            // Tooltips
            for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int x = guiLeft + col * (SLOT_SIZE + 2);
                int y = startY + row * (SLOT_SIZE + 2);
                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    int index = i + searchScroll * COLS;
                    guiGraphics.renderTooltip(this.font, searchResults.get(index), mouseX, mouseY);
                    break;
                }
            }
        } else {
            // Edit mode
            guiGraphics.drawCenteredString(this.font, "\u00a76\u00a7lEdit " + originalItem.getHoverName().getString(), centerX, 8, 0xFFFFFF);

            guiGraphics.drawCenteredString(this.font, "\u00a77Display Name:", centerX, 18, 0xAAAAAA);

            guiGraphics.drawCenteredString(this.font, "\u00a77Buy Price (0 = recipe-based):", centerX, 46, 0xAAAAAA);

            guiGraphics.drawCenteredString(this.font, "\u00a77Sell Price (0 = recipe-based):", centerX, 74, 0xAAAAAA);

            // Draw current icon preview
            guiGraphics.drawCenteredString(this.font, "\u00a77Icon:", centerX, 106, 0xAAAAAA);
            drawSlot(guiGraphics, centerX - 12, 114, 24, 24);
            guiGraphics.renderItem(selectedIcon, centerX - 10, 116);

            // Draw the original item preview too
            guiGraphics.drawCenteredString(this.font, "\u00a77Item:", centerX + 60, 106, 0xAAAAAA);
            drawSlot(guiGraphics, centerX + 48, 114, 24, 24);
            guiGraphics.renderItem(originalItem, centerX + 50, 116);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (showingIconPicker) {
            if (this.searchField != null) this.searchField.mouseClicked(mouseX, mouseY, mouseButton);

            for (var widget : this.children()) {
                if (widget instanceof Button btn && btn.mouseClicked(mouseX, mouseY, mouseButton)) {
                    return true;
                }
            }

            // Check search result clicks
            int availableWidth = COLS * (SLOT_SIZE + 2);
            int guiLeft = (this.width - availableWidth) / 2;
            int startY = 90;

            for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int x = guiLeft + col * (SLOT_SIZE + 2);
                int y = startY + row * (SLOT_SIZE + 2);
                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    int index = i + searchScroll * COLS;
                    selectedIcon = searchResults.get(index).copy();
                    searchField.setValue("");
                    searchResults.clear();
                    showingIconPicker = false;
                    init();
                    return true;
                }
            }
        } else {
            if (this.nameField != null) this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
            if (this.buyPriceField != null) this.buyPriceField.mouseClicked(mouseX, mouseY, mouseButton);
            if (this.sellPriceField != null) this.sellPriceField.mouseClicked(mouseX, mouseY, mouseButton);

            for (var widget : this.children()) {
                if (widget instanceof Button btn && btn.mouseClicked(mouseX, mouseY, mouseButton)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (showingIconPicker) {
            int maxScroll = Math.max(0, (int) Math.ceil((double) searchResults.size() / (double) COLS) - 4);
            if (scrollDelta > 0) searchScroll = Math.max(0, searchScroll - 1);
            else if (scrollDelta < 0) searchScroll = Math.min(maxScroll, searchScroll + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (showingIconPicker) {
                showingIconPicker = false;
                init();
                return true;
            }
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (showingIconPicker && this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.keyPressed(keyCode, scanCode, modifiers);
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
        if (showingIconPicker && this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.charTyped(codePoint, modifiers);
        }
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
