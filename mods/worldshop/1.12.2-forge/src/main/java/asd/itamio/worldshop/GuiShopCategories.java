package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuiShopCategories extends GuiScreen {
    private List<ShopCategory> categories;
    private final boolean adminMode;
    private int scrollOffset = 0;
    private static final int ICON_SIZE = 28;
    private static final int SPACING = 6;
    private static final int COLUMNS = 9;

    private GuiTextField searchField;
    private String searchQuery = "";
    private List<ShopCategory> filteredCategories;

    private boolean layoutEditMode = false;
    private int[] layoutPositions = null;

    public GuiShopCategories(boolean adminMode) {
        this.adminMode = adminMode;
        this.categories = WorldShop.getCategories();
        this.filteredCategories = new ArrayList<>(categories);
        this.layoutPositions = WorldShop.getCategorySlotPositions();
    }

    public GuiShopCategories() {
        this(false);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int fieldW = 150;
        int fieldH = 16;
        this.searchField = new GuiTextField(0, this.fontRenderer, this.width / 2 - fieldW / 2, 28, fieldW, fieldH);
        this.searchField.setText(searchQuery);
        this.searchField.setMaxStringLength(32);

        if (adminMode) {
            int btnY = this.height - 22;
            this.buttonList.add(new GuiButton(10, 4, btnY, 80, 20, layoutEditMode ? "\u00a7eExit Layout" : "\u00a7eLayout"));
            this.buttonList.add(new GuiButton(11, 88, btnY, 80, 20, "\u00a7aAdd Cat"));
            this.buttonList.add(new GuiButton(12, 172, btnY, 80, 20, "\u00a7cSettings"));
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        if (layoutPositions != null) {
            WorldShop.setCategorySlotPositions(layoutPositions);
        }
    }

    private void resetGlState() {
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
    }

    private void updateFilteredCategories() {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            filteredCategories = new ArrayList<>(categories);
        } else {
            String query = searchQuery.toLowerCase().trim();
            filteredCategories = new ArrayList<>();
            for (ShopCategory cat : categories) {
                if (cat.getName().toLowerCase().contains(query)) {
                    filteredCategories.add(cat);
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawRect(0, 0, this.width, this.height, -870441442);
        GlStateManager.disableBlend();

        String title = "\u00a76\u00a7l\u25c8 Modern Shop \u25c8";
        if (adminMode) title += " \u00a7c[ADMIN]";
        this.drawCenteredString(this.fontRenderer, title, this.width / 2, 8, 0xFFFFFF);

        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }
        this.drawString(this.fontRenderer, "\u00a77Search:", this.width / 2 - 100, 32, 0xAAAAAA);

        int cellSize = 34;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 50;
        int rowsPerPage = Math.max(1, (this.height - guiTop - 40) / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        for (int i = 0; i < visibleCount && startIndex + i < this.filteredCategories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            int catIndex = startIndex + i;
            ShopCategory category = this.filteredCategories.get(catIndex);
            drawSlotBackground(x, y);
            ItemStack icon = category.getIcon();
            renderItem(icon, x + 6, y + 6);
        }

        this.resetGlState();

        for (int i = 0; i < visibleCount && startIndex + i < this.filteredCategories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
            int catIndex = startIndex + i;
            ShopCategory category = this.filteredCategories.get(catIndex);
            String name = formatCategoryName(category.getName());
            List<String> tooltip = new ArrayList<>();
            tooltip.add("\u00a7f" + name + " \u00a77(" + category.getItems().size() + " items)");
            if (adminMode) {
                tooltip.add("\u00a7e[ADMIN] Shift+Click to remove");
            }
            this.drawHoveringText(tooltip, mouseX, mouseY);
            break;
        }

        this.resetGlState();

        if (this.filteredCategories.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            this.drawCenteredString(this.fontRenderer, scrollInfo, this.width / 2, this.height - 38, 0xFFFFFF);
        }

        String footer;
        if (layoutEditMode) {
            footer = "\u00a7eLayout Edit Mode | Click slots to rearrange | ESC to exit";
        } else if (adminMode) {
            footer = "\u00a77Click a category | \u00a7eLayout \u00a77to rearrange | ESC to close";
        } else {
            footer = "\u00a77Click a category to browse items | ESC to close";
        }
        this.drawCenteredString(this.fontRenderer, footer, this.width / 2, this.height - 14, 0xAAAAAA);

        for (GuiButton button : this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        for (GuiButton button : this.buttonList) {
            if (!button.mousePressed(this.mc, mouseX, mouseY)) continue;
            button.playPressSound(this.mc.getSoundHandler());
            this.actionPerformed(button);
            return;
        }

        if (mouseButton == 0) {
            int cellSize = 34;
            int gridWidth = COLUMNS * cellSize - SPACING;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 50;
            int rowsPerPage = Math.max(1, (this.height - guiTop - 40) / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            for (int i = 0; i < visibleCount && startIndex + i < this.filteredCategories.size(); i++) {
                int col = i % COLUMNS;
                int x = guiLeft + col * cellSize;
                int row = i / COLUMNS;
                int y = guiTop + row * cellSize;
                if (!isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
                int catIndex = startIndex + i;
                ShopCategory clickedCat = this.filteredCategories.get(catIndex);
                int originalIndex = this.categories.indexOf(clickedCat);

                if (layoutEditMode) {
                    // In layout edit mode, clicking a slot cycles through categories
                    // (simplified layout editing)
                    return;
                }

                if (adminMode && isShiftKeyDown()) {
                    // Remove category (admin only)
                    WorldShop.NETWORK.sendToServer(ShopPacket.removeCategory(originalIndex));
                    return;
                }

                Minecraft.getMinecraft().displayGuiScreen(new GuiShopItems(clickedCat, originalIndex, adminMode));
                return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 10) {
            layoutEditMode = !layoutEditMode;
            if (layoutEditMode && layoutPositions == null) {
                layoutPositions = WorldShop.buildCenterSphereLayout(categories.size(), COLUMNS, 3);
            }
            initGui();
        } else if (button.id == 11) {
            ScreenManager.open(new GuiAddCategory(this));
        } else if (button.id == 12) {
            ScreenManager.open(new GuiShopSettings(this));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchField != null && this.searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                searchQuery = this.searchField.getText();
                updateFilteredCategories();
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.searchField.setFocused(false);
                return;
            }
            this.searchField.textboxKeyTyped(typedChar, keyCode);
            searchQuery = this.searchField.getText();
            updateFilteredCategories();
            this.scrollOffset = 0;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            int guiTop = 50;
            int rowsPerPage = Math.max(1, (this.height - guiTop - 90) / 34);
            this.scrollOffset = scroll > 0 ? Math.max(0, this.scrollOffset - 1) : Math.min(getMaxScrollPages(rowsPerPage) - 1, this.scrollOffset + 1);
        }
    }

    private int getMaxScrollPages(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) this.filteredCategories.size() / (double) totalSlots));
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

    private void drawSlotBackground(int x, int y) {
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawRect(x, y, x + ICON_SIZE, y + ICON_SIZE, -1438366652);
        drawRect(x + 1, y + 1, x + ICON_SIZE - 1, y + ICON_SIZE - 1, -1439485133);
        GlStateManager.disableBlend();
    }

    private void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(stack, x, y);
        this.resetGlState();
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
