package asd.itamio.worldshop;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiEditItem extends GuiScreen {
    private final GuiScreen parent;
    private final int categoryIndex;
    private final String itemId;

    private GuiTextField nameField;
    private GuiTextField buyPriceField;
    private GuiTextField sellPriceField;
    private String statusMessage = "";

    public GuiEditItem(GuiScreen parent, int categoryIndex, String itemId) {
        this.parent = parent;
        this.categoryIndex = categoryIndex;
        this.itemId = itemId;
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

        this.buyPriceField = new GuiTextField(1, this.fontRenderer, centerX - fieldW / 2, 90, fieldW, fieldH);
        this.buyPriceField.setMaxStringLength(10);

        this.sellPriceField = new GuiTextField(2, this.fontRenderer, centerX - fieldW / 2, 130, fieldW, fieldH);
        this.sellPriceField.setMaxStringLength(10);

        int btnW = 100;
        int btnH = 20;
        this.buttonList.add(new GuiButton(0, centerX - btnW - 2, 170, btnW, btnH, "\u00a7aSave"));
        this.buttonList.add(new GuiButton(1, centerX + 2, 170, btnW, btnH, "\u00a7cCancel"));
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
        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lEdit Item", centerX, 12, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "\u00a77Item: " + itemId, centerX, 28, 0xAAAAAA);

        this.drawString(this.fontRenderer, "\u00a77Display Name (empty = no change):", centerX - 100, 40, 0xCCCCCC);
        this.nameField.drawTextBox();

        this.drawString(this.fontRenderer, "\u00a77Buy Price (0 = auto):", centerX - 100, 80, 0xCCCCCC);
        this.buyPriceField.drawTextBox();

        this.drawString(this.fontRenderer, "\u00a77Sell Price (0 = auto):", centerX - 100, 120, 0xCCCCCC);
        this.sellPriceField.drawTextBox();

        if (!statusMessage.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, statusMessage, centerX, 200, 0xFF5555);
        }

        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            String displayName = nameField.getText().trim();
            double buyPrice = 0;
            double sellPrice = 0;
            try {
                if (!buyPriceField.getText().trim().isEmpty()) {
                    buyPrice = Double.parseDouble(buyPriceField.getText().trim());
                }
            } catch (NumberFormatException e) {
                statusMessage = "\u00a7cInvalid buy price!";
                return;
            }
            try {
                if (!sellPriceField.getText().trim().isEmpty()) {
                    sellPrice = Double.parseDouble(sellPriceField.getText().trim());
                }
            } catch (NumberFormatException e) {
                statusMessage = "\u00a7cInvalid sell price!";
                return;
            }
            WorldShop.NETWORK.sendToServer(ShopPacket.editItem(categoryIndex, itemId, displayName, "", buyPrice, sellPrice));
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
        if (this.buyPriceField != null && this.buyPriceField.isFocused()) {
            this.buyPriceField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (this.sellPriceField != null && this.sellPriceField.isFocused()) {
            this.sellPriceField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.nameField != null) this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.buyPriceField != null) this.buyPriceField.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.sellPriceField != null) this.sellPriceField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
