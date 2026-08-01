package asd.itamio.modernshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
 * Popup screen for OPs to search and add blocks/items to a category.
 * Uses ScreenManager.PopupScreen for clean screen transitions.
 */
public class GuiAddItem extends ScreenManager.PopupScreen {
    private final int categoryIndex;
    private final String categoryName;

    private EditBox searchField;
    private String searchText = "";
    private List<ItemStack> searchResults = new ArrayList<>();
    private int searchScroll = 0;
    private String message = "";

    private static final int COLS = 9;
    private static final int SLOT_SIZE = 20;
    private static final int ROWS = 5;
    /** Fully opaque background color to prevent visual overlap with parent screen. */
    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int GAP_BELOW_WIDGET = 10;

    public GuiAddItem(Screen parent, int categoryIndex, String categoryName) {
        super(parent, Component.literal("Add Item to " + categoryName));
        this.categoryIndex = categoryIndex;
        this.categoryName = categoryName;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.searchField = new EditBox(this.font, centerX - 120, 32, 240, 16, Component.literal("Search blocks/items..."));
        this.searchField.setMaxLength(40);
        this.searchField.setFocused(true);
        this.searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchField);

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack"), btn -> {
            closeToParent();
        }).bounds(centerX - 50, this.height - 25, 100, 20).build());
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
                if (searchResults.size() >= 500) break;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // Fully opaque background to prevent parent screen overlap
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);

        int centerX = this.width / 2;

        guiGraphics.drawCenteredString(this.font, "\u00a76\u00a7lAdd Item to \u00a7f" + categoryName, centerX, 8, 0xFFFFFF);
        // Label above search field (y=32)
        guiGraphics.drawCenteredString(this.font, "\u00a77Search blocks/items:", centerX, 20, 0xAAAAAA);

        // Determine startY for search results (below search field at y=32 + 16 = 48)
        int searchFieldBottom = 48;
        int startY = searchFieldBottom + GAP_BELOW_WIDGET;

        // If there's a feedback message, draw it between search field and results
        if (!message.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "\u00a7a" + message, centerX, searchFieldBottom + 2, 0xFFFFFF);
            // Push results down so they don't overlap the message
            startY = searchFieldBottom + 14 + GAP_BELOW_WIDGET;
        }

        // Draw search results grid
        int availableWidth = COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < COLS * ROWS && i + searchScroll * COLS < searchResults.size(); i++) {
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
        for (int i = 0; i < COLS * ROWS && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * COLS;
                ItemStack stack = searchResults.get(index);
                guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                break;
            }
        }

        // Scroll indicator
        if (searchResults.size() > COLS * ROWS) {
            String scrollInfo = "\u00a77Page " + (searchScroll + 1) + "/" + (int) Math.ceil((double) searchResults.size() / (double) (COLS * ROWS));
            guiGraphics.drawCenteredString(this.font, scrollInfo, centerX, this.height - 45, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (this.searchField != null) this.searchField.mouseClicked(mouseX, mouseY, mouseButton);

        for (var widget : this.children()) {
            if (widget instanceof Button btn && btn.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }

        // Check search result clicks
        int searchFieldBottom = 48;
        int startY = searchFieldBottom + GAP_BELOW_WIDGET;
        if (!message.isEmpty()) {
            startY = searchFieldBottom + 14 + GAP_BELOW_WIDGET;
        }

        int availableWidth = COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < COLS * ROWS && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * COLS;
                ItemStack stack = searchResults.get(index);
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                sendAddItem(categoryIndex, itemId);
                message = "\u00a7aAdded " + stack.getHoverName().getString() + "!";
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        int maxScroll = Math.max(0, (int) Math.ceil((double) searchResults.size() / (double) (COLS * ROWS)) - 1);
        if (scrollDelta > 0) searchScroll = Math.max(0, searchScroll - 1);
        else if (scrollDelta < 0) searchScroll = Math.min(maxScroll, searchScroll + 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            closeToParent();
            return true;
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void sendAddItem(int categoryIndex, String itemId) {
        ClientPlayNetworking.send(
                ShopPacket.PACKET_ID,
                ShopPacket.writeDirect(ShopPacket.addItemToCategory(categoryIndex, itemId))
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
