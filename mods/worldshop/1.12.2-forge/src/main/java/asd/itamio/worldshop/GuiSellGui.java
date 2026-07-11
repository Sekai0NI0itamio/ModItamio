package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiSellGui extends GuiScreen {
    private final List<ItemStack> sellSlots = new ArrayList<>();
    private boolean soldItems = false;
    private static final int COLUMNS = 9;
    private static final int SELL_ROWS = 3;
    private static final int INV_ROWS = 4;
    private static final int SLOT_SIZE = 18;
    private static final int SPACING = 2;

    public GuiSellGui() {
        for (int i = 0; i < 27; i++) {
            sellSlots.add(ItemStack.EMPTY);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 25, 200, 20, "\u00a7aSell All Items"));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        if (!soldItems) {
            returnItemsToInventory();
        }
    }

    private void resetGlState() {
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
    }

    private int getCellSize() {
        return 20;
    }

    private int getGuiLeft() {
        int gridWidth = COLUMNS * getCellSize() - SPACING;
        return (this.width - gridWidth) / 2;
    }

    private int getSellTop() {
        return 30;
    }

    private int getInvTop() {
        return getSellTop() + SELL_ROWS * getCellSize() + 20;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawRect(0, 0, this.width, this.height, -870441442);
        GlStateManager.disableBlend();

        int guiLeft = getGuiLeft();
        int cellSize = getCellSize();

        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lSell Items", this.width / 2, 8, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "\u00a77Items to Sell", this.width / 2, getSellTop() - 10, 0xAAAAAA);

        // Draw sell slots
        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = getSellTop() + row * cellSize;
            drawSlotBackground(x, y);
            ItemStack stack = sellSlots.get(i);
            if (!stack.isEmpty()) {
                renderItem(stack, x + 1, y + 1);
            }
        }

        this.drawCenteredString(this.fontRenderer, "\u00a77Inventory", this.width / 2, getInvTop() - 10, 0xAAAAAA);

        EntityPlayerSP player = Minecraft.getMinecraft().player;

        // Draw main inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col2 = 0; col2 < COLUMNS; col2++) {
                int x = guiLeft + col2 * cellSize;
                int y = getInvTop() + row * cellSize;
                drawSlotBackground(x, y);
                ItemStack stack = player.inventory.mainInventory.get(9 + row * COLUMNS + col2);
                if (!stack.isEmpty()) {
                    renderItem(stack, x + 1, y + 1);
                }
            }
        }

        // Draw hotbar
        int hotbarY = getInvTop() + 3 * cellSize + 6;
        for (int col2 = 0; col2 < COLUMNS; col2++) {
            int x = guiLeft + col2 * cellSize;
            drawSlotBackground(x, hotbarY);
            ItemStack stack = player.inventory.mainInventory.get(col2);
            if (!stack.isEmpty()) {
                renderItem(stack, x + 1, hotbarY + 1);
            }
        }

        this.resetGlState();

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
            List<String> tooltip = new ArrayList<>();
            tooltip.add("\u00a7f" + stack.getDisplayName());
            tooltip.add("\u00a77x" + stack.getCount());
            tooltip.add("\u00a7cSell: $" + String.format("%.2f", sellPrice) + " each");
            tooltip.add("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount()));
            tooltip.add("\u00a77Click to pick up | Right-click to return one");
            this.drawHoveringText(tooltip, mouseX, mouseY);
            break;
        }

        // Tooltips for inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * cellSize;
                int y = getInvTop() + row * cellSize;
                if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                ItemStack stack = player.inventory.mainInventory.get(9 + row * COLUMNS + col);
                if (stack.isEmpty()) continue;
                PriceEngine priceEngine = WorldShop.getPriceEngine();
                double sellPrice = priceEngine.getSellPrice(stack);
                List<String> tooltip = new ArrayList<>();
                tooltip.add("\u00a7f" + stack.getDisplayName());
                tooltip.add("\u00a77x" + stack.getCount());
                tooltip.add("\u00a7cSell price: $" + String.format("%.2f", sellPrice) + " each");
                tooltip.add("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount()));
                tooltip.add("\u00a77Click to move to sell area");
                this.drawHoveringText(tooltip, mouseX, mouseY);
                break;
            }
        }

        // Tooltips for hotbar
        for (int col = 0; col < COLUMNS; col++) {
            int x = guiLeft + col * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, hotbarY, SLOT_SIZE, SLOT_SIZE)) continue;
            ItemStack stack = player.inventory.mainInventory.get(col);
            if (stack.isEmpty()) break;
            PriceEngine priceEngine = WorldShop.getPriceEngine();
            double sellPrice = priceEngine.getSellPrice(stack);
            List<String> tooltip = new ArrayList<>();
            tooltip.add("\u00a7f" + stack.getDisplayName());
            tooltip.add("\u00a77x" + stack.getCount());
            tooltip.add("\u00a7cSell price: $" + String.format("%.2f", sellPrice) + " each");
            tooltip.add("\u00a7aTotal: $" + String.format("%.2f", sellPrice * (double) stack.getCount()));
            tooltip.add("\u00a77Click to move to sell area");
            this.drawHoveringText(tooltip, mouseX, mouseY);
            break;
        }

        this.resetGlState();

        double totalValue = calculateTotalValue();
        String totalStr = "\u00a7aTotal Value: $" + String.format("%.2f", totalValue);
        int totalY = getSellTop() + SELL_ROWS * cellSize + 4;
        this.drawCenteredString(this.fontRenderer, totalStr, this.width / 2, totalY, 0xFFFFFF);

        this.resetGlState();
        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (GuiButton button : this.buttonList) {
            if (!button.mousePressed(this.mc, mouseX, mouseY)) continue;
            button.playPressSound(this.mc.getSoundHandler());
            this.actionPerformed(button);
            return;
        }

        int guiLeft = getGuiLeft();
        int cellSize = getCellSize();
        EntityPlayerSP player = Minecraft.getMinecraft().player;

        // Handle sell slot clicks
        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = getSellTop() + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;

            ItemStack held = player.inventory.getItemStack();
            ItemStack slotStack = sellSlots.get(i);

            if (mouseButton == 0) {
                if (held.isEmpty() && !slotStack.isEmpty()) {
                    // Pick up from sell slot
                    player.inventory.setItemStack(slotStack.copy());
                    sellSlots.set(i, ItemStack.EMPTY);
                } else if (!held.isEmpty() && slotStack.isEmpty()) {
                    // Place into empty sell slot
                    sellSlots.set(i, held.copy());
                    player.inventory.setItemStack(ItemStack.EMPTY);
                } else if (!held.isEmpty() && !slotStack.isEmpty()) {
                    if (isSameItem(held, slotStack)) {
                        // Merge
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        int toAdd = Math.min(held.getCount(), space);
                        slotStack.grow(toAdd);
                        held.shrink(toAdd);
                        if (held.isEmpty()) {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                    } else {
                        // Swap
                        player.inventory.setItemStack(slotStack.copy());
                        sellSlots.set(i, held.copy());
                    }
                }
            } else if (mouseButton == 1) {
                if (!slotStack.isEmpty() && held.isEmpty()) {
                    // Take one item
                    ItemStack one = slotStack.copy();
                    one.setCount(1);
                    player.inventory.setItemStack(one);
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
            return;
        }

        // Handle inventory clicks (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int x = guiLeft + col * cellSize;
                int y = getInvTop() + row * cellSize;
                if (!isMouseInSlot(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) continue;
                int invSlot = 9 + row * COLUMNS + col;
                handleInventoryClick(player, invSlot, mouseButton);
                return;
            }
        }

        // Handle hotbar clicks
        int hotbarY = getInvTop() + 3 * cellSize + 6;
        for (int col = 0; col < COLUMNS; col++) {
            int x = guiLeft + col * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, hotbarY, SLOT_SIZE, SLOT_SIZE)) continue;
            handleInventoryClick(player, col, mouseButton);
            return;
        }
    }

    private void handleInventoryClick(EntityPlayer player, int invSlot, int mouseButton) {
        ItemStack invStack = player.inventory.mainInventory.get(invSlot);
        ItemStack held = player.inventory.getItemStack();

        if (mouseButton == 0) {
            if (!invStack.isEmpty() && held.isEmpty()) {
                int sellSlot = findEmptyOrMatchingSellSlot(invStack);
                if (sellSlot >= 0) {
                    ItemStack slotStack = sellSlots.get(sellSlot);
                    if (slotStack.isEmpty()) {
                        sellSlots.set(sellSlot, invStack.copy());
                        player.inventory.mainInventory.set(invSlot, ItemStack.EMPTY);
                    } else {
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        int toAdd = Math.min(invStack.getCount(), space);
                        slotStack.grow(toAdd);
                        invStack.shrink(toAdd);
                        if (invStack.isEmpty()) {
                            player.inventory.mainInventory.set(invSlot, ItemStack.EMPTY);
                        }
                    }
                }
            } else if (!invStack.isEmpty() && !held.isEmpty() && isSameItem(held, invStack)) {
                int space = invStack.getMaxStackSize() - invStack.getCount();
                int toAdd = Math.min(held.getCount(), space);
                invStack.grow(toAdd);
                held.shrink(toAdd);
                if (held.isEmpty()) {
                    player.inventory.setItemStack(ItemStack.EMPTY);
                }
            } else if (invStack.isEmpty() && !held.isEmpty()) {
                player.inventory.mainInventory.set(invSlot, held.copy());
                player.inventory.setItemStack(ItemStack.EMPTY);
            } else if (!invStack.isEmpty() && !held.isEmpty()) {
                // Swap
                player.inventory.mainInventory.set(invSlot, held.copy());
                player.inventory.setItemStack(invStack.copy());
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
                    player.inventory.mainInventory.set(invSlot, ItemStack.EMPTY);
                }
            }
        }
    }

    private int findEmptyOrMatchingSellSlot(ItemStack stack) {
        // First find a matching slot with space
        for (int i = 0; i < sellSlots.size(); i++) {
            ItemStack slot = sellSlots.get(i);
            if (!slot.isEmpty() && isSameItem(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                return i;
            }
        }
        // Then find an empty slot
        for (int i = 0; i < sellSlots.size(); i++) {
            if (sellSlots.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            sellAllItems();
        }
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
        WorldShop.NETWORK.sendToServer(ShopPacket.sellGuiItems(toSell));
        for (int i = 0; i < sellSlots.size(); i++) {
            sellSlots.set(i, ItemStack.EMPTY);
        }
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    private void returnItemsToInventory() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        for (ItemStack stack : sellSlots) {
            if (!stack.isEmpty()) {
                player.inventory.addItemStackToInventory(stack);
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

    private void drawSlotBackground(int x, int y) {
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawRect(x, y, x + SLOT_SIZE, y + SLOT_SIZE, -1438366652);
        drawRect(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, -1439485133);
        GlStateManager.disableBlend();
    }

    private void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(stack, x, y);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(this.fontRenderer, stack, x, y, null);
        this.resetGlState();
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
