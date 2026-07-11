package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class GuiShopCategories extends Screen {
    private List<ShopCategory> categories = WorldShop.getCategories();
    private int scrollOffset = 0;
    private static final int ICON_SIZE = 28;
    private static final int SPACING = 6;
    private static final int COLUMNS = 9;

    protected GuiShopCategories() {
        super(Component.literal("Shop - Categories"));
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, a);

        guiGraphics.fillGradient(0, 0, this.width, this.height, -870441442, -870441442);

        guiGraphics.centeredText(this.font, "\u00a76\u00a7lShop - Categories", this.width / 2, 12, 0xFFFFFF);

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
            drawSlotBackground(guiGraphics, x, y);
            ItemStack icon = category.getIcon();
            guiGraphics.item(icon, x + 6, y + 6);
        }

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
            guiGraphics.setTooltipForNextFrame(this.font, Collections.singletonList(
                    Component.literal("\u00a7f" + name + " \u00a77(" + category.getItems().size() + " items)")),
                    java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        if (this.categories.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            guiGraphics.centeredText(this.font, scrollInfo, this.width / 2, this.height - 30, 0xFFFFFF);
        }
        String footer = "\u00a77Click a category to browse items | ESC to close";
        guiGraphics.centeredText(this.font, footer, this.width / 2, this.height - 15, 0xAAAAAA);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
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
                if (!isMouseInSlot((int) event.x(), (int) event.y(), x, y, ICON_SIZE, ICON_SIZE)) continue;
                int catIndex = startIndex + i;
                Minecraft.getInstance().setScreen(new GuiShopItems(this.categories.get(catIndex), catIndex));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rowsPerPage = Math.max(1, (this.height - 75) / 34);
        this.scrollOffset = scrollY > 0 ? Math.max(0, this.scrollOffset - 1) : Math.min(getMaxScrollPages(rowsPerPage) - 1, this.scrollOffset + 1);
        return true;
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

    private void drawSlotBackground(GuiGraphicsExtractor guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, -1438366652);
        guiGraphics.fill(x + 1, y + 1, x + ICON_SIZE - 1, y + ICON_SIZE - 1, -1439485133);
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
