package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiShopItems extends GuiScreen {
    private final ShopCategory category;
    private final int categoryIndex;
    private final boolean adminMode;
    private final List<ItemStack> items;
    private List<ItemStack> filteredItems;
    private int scrollOffset = 0;
    private static final int SLOT_SIZE = 22;
    private static final int SPACING = 4;
    private static final int COLUMNS = 9;
    private static final int BOTTOM_BAR_HEIGHT = 50;

    private boolean detailView = false;
    private int detailItemIndex = -1;
    private int pendingDetailIndex = -1;
    private GuiTextField quantityField;
    private GuiTextField searchField;
    private String searchQuery = "";
    private boolean stackMode = false;

    public GuiShopItems(ShopCategory category, int categoryIndex, boolean adminMode) {
        this.category = category;
        this.categoryIndex = categoryIndex;
        this.adminMode = adminMode;
        this.items = category.getItems();
        this.filteredItems = new ArrayList<>(items);
    }

    public GuiShopItems(ShopCategory category, int categoryIndex) {
        this(category, categoryIndex, false);
    }

    public void setPendingDetail(int itemIndex) {
        this.pendingDetailIndex = itemIndex;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int searchW = 120;
        int searchH = 14;
        this.searchField = new GuiTextField(1, this.fontRenderer, this.width - searchW - 8, 8, searchW, searchH);
        this.searchField.setText(searchQuery);
        this.searchField.setMaxStringLength(32);

        if (pendingDetailIndex >= 0 && pendingDetailIndex < filteredItems.size()) {
            this.detailView = true;
            this.detailItemIndex = pendingDetailIndex;
            this.pendingDetailIndex = -1;
            this.stackMode = false;
        }

        rebuildButtons();
    }

    private void rebuildButtons() {
        this.buttonList.clear();
        if (detailView) {
            int btnW = 95;
            int btnH = 20;
            int centerX = this.width / 2;
            int bottomY = this.height - 10;
            this.buttonList.add(new GuiButton(0, centerX - btnW - 2, bottomY - btnH * 2 - 8, btnW, btnH, "\u00a7aBuy"));
            this.buttonList.add(new GuiButton(1, centerX + 2, bottomY - btnH * 2 - 8, btnW, btnH, "\u00a7cBack"));
            this.buttonList.add(new GuiButton(2, centerX - btnW - 2, bottomY - btnH - 4, btnW, btnH, stackMode ? "\u00a77Mode: Stacks" : "\u00a77Mode: Items"));
            this.buttonList.add(new GuiButton(3, centerX + 2, bottomY - btnH - 4, btnW, btnH, "\u00a7eMax Afford"));

            int fieldW = 100;
            int fieldH = 20;
            this.quantityField = new GuiTextField(10, this.fontRenderer, centerX - fieldW / 2, 0, fieldW, fieldH);
            this.quantityField.setText("1");
            this.quantityField.setFocused(true);
            this.quantityField.setMaxStringLength(5);

            if (adminMode) {
                this.buttonList.add(new GuiButton(5, 4, bottomY - btnH - 4, 80, btnH, "\u00a7cRemove"));
                this.buttonList.add(new GuiButton(6, 4, bottomY - btnH * 2 - 8, 80, btnH, "\u00a7eEdit"));
            }
        } else {
            this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 22, 200, 20, "\u00a7cBack to Categories"));
            if (adminMode) {
                this.buttonList.add(new GuiButton(4, 4, this.height - 22, 80, 20, "\u00a7aAdd Item"));
            }
        }
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

    private void updateFilteredItems() {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            filteredItems = new ArrayList<>(items);
        } else {
            String query = searchQuery.toLowerCase().trim();
            filteredItems = new ArrayList<>();
            for (ItemStack item : items) {
                if (item.getDisplayName().toLowerCase().contains(query)) {
                    filteredItems.add(item);
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawRect(0, 0, this.width, this.height, -870441442);
        GlStateManager.disableBlend();

        if (detailView) {
            drawDetailView(mouseX, mouseY);
        } else {
            drawGridView(mouseX, mouseY);
        }

        this.resetGlState();
        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }

        if (!detailView && this.searchField != null) {
            this.searchField.drawTextBox();
        }
    }

    private void drawGridView(int mouseX, int mouseY) {
        String title = "\u00a76\u00a7lShop - " + formatCategoryName(this.category.getName());
        if (adminMode) title += " \u00a7c[ADMIN]";
        this.drawCenteredString(this.fontRenderer, title, this.width / 2, 8, 0xFFFFFF);

        if (this.searchField != null) {
            this.drawString(this.fontRenderer, "\u00a77Search:", this.width - 134, 12, 0xAAAAAA);
        }

        int cellSize = 26;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && startIndex + i < this.filteredItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            int itemIndex = startIndex + i;
            ItemStack item = this.filteredItems.get(itemIndex);
            drawSlotBackground(x, y, SLOT_SIZE, SLOT_SIZE);
            renderItem(item, x + 3, y + 3);
        }

        this.resetGlState();

        for (int i = 0; i < visibleCount && startIndex + i < this.filteredItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            int itemIndex = startIndex + i;
            ItemStack item = this.filteredItems.get(itemIndex);
            int originalIndex = this.items.indexOf(item);
            PriceEngine priceEngine = WorldShop.getPriceEngine();
            double buyPrice = priceEngine.getBuyPrice(item);
            double sellPrice = priceEngine.getSellPrice(item);
            List<String> tooltip = new ArrayList<>();
            tooltip.add("\u00a7f" + item.getDisplayName());
            tooltip.add("\u00a7aBuy: $" + String.format("%.2f", buyPrice));
            tooltip.add("\u00a7cSell: $" + String.format("%.2f", sellPrice));
            tooltip.add("");
            tooltip.add("\u00a7eLeft-click: Buy menu");
            tooltip.add("\u00a7bRight-click: Quick buy stack");
            if (adminMode) {
                tooltip.add("\u00a7c[ADMIN] Shift+Click: Remove item");
            }
            this.drawHoveringText(tooltip, mouseX, mouseY);
            break;
        }

        this.resetGlState();

        int barTop = this.height - BOTTOM_BAR_HEIGHT;
        drawRect(0, barTop, this.width, this.height, -14013910);

        if (this.filteredItems.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            this.drawCenteredString(this.fontRenderer, scrollInfo, this.width / 2, barTop + 2, 0xFFFFFF);
        }
        String footer = "\u00a77Left-click: Buy menu | Right-click: Quick buy stack | ESC: Back";
        this.drawCenteredString(this.fontRenderer, footer, this.width / 2, barTop + 14, 0xAAAAAA);
    }

    private void drawDetailView(int mouseX, int mouseY) {
        if (detailItemIndex < 0 || detailItemIndex >= this.filteredItems.size()) {
            return;
        }
        ItemStack item = this.filteredItems.get(detailItemIndex);
        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double buyPricePerItem = priceEngine.getBuyPrice(item);
        double sellPricePerItem = priceEngine.getSellPrice(item);

        int centerX = this.width / 2;
        int y = 10;
        String title = "\u00a76\u00a7l" + item.getDisplayName();
        this.drawCenteredString(this.fontRenderer, title, centerX, y, 0xFFFFFF);

        y += 16;
        int itemCenterY = y + 24;
        drawSlotBackground(centerX - 24, itemCenterY - 24, 48, 48);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) (centerX - 8), (float) (itemCenterY - 8), 0.0f);
        GlStateManager.scale(2.0f, 2.0f, 2.0f);
        Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(item, 0, 0);
        GlStateManager.popMatrix();
        this.resetGlState();

        y = itemCenterY + 30;
        this.drawCenteredString(this.fontRenderer, "\u00a7aBuy: $" + String.format("%.2f", buyPricePerItem) + " each", centerX, y, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "\u00a7cSell: $" + String.format("%.2f", sellPricePerItem) + " each", centerX, y += 12, 0xFFFFFF);

        String modeLabel = stackMode ? "\u00a77Quantity (stacks):" : "\u00a77Quantity (items):";
        this.drawCenteredString(this.fontRenderer, modeLabel, centerX, y += 18, 0xCCCCCC);
        y += 14;

        if (this.quantityField != null) {
            this.quantityField.y = y;
            this.quantityField.drawTextBox();
        }

        int qty = getQuantity();
        int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
        double totalCost = buyPricePerItem * (double) actualItems;
        this.drawCenteredString(this.fontRenderer, "\u00a7eTotal: " + actualItems + " items = $" + String.format("%.2f", totalCost), centerX, y += 24, 0xFFFF55);

        y += 12;
        double balance = getClientBalance();
        int maxAfford = (int) (balance / buyPricePerItem);
        if (stackMode) {
            int maxStacks = maxAfford / item.getMaxStackSize();
            this.drawCenteredString(this.fontRenderer, "\u00a77You can afford: " + maxStacks + " stacks (" + maxAfford + " items)", centerX, y, 0xAAAAAA);
        } else {
            this.drawCenteredString(this.fontRenderer, "\u00a77You can afford: " + maxAfford + " items", centerX, y, 0xAAAAAA);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!detailView && this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        for (GuiButton button : this.buttonList) {
            if (!button.mousePressed(this.mc, mouseX, mouseY)) continue;
            button.playPressSound(this.mc.getSoundHandler());
            this.actionPerformed(button);
            return;
        }

        if (detailView) {
            if (this.quantityField != null) {
                this.quantityField.mouseClicked(mouseX, mouseY, mouseButton);
            }
            return;
        }

        int cellSize = 26;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && startIndex + i < this.filteredItems.size(); i++) {
            int col = i % COLUMNS;
            int x = guiLeft + col * cellSize;
            int row = i / COLUMNS;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            int itemIndex = startIndex + i;
            ItemStack clickedItem = this.filteredItems.get(itemIndex);
            int originalIndex = this.items.indexOf(clickedItem);

            if (adminMode && isShiftKeyDown()) {
                WorldShop.NETWORK.sendToServer(ShopPacket.removeItem(this.categoryIndex, originalIndex));
                return;
            }

            if (mouseButton == 0) {
                this.detailView = true;
                this.detailItemIndex = itemIndex;
                this.stackMode = false;
                rebuildButtons();
            } else if (mouseButton == 1) {
                quickBuyStack(itemIndex);
            }
            return;
        }
    }

    private void quickBuyStack(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= this.filteredItems.size()) {
            return;
        }
        ItemStack item = this.filteredItems.get(itemIndex);
        int originalIndex = this.items.indexOf(item);
        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double buyPrice = priceEngine.getBuyPrice(item);
        double balance = getClientBalance();
        int maxStackSize = item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);
        if (quantity <= 0) {
            return;
        }
        WorldShop.NETWORK.sendToServer(ShopPacket.buyItem(this.categoryIndex, originalIndex, quantity));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (detailView) {
            if (button.id == 0) {
                int qty = getQuantity();
                if (qty > 0 && detailItemIndex >= 0 && detailItemIndex < this.filteredItems.size()) {
                    ItemStack item = this.filteredItems.get(detailItemIndex);
                    int originalIndex = this.items.indexOf(item);
                    int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
                    WorldShop.NETWORK.sendToServer(ShopPacket.buyItem(this.categoryIndex, originalIndex, actualItems));
                }
            } else if (button.id == 1) {
                this.detailView = false;
                this.detailItemIndex = -1;
                rebuildButtons();
            } else if (button.id == 2) {
                this.stackMode = !this.stackMode;
                if (this.quantityField != null) {
                    this.quantityField.setText("1");
                }
                rebuildButtons();
            } else if (button.id == 3 && detailItemIndex >= 0 && detailItemIndex < this.filteredItems.size()) {
                ItemStack item = this.filteredItems.get(detailItemIndex);
                PriceEngine priceEngine = WorldShop.getPriceEngine();
                double buyPrice = priceEngine.getBuyPrice(item);
                double balance = getClientBalance();
                int maxAfford = (int) (balance / buyPrice);
                if (stackMode) {
                    int maxStacks = maxAfford / item.getMaxStackSize();
                    if (this.quantityField != null) {
                        this.quantityField.setText(String.valueOf(maxStacks));
                    }
                } else if (this.quantityField != null) {
                    this.quantityField.setText(String.valueOf(maxAfford));
                }
            } else if (button.id == 5 && detailItemIndex >= 0 && detailItemIndex < this.filteredItems.size()) {
                // Remove item (admin)
                ItemStack item = this.filteredItems.get(detailItemIndex);
                int originalIndex = this.items.indexOf(item);
                WorldShop.NETWORK.sendToServer(ShopPacket.removeItem(this.categoryIndex, originalIndex));
                this.detailView = false;
                this.detailItemIndex = -1;
                rebuildButtons();
            } else if (button.id == 6 && detailItemIndex >= 0 && detailItemIndex < this.filteredItems.size()) {
                // Edit item (admin)
                ItemStack item = this.filteredItems.get(detailItemIndex);
                String itemId = item.getItem().getRegistryName().toString();
                ScreenManager.open(new GuiEditItem(this, this.categoryIndex, itemId));
            }
        } else {
            if (button.id == 0) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiShopCategories(adminMode));
            } else if (button.id == 4) {
                // Add item (admin)
                ScreenManager.open(new GuiAddItem(this, this.categoryIndex));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (keyCode == 28) {
                this.actionPerformed(this.buttonList.get(0));
                return;
            }
            if (Character.isDigit(typedChar) || keyCode == 14 || keyCode == 211 || keyCode == 203 || keyCode == 205) {
                this.quantityField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        if (!detailView && this.searchField != null && this.searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.searchField.setFocused(false);
                return;
            }
            this.searchField.textboxKeyTyped(typedChar, keyCode);
            searchQuery = this.searchField.getText();
            updateFilteredItems();
            this.scrollOffset = 0;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (detailView) {
            return;
        }
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            int cellSize = 26;
            int guiTop = 25;
            int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            this.scrollOffset = scroll > 0 ? Math.max(0, this.scrollOffset - 1) : Math.min(getMaxScrollPages(rowsPerPage) - 1, this.scrollOffset + 1);
        }
    }

    private int getQuantity() {
        if (this.quantityField == null) {
            return 1;
        }
        try {
            return Integer.parseInt(this.quantityField.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getClientBalance() {
        return 999999999.0;
    }

    private int getMaxScrollPages(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) this.filteredItems.size() / (double) totalSlots));
    }

    private String formatCategoryName(String raw) {
        String[] parts = raw.split("_|\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private void drawSlotBackground(int x, int y, int w, int h) {
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawRect(x, y, x + w, y + h, -1438366652);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, -1439485133);
        GlStateManager.disableBlend();
    }

    private void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(stack, x, y);
        this.resetGlState();
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
