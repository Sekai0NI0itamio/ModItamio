package asd.itamio.worldshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
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
    private static final int ADMIN_BUTTON_BAR_HEIGHT = 10;
    private static final int BASE_CELL_SIZE = 26;
    private static final int ADMIN_CELL_SIZE = BASE_CELL_SIZE + ADMIN_BUTTON_BAR_HEIGHT;

    private boolean detailView = false;
    private int detailItemIndex = -1;
    private EditBox quantityField;
    private boolean stackMode = false;

    private int pendingDetailIndex = -1;

    private EditBox searchField;
    private String searchText = "";

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

    public void setPendingDetail(int index) {
        this.pendingDetailIndex = index;
    }

    @Override
    protected void init() {
        super.init();
        if (pendingDetailIndex >= 0 && pendingDetailIndex < items.size()) {
            detailView = true;
            detailItemIndex = pendingDetailIndex;
        }
        pendingDetailIndex = -1;
        rebuildButtons();
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

    private int getOriginalIndex(int filteredIndex) {
        if (searchText.isEmpty()) {
            return filteredIndex;
        }
        if (filteredIndex < 0 || filteredIndex >= filteredItems.size()) return -1;
        ItemStack target = filteredItems.get(filteredIndex);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == target) return i;
        }
        return filteredIndex;
    }

    private void sendToServer(ShopPacket packet) {
        ClientPlayNetworking.send(packet);
    }

    private int getCellSize() {
        return adminMode ? ADMIN_CELL_SIZE : BASE_CELL_SIZE;
    }

    private int getAdminButtonY(int cellY) {
        return cellY + SLOT_SIZE + 1;
    }

    private void doBuy() {
        int qty = getQuantity();
        if (qty > 0 && detailItemIndex >= 0) {
            ItemStack item = this.items.get(detailItemIndex);
            int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
            sendToServer(ShopPacket.buyItem(this.categoryIndex, this.detailItemIndex, actualItems));
        }
    }

    private void rebuildButtons() {
        this.clearWidgets();
        if (detailView) {
            int btnW = 95;
            int btnH = 20;
            int centerX = this.width / 2;
            int bottomY = this.height - 10;

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7aBuy"), button -> doBuy())
                    .bounds(centerX - btnW - 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack"), button -> {
                GuiShopItems.this.detailView = false;
                GuiShopItems.this.detailItemIndex = -1;
                rebuildButtons();
            }).bounds(centerX + 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(stackMode ? Component.literal("\u00a77Mode: Stacks") : Component.literal("\u00a77Mode: Items"), button -> {
                GuiShopItems.this.stackMode = !stackMode;
                if (GuiShopItems.this.quantityField != null) {
                    GuiShopItems.this.quantityField.setValue("1");
                }
                rebuildButtons();
            }).bounds(centerX - btnW - 2, bottomY - btnH - 4, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7eMax Afford"), button -> {
                if (detailItemIndex >= 0 && detailItemIndex < GuiShopItems.this.items.size()) {
                    ItemStack item = GuiShopItems.this.items.get(detailItemIndex);
                    double buyPrice = WorldShop.getPriceEngine().getBuyPrice(item);
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
            }).bounds(centerX + 2, bottomY - btnH - 4, btnW, btnH).build());

            int fieldW = 100;
            int fieldH = 20;
            int centerX2 = this.width / 2;
            this.quantityField = new EditBox(this.font, centerX2 - fieldW / 2, 0, fieldW, fieldH, Component.literal("Quantity"));
            this.quantityField.setValue("1");
            this.quantityField.setFocused(true);
            this.quantityField.setMaxLength(5);
            this.addRenderableWidget(this.quantityField);
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack to Categories"), button -> {
                ScreenManager.open(new GuiShopCategories(!adminMode));
            }).bounds(this.width / 2 - 100, this.height - 22, 200, 20).build());

            if (adminMode) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("\u00a7a+ Add Block"),
                        btn -> ScreenManager.open(new GuiAddItem(GuiShopItems.this, GuiShopItems.this.categoryIndex, GuiShopItems.this.category.getName()))
                ).bounds(this.width / 2 - 50, this.height - 45, 100, 20).build());
            }

            if (this.searchField != null) {
                int fieldW = 200;
                this.searchField.setWidth(fieldW);
                this.searchField.setX(this.width / 2 - fieldW / 2);
                this.searchField.setY(55);
                this.addRenderableWidget(this.searchField);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (detailView && this.quantityField != null) {
            this.quantityField.setY(124);
        }
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, -870441442);

        if (this.searchField != null && !detailView) {
            int sbX = this.width / 2 - 102;
            int sbY = 53;
            int sbW = 204;
            int sbH = 20;
            guiGraphics.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0xFF3A3A3A);
            guiGraphics.fill(sbX + 1, sbY + 1, sbX + sbW - 1, sbY + sbH - 1, 0xFF2E2E2E);
        }

        if (detailView) {
            drawDetailView(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            drawGridView(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawGridView(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        String title = "\u00a76\u00a7lShop - " + formatCategoryName(this.category.getName());
        if (!searchText.isEmpty()) {
            title += " \u00a77(filtered: " + filteredItems.size() + "/" + items.size() + ")";
        }
        guiGraphics.centeredText(this.font, title, this.width / 2, 8, 0xFFFFFF);

        guiGraphics.centeredText(this.font, "\u00a77Search:", this.width / 2, 43, 0xAAAAAA);

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
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            ItemStack item = displayItems.get(startIndex + i);
            drawSlotBackground(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
            guiGraphics.item(item, x + 3, y + 3);

            if (adminMode) {
                int btnY = getAdminButtonY(y);
                int editBtnWidth = 12;
                guiGraphics.fill(x, btnY, x + editBtnWidth, btnY + ADMIN_BUTTON_BAR_HEIGHT - 1, 0xCC44AAFF);
                guiGraphics.text(this.font, "\u00a7b\u00a7lE", x + 2, btnY + 1, 0xFFFFFF);
                int xBtnStartX = x + SLOT_SIZE - editBtnWidth;
                guiGraphics.fill(xBtnStartX, btnY, x + SLOT_SIZE, btnY + ADMIN_BUTTON_BAR_HEIGHT - 1, 0xCCFF4444);
                guiGraphics.text(this.font, "\u00a7c\u00a7lx", xBtnStartX + 2, btnY + 1, 0xFFFFFF);
            }
        }

        for (int i = 0; i < visibleCount && startIndex + i < displayItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            ItemStack item = displayItems.get(startIndex + i);
            double buyPrice = WorldShop.getPriceEngine().getBuyPrice(item);
            double sellPrice = WorldShop.getPriceEngine().getSellPrice(item);
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
            guiGraphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        int barTop = this.height - BOTTOM_BAR_HEIGHT;
        guiGraphics.fill(0, barTop, this.width, this.height, -14013910);

        if (displayItems.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            guiGraphics.centeredText(this.font, scrollInfo, this.width / 2, barTop + 2, 0xFFFFFF);
        }
    }

    private void drawDetailView(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (detailItemIndex < 0 || detailItemIndex >= this.items.size()) {
            return;
        }
        ItemStack item = this.items.get(detailItemIndex);
        double buyPricePerItem = WorldShop.getPriceEngine().getBuyPrice(item);
        double sellPricePerItem = WorldShop.getPriceEngine().getSellPrice(item);

        int centerX = this.width / 2;
        int y = 10;
        String title = "\u00a76\u00a7l" + item.getHoverName().getString();
        guiGraphics.centeredText(this.font, title, centerX, y, 0xFFFFFF);

        y += 16;
        int itemCenterY = y + 24;
        drawSlotBackground(guiGraphics, centerX - 24, itemCenterY - 24, 48, 48);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) (centerX - 16), (float) (itemCenterY - 16));
        guiGraphics.pose().scale(2.0f, 2.0f);
        guiGraphics.item(item, 0, 0);
        guiGraphics.pose().popMatrix();

        y = itemCenterY + 30;
        guiGraphics.centeredText(this.font, "\u00a7aBuy: $" + String.format("%.2f", buyPricePerItem) + " each", centerX, y, 0xFFFFFF);
        guiGraphics.centeredText(this.font, "\u00a7cSell: $" + String.format("%.2f", sellPricePerItem) + " each", centerX, y += 12, 0xFFFFFF);

        String modeLabel = stackMode ? "\u00a77Quantity (stacks):" : "\u00a77Quantity (items):";
        guiGraphics.centeredText(this.font, modeLabel, centerX, y += 18, 0xCCCCCC);
        y += 14;

        int qty = getQuantity();
        int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
        double totalCost = buyPricePerItem * (double) actualItems;
        guiGraphics.centeredText(this.font, "\u00a7eTotal: " + actualItems + " items = $" + String.format("%.2f", totalCost), centerX, y += 24, 0xFFFF55);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();
        if (this.searchField != null && !detailView) {
            this.searchField.onClick(event, doubleClick);
        }

        for (var widget : this.children()) {
            if (widget instanceof Button button) {
                if (button.mouseClicked(event, doubleClick)) {
                    return true;
                }
            }
        }

        if (detailView) {
            if (this.quantityField != null) {
                this.quantityField.onClick(event, doubleClick);
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

            if (mouseButton == 0 && adminMode) {
                int btnY = getAdminButtonY(y);
                int editBtnWidth = 12;
                int xBtnStartX = x + SLOT_SIZE - editBtnWidth;
                if (mouseX >= x && mouseX < x + editBtnWidth && mouseY >= btnY && mouseY < btnY + ADMIN_BUTTON_BAR_HEIGHT - 1) {
                    ItemStack item = GuiShopItems.this.items.get(originalIndex);
                    ScreenManager.open(new GuiEditItem(GuiShopItems.this, GuiShopItems.this.categoryIndex, item));
                    return true;
                }
                if (mouseX >= xBtnStartX && mouseX < x + SLOT_SIZE && mouseY >= btnY && mouseY < btnY + ADMIN_BUTTON_BAR_HEIGHT - 1) {
                    sendRemoveItem(originalIndex);
                    return true;
                }
            }

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

        return super.mouseClicked(event, doubleClick);
    }

    private void quickBuyStack(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= this.items.size()) {
            return;
        }
        ItemStack item = this.items.get(itemIndex);
        double buyPrice = WorldShop.getPriceEngine().getBuyPrice(item);
        double balance = 999999999.0;
        int maxStackSize = item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);
        if (quantity <= 0) {
            return;
        }
        sendToServer(ShopPacket.buyItem(this.categoryIndex, itemIndex, quantity));
    }

    private void sendRemoveItem(int itemIndex) {
        sendToServer(ShopPacket.removeItem(this.categoryIndex, itemIndex));
        if (itemIndex >= 0 && itemIndex < items.size()) {
            items.remove(itemIndex);
            applyFilter();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (detailView) {
            return false;
        }
        int cellSize = getCellSize();
        int guiTop = 75;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);

        this.accumulatedScroll += scrollDeltaY;

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
    public boolean keyPressed(KeyEvent event) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (event.key() == 257 || event.key() == 335) {
                doBuy();
                return true;
            }
            if (this.quantityField.keyPressed(event)) {
                return true;
            }
        }

        if (this.searchField != null && this.searchField.isFocused() && !detailView) {
            if (this.searchField.keyPressed(event)) {
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            String s = event.codepointAsString();
            if (s.length() == 1 && Character.isDigit(s.charAt(0))) {
                return this.quantityField.charTyped(event);
            }
            return true;
        }

        if (this.searchField != null && this.searchField.isFocused() && !detailView) {
            return this.searchField.charTyped(event);
        }

        return super.charTyped(event);
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

    private void drawSlotBackground(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h) {
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
