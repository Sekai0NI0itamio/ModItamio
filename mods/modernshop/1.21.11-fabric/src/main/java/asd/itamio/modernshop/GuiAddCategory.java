package asd.itamio.modernshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Popup screen for adding a new category.
 * OP can name the category and set a block/item icon by searching.
 * Uses ScreenManager.PopupScreen for clean screen transitions.
 */
public class GuiAddCategory extends ScreenManager.PopupScreen {
    private final int categoryIndex; // -1 for new, or index to edit custom category

    private EditBox nameField;
    private EditBox searchField;
    private String searchText = "";
    private List<ItemStack> searchResults = new ArrayList<>();
    private int searchScroll = 0;
    private ItemStack selectedIcon = null;
    private String selectedIconId = "";

    private static final int SEARCH_COLS = 7;
    private static final int SLOT_SIZE = 20;
    /** Fully opaque background color to prevent visual overlap with parent screen. */
    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int GAP_BELOW_WIDGET = 10;

    public GuiAddCategory(Screen parent) {
        super(parent, Component.literal("Add Category"));
        this.categoryIndex = -1;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        // Category name field
        this.nameField = new EditBox(this.font, centerX - 100, 32, 200, 20, Component.literal("Category Name"));
        this.nameField.setMaxLength(40);
        this.nameField.setFocused(true);
        this.addRenderableWidget(this.nameField);

        // Search field for icon (with gap below name field)
        this.searchField = new EditBox(this.font, centerX - 100, 68, 200, 16, Component.literal("Search icon..."));
        this.searchField.setMaxLength(40);
        this.searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchField);

        // Confirm and Cancel buttons
        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aConfirm"), btn -> {
            String name = nameField.getValue().trim();
            if (!name.isEmpty() && selectedIcon != null) {
                sendAddCategory(name, selectedIconId);
                closeToParent();
            }
        }).bounds(centerX - 105, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7cCancel"), btn -> {
            closeToParent();
        }).bounds(centerX + 5, this.height - 30, 100, 20).build());
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
        // Fully opaque background to prevent parent screen overlap
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);

        int centerX = this.width / 2;

        guiGraphics.drawCenteredString(this.font, "\u00a76\u00a7lAdd Category", centerX, 8, 0xFFFFFF);
        // Label above name field (y=32)
        guiGraphics.drawCenteredString(this.font, "\u00a77Category Name:", centerX, 20, 0xAAAAAA);
        // Label above search field (y=68)
        guiGraphics.drawCenteredString(this.font, "\u00a77Search for icon block/item:", centerX, 56, 0xAAAAAA);

        // Determine startY for search results - depends on whether icon preview is shown
        int searchFieldBottom = 68 + 16; // y=84
        int startY;

        // Draw selected icon preview with generous gap below search field
        if (selectedIcon != null) {
            startY = searchFieldBottom + GAP_BELOW_WIDGET + 10 + 24 + GAP_BELOW_WIDGET;
            // Icon label
            int previewLabelY = searchFieldBottom + GAP_BELOW_WIDGET;
            guiGraphics.drawCenteredString(this.font, "\u00a7aSelected Icon:", centerX, previewLabelY, 0xAAAAAA);
            // Icon slot
            drawSlot(guiGraphics, centerX - 12, previewLabelY + 10, 24, 24);
            guiGraphics.renderItem(selectedIcon, centerX - 10, previewLabelY + 12);
        } else {
            startY = searchFieldBottom + GAP_BELOW_WIDGET;
        }

        // Draw search results grid
        int availableWidth = SEARCH_COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < SEARCH_COLS * 5 && i + searchScroll * SEARCH_COLS < searchResults.size(); i++) {
            int col = i % SEARCH_COLS;
            int row = i / SEARCH_COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            int index = i + searchScroll * SEARCH_COLS;
            ItemStack stack = searchResults.get(index);
            drawSlot(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
            guiGraphics.renderItem(stack, x + 2, y + 2);
        }

        // Tooltips for search results
        for (int i = 0; i < SEARCH_COLS * 5 && i + searchScroll * SEARCH_COLS < searchResults.size(); i++) {
            int col = i % SEARCH_COLS;
            int row = i / SEARCH_COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * SEARCH_COLS;
                ItemStack stack = searchResults.get(index);
                guiGraphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.nameField != null) this.nameField.onClick(event, doubleClick);
        if (this.searchField != null) this.searchField.onClick(event, doubleClick);

        // Check button clicks first
        for (var widget : this.children()) {
            if (widget instanceof Button btn && btn.mouseClicked(event, doubleClick)) {
                return true;
            }
        }

        // Check search result clicks
        int searchFieldBottom = 68 + 16;
        int startY;

        if (selectedIcon != null) {
            startY = searchFieldBottom + GAP_BELOW_WIDGET + 10 + 24 + GAP_BELOW_WIDGET;
        } else {
            startY = searchFieldBottom + GAP_BELOW_WIDGET;
        }

        int availableWidth = SEARCH_COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < SEARCH_COLS * 5 && i + searchScroll * SEARCH_COLS < searchResults.size(); i++) {
            int col = i % SEARCH_COLS;
            int row = i / SEARCH_COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * SEARCH_COLS;
                selectedIcon = searchResults.get(index).copy();
                selectedIconId = BuiltInRegistries.ITEM.getKey(selectedIcon.getItem()).toString();
                searchField.setValue("");
                searchResults.clear();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        int maxScroll = Math.max(0, (int) Math.ceil((double) searchResults.size() / (double) SEARCH_COLS) - 5);
        if (scrollDeltaY > 0) searchScroll = Math.max(0, searchScroll - 1);
        else if (scrollDeltaY < 0) searchScroll = Math.min(maxScroll, searchScroll + 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESC
            closeToParent();
            return true;
        }
        if (this.nameField != null && this.nameField.isFocused()) {
            return this.nameField.keyPressed(event);
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.nameField != null && this.nameField.isFocused()) {
            return this.nameField.charTyped(event);
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.charTyped(event);
        }
        return super.charTyped(event);
    }

    private void sendAddCategory(String name, String iconItemId) {
        ClientPlayNetworking.send(ShopPacket.addCategory(name, iconItemId));
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
