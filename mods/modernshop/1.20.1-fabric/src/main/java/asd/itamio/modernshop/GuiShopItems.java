package asd.itamio.modernshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GuiShopItems extends Screen {
    private final ShopCategory category;
    private final int categoryIndex;
    private final boolean adminMode;
    private final List<ItemStack> items;
    private List<ItemStack> filteredItems;
    private int scrollOffset = 0;
    private double accumulatedScroll = 0.0;
    private static final int SLOT_SIZE = 22;
    private static final int COLUMNS = 9;
    private static final int BOTTOM_BAR_HEIGHT = 50;
    // In admin mode, extra height below each slot for X/E buttons
    private static final int ADMIN_BUTTON_BAR_HEIGHT = 10;
    private static final int BASE_CELL_SIZE = 26;
    private static final int ADMIN_CELL_SIZE = BASE_CELL_SIZE + ADMIN_BUTTON_BAR_HEIGHT;

    private boolean detailView = false;
    private int detailItemIndex = -1;
    private EditBox quantityField;
    private boolean stackMode = false;

    private EditBox searchField;
    private String searchText = "";

    // Admin buttons
    private Button addBlockButton;

    public GuiShopItems(ShopCategory category, int categoryIndex) {
        this(category, categoryIndex, false);
    }

    public GuiShopItems(ShopCategory category, int categoryIndex, boolean adminMode) {
        super(Component.literal("Shop - " + category.getName()));
        this.category = category;
        this.categoryIndex = categoryIndex;
        this.adminMode = adminMode;
        this.items = category.getItems();
        this.filteredItems = new ArrayList<>(this.items);
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
        // Create search field at top
        int fieldW = 200;
        int fieldH = 16;
        this.searchField = new EditBox(this.font, this.width / 2 - fieldW / 2, 55, fieldW, fieldH, Component.literal("Search..."));
        this.searchField.setMaxLength(40);
        this.searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchField);
    }

    private void onSearchChanged(String text) {
        this.searchText = text.toLowerCase().trim();
        this.scrollOffset = 0;
        this.accumulatedScroll = 0.0;
        applyFilter();
    }

    private void applyFilter() {
        if (searchText.isEmpty()) {
            this.filteredItems = new ArrayList<>(this.items);
        } else {
            this.filteredItems = this.items.stream()
                    .filter(item -> item.getHoverName().getString().toLowerCase().contains(searchText))
                    .collect(Collectors.toList());
        }
    }

    private int getFilteredIndex(int originalIndex) {
        // Map from the original item index to the filtered list index
        // This is used for buy/sell operations that reference the original items list
        return originalIndex;
    }

    private int getOriginalIndex(int filteredIndex) {
        if (searchText.isEmpty()) {
            return filteredIndex;
        }
        // Find the original index of the filtered item
        if (filteredIndex < 0 || filteredIndex >= filteredItems.size()) return -1;
        ItemStack target = filteredItems.get(filteredIndex);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == target) return i;
        }
        return filteredIndex;
    }

    private void sendToServer(ShopPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ShopPacket.write(packet, buf);
        ClientPlayNetworking.send(ShopPacket.PACKET_ID, buf);
    }

    /** Returns the actual cell size based on admin mode. */
    private int getCellSize() {
        return adminMode ? ADMIN_CELL_SIZE : BASE_CELL_SIZE;
    }

    /** Returns the Y position of the admin button bar for a cell (below the slot). */
    private int getAdminButtonY(int cellY) {
        return cellY + SLOT_SIZE + 1;
    }

    private void rebuildButtons() {
        this.clearWidgets();
        if (detailView) {
            int btnW = 95;
            int btnH = 20;
            int centerX = this.width / 2;
            int bottomY = this.height - 10;

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7aBuy"), new Button.OnPress() {
                @Override
                public void onPress(Button button) {
                    int qty = getQuantity();
                    if (qty > 0 && detailItemIndex >= 0) {
                        ItemStack item = GuiShopItems.this.items.get(detailItemIndex);
                        int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
                        sendToServer(ShopPacket.buyItem(GuiShopItems.this.categoryIndex, GuiShopItems.this.detailItemIndex, actualItems));
                    }
                }
            }).bounds(centerX - btnW - 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack"), new Button.OnPress() {
                @Override
                public void onPress(Button button) {
                    GuiShopItems.this.detailView = false;
                    GuiShopItems.this.detailItemIndex = -1;
                    rebuildButtons();
                }
            }).bounds(centerX + 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal(stackMode ? "\u00a77Mode: Stacks" : "\u00a77Mode: Items"), new Button.OnPress() {
                @Override
                public void onPress(Button button) {
                    GuiShopItems.this.stackMode = !GuiShopItems.this.stackMode;
                    if (GuiShopItems.this.quantityField != null) {
                        GuiShopItems.this.quantityField.setValue("1");
                    }
                    rebuildButtons();
                }
            }).bounds(centerX - btnW - 2, bottomY - btnH - 4, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7eMax Afford"), new Button.OnPress() {
                @Override
                public void onPress(Button button) {
                    if (detailItemIndex >= 0 && detailItemIndex < GuiShopItems.this.items.size()) {
                        ItemStack item = GuiShopItems.this.items.get(detailItemIndex);
                        double buyPrice = ModernShop.getPriceEngine().getBuyPrice(item);
                        double balance = 999999999.0;
                        int maxAfford = (int) (balance / buyPrice);
                        if (stackMode) {
                            int maxStacks = maxAfford / item.getMaxStackSize();
                            if (GuiShopItems.this.quantityField != null) {
                                GuiShopItems.this.quantityField.setValue(String.valueOf(maxStacks));
                            }
                        } else if (GuiShopItems.this.quantityField != null) {
                            GuiShopItems.this.quantityField.setValue(String.valueOf(maxAfford));
                        }
                    }
                }
            }).bounds(centerX + 2, bottomY - btnH - 4, btnW, btnH).build());

            int fieldW = 100;
            int fieldH = 20;
            int centerX2 = this.width / 2;
            this.quantityField = new EditBox(this.font, centerX2 - fieldW / 2, 0, fieldW, fieldH, Component.literal("Quantity"));
            this.quantityField.setValue("1");
            this.quantityField.setFocused(true);
            this.quantityField.setMaxLength(5);
            this.quantityField.setFilter(new java.util.function.Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.matches("\\d*");
            }
        });
            this.addRenderableWidget(this.quantityField);
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack to Categories"), new Button.OnPress() {
                @Override
                public void onPress(Button button) {
                    ScreenManager.open(new GuiShopCategories());
                }
            }).bounds(this.width / 2 - 100, this.height - 22, 200, 20).build());

            // Admin: Add Block button
            if (adminMode) {
                this.addBlockButton = Button.builder(
                        Component.literal("\u00a7a+ Add Block"),
                        btn -> ScreenManager.open(new GuiAddItem(GuiShopItems.this, GuiShopItems.this.categoryIndex, GuiShopItems.this.category.getName()))
                ).bounds(this.width / 2 - 50, this.height - 45, 100, 20).build();
                this.addRenderableWidget(this.addBlockButton);
            }

            // Re-add search field
            if (this.searchField != null) {
                int fieldW = 200;
                int fieldH = 16;
                this.searchField.setWidth(fieldW);
                this.searchField.setX(this.width / 2 - fieldW / 2);
                this.searchField.setY(55);
                this.addRenderableWidget(this.searchField);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, -870441442);

        if (detailView) {
            drawDetailView(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            drawGridView(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawGridView(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String title = "\u00a76\u00a7lShop - " + formatCategoryName(this.category.getName());
        if (!searchText.isEmpty()) {
            title += " \u00a77(filtered: " + filteredItems.size() + "/" + items.size() + ")";
        }
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, 8, 0xFFFFFF);

        // Draw search label above search field
        guiGraphics.drawCenteredString(this.font, "\u00a77Search:", this.width / 2, 43, 0xAAAAAA);

        int cellSize = getCellSize();
        int gridWidth = COLUMNS * cellSize - 4;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 75; // Move down to make room for search bar
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;
        List<ItemStack> displayItems = filteredItems;

        for (int i = 0; i < visibleCount && startIndex + i < displayItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            int itemIndex = startIndex + i;
            ItemStack item = displayItems.get(itemIndex);
            // Draw the item slot at the top of the cell
            drawSlotBackground(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
            guiGraphics.renderItem(item, x + 3, y + 3);

            // In admin mode, draw X and E buttons BELOW the slot (not overlapping the item)
            if (adminMode) {
                int btnY = getAdminButtonY(y);
                // E (Edit) button on the left below the slot
                int editBtnWidth = 12;
                guiGraphics.fill(x, btnY, x + editBtnWidth, btnY + ADMIN_BUTTON_BAR_HEIGHT - 1, 0xCC44AAFF);
                guiGraphics.drawString(this.font, "\u00a7b\u00a7lE", x + 2, btnY + 1, 0xFFFFFF);
                // X (Remove) button on the right below the slot
                int xBtnStartX = x + SLOT_SIZE - editBtnWidth;
                guiGraphics.fill(xBtnStartX, btnY, x + SLOT_SIZE, btnY + ADMIN_BUTTON_BAR_HEIGHT - 1, 0xCCFF4444);
                guiGraphics.drawString(this.font, "\u00a7c\u00a7lx", xBtnStartX + 2, btnY + 1, 0xFFFFFF);
            }
        }

        for (int i = 0; i < visibleCount && startIndex + i < displayItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            int itemIndex = startIndex + i;
            ItemStack item = displayItems.get(itemIndex);
            double buyPrice = ModernShop.getPriceEngine().getBuyPrice(item);
            double sellPrice = ModernShop.getPriceEngine().getSellPrice(item);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("\u00a7f" + item.getHoverName().getString()));
            tooltip.add(Component.literal("\u00a7aBuy: $" + String.format("%.2f", buyPrice)));
            tooltip.add(Component.literal("\u00a7cSell: $" + String.format("%.2f", sellPrice)));
            if (adminMode) {
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("\u00a7cX: Remove item"));
                tooltip.add(Component.literal("\u00a7eEdit: Edit item"));
                tooltip.add(Component.literal("\u00a7eLeft-click: Buy menu"));
                tooltip.add(Component.literal("\u00a7bRight-click: Quick buy stack"));
            } else {
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("\u00a7eLeft-click: Buy menu"));
                tooltip.add(Component.literal("\u00a7bRight-click: Quick buy stack"));
            }
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        int barTop = this.height - BOTTOM_BAR_HEIGHT;
        guiGraphics.fill(0, barTop, this.width, this.height, -14013910);

        if (displayItems.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            guiGraphics.drawCenteredString(this.font, scrollInfo, this.width / 2, barTop + 2, 0xFFFFFF);
        }
    }

    private void drawDetailView(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (detailItemIndex < 0 || detailItemIndex >= this.items.size()) {
            return;
        }
        ItemStack item = this.items.get(detailItemIndex);
        double buyPricePerItem = ModernShop.getPriceEngine().getBuyPrice(item);
        double sellPricePerItem = ModernShop.getPriceEngine().getSellPrice(item);

        int centerX = this.width / 2;
        int y = 10;
        String title = "\u00a76\u00a7l" + item.getHoverName().getString();
        guiGraphics.drawCenteredString(this.font, title, centerX, y, 0xFFFFFF);

        y += 16;
        int itemCenterY = y + 24;
        // Draw centered slot (48x48)
        drawSlotBackground(guiGraphics, centerX - 24, itemCenterY - 24, 48, 48);
        // Render item centered in the slot: translate so the 32x32 scaled item is centered
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) (centerX - 16), (float) (itemCenterY - 16), 0.0f);
        guiGraphics.pose().scale(2.0f, 2.0f, 2.0f);
        guiGraphics.renderItem(item, 0, 0);
        guiGraphics.pose().popPose();

        y = itemCenterY + 30;
        guiGraphics.drawCenteredString(this.font, "\u00a7aBuy: $" + String.format("%.2f", buyPricePerItem) + " each", centerX, y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "\u00a7cSell: $" + String.format("%.2f", sellPricePerItem) + " each", centerX, y += 12, 0xFFFFFF);

        String modeLabel = stackMode ? "\u00a77Quantity (stacks):" : "\u00a77Quantity (items):";
        guiGraphics.drawCenteredString(this.font, modeLabel, centerX, y += 18, 0xCCCCCC);
        y += 14;

        if (this.quantityField != null) {
            this.quantityField.setY(y);
            this.quantityField.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int qty = getQuantity();
        int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
        double totalCost = buyPricePerItem * (double) actualItems;
        guiGraphics.drawCenteredString(this.font, "\u00a7eTotal: " + actualItems + " items = $" + String.format("%.2f", totalCost), centerX, y += 24, 0xFFFF55);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        // Check search field first
        if (this.searchField != null && !detailView) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        for (var widget : this.children()) {
            if (widget instanceof Button button) {
                if (button.mouseClicked(mouseX, mouseY, mouseButton)) {
                    return true;
                }
            }
        }

        if (detailView) {
            if (this.quantityField != null) {
                this.quantityField.mouseClicked(mouseX, mouseY, mouseButton);
            }
            return true;
        }

        int cellSize = getCellSize();
        int gridWidth = COLUMNS * cellSize - 4;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 75;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;
        List<ItemStack> displayItems = filteredItems;

        for (int i = 0; i < visibleCount && startIndex + i < displayItems.size(); i++) {
            int col = i % COLUMNS;
            int x = guiLeft + col * cellSize;
            int row = i / COLUMNS;
            int y = guiTop + row * cellSize;
            int displayIndex = startIndex + i;
            int originalIndex = getOriginalIndex(displayIndex);
            if (originalIndex < 0) continue;

            // Check admin button clicks first (only left click) — buttons are BELOW the slot now
            if (mouseButton == 0 && adminMode) {
                int btnY = getAdminButtonY(y);
                int editBtnWidth = 12;
                int xBtnStartX = x + SLOT_SIZE - editBtnWidth;
                // E (Edit) button below-left
                if (mouseX >= x && mouseX < x + editBtnWidth && mouseY >= btnY && mouseY < btnY + ADMIN_BUTTON_BAR_HEIGHT - 1) {
                    ItemStack item = GuiShopItems.this.items.get(originalIndex);
                    ScreenManager.open(new GuiEditItem(GuiShopItems.this, GuiShopItems.this.categoryIndex, item));
                    return true;
                }
                // X (Remove) button below-right
                if (mouseX >= xBtnStartX && mouseX < x + SLOT_SIZE && mouseY >= btnY && mouseY < btnY + ADMIN_BUTTON_BAR_HEIGHT - 1) {
                    sendRemoveItem(originalIndex);
                    return true;
                }
            }

            // Check click on the item slot area (not admin buttons)
            if (isMouseInSlot((int) mouseX, (int) mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                if (mouseButton == 0) {
                    this.detailView = true;
                    this.detailItemIndex = originalIndex;
                    this.stackMode = false;
                    rebuildButtons();
                } else if (mouseButton == 1) {
                    quickBuyStack(originalIndex);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void quickBuyStack(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= this.items.size()) {
            return;
        }
        ItemStack item = this.items.get(itemIndex);
        double buyPrice = ModernShop.getPriceEngine().getBuyPrice(item);
        double balance = 999999999.0;
        int maxStackSize = item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);
        if (quantity <= 0) {
            return;
        }
        sendToServer(ShopPacket.buyItem(this.categoryIndex, itemIndex, quantity));
    }

    /**
     * Send a packet to remove an item from the category (admin/OP only).
     * The server will verify permissions.
     */
    private void sendRemoveItem(int itemIndex) {
        sendToServer(ShopPacket.removeItem(this.categoryIndex, itemIndex));
        // Remove from local list for responsive UI
        if (itemIndex >= 0 && itemIndex < items.size()) {
            items.remove(itemIndex);
            applyFilter();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (detailView) {
            return false;
        }
        int cellSize = getCellSize();
        int guiTop = 75;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        List<ItemStack> displayItems = filteredItems;

        // Accumulate scroll delta for smooth row-by-row scrolling
        this.accumulatedScroll += scrollDelta;

        // Threshold for triggering a scroll step
        double SCROLL_THRESHOLD = 5.0;

        int steps = (int) (this.accumulatedScroll / SCROLL_THRESHOLD);
        if (steps != 0) {
            this.accumulatedScroll -= steps * SCROLL_THRESHOLD;
            int maxPages = getMaxScrollPages(rowsPerPage);
            this.scrollOffset = Math.max(0, Math.min(maxPages - 1, this.scrollOffset - steps));
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                for (var widget : this.children()) {
                    if (widget instanceof Button button && button.getMessage().getString().contains("Buy")) {
                        button.onPress();
                        return true;
                    }
                }
                return true;
            }
            if (this.quantityField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // Handle search field input
        if (this.searchField != null && this.searchField.isFocused() && !detailView) {
            if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (Character.isDigit(codePoint)) {
                return this.quantityField.charTyped(codePoint, modifiers);
            }
            return true;
        }

        // Handle search field character typing
        if (this.searchField != null && this.searchField.isFocused() && !detailView) {
            return this.searchField.charTyped(codePoint, modifiers);
        }

        return super.charTyped(codePoint, modifiers);
    }

    private int getQuantity() {
        if (this.quantityField == null) {
            return 1;
        }
        try {
            return Integer.parseInt(this.quantityField.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int getMaxScrollPages(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        List<ItemStack> displayItems = filteredItems;
        return Math.max(1, (int) Math.ceil((double) displayItems.size() / (double) totalSlots));
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

    private void drawSlotBackground(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, -1438366652);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, -1439485133);
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
