package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class GuiSellGui extends Screen {
    private final List<ItemStack> sellSlots = new ArrayList<>();
    private boolean soldItems = false;
    private static final int COLUMNS = 9;
    private static final int SELL_ROWS = 3;
    private static final int SLOT_SIZE = 18;

    protected GuiSellGui() {
        super(Component.literal("Sell Items"));
        for (int i = 0; i < 27; i++) {
            sellSlots.add(ItemStack.EMPTY);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aSell All Items"), button -> sellAllItems())
                .bounds(this.width / 2 - 100, this.height - 25, 200, 20)
                .build());
    }

    @Override
    public void removed() {
        super.removed();
        if (!soldItems) {
            returnItemsToInventory();
        }
    }

    private int getCellSize() {
        return 20;
    }

    private int getGuiLeft() {
        int gridWidth = COLUMNS * getCellSize();
        return (this.width - gridWidth) / 2;
    }

    private int getSellTop() {
        return 30;
    }

    private int getInvTop() {
        return getSellTop() + SELL_ROWS * getCellSize() + 20;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, a);

        guiGraphics.fillGradient(0, 0, this.width, this.height, -870441442, -870441442);

        int guiLeft = getGuiLeft();
        int cellSize = getCellSize();

        guiGraphics.centeredText(this.font, "\u00a76\u00a7lSell Items", this.width / 2, 8, 0xFFFFFF);
        guiGraphics.centeredText(this.font, "\u00a77Items to Sell", this.width / 2, getSellTop() - 10, 0xAAAAAA);

        // Draw sell slots
        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = getSellTop() + row * cellSize;
            drawSlotBackground(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = sellSlots.get(i);
            if (!stack.isEmpty()) {
                guiGraphics.item(stack, x + 1, y + 1);
                guiGraphics.itemDecorations(this.font, stack, x + 1, y + 1);
            }
        }

        guiGraphics.centeredText(this.font, "\u00a77Inventory", this.width / 2, getInvTop() - 10, 0xAAAAAA);

        var player = Minecraft.getInstance().player;
        var invItems = player.getInventory().getNonEquipmentItems();

        // Draw main inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * cellSize;
                int y = getInvTop() + row * cellSize;
                drawSlotBackground(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
                ItemStack stack = invItems.get(9 + row * COLUMNS + col);
                if (!stack.isEmpty()) {
                    guiGraphics.item(stack, x + 1, y + 1);
                    guiGraphics.itemDecorations(this.font, stack, x + 1, y + 1);
                }
            }
        }

        // Draw hotbar
        int hotbarY = getInvTop() + 3 * cellSize + 6;
        for (int col = 0; col < COLUMNS; col++) {
            int x = guiLeft + col * cellSize;
            drawSlotBackground(guiGraphics, x, hotbarY, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = invItems.get(col);
            if (!stack.isEmpty()) {
                guiGraphics.item(stack, x + 1, hotbarY + 1);
                guiGraphics.itemDecorations(this.font, stack, x + 1, hotbarY + 1);
            }
        }

        // Tooltips for sell slots
        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = getSellTop() + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            ItemStack stack = sellSlots.get(i);
            if (stack.isEmpty()) break;
            PriceEngine priceEngine = WorldShop.getPriceEngine();
            double sellPrice = priceEngine.getSellPrice(stack);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("\u00a7f" + stack.getHoverName().getString()));
            tooltip.add(Component.literal("\u00a77x" + stack.getCount()));
            tooltip.add(Component.literal("\u00a7cSell: $" + String.format("%.2f", sellPrice) + " each"));
            tooltip.add(Component.literal("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount())));
            tooltip.add(Component.literal("\u00a77Click to pick up | Right-click to return one"));
            guiGraphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        // Tooltips for inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * cellSize;
                int y = getInvTop() + row * cellSize;
                if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                ItemStack stack = invItems.get(9 + row * COLUMNS + col);
                if (stack.isEmpty()) continue;
                PriceEngine priceEngine = WorldShop.getPriceEngine();
                double sellPrice = priceEngine.getSellPrice(stack);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("\u00a7f" + stack.getHoverName().getString()));
                tooltip.add(Component.literal("\u00a77x" + stack.getCount()));
                tooltip.add(Component.literal("\u00a7cSell price: $" + String.format("%.2f", sellPrice) + " each"));
                tooltip.add(Component.literal("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount())));
                tooltip.add(Component.literal("\u00a77Click to move to sell area"));
                guiGraphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }
        }

        // Tooltips for hotbar
        int hotbarY2 = getInvTop() + 3 * cellSize + 6;
        for (int col = 0; col < COLUMNS; col++) {
            int x = guiLeft + col * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, hotbarY2, SLOT_SIZE, SLOT_SIZE)) continue;
            ItemStack stack = invItems.get(col);
            if (stack.isEmpty()) break;
            PriceEngine priceEngine = WorldShop.getPriceEngine();
            double sellPrice = priceEngine.getSellPrice(stack);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("\u00a7f" + stack.getHoverName().getString()));
            tooltip.add(Component.literal("\u00a77x" + stack.getCount()));
            tooltip.add(Component.literal("\u00a7cSell price: $" + String.format("%.2f", sellPrice) + " each"));
            tooltip.add(Component.literal("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount())));
            tooltip.add(Component.literal("\u00a77Click to move to sell area"));
            guiGraphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        double totalValue = calculateTotalValue();
        String totalStr = "\u00a7aTotal Value: $" + String.format("%.2f", totalValue);
        int totalY = getSellTop() + SELL_ROWS * cellSize + 4;
        guiGraphics.centeredText(this.font, totalStr, this.width / 2, totalY, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        int guiLeft = getGuiLeft();
        int cellSize = getCellSize();
        var player = Minecraft.getInstance().player;
        int mouseButton = event.button();

        // Handle sell slot clicks
        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = getSellTop() + row * cellSize;
            if (!isMouseInSlot((int) event.x(), (int) event.y(), x, y, SLOT_SIZE, SLOT_SIZE)) continue;

            ItemStack held = player.inventoryMenu.getCarried();
            ItemStack slotStack = sellSlots.get(i);

            if (mouseButton == 0) {
                if (held.isEmpty() && !slotStack.isEmpty()) {
                    player.inventoryMenu.setCarried(slotStack.copy());
                    sellSlots.set(i, ItemStack.EMPTY);
                } else if (!held.isEmpty() && slotStack.isEmpty()) {
                    sellSlots.set(i, held.copy());
                    player.inventoryMenu.setCarried(ItemStack.EMPTY);
                } else if (!held.isEmpty() && !slotStack.isEmpty()) {
                    if (isSameItem(held, slotStack)) {
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        int toAdd = Math.min(held.getCount(), space);
                        slotStack.grow(toAdd);
                        held.shrink(toAdd);
                        if (held.isEmpty()) {
                            player.inventoryMenu.setCarried(ItemStack.EMPTY);
                        }
                    } else {
                        player.inventoryMenu.setCarried(slotStack.copy());
                        sellSlots.set(i, held.copy());
                    }
                }
            } else if (mouseButton == 1) {
                if (!slotStack.isEmpty() && held.isEmpty()) {
                    ItemStack one = slotStack.copy();
                    one.setCount(1);
                    player.inventoryMenu.setCarried(one);
                    slotStack.shrink(1);
                    if (slotStack.isEmpty()) {
                        sellSlots.set(i, ItemStack.EMPTY);
                    }
                } else if (!slotStack.isEmpty() && !held.isEmpty() && isSameItem(held, slotStack) && held.getCount() < held.getMaxStackSize()) {
                    held.grow(1);
                    slotStack.shrink(1);
                    if (slotStack.isEmpty()) {
                        sellSlots.set(i, ItemStack.EMPTY);
                    }
                }
            }
            return true;
        }

        var invItems = player.getInventory().getNonEquipmentItems();

        // Handle inventory clicks (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * cellSize;
                int y = getInvTop() + row * cellSize;
                if (!isMouseInSlot((int) event.x(), (int) event.y(), x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                int invSlot = 9 + row * COLUMNS + col;
                handleInventoryClick(player, invSlot, mouseButton);
                return true;
            }
        }

        // Handle hotbar clicks
        int hotbarY = getInvTop() + 3 * cellSize + 6;
        for (int col = 0; col < COLUMNS; col++) {
            int x = guiLeft + col * cellSize;
            if (!isMouseInSlot((int) event.x(), (int) event.y(), x, hotbarY, SLOT_SIZE, SLOT_SIZE)) continue;
            handleInventoryClick(player, col, mouseButton);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void handleInventoryClick(net.minecraft.client.player.LocalPlayer player, int invSlot, int mouseButton) {
        var invItems = player.getInventory().getNonEquipmentItems();
        ItemStack invStack = invItems.get(invSlot);
        ItemStack held = player.inventoryMenu.getCarried();

        if (mouseButton == 0) {
            if (!invStack.isEmpty() && held.isEmpty()) {
                int sellSlot = findEmptyOrMatchingSellSlot(invStack);
                if (sellSlot >= 0) {
                    ItemStack slotStack = sellSlots.get(sellSlot);
                    if (slotStack.isEmpty()) {
                        sellSlots.set(sellSlot, invStack.copy());
                        invItems.set(invSlot, ItemStack.EMPTY);
                    } else {
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        int toAdd = Math.min(invStack.getCount(), space);
                        slotStack.grow(toAdd);
                        invStack.shrink(toAdd);
                        if (invStack.isEmpty()) {
                            invItems.set(invSlot, ItemStack.EMPTY);
                        }
                    }
                }
            } else if (!invStack.isEmpty() && !held.isEmpty() && isSameItem(held, invStack)) {
                int space = invStack.getMaxStackSize() - invStack.getCount();
                int toAdd = Math.min(held.getCount(), space);
                invStack.grow(toAdd);
                held.shrink(toAdd);
                if (held.isEmpty()) {
                    player.inventoryMenu.setCarried(ItemStack.EMPTY);
                }
            } else if (invStack.isEmpty() && !held.isEmpty()) {
                invItems.set(invSlot, held.copy());
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            } else if (!invStack.isEmpty() && !held.isEmpty()) {
                invItems.set(invSlot, held.copy());
                player.inventoryMenu.setCarried(invStack.copy());
            }
        } else if (mouseButton == 1 && !invStack.isEmpty() && held.isEmpty()) {
            int sellSlot = findEmptyOrMatchingSellSlot(invStack);
            if (sellSlot >= 0) {
                ItemStack slotStack = sellSlots.get(sellSlot);
                if (slotStack.isEmpty()) {
                    ItemStack one = invStack.copy();
                    one.setCount(1);
                    sellSlots.set(sellSlot, one);
                } else {
                    slotStack.grow(1);
                }
                invStack.shrink(1);
                if (invStack.isEmpty()) {
                    invItems.set(invSlot, ItemStack.EMPTY);
                }
            }
        }
    }

    private int findEmptyOrMatchingSellSlot(ItemStack stack) {
        for (int i = 0; i < sellSlots.size(); i++) {
            ItemStack slot = sellSlots.get(i);
            if (!slot.isEmpty() && isSameItem(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                return i;
            }
        }
        for (int i = 0; i < sellSlots.size(); i++) {
            if (sellSlots.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void sellAllItems() {
        List<ItemStack> toSell = new ArrayList<>();
        for (ItemStack stack : sellSlots) {
            if (!stack.isEmpty()) {
                toSell.add(stack.copy());
            }
        }
        if (toSell.isEmpty()) {
            return;
        }
        soldItems = true;
        ClientPacketDistributor.sendToServer(ShopPacket.sellGuiItems(toSell));
        for (int i = 0; i < sellSlots.size(); i++) {
            sellSlots.set(i, ItemStack.EMPTY);
        }
        Minecraft.getInstance().setScreen(null);
    }

    private void returnItemsToInventory() {
        var player = Minecraft.getInstance().player;
        for (ItemStack stack : sellSlots) {
            if (!stack.isEmpty()) {
                player.getInventory().add(stack);
            }
        }
        sellSlots.clear();
        for (int i = 0; i < 27; i++) {
            sellSlots.add(ItemStack.EMPTY);
        }
    }

    private double calculateTotalValue() {
        PriceEngine priceEngine = WorldShop.getPriceEngine();
        double total = 0.0;
        for (ItemStack stack : sellSlots) {
            if (!stack.isEmpty()) {
                total += priceEngine.getSellPrice(stack) * (double) stack.getCount();
            }
        }
        return total;
    }

    private void drawSlotBackground(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, -1438366652);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, -1439485133);
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
