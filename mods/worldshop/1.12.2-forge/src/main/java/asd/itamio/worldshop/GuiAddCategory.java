package asd.itamio.worldshop;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiAddCategory extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField nameField;
    private GuiTextField iconField;
    private String statusMessage = "";

    public GuiAddCategory(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int fieldW = 200;
        int fieldH = 20;

        this.nameField = new GuiTextField(0, this.fontRenderer, centerX - fieldW / 2, 50, fieldW, fieldH);
        this.nameField.setMaxStringLength(32);
        this.nameField.setFocused(true);

        this.iconField = new GuiTextField(1, this.fontRenderer, centerX - fieldW / 2, 90, fieldW, fieldH);
        this.iconField.setMaxStringLength(64);
        this.iconField.setText("minecraft:chest");

        int btnW = 100;
        int btnH = 20;
        this.buttonList.add(new GuiButton(0, centerX - btnW - 2, 130, btnW, btnH, "\u00a7aCreate"));
        this.buttonList.add(new GuiButton(1, centerX + 2, 130, btnW, btnH, "\u00a7cCancel"));
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
        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lAdd New Category", centerX, 20, 0xFFFFFF);

        this.drawString(this.fontRenderer, "\u00a77Category Name:", centerX - 100, 40, 0xCCCCCC);
        this.nameField.drawTextBox();

        this.drawString(this.fontRenderer, "\u00a77Icon Item ID:", centerX - 100, 80, 0xCCCCCC);
        this.iconField.drawTextBox();

        if (!statusMessage.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, statusMessage, centerX, 160, 0xFF5555);
        }

        // Preview icon
        String iconId = iconField.getText().trim();
        Item iconItem = Item.getByNameOrId(iconId);
        if (iconItem != null) {
            ItemStack previewStack = new ItemStack(iconItem);
            this.drawString(this.fontRenderer, "\u00a77Preview:", centerX + 110, 90, 0xCCCCCC);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(previewStack, centerX + 110, 102);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }

        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            String name = nameField.getText().trim();
            String iconId = iconField.getText().trim();
            if (name.isEmpty()) {
                statusMessage = "\u00a7cName cannot be empty!";
                return;
            }
            if (iconId.isEmpty()) {
                statusMessage = "\u00a7cIcon item ID cannot be empty!";
                return;
            }
            Item iconItem = Item.getByNameOrId(iconId);
            if (iconItem == null) {
                statusMessage = "\u00a7cUnknown item: " + iconId;
                return;
            }
            WorldShop.NETWORK.sendToServer(ShopPacket.addCategory(name, iconId));
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
        if (this.nameField != null && this.nameField.isFocused()) {
            this.nameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (this.iconField != null && this.iconField.isFocused()) {
            this.iconField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.nameField != null) this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.iconField != null) this.iconField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
