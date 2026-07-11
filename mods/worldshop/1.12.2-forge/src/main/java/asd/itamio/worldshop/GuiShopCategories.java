package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class GuiShopCategories extends GuiScreen {
    private List<ShopCategory> categories = WorldShop.getCategories();
    private int scrollOffset = 0;
    private static final int ICON_SIZE = 28;
    private static final int SPACING = 6;
    private static final int COLUMNS = 9;

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    private void resetGlState() {
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawRect(0, 0, this.width, this.height, -870441442);
        GlStateManager.disableBlend();

        this.drawCenteredString(this.fontRenderer, "\u00a76\u00a7lShop - Categories", this.width / 2, 12, 0xFFFFFF);

        int cellSize = 34;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 35;
        int rowsPerPage = Math.max(1, (this.height - guiTop - 40) / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        // Draw category icons
        for (int i = 0; i < visibleCount && startIndex + i < this.categories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            int catIndex = startIndex + i;
            ShopCategory category = this.categories.get(catIndex);
            drawSlotBackground(x, y);
            ItemStack icon = category.getIcon();
            renderItem(icon, x + 6, y + 6);
        }

        this.resetGlState();

        // Draw tooltips
        for (int i = 0; i < visibleCount && startIndex + i < this.categories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
            int catIndex = startIndex + i;
            ShopCategory category = this.categories.get(catIndex);
            String name = formatCategoryName(category.getName());
            this.drawHoveringText(Collections.singletonList("\u00a7f" + name + " \u00a77(" + category.getItems().size() + " items)"), mouseX, mouseY);
            break;
        }

        this.resetGlState();

        if (this.categories.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            this.drawCenteredString(this.fontRenderer, scrollInfo, this.width / 2, this.height - 30, 0xFFFFFF);
        }
        String footer = "\u00a77Click a category to browse items | ESC to close";
        this.drawCenteredString(this.fontRenderer, footer, this.width / 2, this.height - 15, 0xAAAAAA);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int cellSize = 34;
            int gridWidth = COLUMNS * cellSize - SPACING;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 35;
            int rowsPerPage = Math.max(1, (this.height - guiTop - 40) / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            for (int i = 0; i < visibleCount && startIndex + i < this.categories.size(); i++) {
                int col = i % COLUMNS;
                int x = guiLeft + col * cellSize;
                int row = i / COLUMNS;
                int y = guiTop + row * cellSize;
                if (!isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
                int catIndex = startIndex + i;
                Minecraft.getMinecraft().displayGuiScreen(new GuiShopItems(this.categories.get(catIndex), catIndex));
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            int rowsPerPage = Math.max(1, (this.height - 75) / 34);
            this.scrollOffset = scroll > 0 ? Math.max(0, this.scrollOffset - 1) : Math.min(getMaxScrollPages(rowsPerPage) - 1, this.scrollOffset + 1);
        }
    }

    private int getMaxScrollPages(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) this.categories.size() / (double) totalSlots));
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
