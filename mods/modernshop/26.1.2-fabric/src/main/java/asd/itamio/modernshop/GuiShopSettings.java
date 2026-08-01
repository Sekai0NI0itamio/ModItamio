package asd.itamio.modernshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin settings screen for the Modern Shop mod.
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

    private boolean sellhandConfirmation = true;
    private int recalculateCategoryIndex = -1;
    private int resetCategoryIndex = -1;
    private ItemStack recalculateBlock = null;
    private ItemStack resetBlock = null;

    private int guiLeft;
    private int guiTop;
    private int xSize;
    private int ySize;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private boolean isDraggingScrollbar = false;
    private double dragStartMouseY = 0;
    private int dragStartScrollOffset = 0;
    private static final int SCROLLBAR_WIDTH = 6;
    private int scrollbarX = 0;
    private int scrollbarTop = 0;
    private int scrollbarHeight = 0;

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

        int totalRows = 7;
        int availableHeight = ySize - 30;
        int contentHeight = totalRows * (ROW_HEIGHT + GAP);
        maxScroll = Math.max(0, contentHeight - availableHeight + GAP);
        scrollbarX = guiLeft + xSize - SCROLLBAR_WIDTH - 4;
        scrollbarTop = guiTop + 24;
        scrollbarHeight = ySize - 30 - 10;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, BG_COLOR);

        guiGraphics.text(font, "\u00a7l\u00a7eShop Settings", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        guiGraphics.text(font, "\u00a77[X] Close", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        if (activePicker == PICKER_RECALCULATE_CATEGORY || activePicker == PICKER_RESET_CATEGORY) {
            renderCategoryPicker(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            renderBlockPicker(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        int startY = guiTop + 24;
        int rowX = guiLeft + 8;
        int rowWidth = xSize - 16 - SCROLLBAR_WIDTH - 4;
        int currentY = startY - scrollOffset;

        renderToggleRow(guiGraphics, mouseX, mouseY, rowX, currentY, rowWidth);

        renderActionRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a7cReset Category Order", "Resets categories to default order");

        renderActionRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a7cReset All Item Prices", "Clears all cached price calculations");

        renderCategoryBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Recalculate Category", recalculateCategoryIndex,
            PICKER_RECALCULATE_CATEGORY);

        renderCategoryBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Reset Category", resetCategoryIndex,
            PICKER_RESET_CATEGORY);

        renderBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Recalculate Block", recalculateBlock,
            PICKER_RECALCULATE_BLOCK);

        renderBlockRow(guiGraphics, mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Reset Block", resetBlock,
            PICKER_RESET_BLOCK);

        if (maxScroll > 0) {
            int totalRows = 7;
            int availableHeight = ySize - 30;
            int contentHeight = totalRows * (ROW_HEIGHT + GAP);
            int maxPages = Math.max(1, (int) Math.ceil((double) contentHeight / (double) availableHeight));
            drawScrollbar(guiGraphics, scrollbarX, scrollbarTop, scrollbarHeight, maxPages, mouseX, mouseY);
        }
    }

    private void renderToggleRow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, int x, int y, int width) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;
        String toggleText = sellhandConfirmation ? "\u00a7a[ON]" : "\u00a7c[OFF]";
        guiGraphics.text(font, "\u00a77Sellhand Confirmation: " + toggleText, x + 2, y + 4, TEXT_COLOR);
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        if (hovered && y > guiTop) {
            guiGraphics.fill(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
        }
        if (hovered && y > guiTop) {
            String tooltip = sellhandConfirmation
                ? "Click to disable confirmation dialog"
                : "Click to enable confirmation dialog for /sellhand";
            renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    private void renderActionRow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, int x, int y, int width, String text, String tooltip) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        if (hovered && y > guiTop) {
            guiGraphics.fill(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
        }
        guiGraphics.text(font, text, x + 2, y + 4, TEXT_COLOR);
        if (hovered && tooltip != null && y > guiTop) {
            renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    private void renderCategoryBlockRow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, int x, int y, int width,
                                         String label, int selectedIndex, int pickerType) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;

        guiGraphics.text(font, label, x + 2, y + 4, TEXT_COLOR);

        String selectedName = (selectedIndex >= 0 && selectedIndex < categories.size())
            ? categories.get(selectedIndex).getName() : "None";
        String displayText = "[" + selectedName + "]";
        int displayWidth = font.width(displayText);

        int selectBtnX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int selectBtnY = y + 2;
        int selectBtnH = ROW_HEIGHT - 4;

        int execBtnX = x + width - EXECUTE_BTN_WIDTH;
        int execBtnY = y + 2;

        boolean selectHover = mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH
            && mouseY >= selectBtnY && mouseY <= selectBtnY + selectBtnH;
        if (selectHover && y > guiTop) {
            guiGraphics.fill(selectBtnX, selectBtnY, selectBtnX + SELECT_BTN_WIDTH, selectBtnY + selectBtnH, 0x33FFFFFF);
        }
        guiGraphics.text(font, "\u00a77[Select]", selectBtnX + 2, selectBtnY + 2, LABEL_COLOR);

        boolean execHover = mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH
            && mouseY >= execBtnY && mouseY <= execBtnY + selectBtnH;
        if (execHover && y > guiTop) {
            guiGraphics.fill(execBtnX, execBtnY, execBtnX + EXECUTE_BTN_WIDTH, execBtnY + selectBtnH, 0x33FFFFFF);
        }
        String execLabel = (selectedIndex >= 0) ? "\u00a7aExecute" : "\u00a78Execute";
        guiGraphics.text(font, execLabel, execBtnX + 2, execBtnY + 2, TEXT_COLOR);

        int nameX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - displayWidth - 8;
        if (nameX > x + 100) {
            guiGraphics.text(font, "\u00a7e" + displayText, nameX, y + 4, TEXT_COLOR);
        }

        if (y > guiTop) {
            String tooltip = (selectedIndex >= 0)
                ? "Selected: " + selectedName + ". Click Execute to run."
                : "Click [Select] to choose a category, then Execute.";
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT) {
                renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
            }
        }
    }

    private void renderBlockRow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, int x, int y, int width,
                                 String label, ItemStack selectedBlock, int pickerType) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;

        String blockName = (selectedBlock != null && !selectedBlock.isEmpty())
            ? selectedBlock.getHoverName().getString() : "None";
        guiGraphics.text(font, label, x + 2, y + 4, TEXT_COLOR);
        String displayText = "[" + blockName + "]";
        int displayWidth = font.width(displayText);

        int selectBtnX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int selectBtnY = y + 2;
        int selectBtnH = ROW_HEIGHT - 4;
        boolean selectHover = mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH
            && mouseY >= selectBtnY && mouseY <= selectBtnY + selectBtnH;
        if (selectHover && y > guiTop) {
            guiGraphics.fill(selectBtnX, selectBtnY, selectBtnX + SELECT_BTN_WIDTH, selectBtnY + selectBtnH, 0x33FFFFFF);
        }
        guiGraphics.text(font, "\u00a77[Select]", selectBtnX + 2, selectBtnY + 2, LABEL_COLOR);

        int execBtnX = x + width - EXECUTE_BTN_WIDTH;
        int execBtnY = y + 2;
        boolean execHover = mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH
            && mouseY >= execBtnY && mouseY <= execBtnY + selectBtnH;
        if (execHover && y > guiTop) {
            guiGraphics.fill(execBtnX, execBtnY, execBtnX + EXECUTE_BTN_WIDTH, execBtnY + selectBtnH, 0x33FFFFFF);
        }
        String execLabel = (selectedBlock != null && !selectedBlock.isEmpty()) ? "\u00a7aExecute" : "\u00a78Execute";
        guiGraphics.text(font, execLabel, execBtnX + 2, execBtnY + 2, TEXT_COLOR);

        int nameX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - displayWidth - 8;
        if (nameX > x + 80) {
            guiGraphics.text(font, "\u00a7e" + displayText, nameX, y + 4, TEXT_COLOR);
        }

        if (y > guiTop) {
            String tooltip = (selectedBlock != null && !selectedBlock.isEmpty())
                ? "Selected: " + blockName + ". Click Execute to run."
                : "Click [Select] to choose a block, then Execute.";
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT) {
                renderTooltipBounded(guiGraphics, tooltip, mouseX, mouseY);
            }
        }
    }

    private void renderCategoryPicker(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF2A2A2A);
        guiGraphics.text(font, "\u00a7l\u00a7eSelect Category", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        guiGraphics.text(font, "\u00a77[X] Cancel", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

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
                guiGraphics.item(icon, guiLeft + 10, y);
            }
            guiGraphics.text(font, cat.getName(), guiLeft + 30, y + 4, TEXT_COLOR);
        }

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

    private void renderBlockPicker(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF2A2A2A);
        guiGraphics.text(font, "\u00a7l\u00a7eSelect Block", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        guiGraphics.text(font, "\u00a77[X] Cancel", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        guiGraphics.text(font, "\u00a77Search: " + (blockSearchQuery.isEmpty() ? "\u00a78Type to search..." : "\u00a7f" + blockSearchQuery), guiLeft + 8, guiTop + 20, TEXT_COLOR);

        List<ItemStack> searchResults = buildSearchResults();

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
            guiGraphics.item(stack, guiLeft + 10, y);
            guiGraphics.text(font, stack.getHoverName().getString(), guiLeft + 30, y + 4, TEXT_COLOR);
        }

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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (mouseX >= guiLeft + xSize - 60 && mouseX <= guiLeft + xSize - 8 && mouseY >= guiTop + 2 && mouseY <= guiTop + 14) {
            onClose();
            return true;
        }

        if (activePicker != PICKER_NONE) {
            return handlePickerClick(mouseX, mouseY, button);
        }

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

        if (mouseX >= rowX && mouseX <= rowX + rowWidth && mouseY >= currentY && mouseY <= currentY + ROW_HEIGHT) {
            sellhandConfirmation = !sellhandConfirmation;
            sendSaveConfigPacket(sellhandConfirmation);
            return true;
        }

        if (isRowClicked(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth)) {
            sendSettingsPacket(ShopPacket.RESET_CATEGORY_ORDER, -1, null);
            return true;
        }

        if (isRowClicked(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth)) {
            sendSettingsPacket(ShopPacket.RESET_ALL_PRICES, -1, null);
            return true;
        }

        currentY += ROW_HEIGHT + GAP;
        if (handleCategoryBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RECALCULATE_CATEGORY, ShopPacket.RECALCULATE_CATEGORY, recalculateCategoryIndex)) return true;

        currentY += ROW_HEIGHT + GAP;
        if (handleCategoryBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RESET_CATEGORY, ShopPacket.RESET_CATEGORY, resetCategoryIndex)) return true;

        currentY += ROW_HEIGHT + GAP;
        if (handleBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RECALCULATE_BLOCK, ShopPacket.RECALCULATE_BLOCK, recalculateBlock)) return true;

        currentY += ROW_HEIGHT + GAP;
        if (handleBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RESET_BLOCK, ShopPacket.RESET_BLOCK, resetBlock)) return true;

        return super.mouseClicked(event, doubleClick);
    }

    private boolean isRowClicked(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
    }

    private boolean handleCategoryBlockRowClick(double mouseX, double mouseY, int x, int y, int rowWidth,
                                                 int pickerType, int packetType, int selectedIndex) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return false;
        if (!isRowClicked(mouseX, mouseY, x, y, rowWidth)) return false;

        int selectBtnX = x + rowWidth - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int execBtnX = x + rowWidth - EXECUTE_BTN_WIDTH;
        int btnY = y + 2;
        int btnH = ROW_HEIGHT - 4;

        if (mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            activePicker = pickerType;
            pickerScroll = 0;
            return true;
        }

        if (mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (selectedIndex >= 0 && selectedIndex < categories.size()) {
                sendSettingsPacket(packetType, selectedIndex, null);
            }
            return true;
        }

        return true;
    }

    private boolean handleBlockRowClick(double mouseX, double mouseY, int x, int y, int rowWidth,
                                         int pickerType, int packetType, ItemStack selectedBlock) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return false;
        if (!isRowClicked(mouseX, mouseY, x, y, rowWidth)) return false;

        int selectBtnX = x + rowWidth - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int execBtnX = x + rowWidth - EXECUTE_BTN_WIDTH;
        int btnY = y + 2;
        int btnH = ROW_HEIGHT - 4;

        if (mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            activePicker = pickerType;
            pickerScroll = 0;
            blockSearchQuery = "";
            return true;
        }

        if (mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (selectedBlock != null && !selectedBlock.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.wrapAsHolder(selectedBlock.getItem()).getRegisteredName();
                sendSettingsPacket(packetType, -1, itemId);
            }
            return true;
        }

        return true;
    }

    private boolean handlePickerClick(double mouseX, double mouseY, int button) {
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
                String name = BuiltInRegistries.ITEM.wrapAsHolder(item).getRegisteredName().toLowerCase();
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

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            if (keyCode == 259 && !blockSearchQuery.isEmpty()) {
                blockSearchQuery = blockSearchQuery.substring(0, blockSearchQuery.length() - 1);
                pickerScroll = 0;
                return true;
            }
            return true;
        }
        if (keyCode == 256 || keyCode == 27) {
            if (activePicker != PICKER_NONE) {
                activePicker = PICKER_NONE;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            String s = event.codepointAsString();
            if (s.length() == 1) {
                char codePoint = s.charAt(0);
                if (Character.isLetterOrDigit(codePoint) || codePoint == ':' || codePoint == '_' || codePoint == '-' || codePoint == '.' || codePoint == ' ') {
                    blockSearchQuery += codePoint;
                    pickerScroll = 0;
                    return true;
                }
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        double delta = scrollDeltaY;
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
        if (delta < 0) {
            scrollOffset = Math.min(scrollOffset + 1, maxScroll);
        } else {
            scrollOffset = Math.max(scrollOffset - 1, 0);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
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
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.isDraggingScrollbar) {
            this.isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (parentScreen != null) {
            Minecraft.getInstance().setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    private void renderTooltipBounded(GuiGraphicsExtractor guiGraphics, String text, int mouseX, int mouseY) {
        int tooltipWidth = this.font.width(text);
        int tx = Math.min(mouseX, this.width - tooltipWidth - 10);
        int ty = Math.min(mouseY - 12, this.height - 30);
        List<Component> tooltipList = new ArrayList<>();
        tooltipList.add(Component.literal(text));
        guiGraphics.setTooltipForNextFrame(this.font, tooltipList, java.util.Optional.empty(), Math.max(5, tx), Math.max(5, ty));
    }

    private void drawScrollbar(GuiGraphicsExtractor guiGraphics, int x, int top, int height, int maxPages, int mouseX, int mouseY) {
        if (maxPages <= 1) return;
        guiGraphics.fill(x, top, x + SCROLLBAR_WIDTH, top + height, 0xFF333333);
        int thumbHeight = Math.max(8, height / maxPages);
        int maxScrollPos = height - thumbHeight;
        int thumbY = top + (maxPages > 1 ? (this.scrollOffset * maxScrollPos) / (maxPages - 1) : 0);
        int thumbColor = (mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY < thumbY + thumbHeight) ? 0xFFAAAAAA : 0xFF888888;
        guiGraphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
    }

    private void sendSettingsPacket(int packetType, int categoryIndex, String itemId) {
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
        ClientPlayNetworking.send(pkt);
    }

    private void sendSaveConfigPacket(boolean sellhandConfirmation) {
        ClientPlayNetworking.send(ShopPacket.saveConfig(sellhandConfirmation));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
