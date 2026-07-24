package asd.itamio.worldshop;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiAddItem extends GuiScreen {
    private final GuiScreen parent;
    private final int categoryIndex;
    private GuiTextField itemIdField;
    private String statusMessage = "";

    public GuiAddItem(GuiScreen parent, int categoryIndex) {
        this.parent = parent;
        this.categoryIndex = categoryIndex;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int fieldW = 200;
        int fieldH = 20;

        this.itemIdField = new GuiTextField(0, this.fontRenderer, centerX - fieldW / 2, 50, fieldW, fieldH);
        this.itemIdField.setMaxStringLength(64);
        this.itemIdField.setFocused(true);
        this.itemIdField.setText("minecraft:");

        int btnW = 100;
        int btnH = 20;
        this.buttonList.add(new GuiButton(0, centerX - btnW - 2, 90, btnW, btnH, "\u00a7aAdd Item"));
        this.buttonList.add(new GuiButton(1, centerX + 2, 90, btnW, btnH, "\u00a7cCancel"));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int centerX = this.width / 2;
        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lAdd Item to Category", centerX, 20, 0xFFFFFF);

        this.drawString(this.fontRenderer, "\u00a77Item ID (e.g. minecraft:diamond):", centerX - 100, 40, 0xCCCCCC);
        this.itemIdField.drawTextBox();

        if (!statusMessage.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, statusMessage, centerX, 120, 0xFF5555);
        }

        String itemId = itemIdField.getText().trim();
        Item item = Item.getByNameOrId(itemId);
        if (item != null) {
            ItemStack previewStack = new ItemStack(item);
            this.drawString(this.fontRenderer, "\u00a77Preview:", centerX + 110, 50, 0xCCCCCC);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(previewStack, centerX + 110, 62);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }

        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            String itemId = itemIdField.getText().trim();
            if (itemId.isEmpty()) {
                statusMessage = "\u00a7cItem ID cannot be empty!";
                return;
            }
            Item item = Item.getByNameOrId(itemId);
            if (item == null) {
                statusMessage = "\u00a7cUnknown item: " + itemId;
                return;
            }
            WorldShop.NETWORK.sendToServer(ShopPacket.addItemToCategory(categoryIndex, itemId));
            ScreenManager.closeToParent(parent);
        } else if (button.id == 1) {
            ScreenManager.closeToParent(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            ScreenManager.closeToParent(parent);
            return;
        }
        if (this.itemIdField != null && this.itemIdField.isFocused()) {
            this.itemIdField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.itemIdField != null) this.itemIdField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
