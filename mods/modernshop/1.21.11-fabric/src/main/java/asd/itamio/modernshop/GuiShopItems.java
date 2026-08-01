package asd.itamio.modernshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GuiShopItems extends Screen {
    private final ShopCategory category;
    private final int categoryIndex;
    private final List<ItemStack> allItems;
    private List<ItemStack> filteredItems;
    private int scrollOffset = 0;
    private static final int SLOT_SIZE = 22;
    private static final int COLUMNS = 9;
    private static final int SPACING = 4;
    private static final int BOTTOM_BAR_HEIGHT = 50;

    private boolean detailView = false;
    private int detailItemIndex = -1;
    private EditBox quantityField;
    private EditBox searchBox;
    private boolean stackMode = false;
    private double accumulatedScroll = 0.0;
    private static final double SCROLL_THRESHOLD = 1.0;

    // Pending detail index (set by deep-link from OPEN_ITEM_DETAIL packet)
    private int pendingDetail = -1;

    public GuiShopItems(ShopCategory category, int categoryIndex) {
        super(Component.literal("Shop - " + category.getName()));
        this.category = category;
        this.categoryIndex = categoryIndex;
        this.allItems = category.getItems();
        this.filteredItems = new ArrayList<>(this.allItems);
    }

    /**
     * Set a pending detail index to auto-open the detail view at this item
     * when the screen is initialized.
     */
    public void setPendingDetail(int itemIndex) {
        this.pendingDetail = itemIndex;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        // Add search bar (top of screen, below title)
        int searchWidth = Math.min(250, this.width - 40);
        int searchX = (this.width - searchWidth) / 2;
        this.searchBox = new EditBox(this.font, searchX, 8, searchWidth, 14, Component.literal("Search items..."));
        this.searchBox.setMaxLength(50);
        this.searchBox.setResponder(s -> {
            filterItems(s);
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchBox);

        // Consume pending deep-link: auto-open detail view at the requested item
        if (pendingDetail >= 0 && pendingDetail < this.filteredItems.size()) {
            this.detailView = true;
            this.detailItemIndex = pendingDetail;
            this.stackMode = false;
            this.pendingDetail = -1;
        }

        rebuildButtons();
    }

    private void filterItems(String search) {
        if (search == null || search.trim().isEmpty()) {
            this.filteredItems = new ArrayList<>(this.allItems);
        } else {
            String lower = search.toLowerCase();
            this.filteredItems = this.allItems.stream()
                    .filter(item -> item.getDisplayName().getString().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }
    }

    private void rebuildButtons() {
        // Clear all widgets except the search box
        var widgets = this.children();
        for (var widget : new ArrayList<>(widgets)) {
            if (widget != this.searchBox) {
                this.removeWidget(widget);
            }
        }

        if (detailView) {
            int btnW = 95;
            int btnH = 20;
            int centerX = this.width / 2;
            int bottomY = this.height - 10;

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7aBuy"), button -> {
                int qty = getQuantity();
                if (qty > 0 && detailItemIndex >= 0 && detailItemIndex < filteredItems.size()) {
                    ItemStack item = this.filteredItems.get(detailItemIndex);
                    int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
                    ClientPlayNetworking.send(new ShopPayload(ShopPayload.ShopMessage.buyItem(this.categoryIndex, findOriginalIndex(this.filteredItems.get(detailItemIndex)), actualItems)));
                }
            }).bounds(centerX - btnW - 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack"), button -> {
                this.detailView = false;
                this.detailItemIndex = -1;
                rebuildButtons();
            }).bounds(centerX + 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal(stackMode ? "\u00a77Mode: Stacks" : "\u00a77Mode: Items"), button -> {
                this.stackMode = !this.stackMode;
                if (this.quantityField != null) {
                    this.quantityField.setValue("1");
                }
                rebuildButtons();
            }).bounds(centerX - btnW - 2, bottomY - btnH - 4, btnW, btnH).build());

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7eMax Afford"), button -> {
                if (detailItemIndex >= 0 && detailItemIndex < this.filteredItems.size()) {
                    ItemStack item = this.filteredItems.get(detailItemIndex);
                    PriceEngine priceEngine = ModernShop.getPriceEngine();
                    double buyPrice = priceEngine.getBuyPrice(item);
                    double balance = getClientBalance();
                    int maxAfford = (int) (balance / buyPrice);
                    if (stackMode) {
                        int maxStacks = maxAfford / item.getMaxStackSize();
                        if (this.quantityField != null) {
                            this.quantityField.setValue(String.valueOf(maxStacks));
                        }
                    } else if (this.quantityField != null) {
                        this.quantityField.setValue(String.valueOf(maxAfford));
                    }
                }
            }).bounds(centerX + 2, bottomY - btnH - 4, btnW, btnH).build());

            int fieldW = 100;
            int fieldH = 16;
            this.quantityField = new EditBox(this.font, centerX - fieldW / 2, 0, fieldW, fieldH, Component.literal("Quantity"));
            this.quantityField.setValue("1");
            this.quantityField.setFocused(true);
            this.quantityField.setMaxLength(5);
            this.quantityField.setFilter(s -> s.matches("\\d*"));
            this.addRenderableWidget(this.quantityField);
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack to Categories"), button -> {
                Minecraft.getInstance().setScreen(new GuiShopCategories());
            }).bounds(this.width / 2 - 100, this.height - 22, 200, 20).build());
        }
    }

    private int findOriginalIndex(ItemStack filteredStack) {
        for (int i = 0; i < this.allItems.size(); i++) {
            if (ItemStack.isSameItem(this.allItems.get(i), filteredStack)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, -870441442);

        if (detailView) {
            drawDetailView(guiGraphics, mouseX, mouseY);
        } else {
            drawGridView(guiGraphics, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawGridView(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title (moved down to account for search bar)
        int titleY = 26;
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a76\u00a7lShop - " + formatCategoryName(this.category.getName())), this.width / 2, titleY, 0xFFFFFF);

        int cellSize = 26;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 38;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        // Draw items
        for (int i = 0; i < visibleCount && startIndex + i < this.filteredItems.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            drawSlotBackground(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
            guiGraphics.renderItem(this.filteredItems.get(startIndex + i), x + 3, y + 3);
        }

        // Draw bottom bar
        int barTop = this.height - BOTTOM_BAR_HEIGHT;
        guiGraphics.fill(0, barTop, this.width, this.height, -14013910);

        // Scroll info
        int totalRows = (int) Math.ceil((double) this.filteredItems.size() / (double) COLUMNS);
        int currentRow = this.scrollOffset + 1;
        String scrollInfo = "\u00a77Row: " + currentRow + "/" + Math.max(1, totalRows);
        guiGraphics.drawCenteredString(this.font, Component.literal(scrollInfo), this.width / 2, barTop + 2, 0xFFFFFF);

        // Search results count
        if (this.filteredItems.size() != this.allItems.size()) {
            String resultInfo = "\u00a77Found " + this.filteredItems.size() + " items";
            guiGraphics.drawCenteredString(this.font, Component.literal(resultInfo), this.width / 2 + 120, barTop + 2, 0xAAAAAA);
        }

        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a77Left-click: Buy menu | Right-click: Quick buy stack | ESC: Back"), this.width / 2, barTop + 14, 0xAAAAAA);
    }

    private void drawDetailView(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (detailItemIndex < 0 || detailItemIndex >= this.filteredItems.size()) {
            return;
        }
        ItemStack item = this.filteredItems.get(detailItemIndex);
        PriceEngine priceEngine = ModernShop.getPriceEngine();
        double buyPricePerItem = priceEngine.getBuyPrice(item);
        double sellPricePerItem = priceEngine.getSellPrice(item);

        int centerX = this.width / 2;
        int y = 10;
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a76\u00a7l" + item.getDisplayName().getString()), centerX, y, 0xFFFFFF);

        y += 16;
        int itemCenterY = y + 24;
        drawSlotBackground(guiGraphics, centerX - 24, itemCenterY - 24, 48, 48);
        guiGraphics.renderItem(item, centerX - 8, itemCenterY - 8);

        y = itemCenterY + 30;
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a7aBuy: $" + String.format("%.2f", buyPricePerItem) + " each"), centerX, y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a7cSell: $" + String.format("%.2f", sellPricePerItem) + " each"), centerX, y += 12, 0xFFFFFF);

        String modeLabel = stackMode ? "\u00a77Quantity (stacks):" : "\u00a77Quantity (items):";
        guiGraphics.drawCenteredString(this.font, Component.literal(modeLabel), centerX, y += 18, 0xCCCCCC);
        y += 14;

        if (this.quantityField != null) {
            this.quantityField.setY(y);
            this.quantityField.render(guiGraphics, mouseX, mouseY, 0);
        }

        int qty = getQuantity();
        int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
        double totalCost = buyPricePerItem * (double) actualItems;
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a7eTotal: " + actualItems + " items = $" + String.format("%.2f", totalCost)), centerX, y += 24, 0xFFFF55);

        y += 12;
        double balance = getClientBalance();
        int maxAfford = (int) (balance / buyPricePerItem);
        if (stackMode) {
            int maxStacks = maxAfford / item.getMaxStackSize();
            guiGraphics.drawCenteredString(this.font, Component.literal("\u00a77You can afford: " + maxStacks + " stacks (" + maxAfford + " items)"), centerX, y, 0xAAAAAA);
        } else {
            guiGraphics.drawCenteredString(this.font, Component.literal("\u00a77You can afford: " + maxAfford + " items"), centerX, y, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            return true;
        }

        if (detailView) {
            return true;
        }

        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();

            int cellSize = 26;
            int gridWidth = COLUMNS * cellSize - SPACING;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 38;
            int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            for (int i = 0; i < visibleCount && startIndex + i < this.filteredItems.size(); i++) {
                int col = i % COLUMNS;
                int x = guiLeft + col * cellSize;
                int row = i / COLUMNS;
                int y = guiTop + row * cellSize;
                if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                int itemIndex = startIndex + i;
                this.detailView = true;
                this.detailItemIndex = itemIndex;
                this.stackMode = false;
                rebuildButtons();
                return true;
            }
        } else if (event.button() == 1) {
            double mouseX = event.x();
            double mouseY = event.y();

            int cellSize = 26;
            int gridWidth = COLUMNS * cellSize - SPACING;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 38;
            int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            for (int i = 0; i < visibleCount && startIndex + i < this.filteredItems.size(); i++) {
                int col = i % COLUMNS;
                int x = guiLeft + col * cellSize;
                int row = i / COLUMNS;
                int y = guiTop + row * cellSize;
                if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                int itemIndex = startIndex + i;
                quickBuyStack(itemIndex);
                return true;
            }
        }

        return true;
    }

    private void quickBuyStack(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= this.filteredItems.size()) {
            return;
        }
        ItemStack item = this.filteredItems.get(itemIndex);
        PriceEngine priceEngine = ModernShop.getPriceEngine();
        double buyPrice = priceEngine.getBuyPrice(item);
        double balance = getClientBalance();
        int maxStackSize = item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);
        if (quantity <= 0) {
            return;
        }
        int originalIndex = findOriginalIndex(item);
        ClientPlayNetworking.send(new ShopPayload(ShopPayload.ShopMessage.buyItem(this.categoryIndex, originalIndex, quantity)));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (event.key() == 257 || event.key() == 335) {
                // Enter key - trigger buy
                for (var widget : this.children()) {
                    if (widget instanceof Button btn && btn.getMessage().getString().contains("Buy")) {
                        btn.onPress(new InputWithModifiers() {
                            @Override
                            public int input() { return 0; }
                            @Override
                            public int modifiers() { return 0; }
                        });
                        return true;
                    }
                }
                return true;
            }
            return this.quantityField.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detailView) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollY != 0) {
            // Accumulate scroll for smoother row-by-row scrolling
            accumulatedScroll += scrollY;

            int cellSize = 26;
            int guiTop = 38;
            int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int totalRows = (int) Math.ceil((double) this.filteredItems.size() / (double) COLUMNS);
            int maxScrollOffset = Math.max(0, totalRows - rowsPerPage);

            // Process accumulated scroll (1 row per scroll unit)
            int scrollUnits = (int) Math.floor(Math.abs(accumulatedScroll) / SCROLL_THRESHOLD);
            if (scrollUnits > 0) {
                int direction = accumulatedScroll > 0 ? -1 : 1; // Positive Y = scroll down
                int newOffset = this.scrollOffset + direction * scrollUnits;
                this.scrollOffset = Math.max(0, Math.min(maxScrollOffset, newOffset));
                accumulatedScroll = accumulatedScroll % SCROLL_THRESHOLD;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    private double getClientBalance() {
        return 999999999.0;
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
