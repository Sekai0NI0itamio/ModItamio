package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GuiShopItems extends Screen {
    private final ShopCategory category;
    private final int categoryIndex;
    private final List<ItemStack> items;
    private int scrollOffset = 0;
    private static final int SLOT_SIZE = 22;
    private static final int SPACING = 4;
    private static final int COLUMNS = 9;
    private static final int BOTTOM_BAR_HEIGHT = 50;

    private boolean detailView = false;
    private int detailItemIndex = -1;
    private EditBox quantityField;
    private boolean stackMode = false;

    public GuiShopItems(ShopCategory category, int categoryIndex) {
        super(Component.literal("Shop Items"));
        this.category = category;
        this.categoryIndex = categoryIndex;
        this.items = category.getItems();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearWidgets();
        if (detailView) {
            int btnW = 95;
            int btnH = 20;
            int centerX = this.width / 2;
            int bottomY = this.height - 10;

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7aBuy"), button -> {
                int qty = getQuantity();
                if (qty > 0 && detailItemIndex >= 0) {
                    ItemStack item = this.items.get(detailItemIndex);
                    int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
                    Minecraft.getInstance().getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(ShopPacket.buyItem(this.categoryIndex, this.detailItemIndex, actualItems)));
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
                if (detailItemIndex >= 0 && detailItemIndex < this.items.size()) {
                    ItemStack item = this.items.get(detailItemIndex);
                    PriceEngine priceEngine = WorldShop.getPriceEngine();
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
            int fieldH = 20;
            this.quantityField = new EditBox(this.font, centerX - fieldW / 2, 0, fieldW, fieldH, Component.literal("Quantity"));
            this.quantityField.setValue("1");
            this.quantityField.setFocused(true);
            this.quantityField.setMaxLength(5);
            this.addRenderableWidget(this.quantityField);
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack to Categories"), button -> {
                Minecraft.getInstance().setScreen(new GuiShopCategories());
            }).bounds(this.width / 2 - 100, this.height - 22, 200, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(0, 0, this.width, this.height, -870441442);

        if (detailView) {
            drawDetailView(guiGraphics, mouseX, mouseY);
        } else {
            drawGridView(guiGraphics, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void drawGridView(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String title = "\u00a76\u00a7lShop - " + formatCategoryName(this.category.getName());
        guiGraphics.drawCenteredString(this.font, Component.literal(title), this.width / 2, 8, 0xFFFFFF);

        int cellSize = 26;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        // Draw items
        for (int i = 0; i < visibleCount && startIndex + i < this.items.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            int itemIndex = startIndex + i;
            ItemStack item = this.items.get(itemIndex);
            drawSlotBackground(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
            guiGraphics.renderItem(item, x + 3, y + 3);
        }

        // Draw tooltips
        for (int i = 0; i < visibleCount && startIndex + i < this.items.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            int itemIndex = startIndex + i;
            ItemStack item = this.items.get(itemIndex);
            PriceEngine priceEngine = WorldShop.getPriceEngine();
            double buyPrice = priceEngine.getBuyPrice(item);
            double sellPrice = priceEngine.getSellPrice(item);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("\u00a7f" + item.getHoverName().getString()));
            tooltip.add(Component.literal("\u00a7aBuy: $" + String.format("%.2f", buyPrice)));
            tooltip.add(Component.literal("\u00a7cSell: $" + String.format("%.2f", sellPrice)));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("\u00a7eLeft-click: Buy menu"));
            tooltip.add(Component.literal("\u00a7bRight-click: Quick buy stack"));
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        int barTop = this.height - BOTTOM_BAR_HEIGHT;
        guiGraphics.fill(0, barTop, this.width, this.height, -14013910);

        if (this.items.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            guiGraphics.drawCenteredString(this.font, Component.literal(scrollInfo), this.width / 2, barTop + 2, 0xFFFFFF);
        }
        String footer = "\u00a77Left-click: Buy menu | Right-click: Quick buy stack | ESC: Back";
        guiGraphics.drawCenteredString(this.font, Component.literal(footer), this.width / 2, barTop + 14, 0xAAAAAA);
    }

    private void drawDetailView(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (detailItemIndex < 0 || detailItemIndex >= this.items.size()) {
            return;
        }
        ItemStack item = this.items.get(detailItemIndex);
        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double buyPricePerItem = priceEngine.getBuyPrice(item);
        double sellPricePerItem = priceEngine.getSellPrice(item);

        int centerX = this.width / 2;
        int y = 10;
        String title = "\u00a76\u00a7l" + item.getHoverName().getString();
        guiGraphics.drawCenteredString(this.font, Component.literal(title), centerX, y, 0xFFFFFF);

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
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }

        if (detailView) {
            return false;
        }

        // Handle grid clicks
        int cellSize = 26;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 25;
        int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && startIndex + i < this.items.size(); i++) {
            int col = i % COLUMNS;
            int x = guiLeft + col * cellSize;
            int row = i / COLUMNS;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            int itemIndex = startIndex + i;
            if (mouseButton == 0) {
                this.detailView = true;
                this.detailItemIndex = itemIndex;
                this.stackMode = false;
                rebuildButtons();
            } else if (mouseButton == 1) {
                quickBuyStack(itemIndex);
            }
            return true;
        }

        return false;
    }

    private void quickBuyStack(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= this.items.size()) {
            return;
        }
        ItemStack item = this.items.get(itemIndex);
        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double buyPrice = priceEngine.getBuyPrice(item);
        double balance = getClientBalance();
        int maxStackSize = item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);
        if (quantity <= 0) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(ShopPacket.buyItem(this.categoryIndex, itemIndex, quantity)));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                // Enter key - trigger buy
                for (var widget : this.children()) {
                    if (widget instanceof Button && ((Button) widget).getMessage().getString().contains("Buy")) {
                        ((Button) widget).onPress();
                        return true;
                    }
                }
                return true;
            }
            if (this.quantityField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (Character.isDigit(codePoint) || codePoint == '-' || codePoint == 8) {
                if (this.quantityField.charTyped(codePoint, modifiers)) {
                    return true;
                }
            }
            return false;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detailView) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollY != 0) {
            int cellSize = 26;
            int guiTop = 25;
            int availableHeight = this.height - guiTop - BOTTOM_BAR_HEIGHT;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            this.scrollOffset = scrollY > 0 ? Math.max(0, this.scrollOffset - 1) : Math.min(getMaxScrollPages(rowsPerPage) - 1, this.scrollOffset + 1);
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

    // Client-side balance is a placeholder — the server validates actual balance
    private double getClientBalance() {
        return 999999999.0;
    }

    private int getMaxScrollPages(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) this.items.size() / (double) totalSlots));
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
