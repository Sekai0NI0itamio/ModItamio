package asd.itamio.worldshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin settings screen for the World Shop mod.
 * Accessible from the categories screen (top-left Settings button).
 * Requires OP level 2 for all operations.
 * Each category/block operation has its own inline selector.
 */
public class GuiShopSettings extends Screen {

    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 22;
    private static final int SELECT_BTN_WIDTH = 50;
    private static final int EXECUTE_BTN_WIDTH = 60;

    private final Screen parentScreen;
    private final List<ShopCategory> categories;

    // Settings state
    private boolean sellhandConfirmation = true;
    private int recalculateCategoryIndex = -1;
    private int resetCategoryIndex = -1;
    private ItemStack recalculateBlock = null;
    private ItemStack resetBlock = null;

    // UI state for main content
    private int guiLeft;
    private int guiTop;
    private int xSize;
    private int ySize;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Scrollbar state
    private boolean isDraggingScrollbar = false;
    private double dragStartMouseY = 0;
    private int dragStartScrollOffset = 0;
    private static final int SCROLLBAR_WIDTH = 6;
    private int scrollbarX = 0;
    private int scrollbarTop = 0;
    private int scrollbarHeight = 0;

    // Picker state: which row is currently selecting
    private static final int PICKER_NONE = 0;
    private static final int PICKER_RECALCULATE_CATEGORY = 1;
    private static final int PICKER_RESET_CATEGORY = 2;
    private static final int PICKER_RECALCULATE_BLOCK = 3;
    private static final int PICKER_RESET_BLOCK = 4;
    private int activePicker = PICKER_NONE;
    private int pickerScroll = 0;
    private String blockSearchQuery = "";

    public GuiShopSettings(Screen parentScreen, List<ShopCategory> categories) {
        super(Component.literal("Shop Settings"));
        this.parentScreen = parentScreen;
        this.categories = categories;
    }

    @Override
    protected void init() {
        super.init();
        this.xSize = Math.min(320, this.width - 40);
        this.ySize = Math.min(240, this.height - 40);
        this.guiLeft = (this.width - xSize) / 2;
        this.guiTop = (this.height - ySize) / 2;

        // Calculate max scroll: 7 rows (toggle + 2 buttons + 4 category/block rows)
        int totalRows = 7;
        int availableHeight = ySize - 30;
        int contentHeight = totalRows * (ROW_HEIGHT + GAP);
        maxScroll = Math.max(0, contentHeight - availableHeight + GAP);
        // Scrollbar position
        scrollbarX = guiLeft + xSize - SCROLLBAR_WIDTH - 4;
        scrollbarTop = guiTop + 24;
        scrollbarHeight = ySize - 30 - 10;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Background
        guiGraphics.fill(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, BG_COLOR);

        // Title
        guiGraphics.drawString(font, "\u00a7l\u00a7eShop Settings", guiLeft + 8, guiTop + 6, TEXT_COLOR);

        // Close button (top-right)
        guiGraphics.drawString(font, "\u00a77[X] Close", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        if (activePicker == PICKER_RECALCULATE_CATEGORY || activePicker == PICKER_RESET_CATEGORY) {
            renderCategoryPicker(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            renderBlockPicker(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        // Render settings content with scroll
        int startY = guiTop + 24;
        int rowX = guiLeft + 8;
        int rowWidth = xSize - 16 - SCROLLBAR_WIDTH - 4;
        int currentY = startY - scrollOffset;

        // Row 1: Sellhand Confirmation toggle
        renderToggleRow(guiGraphics, mouseX, mouseY, rowX, currentY, rowWidth);

        // Row 2: Reset Category Order
        renderActionRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a7cReset Category Order", "Resets categories to default order",
            ShopPacket.RESET_CATEGORY_ORDER, -1, null);

        // Row 3: Reset All Item Price Calculation
        renderActionRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a7cReset All Item Prices", "Clears all cached price calculations",
            ShopPacket.RESET_ALL_PRICES, -1, null);

        // Row 4: Recalculate Category with inline selector
        renderCategoryBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Recalculate Category", recalculateCategoryIndex,
            PICKER_RECALCULATE_CATEGORY, ShopPacket.RECALCULATE_CATEGORY);

        // Row 5: Reset Category with inline selector
        renderCategoryBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Reset Category", resetCategoryIndex,
            PICKER_RESET_CATEGORY, ShopPacket.RESET_CATEGORY);

        // Row 6: Recalculate Block with inline selector
        renderBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Recalculate Block", recalculateBlock,
            PICKER_RECALCULATE_BLOCK, ShopPacket.RECALCULATE_BLOCK);

        // Row 7: Reset Block with inline selector
        renderBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Reset Block", resetBlock,
            PICKER_RESET_BLOCK, ShopPacket.RESET_BLOCK);

        // Draw scrollbar
        if (maxScroll > 0) {
            int totalRows = 7;
            int availableHeight = ySize - 30;
            int contentHeight = totalRows * (ROW_HEIGHT + GAP);
            int maxPages = Math.max(1, (int) Math.ceil((double) contentHeight / (double) availableHeight));
            drawScrollbar(guiGraphics, scrollbarX, scrollbarTop, scrollbarHeight, maxPages, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** Render a toggle row (ON/OFF clickable). */
    private void renderToggleRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;
        String toggleText = sellhandConfirmation ? "\u00a7a[ON]" : "\u00a7c[OFF]";
        guiGraphics.drawString(font, "\u00a77Sellhand Confirmation: " + toggleText, x + 2, y + 4, TEXT_COLOR);
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        if (hovered && y > guiTop) {
            guiGraphics.fill(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
        }
        // Tooltip
        if (hovered && y > guiTop) {
            String tooltip = sellhandConfirmation
                ? "Click to disable confirmation dialog"
                : "Click to enable confirmation dialog for /sellhand";
            renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    /** Render a simple action button row (full width clickable). */
    private void renderActionRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, String text, String tooltip, int packetType, int catIdx, String itemId) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        if (hovered && y > guiTop) {
            guiGraphics.fill(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
        }
        guiGraphics.drawString(font, text, x + 2, y + 4, TEXT_COLOR);
        if (hovered && tooltip != null && y > guiTop) {
            renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    /** Render a row with category inline selector: label + [selected name] [Select] [Execute]. */
    private void renderCategoryBlockRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width,
                                         String label, int selectedIndex, int pickerType, int packetType) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;

        // Draw label
        guiGraphics.drawString(font, label, x + 2, y + 4, TEXT_COLOR);

        // Selection area on the right
        String selectedName = (selectedIndex >= 0 && selectedIndex < categories.size())
            ? categories.get(selectedIndex).getName() : "None";
        String displayText = "[" + selectedName + "]";
        int displayWidth = font.width(displayText);

        // [Select] button
        int selectBtnX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int selectBtnY = y + 2;
        int selectBtnH = ROW_HEIGHT - 4;

        // [Execute] button
        int execBtnX = x + width - EXECUTE_BTN_WIDTH;
        int execBtnY = y + 2;

        // Highlight if hovering over select button
        boolean selectHover = mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH
            && mouseY >= selectBtnY && mouseY <= selectBtnY + selectBtnH;
        if (selectHover && y > guiTop) {
            guiGraphics.fill(selectBtnX, selectBtnY, selectBtnX + SELECT_BTN_WIDTH, selectBtnY + selectBtnH, 0x33FFFFFF);
        }
        guiGraphics.drawString(font, "\u00a77[Select]", selectBtnX + 2, selectBtnY + 2, LABEL_COLOR);

        // Highlight if hovering over execute button
        boolean execHover = mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH
            && mouseY >= execBtnY && mouseY <= execBtnY + selectBtnH;
        if (execHover && y > guiTop) {
            guiGraphics.fill(execBtnX, execBtnY, execBtnX + EXECUTE_BTN_WIDTH, execBtnY + selectBtnH, 0x33FFFFFF);
        }
        String execLabel = (selectedIndex >= 0) ? "\u00a7aExecute" : "\u00a78Execute";
        guiGraphics.drawString(font, execLabel, execBtnX + 2, execBtnY + 2, TEXT_COLOR);

        // Draw selected name (between label and buttons)
        int nameX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - displayWidth - 8;
        if (nameX > x + 100) {
            guiGraphics.drawString(font, "\u00a7e" + displayText, nameX, y + 4, TEXT_COLOR);
        }

        // Tooltip on hover
        if (y > guiTop) {
            String tooltip = (selectedIndex >= 0)
                ? "Selected: " + selectedName + ". Click Execute to run."
                : "Click [Select] to choose a category, then Execute.";
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT) {
                renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
            }
        }
    }

    /** Render a row with block inline selector: label + [selected name] [Select] [Execute]. */
    private void renderBlockRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width,
                                 String label, ItemStack selectedBlock, int pickerType, int packetType) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;

        // Draw label
        String blockName = (selectedBlock != null && !selectedBlock.isEmpty())
            ? selectedBlock.getHoverName().getString() : "None";
        guiGraphics.drawString(font, label, x + 2, y + 4, TEXT_COLOR);
        String displayText = "[" + blockName + "]";
        int displayWidth = font.width(displayText);

        // [Select] button
        int selectBtnX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int selectBtnY = y + 2;
        int selectBtnH = ROW_HEIGHT - 4;
        boolean selectHover = mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH
            && mouseY >= selectBtnY && mouseY <= selectBtnY + selectBtnH;
        if (selectHover && y > guiTop) {
            guiGraphics.fill(selectBtnX, selectBtnY, selectBtnX + SELECT_BTN_WIDTH, selectBtnY + selectBtnH, 0x33FFFFFF);
        }
        guiGraphics.drawString(font, "\u00a77[Select]", selectBtnX + 2, selectBtnY + 2, LABEL_COLOR);

        // [Execute] button
        int execBtnX = x + width - EXECUTE_BTN_WIDTH;
        int execBtnY = y + 2;
        boolean execHover = mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH
            && mouseY >= execBtnY && mouseY <= execBtnY + selectBtnH;
        if (execHover && y > guiTop) {
            guiGraphics.fill(execBtnX, execBtnY, execBtnX + EXECUTE_BTN_WIDTH, execBtnY + selectBtnH, 0x33FFFFFF);
        }
        String execLabel = (selectedBlock != null && !selectedBlock.isEmpty()) ? "\u00a7aExecute" : "\u00a78Execute";
        guiGraphics.drawString(font, execLabel, execBtnX + 2, execBtnY + 2, TEXT_COLOR);

        // Draw selected name
        int nameX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - displayWidth - 8;
        if (nameX > x + 80) {
            guiGraphics.drawString(font, "\u00a7e" + displayText, nameX, y + 4, TEXT_COLOR);
        }

        // Tooltip
        if (y > guiTop) {
            String tooltip = (selectedBlock != null && !selectedBlock.isEmpty())
                ? "Selected: " + blockName + ". Click Execute to run."
                : "Click [Select] to choose a block, then Execute.";
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT) {
                renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
            }
        }
    }

    // ========== Category Picker ==========

    private void renderCategoryPicker(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF2A2A2A);
        guiGraphics.drawString(font, "\u00a7l\u00a7eSelect Category", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        guiGraphics.drawString(font, "\u00a77[X] Cancel", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        int startY = guiTop + 24;
        int rowHeight = 18;
        int visibleRows = (ySize - 30) / (rowHeight + 2);

        for (int i = 0; i < visibleRows && (i + pickerScroll) < categories.size(); i++) {
            int catIndex = i + pickerScroll;
            ShopCategory cat = categories.get(catIndex);
            int y = startY + i * (rowHeight + 2);
            boolean hovered = mouseX >= guiLeft + 8 && mouseX <= guiLeft + xSize - 16 && mouseY >= y && mouseY <= y + rowHeight;
            if (hovered) {
                guiGraphics.fill(guiLeft + 8, y, guiLeft + xSize - 16, y + rowHeight, 0x33FFFFFF);
            }
            ItemStack icon = cat.getIcon();
            if (icon != null && !icon.isEmpty()) {
                guiGraphics.renderItem(icon, guiLeft + 10, y);
            }
            guiGraphics.drawString(font, cat.getName(), guiLeft + 30, y + 4, TEXT_COLOR);
        }

        // Draw scrollbar for category picker
        if (categories.size() > visibleRows) {
            int pickerScrollbarX = guiLeft + xSize - SCROLLBAR_WIDTH - 4;
            int pickerScrollbarHeight = ySize - 30;
            int maxPages = Math.max(1, (int) Math.ceil((double) categories.size() / (double) visibleRows));
            guiGraphics.fill(pickerScrollbarX, startY, pickerScrollbarX + SCROLLBAR_WIDTH, startY + pickerScrollbarHeight, 0xFF333333);
            int thumbHeight = Math.max(8, pickerScrollbarHeight / maxPages);
            int maxScrollPos = pickerScrollbarHeight - thumbHeight;
            int thumbY = startY + (maxPages > 1 ? (pickerScroll * maxScrollPos) / (maxPages - 1) : 0);
            guiGraphics.fill(pickerScrollbarX, thumbY, pickerScrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF888888);
        }
    }

    // ========== Block Picker ==========

    private void renderBlockPicker(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF2A2A2A);
        guiGraphics.drawString(font, "\u00a7l\u00a7eSelect Block", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        guiGraphics.drawString(font, "\u00a77[X] Cancel", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        // Search label
        guiGraphics.drawString(font, "\u00a77Search: " + (blockSearchQuery.isEmpty() ? "\u00a78Type to search..." : "\u00a7f" + blockSearchQuery), guiLeft + 8, guiTop + 20, TEXT_COLOR);

        // Build search results
        List<ItemStack> searchResults = new ArrayList<>();
        if (!blockSearchQuery.isEmpty()) {
            String query = blockSearchQuery.toLowerCase();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String name = id.toString().toLowerCase();
                ItemStack stack = new ItemStack(item);
                String displayName = stack.getHoverName().getString().toLowerCase();
                if (name.contains(query) || displayName.contains(query)) {
                    searchResults.add(stack);
                    if (searchResults.size() >= 100) break;
                }
            }
        }

        // Render results
        int startY = guiTop + 36;
        int rowHeight = 18;
        int visibleRows = (ySize - 40) / (rowHeight + 2);

        for (int i = 0; i < visibleRows && (i + pickerScroll) < searchResults.size(); i++) {
            int resultIndex = i + pickerScroll;
            ItemStack stack = searchResults.get(resultIndex);
            int y = startY + i * (rowHeight + 2);
            boolean hovered = mouseX >= guiLeft + 8 && mouseX <= guiLeft + xSize - 16 && mouseY >= y && mouseY <= y + rowHeight;
            if (hovered) {
                guiGraphics.fill(guiLeft + 8, y, guiLeft + xSize - 16, y + rowHeight, 0x33FFFFFF);
            }
            guiGraphics.renderItem(stack, guiLeft + 10, y);
            guiGraphics.drawString(font, stack.getHoverName().getString(), guiLeft + 30, y + 4, TEXT_COLOR);
        }

        // Draw scrollbar for block picker
        if (searchResults.size() > visibleRows) {
            int pickerScrollbarX = guiLeft + xSize - SCROLLBAR_WIDTH - 4;
            int pickerScrollbarHeight = ySize - 40;
            int maxPages = Math.max(1, (int) Math.ceil((double) searchResults.size() / (double) visibleRows));
            guiGraphics.fill(pickerScrollbarX, startY, pickerScrollbarX + SCROLLBAR_WIDTH, startY + pickerScrollbarHeight, 0xFF333333);
            int thumbHeight = Math.max(8, pickerScrollbarHeight / maxPages);
            int maxScrollPos = pickerScrollbarHeight - thumbHeight;
            int thumbY = startY + (maxPages > 1 ? (pickerScroll * maxScrollPos) / (maxPages - 1) : 0);
            guiGraphics.fill(pickerScrollbarX, thumbY, pickerScrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF888888);
        }
    }

    // ========== Mouse Click Handling ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Close button
        if (mouseX >= guiLeft + xSize - 60 && mouseX <= guiLeft + xSize - 8 && mouseY >= guiTop + 2 && mouseY <= guiTop + 14) {
            onClose();
            return true;
        }

        if (activePicker != PICKER_NONE) {
            return handlePickerClick(mouseX, mouseY, button);
        }

        // Check scrollbar drag
        if (maxScroll > 0 && mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_WIDTH
            && mouseY >= scrollbarTop && mouseY < scrollbarTop + scrollbarHeight) {
            int totalRows = 7;
            int availableHeight = ySize - 30;
            int contentHeight = totalRows * (ROW_HEIGHT + GAP);
            int maxPages = Math.max(1, (int) Math.ceil((double) contentHeight / (double) availableHeight));
            int thumbHeight = Math.max(8, scrollbarHeight / maxPages);
            int maxScrollPos = scrollbarHeight - thumbHeight;
            int thumbY = scrollbarTop + (maxPages > 1 ? (this.scrollOffset * maxScrollPos) / (maxPages - 1) : 0);

            if (mouseY < thumbY) {
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            } else if (mouseY > thumbY + thumbHeight) {
                this.scrollOffset = Math.min(maxPages - 1, this.scrollOffset + 1);
            } else {
                this.isDraggingScrollbar = true;
                this.dragStartMouseY = mouseY;
                this.dragStartScrollOffset = this.scrollOffset;
            }
            return true;
        }

        int startY = guiTop + 24;
        int rowX = guiLeft + 8;
        int rowWidth = xSize - 16 - SCROLLBAR_WIDTH - 4;
        int currentY = startY - scrollOffset;

        // Row 1: Sellhand Confirmation toggle
        if (mouseX >= rowX && mouseX <= rowX + rowWidth && mouseY >= currentY && mouseY <= currentY + ROW_HEIGHT) {
            sellhandConfirmation = !sellhandConfirmation;
            sendSaveConfigPacket(sellhandConfirmation);
            return true;
        }

        // Row 2: Reset Category Order
        if (isRowClicked(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth)) {
            sendSettingsPacket(ShopPacket.RESET_CATEGORY_ORDER, -1, null);
            return true;
        }

        // Row 3: Reset All Item Prices
        if (isRowClicked(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth)) {
            sendSettingsPacket(ShopPacket.RESET_ALL_PRICES, -1, null);
            return true;
        }

        // Row 4: Recalculate Category
        currentY += ROW_HEIGHT + GAP;
        if (handleCategoryBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RECALCULATE_CATEGORY, ShopPacket.RECALCULATE_CATEGORY, recalculateCategoryIndex)) return true;

        // Row 5: Reset Category
        currentY += ROW_HEIGHT + GAP;
        if (handleCategoryBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RESET_CATEGORY, ShopPacket.RESET_CATEGORY, resetCategoryIndex)) return true;

        // Row 6: Recalculate Block
        currentY += ROW_HEIGHT + GAP;
        if (handleBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RECALCULATE_BLOCK, ShopPacket.RECALCULATE_BLOCK, recalculateBlock)) return true;

        // Row 7: Reset Block
        currentY += ROW_HEIGHT + GAP;
        if (handleBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RESET_BLOCK, ShopPacket.RESET_BLOCK, resetBlock)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isRowClicked(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
    }

    /**
     * Handle click on a category select/execute row.
     * Returns true if the click was handled.
     */
    private boolean handleCategoryBlockRowClick(double mouseX, double mouseY, int x, int y, int rowWidth,
                                                 int pickerType, int packetType, int selectedIndex) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return false;
        if (!isRowClicked(mouseX, mouseY, x, y, rowWidth)) return false;

        int selectBtnX = x + rowWidth - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int execBtnX = x + rowWidth - EXECUTE_BTN_WIDTH;
        int btnY = y + 2;
        int btnH = ROW_HEIGHT - 4;

        // Click on [Select] button
        if (mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            activePicker = pickerType;
            pickerScroll = 0;
            return true;
        }

        // Click on [Execute] button
        if (mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (selectedIndex >= 0 && selectedIndex < categories.size()) {
                sendSettingsPacket(packetType, selectedIndex, null);
            }
            return true;
        }

        return true;
    }

    /**
     * Handle click on a block select/execute row.
     */
    private boolean handleBlockRowClick(double mouseX, double mouseY, int x, int y, int rowWidth,
                                         int pickerType, int packetType, ItemStack selectedBlock) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return false;
        if (!isRowClicked(mouseX, mouseY, x, y, rowWidth)) return false;

        int selectBtnX = x + rowWidth - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int execBtnX = x + rowWidth - EXECUTE_BTN_WIDTH;
        int btnY = y + 2;
        int btnH = ROW_HEIGHT - 4;

        // Click on [Select] button
        if (mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            activePicker = pickerType;
            pickerScroll = 0;
            blockSearchQuery = "";
            return true;
        }

        // Click on [Execute] button
        if (mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (selectedBlock != null && !selectedBlock.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(selectedBlock.getItem()).toString();
                sendSettingsPacket(packetType, -1, itemId);
            }
            return true;
        }

        return true;
    }

    // ========== Picker Click Handling ==========

    private boolean handlePickerClick(double mouseX, double mouseY, int button) {
        // Cancel button
        if (mouseX >= guiLeft + xSize - 60 && mouseX <= guiLeft + xSize - 8 && mouseY >= guiTop + 2 && mouseY <= guiTop + 14) {
            activePicker = PICKER_NONE;
            return true;
        }

        if (activePicker == PICKER_RECALCULATE_CATEGORY || activePicker == PICKER_RESET_CATEGORY) {
            int startY = guiTop + 24;
            int rowHeight = 18;
            int visibleRows = (ySize - 30) / (rowHeight + 2);
            for (int i = 0; i < visibleRows && (i + pickerScroll) < categories.size(); i++) {
                int y = startY + i * (rowHeight + 2);
                if (mouseX >= guiLeft + 8 && mouseX <= guiLeft + xSize - 16 && mouseY >= y && mouseY <= y + rowHeight) {
                    int selected = i + pickerScroll;
                    if (activePicker == PICKER_RECALCULATE_CATEGORY) {
                        recalculateCategoryIndex = selected;
                    } else {
                        resetCategoryIndex = selected;
                    }
                    activePicker = PICKER_NONE;
                    return true;
                }
            }
            // Scroll click on picker scrollbar
            handlePickerScrollbarClick(mouseX, mouseY, startY, visibleRows, categories.size());
            return true;
        }

        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            int startY = guiTop + 36;
            int rowHeight = 18;
            List<ItemStack> searchResults = buildSearchResults();
            int visibleRows = (ySize - 40) / (rowHeight + 2);
            for (int i = 0; i < visibleRows && (i + pickerScroll) < searchResults.size(); i++) {
                int y = startY + i * (rowHeight + 2);
                if (mouseX >= guiLeft + 8 && mouseX <= guiLeft + xSize - 16 && mouseY >= y && mouseY <= y + rowHeight) {
                    ItemStack selected = searchResults.get(i + pickerScroll);
                    if (activePicker == PICKER_RECALCULATE_BLOCK) {
                        recalculateBlock = selected;
                    } else {
                        resetBlock = selected;
                    }
                    activePicker = PICKER_NONE;
                    return true;
                }
            }
            // Scroll click on picker scrollbar
            handlePickerScrollbarClick(mouseX, mouseY, startY, visibleRows, searchResults.size());
            return true;
        }

        return true;
    }

    private void handlePickerScrollbarClick(double mouseX, double mouseY, int startY, int visibleRows, int totalItems) {
        int pickerScrollbarX = guiLeft + xSize - SCROLLBAR_WIDTH - 4;
        int pickerScrollbarHeight = ySize - 30;
        if (totalItems > visibleRows && mouseX >= pickerScrollbarX && mouseX < pickerScrollbarX + SCROLLBAR_WIDTH
            && mouseY >= startY && mouseY < startY + pickerScrollbarHeight) {
            int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / (double) visibleRows));
            int thumbHeight = Math.max(8, pickerScrollbarHeight / maxPages);
            int maxScrollPos = pickerScrollbarHeight - thumbHeight;
            int thumbY = startY + (maxPages > 1 ? (pickerScroll * maxScrollPos) / (maxPages - 1) : 0);
            if (mouseY < thumbY) {
                pickerScroll = Math.max(0, pickerScroll - 1);
            } else if (mouseY > thumbY + thumbHeight) {
                pickerScroll = Math.min(maxPages - 1, pickerScroll + 1);
            }
        }
    }

    private List<ItemStack> buildSearchResults() {
        List<ItemStack> results = new ArrayList<>();
        if (!blockSearchQuery.isEmpty()) {
            String query = blockSearchQuery.toLowerCase();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String name = id.toString().toLowerCase();
                ItemStack stack = new ItemStack(item);
                String displayName = stack.getHoverName().getString().toLowerCase();
                if (name.contains(query) || displayName.contains(query)) {
                    results.add(stack);
                    if (results.size() >= 100) break;
                }
            }
        }
        return results;
    }

    // ========== Key / Scroll Handlers ==========

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            if (keyCode == 259 && !blockSearchQuery.isEmpty()) { // Backspace
                blockSearchQuery = blockSearchQuery.substring(0, blockSearchQuery.length() - 1);
                pickerScroll = 0;
                return true;
            }
            return true;
        }
        if (keyCode == 256 || keyCode == 27) { // Escape
            if (activePicker != PICKER_NONE) {
                activePicker = PICKER_NONE;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == ':' || codePoint == '_' || codePoint == '-' || codePoint == '.' || codePoint == ' ') {
                blockSearchQuery += codePoint;
                pickerScroll = 0;
                return true;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activePicker != PICKER_NONE) {
            int totalItems;
            int visibleRows;
            if (activePicker == PICKER_RECALCULATE_CATEGORY || activePicker == PICKER_RESET_CATEGORY) {
                totalItems = categories.size();
                visibleRows = (ySize - 30) / (18 + 2);
            } else {
                totalItems = buildSearchResults().size();
                visibleRows = (ySize - 40) / (18 + 2);
            }
            if (delta < 0) {
                if (pickerScroll + visibleRows < totalItems) pickerScroll++;
            } else {
                if (pickerScroll > 0) pickerScroll--;
            }
            return true;
        }
        // Scroll main settings
        if (delta < 0) {
            scrollOffset = Math.min(scrollOffset + 1, maxScroll);
        } else {
            scrollOffset = Math.max(scrollOffset - 1, 0);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScrollbar) {
            int totalRows = 7;
            int availableHeight = ySize - 30;
            int contentHeight = totalRows * (ROW_HEIGHT + GAP);
            int maxPages = Math.max(1, (int) Math.ceil((double) contentHeight / (double) availableHeight));
            double deltaY = mouseY - this.dragStartMouseY;
            int thumbHeight = Math.max(8, scrollbarHeight / maxPages);
            int maxScrollPos = scrollbarHeight - thumbHeight;
            if (maxScrollPos > 0) {
                int newOffset = this.dragStartScrollOffset + (int) (deltaY / maxScrollPos * (maxPages - 1));
                this.scrollOffset = Math.max(0, Math.min(maxPages - 1, newOffset));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDraggingScrollbar) {
            this.isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (parentScreen != null) {
            Minecraft.getInstance().setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    // ========== Helpers ==========

    /** Render a tooltip positioned to stay within screen bounds. */
    private void renderTooltipBounded(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        int tooltipWidth = this.font.width(text);
        int tx = Math.min(mouseX, this.width - tooltipWidth - 10);
        int ty = Math.min(mouseY - 12, this.height - 30);
        guiGraphics.renderTooltip(this.font, Component.literal(text), Math.max(5, tx), Math.max(5, ty));
    }

    /** Draw a vertical scrollbar track and thumb. */
    private void drawScrollbar(GuiGraphics guiGraphics, int x, int top, int height, int maxPages, int mouseX, int mouseY) {
        if (maxPages <= 1) return;
        guiGraphics.fill(x, top, x + SCROLLBAR_WIDTH, top + height, 0xFF333333);
        int thumbHeight = Math.max(8, height / maxPages);
        int maxScrollPos = height - thumbHeight;
        int thumbY = top + (maxPages > 1 ? (this.scrollOffset * maxScrollPos) / (maxPages - 1) : 0);
        int thumbColor = (mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY < thumbY + thumbHeight) ? 0xFFAAAAAA : 0xFF888888;
        guiGraphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
    }

    private void sendSettingsPacket(int packetType, int categoryIndex, String itemId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ShopPacket pkt;
        switch (packetType) {
            case ShopPacket.RESET_CATEGORY_ORDER:
                pkt = ShopPacket.resetCategoryOrder();
                break;
            case ShopPacket.RESET_ALL_PRICES:
                pkt = ShopPacket.resetAllPrices();
                break;
            case ShopPacket.RECALCULATE_CATEGORY:
                pkt = ShopPacket.recalculateCategory(categoryIndex);
                break;
            case ShopPacket.RESET_CATEGORY:
                pkt = ShopPacket.resetCategory(categoryIndex);
                break;
            case ShopPacket.RECALCULATE_BLOCK:
                pkt = ShopPacket.recalculateBlock(itemId);
                break;
            case ShopPacket.RESET_BLOCK:
                pkt = ShopPacket.resetBlock(itemId);
                break;
            default:
                return;
        }
        ShopPacket.write(pkt, buf);
        ClientPlayNetworking.send(ShopPacket.PACKET_ID, buf);
    }

    private void sendSaveConfigPacket(boolean sellhandConfirmation) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ShopPacket.write(ShopPacket.saveConfig(sellhandConfirmation), buf);
        ClientPlayNetworking.send(ShopPacket.PACKET_ID, buf);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}