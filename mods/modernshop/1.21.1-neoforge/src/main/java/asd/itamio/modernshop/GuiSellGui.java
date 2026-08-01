package asd.itamio.modernshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GuiSellGui extends Screen {
    private final List<ItemStack> sellSlots = new ArrayList<>();
    private boolean soldItems = false;
    private static final int COLUMNS = 9;
    private static final int SELL_ROWS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int CELL_SIZE = 20;

    public GuiSellGui() {
        super(Component.literal("Sell Items"));
        for (int i = 0; i < 27; i++) {
            sellSlots.add(ItemStack.EMPTY);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aSell All Items"), new Button.OnPress() {
            @Override
            public void onPress(Button button) {
                sellAllItems();
            }
        }).bounds(this.width / 2 - 100, this.height - 25, 200, 20).build());
    }

    @Override
    public void onClose() {
        super.onClose();
        if (!soldItems) {
            returnItemsToInventory();
        }
    }

    private void sendToServer(ShopPacket packet) {
        Minecraft.getInstance().getConnection().send(packet);
    }

    private int getGuiLeft() {
        int gridWidth = COLUMNS * CELL_SIZE - 2;
        return (this.width - gridWidth) / 2;
    }

    private int getSellTop() {
        return 30;
    }

    private int getInvTop() {
        return getSellTop() + SELL_ROWS * CELL_SIZE + 20;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.fill(0, 0, this.width, this.height, -870441442);

        int guiLeft = getGuiLeft();

        guiGraphics.drawCenteredString(this.font, "\u00a76\u00a7lSell Items", this.width / 2, 8, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "\u00a77Items to Sell", this.width / 2, getSellTop() - 10, 0xAAAAAA);

        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * CELL_SIZE;
            int y = getSellTop() + row * CELL_SIZE;
            drawSlotBackground(guiGraphics, x, y);
            ItemStack stack = sellSlots.get(i);
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, x + 1, y + 1);
                guiGraphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
            }
        }

        guiGraphics.drawCenteredString(this.font, "\u00a77Inventory", this.width / 2, getInvTop() - 10, 0xAAAAAA);

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int x = guiLeft + col * CELL_SIZE;
                    int y = getInvTop() + row * CELL_SIZE;
                    drawSlotBackground(guiGraphics, x, y);
                    ItemStack stack = player.getInventory().items.get(9 + row * COLUMNS + col);
                    if (!stack.isEmpty()) {
                        guiGraphics.renderItem(stack, x + 1, y + 1);
                        guiGraphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
                    }
                }
            }

            int hotbarY = getInvTop() + 3 * CELL_SIZE + 6;
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * CELL_SIZE;
                drawSlotBackground(guiGraphics, x, hotbarY);
                ItemStack stack = player.getInventory().items.get(col);
                if (!stack.isEmpty()) {
                    guiGraphics.renderItem(stack, x + 1, hotbarY + 1);
                    guiGraphics.renderItemDecorations(this.font, stack, x + 1, hotbarY + 1);
                }
            }
        }

        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * CELL_SIZE;
            int y = getSellTop() + row * CELL_SIZE;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
            ItemStack stack = sellSlots.get(i);
            if (stack.isEmpty()) break;
            double sellPrice = ModernShop.getPriceEngine().getSellPrice(stack);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("\u00a7f" + stack.getHoverName().getString()));
            tooltip.add(Component.literal("\u00a77x" + stack.getCount()));
            tooltip.add(Component.literal("\u00a7cSell: $" + String.format("%.2f", sellPrice) + " each"));
            tooltip.add(Component.literal("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount())));
            tooltip.add(Component.literal("\u00a77Click to pick up | Right-click to return one"));
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        if (player != null) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int x = guiLeft + col * CELL_SIZE;
                    int y = getInvTop() + row * CELL_SIZE;
                    if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                    ItemStack stack = player.getInventory().items.get(9 + row * COLUMNS + col);
                    if (stack.isEmpty()) continue;
                    double sellPrice = ModernShop.getPriceEngine().getSellPrice(stack);
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("\u00a7f" + stack.getHoverName().getString()));
                    tooltip.add(Component.literal("\u00a77x" + stack.getCount()));
                    tooltip.add(Component.literal("\u00a7cSell price: $" + String.format("%.2f", sellPrice) + " each"));
                    tooltip.add(Component.literal("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount())));
                    tooltip.add(Component.literal("\u00a77Click to move to sell area"));
                    guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                    break;
                }
            }

            int hotbarY = getInvTop() + 3 * CELL_SIZE + 6;
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * CELL_SIZE;
                if (!isMouseInSlot(mouseX, mouseY, x, hotbarY, SLOT_SIZE, SLOT_SIZE)) continue;
                ItemStack stack = player.getInventory().items.get(col);
                if (stack.isEmpty()) continue;
                double sellPrice = ModernShop.getPriceEngine().getSellPrice(stack);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("\u00a7f" + stack.getHoverName().getString()));
                tooltip.add(Component.literal("\u00a77x" + stack.getCount()));
                tooltip.add(Component.literal("\u00a7cSell price: $" + String.format("%.2f", sellPrice) + " each"));
                tooltip.add(Component.literal("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount())));
                tooltip.add(Component.literal("\u00a77Click to move to sell area"));
                guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }
        }

        double totalValue = calculateTotalValue();
        String totalStr = "\u00a7aTotal Value: $" + String.format("%.2f", totalValue);
        int totalY = getSellTop() + SELL_ROWS * CELL_SIZE + 4;
        guiGraphics.drawCenteredString(this.font, totalStr, this.width / 2, totalY, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        for (var widget : this.children()) {
            if (widget instanceof Button button) {
                if (button.mouseClicked(mouseX, mouseY, mouseButton)) {
                    return true;
                }
            }
        }

        int guiLeft = getGuiLeft();
        Player player = Minecraft.getInstance().player;
        if (player == null) return super.mouseClicked(mouseX, mouseY, mouseButton);

        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * CELL_SIZE;
            int y = getSellTop() + row * CELL_SIZE;
            if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;

            ItemStack held = player.containerMenu.getCarried();
            ItemStack slotStack = sellSlots.get(i);

            if (mouseButton == 0) {
                if (held.isEmpty() && !slotStack.isEmpty()) {
                    player.containerMenu.setCarried(slotStack.copy());
                    sellSlots.set(i, ItemStack.EMPTY);
                } else if (!held.isEmpty() && slotStack.isEmpty()) {
                    sellSlots.set(i, held.copy());
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                } else if (!held.isEmpty() && !slotStack.isEmpty()) {
                    if (held.getItem() == slotStack.getItem()) {
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        int toAdd = Math.min(held.getCount(), space);
                        slotStack.grow(toAdd);
                        held.shrink(toAdd);
                        if (held.isEmpty()) {
                            player.containerMenu.setCarried(ItemStack.EMPTY);
                        }
                    } else {
                        player.containerMenu.setCarried(slotStack.copy());
                        sellSlots.set(i, held.copy());
                    }
                }
            } else if (mouseButton == 1) {
                if (!slotStack.isEmpty() && held.isEmpty()) {
                    ItemStack one = slotStack.copy();
                    one.setCount(1);
                    player.containerMenu.setCarried(one);
                    slotStack.shrink(1);
                    if (slotStack.isEmpty()) {
                        sellSlots.set(i, ItemStack.EMPTY);
                    }
                } else if (!slotStack.isEmpty() && !held.isEmpty() && held.getItem() == slotStack.getItem() && held.getCount() < held.getMaxStackSize()) {
                    held.grow(1);
                    slotStack.shrink(1);
                    if (slotStack.isEmpty()) {
                        sellSlots.set(i, ItemStack.EMPTY);
                    }
                }
            }
            return true;
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * CELL_SIZE;
                int y = getInvTop() + row * CELL_SIZE;
                if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                int invSlot = 9 + row * COLUMNS + col;
                handleInventoryClick(player, invSlot, mouseButton);
                return true;
            }
        }

        int hotbarY = getInvTop() + 3 * CELL_SIZE + 6;
        for (int col = 0; col < COLUMNS; col++) {
            int x = guiLeft + col * CELL_SIZE;
            if (!isMouseInSlot((int) mouseX, (int) mouseY, x, hotbarY, SLOT_SIZE, SLOT_SIZE)) continue;
            handleInventoryClick(player, col, mouseButton);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleInventoryClick(Player player, int invSlot, int mouseButton) {
        ItemStack invStack = player.getInventory().items.get(invSlot);
        ItemStack held = player.containerMenu.getCarried();

        if (mouseButton == 0) {
            if (!invStack.isEmpty() && held.isEmpty()) {
                int sellSlot = findEmptyOrMatchingSellSlot(invStack);
                if (sellSlot >= 0) {
                    ItemStack slotStack = sellSlots.get(sellSlot);
                    if (slotStack.isEmpty()) {
                        sellSlots.set(sellSlot, invStack.copy());
                        player.getInventory().items.set(invSlot, ItemStack.EMPTY);
                    } else {
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        int toAdd = Math.min(invStack.getCount(), space);
                        slotStack.grow(toAdd);
                        invStack.shrink(toAdd);
                        if (invStack.isEmpty()) {
                            player.getInventory().items.set(invSlot, ItemStack.EMPTY);
                        }
                    }
                }
            } else if (!invStack.isEmpty() && !held.isEmpty() && held.getItem() == invStack.getItem()) {
                int space = invStack.getMaxStackSize() - invStack.getCount();
                int toAdd = Math.min(held.getCount(), space);
                invStack.grow(toAdd);
                held.shrink(toAdd);
                if (held.isEmpty()) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                }
            } else if (invStack.isEmpty() && !held.isEmpty()) {
                player.getInventory().items.set(invSlot, held.copy());
                player.containerMenu.setCarried(ItemStack.EMPTY);
            } else if (!invStack.isEmpty() && !held.isEmpty()) {
                player.getInventory().items.set(invSlot, held.copy());
                player.containerMenu.setCarried(invStack.copy());
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
                    player.getInventory().items.set(invSlot, ItemStack.EMPTY);
                }
            }
        }
    }

    private int findEmptyOrMatchingSellSlot(ItemStack stack) {
        for (int i = 0; i < sellSlots.size(); i++) {
            ItemStack slot = sellSlots.get(i);
            if (!slot.isEmpty() && slot.getItem() == stack.getItem() && slot.getCount() < slot.getMaxStackSize()) {
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
        sendToServer(ShopPacket.sellGuiItems(toSell));
        for (int i = 0; i < sellSlots.size(); i++) {
            sellSlots.set(i, ItemStack.EMPTY);
        }
        Minecraft.getInstance().setScreen(null);
    }

    private void returnItemsToInventory() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        for (ItemStack stack : sellSlots) {
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        sellSlots.clear();
        for (int i = 0; i < 27; i++) {
            sellSlots.add(ItemStack.EMPTY);
        }
    }

    private double calculateTotalValue() {
        double total = 0.0;
        for (ItemStack stack : sellSlots) {
            if (!stack.isEmpty()) {
                total += ModernShop.getPriceEngine().getSellPrice(stack) * (double) stack.getCount();
            }
        }
        return total;
    }

    private void drawSlotBackground(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, -1438366652);
        guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, -1439485133);
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
