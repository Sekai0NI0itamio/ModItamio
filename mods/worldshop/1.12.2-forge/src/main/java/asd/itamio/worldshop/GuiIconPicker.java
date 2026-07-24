package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A standalone full-screen icon picker.
 * Opens as a separate screen (fully replacing the edit screen),
 * and calls the callback with the chosen ItemStack when the user clicks one.
 *
 * Ported from the 1.20.1-fabric reference, adapted to 1.12.2 Forge MCP APIs.
 */
public class GuiIconPicker extends GuiScreen {
    private final GuiScreen parent;
    private final Consumer<ItemStack> onIconSelected;

    private GuiTextField searchField;
    private String searchText = "";
    private final List<ItemStack> searchResults = new ArrayList<>();
    private int searchScroll = 0;
    private ItemStack currentIcon;

    private static final int COLS = 7;
    private static final int SLOT_SIZE = 20;
    private static final int BG_COLOR = 0xFF1A1A1A;

    /**
     * @param parent         The screen that opened this picker (will be returned to on back/cancel)
     * @param currentIcon    The currently selected icon to show a preview of
     * @param onIconSelected Callback invoked with the chosen ItemStack when user clicks an icon
     */
    public GuiIconPicker(GuiScreen parent, ItemStack currentIcon, Consumer<ItemStack> onIconSelected) {
        this.parent = parent;
        this.currentIcon = currentIcon == null ? ItemStack.EMPTY : currentIcon.copy();
        this.onIconSelected = onIconSelected;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;

        this.searchField = new GuiTextField(0, this.fontRenderer, centerX - 120, 35, 240, 16);
        this.searchField.setMaxStringLength(40);
        this.searchField.setFocused(true);

        this.buttonList.add(new GuiButton(0, centerX - 50, this.height - 25, 100, 20, "\u00a7cBack to Edit"));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    private void onSearchChanged() {
        this.searchText = this.searchField.getText().toLowerCase().trim();
        this.searchScroll = 0;
        searchResults.clear();
        if (searchText.isEmpty()) return;

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;
            String name = stack.getDisplayName().toLowerCase();
            String id = item.getRegistryName() != null ? item.getRegistryName().toString().toLowerCase() : "";
            if (name.contains(searchText) || id.contains(searchText)) {
                searchResults.add(stack);
                if (searchResults.size() >= 200) break;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Fully opaque background — the edit screen is completely hidden behind this
        drawRect(0, 0, this.width, this.height, BG_COLOR);

        int centerX = this.width / 2;

        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lSelect Icon", centerX, 8, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "\u00a77Search for a block/item to use as icon:", centerX, 22, 0xAAAAAA);

        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }

        // Current icon preview (well below search field at y=35+16=51)
        this.drawCenteredString(this.fontRenderer, "\u00a77Current icon:", centerX, 68, 0xAAAAAA);
        drawSlot(centerX - 12, 78, 24, 24);
        if (currentIcon != null && !currentIcon.isEmpty()) {
            renderItem(currentIcon, centerX - 10, 80);
        }

        // Search results grid
        int startY = 115;
        int availableWidth = COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            int index = i + searchScroll * COLS;
            ItemStack stack = searchResults.get(index);
            drawSlot(x, y, SLOT_SIZE, SLOT_SIZE);
            renderItem(stack, x + 2, y + 2);
        }

        this.resetGlState();

        // Tooltips for search results
        for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * COLS;
                ItemStack stack = searchResults.get(index);
                List<String> tooltip = new ArrayList<>();
                tooltip.add(stack.getDisplayName());
                if (stack.getItem().getRegistryName() != null) {
                    tooltip.add("\u00a77" + stack.getItem().getRegistryName().toString());
                }
                this.drawHoveringText(tooltip, mouseX, mouseY);
                break;
            }
        }

        this.resetGlState();

        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.searchField != null) this.searchField.mouseClicked(mouseX, mouseY, mouseButton);

        for (GuiButton button : this.buttonList) {
            if (!button.mousePressed(this.mc, mouseX, mouseY)) continue;
            button.playPressSound(this.mc.getSoundHandler());
            this.actionPerformed(button);
            return;
        }

        // Check search result clicks
        int startY = 115;
        int availableWidth = COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * COLS;
                ItemStack chosen = searchResults.get(index).copy();
                // Fire callback with chosen icon, then return to parent
                if (onIconSelected != null) {
                    onIconSelected.accept(chosen);
                }
                ScreenManager.closeToParent(parent);
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            int maxScroll = Math.max(0, (int) Math.ceil((double) searchResults.size() / (double) COLS) - 4);
            if (scroll > 0) searchScroll = Math.max(0, searchScroll - 1);
            else searchScroll = Math.min(maxScroll, searchScroll + 1);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            ScreenManager.closeToParent(parent);
            return;
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            this.searchField.textboxKeyTyped(typedChar, keyCode);
            onSearchChanged();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void drawSlot(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, -1438366652);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, -1439485133);
    }

    private void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(stack, x, y);
        resetGlState();
    }

    private void resetGlState() {
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            ScreenManager.closeToParent(parent);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
