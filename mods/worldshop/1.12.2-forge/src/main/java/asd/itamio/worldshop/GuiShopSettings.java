package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin settings screen for the Modern Shop mod.
 * Accessible from the categories screen (Settings button).
 * Requires OP level 2 for all operations.
 * Each category/block operation has its own inline selector.
 *
 * 1.12.2-forge adaptation of the 1.20.1-fabric reference.
 */
@SideOnly(Side.CLIENT)
public class GuiShopSettings extends GuiScreen {

    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 22;
    private static final int SELECT_BTN_WIDTH = 50;
    private static final int EXECUTE_BTN_WIDTH = 60;

    private final GuiScreen parentScreen;
    private final List<ShopCategory> categories;

    // Settings state
    private boolean sellhandConfirmation = true;
    private int recalculateCategoryIndex = -1;
    private int resetCategoryIndex = -1;
    private ItemStack recalculateBlock = ItemStack.EMPTY;
    private ItemStack resetBlock = ItemStack.EMPTY;

    // UI state for main content
    private int guiLeft;
    private int guiTop;
    private int xSize;
    private int ySize;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Scrollbar state
    private boolean isDraggingScrollbar = false;
    private int dragStartMouseY = 0;
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

    public GuiShopSettings(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.categories = WorldShop.getCategories();
        // Pull initial toggle state from the server config snapshot (client cache)
        ShopConfig cfg = WorldShop.getShopConfig();
        if (cfg != null) {
            this.sellhandConfirmation = cfg.isSellhandConfirmation();
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.xSize = Math.min(320, this.width - 40);
        this.ySize = Math.min(240, this.height - 40);
        this.guiLeft = (this.width - xSize) / 2;
        this.guiTop = (this.height - ySize) / 2;

        // Calculate max scroll: 7 rows
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
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    private void resetGlState() {
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Panel background
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, BG_COLOR);

        // Title
        this.drawString(this.fontRenderer, "\u00a7l\u00a7eShop Settings", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        this.drawString(this.fontRenderer, "\u00a77[X] Close", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        if (activePicker == PICKER_RECALCULATE_CATEGORY || activePicker == PICKER_RESET_CATEGORY) {
            renderCategoryPicker(mouseX, mouseY, partialTicks);
            return;
        }

        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            renderBlockPicker(mouseX, mouseY, partialTicks);
            return;
        }

        // Render settings content with scroll
        int startY = guiTop + 24;
        int rowX = guiLeft + 8;
        int rowWidth = xSize - 16 - SCROLLBAR_WIDTH - 4;
        int currentY = startY - scrollOffset;

        // Row 1: Sellhand Confirmation toggle
        renderToggleRow(mouseX, mouseY, rowX, currentY, rowWidth);

        // Row 2: Reset Category Order
        renderActionRow(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a7cReset Category Order", "Resets categories to default order");

        // Row 3: Reset All Item Price Calculation
        renderActionRow(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a7cReset All Item Prices", "Clears all cached price calculations");

        // Row 4: Recalculate Category with inline selector
        renderCategoryBlockRow(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Recalculate Category", recalculateCategoryIndex);

        // Row 5: Reset Category with inline selector
        renderCategoryBlockRow(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Reset Category", resetCategoryIndex);

        // Row 6: Recalculate Block with inline selector
        renderBlockRow(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Recalculate Block", recalculateBlock);

        // Row 7: Reset Block with inline selector
        renderBlockRow(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth,
            "\u00a76Reset Block", resetBlock);

        // Draw scrollbar
        if (maxScroll > 0) {
            int totalRows = 7;
            int availableHeight = ySize - 30;
            int contentHeight = totalRows * (ROW_HEIGHT + GAP);
            int maxPages = Math.max(1, (int) Math.ceil((double) contentHeight / (double) availableHeight));
            drawScrollbar(scrollbarX, scrollbarTop, scrollbarHeight, maxPages, mouseX, mouseY);
        }

        this.resetGlState();
    }

    /** Render a toggle row (ON/OFF clickable). */
    private void renderToggleRow(int mouseX, int mouseY, int x, int y, int width) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;
        String toggleText = sellhandConfirmation ? "\u00a7a[ON]" : "\u00a7c[OFF]";
        this.drawString(this.fontRenderer, "\u00a77Sellhand Confirmation: " + toggleText, x + 2, y + 4, TEXT_COLOR);
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        if (hovered && y > guiTop) {
            drawRect(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
            this.drawString(this.fontRenderer, "\u00a77Sellhand Confirmation: " + toggleText, x + 2, y + 4, TEXT_COLOR);
        }
        if (hovered && y > guiTop) {
            String tooltip = sellhandConfirmation
                ? "Click to disable confirmation dialog"
                : "Click to enable confirmation dialog for /sellhand";
            renderTooltipBounded(tooltip, mouseX, mouseY);
        }
    }

    /** Render a simple action button row (full width clickable). */
    private void renderActionRow(int mouseX, int mouseY, int x, int y, int width, String text, String tooltip) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        if (hovered && y > guiTop) {
            drawRect(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
        }
        this.drawString(this.fontRenderer, text, x + 2, y + 4, TEXT_COLOR);
        if (hovered && tooltip != null && y > guiTop) {
            renderTooltipBounded(tooltip, mouseX, mouseY);
        }
    }

    /** Render a row with category inline selector: label + [selected name] [Select] [Execute]. */
    private void renderCategoryBlockRow(int mouseX, int mouseY, int x, int y, int width,
                                        String label, int selectedIndex) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;

        this.drawString(this.fontRenderer, label, x + 2, y + 4, TEXT_COLOR);

        String selectedName = (selectedIndex >= 0 && selectedIndex < categories.size())
            ? categories.get(selectedIndex).getName() : "None";
        String displayText = "[" + selectedName + "]";
        int displayWidth = this.fontRenderer.getStringWidth(displayText);

        int selectBtnX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int selectBtnY = y + 2;
        int selectBtnH = ROW_HEIGHT - 4;

        int execBtnX = x + width - EXECUTE_BTN_WIDTH;
        int execBtnY = y + 2;

        boolean selectHover = mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH
            && mouseY >= selectBtnY && mouseY <= selectBtnY + selectBtnH;
        if (selectHover && y > guiTop) {
            drawRect(selectBtnX, selectBtnY, selectBtnX + SELECT_BTN_WIDTH, selectBtnY + selectBtnH, 0x33FFFFFF);
        }
        this.drawString(this.fontRenderer, "\u00a77[Select]", selectBtnX + 2, selectBtnY + 2, LABEL_COLOR);

        boolean execHover = mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH
            && mouseY >= execBtnY && mouseY <= execBtnY + selectBtnH;
        if (execHover && y > guiTop) {
            drawRect(execBtnX, execBtnY, execBtnX + EXECUTE_BTN_WIDTH, execBtnY + selectBtnH, 0x33FFFFFF);
        }
        String execLabel = (selectedIndex >= 0) ? "\u00a7aExecute" : "\u00a78Execute";
        this.drawString(this.fontRenderer, execLabel, execBtnX + 2, execBtnY + 2, TEXT_COLOR);

        int nameX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - displayWidth - 8;
        if (nameX > x + 100) {
            this.drawString(this.fontRenderer, "\u00a7e" + displayText, nameX, y + 4, TEXT_COLOR);
        }

        if (y > guiTop) {
            String tooltip = (selectedIndex >= 0)
                ? "Selected: " + selectedName + ". Click Execute to run."
                : "Click [Select] to choose a category, then Execute.";
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT) {
                renderTooltipBounded(tooltip, mouseX, mouseY);
            }
        }
    }

    /** Render a row with block inline selector: label + [selected name] [Select] [Execute]. */
    private void renderBlockRow(int mouseX, int mouseY, int x, int y, int width,
                                String label, ItemStack selectedBlock) {
        if (y < guiTop - ROW_HEIGHT || y > guiTop + ySize) return;

        String blockName = (selectedBlock != null && !selectedBlock.isEmpty())
            ? selectedBlock.getDisplayName() : "None";
        this.drawString(this.fontRenderer, label, x + 2, y + 4, TEXT_COLOR);
        String displayText = "[" + blockName + "]";
        int displayWidth = this.fontRenderer.getStringWidth(displayText);

        int selectBtnX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - 4;
        int selectBtnY = y + 2;
        int selectBtnH = ROW_HEIGHT - 4;
        boolean selectHover = mouseX >= selectBtnX && mouseX <= selectBtnX + SELECT_BTN_WIDTH
            && mouseY >= selectBtnY && mouseY <= selectBtnY + selectBtnH;
        if (selectHover && y > guiTop) {
            drawRect(selectBtnX, selectBtnY, selectBtnX + SELECT_BTN_WIDTH, selectBtnY + selectBtnH, 0x33FFFFFF);
        }
        this.drawString(this.fontRenderer, "\u00a77[Select]", selectBtnX + 2, selectBtnY + 2, LABEL_COLOR);

        int execBtnX = x + width - EXECUTE_BTN_WIDTH;
        int execBtnY = y + 2;
        boolean execHover = mouseX >= execBtnX && mouseX <= execBtnX + EXECUTE_BTN_WIDTH
            && mouseY >= execBtnY && mouseY <= execBtnY + selectBtnH;
        if (execHover && y > guiTop) {
            drawRect(execBtnX, execBtnY, execBtnX + EXECUTE_BTN_WIDTH, execBtnY + selectBtnH, 0x33FFFFFF);
        }
        String execLabel = (selectedBlock != null && !selectedBlock.isEmpty()) ? "\u00a7aExecute" : "\u00a78Execute";
        this.drawString(this.fontRenderer, execLabel, execBtnX + 2, execBtnY + 2, TEXT_COLOR);

        int nameX = x + width - SELECT_BTN_WIDTH - EXECUTE_BTN_WIDTH - displayWidth - 8;
        if (nameX > x + 80) {
            this.drawString(this.fontRenderer, "\u00a7e" + displayText, nameX, y + 4, TEXT_COLOR);
        }

        if (y > guiTop) {
            String tooltip = (selectedBlock != null && !selectedBlock.isEmpty())
                ? "Selected: " + blockName + ". Click Execute to run."
                : "Click [Select] to choose a block, then Execute.";
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT) {
                renderTooltipBounded(tooltip, mouseX, mouseY);
            }
        }
    }

    // ========== Category Picker ==========

    private void renderCategoryPicker(int mouseX, int mouseY, float partialTick) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF2A2A2A);
        this.drawString(this.fontRenderer, "\u00a7l\u00a7eSelect Category", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        this.drawString(this.fontRenderer, "\u00a77[X] Cancel", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        int startY = guiTop + 24;
        int rowHeight = 18;
        int visibleRows = (ySize - 30) / (rowHeight + 2);

        for (int i = 0; i < visibleRows && (i + pickerScroll) < categories.size(); i++) {
            int catIndex = i + pickerScroll;
            ShopCategory cat = categories.get(catIndex);
            int y = startY + i * (rowHeight + 2);
            boolean hovered = mouseX >= guiLeft + 8 && mouseX <= guiLeft + xSize - 16 && mouseY >= y && mouseY <= y + rowHeight;
            if (hovered) {
                drawRect(guiLeft + 8, y, guiLeft + xSize - 16, y + rowHeight, 0x33FFFFFF);
            }
            ItemStack icon = cat.getIcon();
            if (icon != null && !icon.isEmpty()) {
                RenderHelper.enableGUIStandardItemLighting();
                Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(icon, guiLeft + 10, y);
                RenderHelper.disableStandardItemLighting();
                this.resetGlState();
            }
            this.drawString(this.fontRenderer, cat.getName(), guiLeft + 30, y + 4, TEXT_COLOR);
        }

        if (categories.size() > visibleRows) {
            int pickerScrollbarX = guiLeft + xSize - SCROLLBAR_WIDTH - 4;
            int pickerScrollbarHeight = ySize - 30;
            int maxPages = Math.max(1, (int) Math.ceil((double) categories.size() / (double) visibleRows));
            drawRect(pickerScrollbarX, startY, pickerScrollbarX + SCROLLBAR_WIDTH, startY + pickerScrollbarHeight, 0xFF333333);
            int thumbHeight = Math.max(8, pickerScrollbarHeight / maxPages);
            int maxScrollPos = pickerScrollbarHeight - thumbHeight;
            int thumbY = startY + (maxPages > 1 ? (pickerScroll * maxScrollPos) / (maxPages - 1) : 0);
            drawRect(pickerScrollbarX, thumbY, pickerScrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF888888);
        }
    }

    // ========== Block Picker ==========

    private void renderBlockPicker(int mouseX, int mouseY, float partialTick) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF2A2A2A);
        this.drawString(this.fontRenderer, "\u00a7l\u00a7eSelect Block", guiLeft + 8, guiTop + 6, TEXT_COLOR);
        this.drawString(this.fontRenderer, "\u00a77[X] Cancel", guiLeft + xSize - 60, guiTop + 6, TEXT_COLOR);

        this.drawString(this.fontRenderer, "\u00a77Search: " + (blockSearchQuery.isEmpty() ? "\u00a78Type to search..." : "\u00a7f" + blockSearchQuery), guiLeft + 8, guiTop + 20, TEXT_COLOR);

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
                drawRect(guiLeft + 8, y, guiLeft + xSize - 16, y + rowHeight, 0x33FFFFFF);
            }
            RenderHelper.enableGUIStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(stack, guiLeft + 10, y);
            RenderHelper.disableStandardItemLighting();
            this.resetGlState();
            this.drawString(this.fontRenderer, stack.getDisplayName(), guiLeft + 30, y + 4, TEXT_COLOR);
        }

        if (searchResults.size() > visibleRows) {
            int pickerScrollbarX = guiLeft + xSize - SCROLLBAR_WIDTH - 4;
            int pickerScrollbarHeight = ySize - 40;
            int maxPages = Math.max(1, (int) Math.ceil((double) searchResults.size() / (double) visibleRows));
            drawRect(pickerScrollbarX, startY, pickerScrollbarX + SCROLLBAR_WIDTH, startY + pickerScrollbarHeight, 0xFF333333);
            int thumbHeight = Math.max(8, pickerScrollbarHeight / maxPages);
            int maxScrollPos = pickerScrollbarHeight - thumbHeight;
            int thumbY = startY + (maxPages > 1 ? (pickerScroll * maxScrollPos) / (maxPages - 1) : 0);
            drawRect(pickerScrollbarX, thumbY, pickerScrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF888888);
        }
    }

    // ========== Mouse Click Handling ==========

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Close button
        if (mouseX >= guiLeft + xSize - 60 && mouseX <= guiLeft + xSize - 8 && mouseY >= guiTop + 2 && mouseY <= guiTop + 14) {
            close();
            return;
        }

        if (activePicker != PICKER_NONE) {
            handlePickerClick(mouseX, mouseY, mouseButton);
            return;
        }

        // Scrollbar drag/click
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
            return;
        }

        int startY = guiTop + 24;
        int rowX = guiLeft + 8;
        int rowWidth = xSize - 16 - SCROLLBAR_WIDTH - 4;
        int currentY = startY - scrollOffset;

        // Row 1: Sellhand Confirmation toggle
        if (mouseX >= rowX && mouseX <= rowX + rowWidth && mouseY >= currentY && mouseY <= currentY + ROW_HEIGHT) {
            sellhandConfirmation = !sellhandConfirmation;
            sendSaveConfigPacket(sellhandConfirmation);
            return;
        }

        // Row 2: Reset Category Order
        if (isRowClicked(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth)) {
            WorldShop.NETWORK.sendToServer(ShopPacket.resetCategoryOrder());
            return;
        }

        // Row 3: Reset All Item Prices
        if (isRowClicked(mouseX, mouseY, rowX, currentY += ROW_HEIGHT + GAP, rowWidth)) {
            WorldShop.NETWORK.sendToServer(ShopPacket.resetAllPrices());
            return;
        }

        // Row 4: Recalculate Category
        currentY += ROW_HEIGHT + GAP;
        if (handleCategoryBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RECALCULATE_CATEGORY, ShopPacket.RECALCULATE_CATEGORY, recalculateCategoryIndex)) return;

        // Row 5: Reset Category
        currentY += ROW_HEIGHT + GAP;
        if (handleCategoryBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RESET_CATEGORY, ShopPacket.RESET_CATEGORY, resetCategoryIndex)) return;

        // Row 6: Recalculate Block
        currentY += ROW_HEIGHT + GAP;
        if (handleBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RECALCULATE_BLOCK, ShopPacket.RECALCULATE_BLOCK, recalculateBlock)) return;

        // Row 7: Reset Block
        currentY += ROW_HEIGHT + GAP;
        if (handleBlockRowClick(mouseX, mouseY, rowX, currentY, rowWidth, PICKER_RESET_BLOCK, ShopPacket.RESET_BLOCK, resetBlock)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isRowClicked(int mouseX, int mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
    }

    private boolean handleCategoryBlockRowClick(int mouseX, int mouseY, int x, int y, int rowWidth,
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
                if (packetType == ShopPacket.RECALCULATE_CATEGORY) {
                    WorldShop.NETWORK.sendToServer(ShopPacket.recalculateCategory(selectedIndex));
                } else if (packetType == ShopPacket.RESET_CATEGORY) {
                    WorldShop.NETWORK.sendToServer(ShopPacket.resetCategory(selectedIndex));
                }
            }
            return true;
        }

        return true;
    }

    private boolean handleBlockRowClick(int mouseX, int mouseY, int x, int y, int rowWidth,
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
                ResourceLocation id = selectedBlock.getItem().getRegistryName();
                String itemId = (id != null) ? id.toString() : "";
                if (!itemId.isEmpty()) {
                    if (packetType == ShopPacket.RECALCULATE_BLOCK) {
                        WorldShop.NETWORK.sendToServer(ShopPacket.recalculateBlock(itemId));
                    } else if (packetType == ShopPacket.RESET_BLOCK) {
                        WorldShop.NETWORK.sendToServer(ShopPacket.resetBlock(itemId));
                    }
                }
            }
            return true;
        }

        return true;
    }

    // ========== Picker Click Handling ==========

    private void handlePickerClick(int mouseX, int mouseY, int button) {
        // Cancel button
        if (mouseX >= guiLeft + xSize - 60 && mouseX <= guiLeft + xSize - 8 && mouseY >= guiTop + 2 && mouseY <= guiTop + 14) {
            activePicker = PICKER_NONE;
            return;
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
                    return;
                }
            }
            handlePickerScrollbarClick(mouseX, mouseY, startY, visibleRows, categories.size());
            return;
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
                    return;
                }
            }
            handlePickerScrollbarClick(mouseX, mouseY, startY, visibleRows, searchResults.size());
            return;
        }
    }

    private void handlePickerScrollbarClick(int mouseX, int mouseY, int startY, int visibleRows, int totalItems) {
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
        if (blockSearchQuery.isEmpty()) return results;
        String query = blockSearchQuery.toLowerCase();
        for (Item item : Item.REGISTRY) {
            ResourceLocation id = item.getRegistryName();
            if (id == null) continue;
            String name = id.toString().toLowerCase();
            ItemStack stack = new ItemStack(item);
            String displayName = stack.getDisplayName().toLowerCase();
            if (name.contains(query) || displayName.contains(query)) {
                results.add(stack);
                if (results.size() >= 100) break;
            }
        }
        return results;
    }

    // ========== Key / Scroll Handlers ==========

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (activePicker == PICKER_RECALCULATE_BLOCK || activePicker == PICKER_RESET_BLOCK) {
            if (keyCode == Keyboard.KEY_BACK && !blockSearchQuery.isEmpty()) {
                blockSearchQuery = blockSearchQuery.substring(0, blockSearchQuery.length() - 1);
                pickerScroll = 0;
                return;
            }
            if (Character.isLetterOrDigit(typedChar) || typedChar == ':' || typedChar == '_' || typedChar == '-' || typedChar == '.' || typedChar == ' ') {
                blockSearchQuery += typedChar;
                pickerScroll = 0;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (activePicker != PICKER_NONE) {
                activePicker = PICKER_NONE;
                return;
            }
            close();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

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
            if (scroll < 0) {
                if (pickerScroll + visibleRows < totalItems) pickerScroll++;
            } else {
                if (pickerScroll > 0) pickerScroll--;
            }
            return;
        }

        // Scroll main settings
        if (scroll < 0) {
            scrollOffset = Math.min(scrollOffset + 1, maxScroll);
        } else {
            scrollOffset = Math.max(scrollOffset - 1, 0);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.isDraggingScrollbar) {
            int totalRows = 7;
            int availableHeight = ySize - 30;
            int contentHeight = totalRows * (ROW_HEIGHT + GAP);
            int maxPages = Math.max(1, (int) Math.ceil((double) contentHeight / (double) availableHeight));
            int deltaY = mouseY - this.dragStartMouseY;
            int thumbHeight = Math.max(8, scrollbarHeight / maxPages);
            int maxScrollPos = scrollbarHeight - thumbHeight;
            if (maxScrollPos > 0) {
                int newOffset = this.dragStartScrollOffset + (int) ((double) deltaY / (double) maxScrollPos * (double) (maxPages - 1));
                this.scrollOffset = Math.max(0, Math.min(maxPages - 1, newOffset));
            }
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.isDraggingScrollbar) {
            this.isDraggingScrollbar = false;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private void close() {
        ScreenManager.closeToParent(parentScreen);
    }

    // ========== Helpers ==========

    private void renderTooltipBounded(String text, int mouseX, int mouseY) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(text);
        int tooltipWidth = this.fontRenderer.getStringWidth(text);
        int tx = Math.min(mouseX, this.width - tooltipWidth - 10);
        int ty = Math.min(mouseY - 12, this.height - 30);
        this.drawHoveringText(tooltip, Math.max(5, tx), Math.max(5, ty));
    }

    private void drawScrollbar(int x, int top, int height, int maxPages, int mouseX, int mouseY) {
        if (maxPages <= 1) return;
        drawRect(x, top, x + SCROLLBAR_WIDTH, top + height, 0xFF333333);
        int thumbHeight = Math.max(8, height / maxPages);
        int maxScrollPos = height - thumbHeight;
        int thumbY = top + (maxPages > 1 ? (this.scrollOffset * maxScrollPos) / (maxPages - 1) : 0);
        int thumbColor = (mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY < thumbY + thumbHeight) ? 0xFFAAAAAA : 0xFF888888;
        drawRect(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
    }

    private void sendSaveConfigPacket(boolean sellhandConfirmation) {
        WorldShop.NETWORK.sendToServer(ShopPacket.saveConfig(sellhandConfirmation));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
