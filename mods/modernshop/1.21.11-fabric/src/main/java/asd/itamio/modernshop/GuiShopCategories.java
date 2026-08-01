package asd.itamio.modernshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GuiShopCategories extends Screen {
    private static final int ICON_SIZE = 28;
    private static final int COLUMNS = 9;
    private static final int SPACING = 6;

    private List<ShopCategory> allCategories = ModernShop.getCategories();
    private List<ShopCategory> filteredCategories;
    private int scrollOffset = 0;
    private EditBox searchBox;
    private double accumulatedScroll = 0.0;
    private static final double SCROLL_THRESHOLD = 1.0;

    public GuiShopCategories() {
        super(Component.literal("Shop - Categories"));
        this.filteredCategories = allCategories;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        // Search bar
        int searchWidth = Math.min(300, this.width - 40);
        int searchX = (this.width - searchWidth) / 2;
        this.searchBox = new EditBox(this.font, searchX, 22, searchWidth, 16, Component.literal("Search categories..."));
        this.searchBox.setMaxLength(50);
        this.searchBox.setResponder(s -> {
            filterCategories(s);
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchBox);
    }

    private void filterCategories(String search) {
        if (search == null || search.trim().isEmpty()) {
            this.filteredCategories = allCategories;
        } else {
            String lower = search.toLowerCase();
            this.filteredCategories = allCategories.stream()
                    .filter(cat -> cat.getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, -870441442);
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a76\u00a7lShop - Categories"), this.width / 2, 8, 0xFFFFFF);

        int cellSize = 34;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 45;
        int availableHeight = this.height - guiTop - 40;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        // Draw category icons
        for (int i = 0; i < visibleCount && startIndex + i < this.filteredCategories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            int catIndex = startIndex + i;
            ShopCategory category = this.filteredCategories.get(catIndex);
            drawSlotBackground(guiGraphics, x, y);
            guiGraphics.renderItem(category.getIcon(), x + 6, y + 6);
        }

        // Draw scroll indicator
        if (this.filteredCategories.size() > visibleCount) {
            int maxPages = getMaxScrollPages(rowsPerPage);
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + maxPages;
            guiGraphics.drawCenteredString(this.font, Component.literal(scrollInfo), this.width / 2, this.height - 30, 0xFFFFFF);
        }

        // Show results count when searching
        if (!this.filteredCategories.equals(allCategories)) {
            String resultInfo = "\u00a77Found " + this.filteredCategories.size() + " categories";
            guiGraphics.drawCenteredString(this.font, Component.literal(resultInfo), this.width / 2, this.height - 40, 0xAAAAAA);
        }

        String footer = "\u00a77Click a category to browse items | ESC to close";
        guiGraphics.drawCenteredString(this.font, Component.literal(footer), this.width / 2, this.height - 15, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            return true;
        }

        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();

            int cellSize = 34;
            int gridWidth = COLUMNS * cellSize - SPACING;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 45;
            int availableHeight = this.height - guiTop - 40;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            for (int i = 0; i < visibleCount && startIndex + i < this.filteredCategories.size(); i++) {
                int col = i % COLUMNS;
                int x = guiLeft + col * cellSize;
                int row = i / COLUMNS;
                int y = guiTop + row * cellSize;
                if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
                int catIndex = startIndex + i;
                Minecraft.getInstance().setScreen(new GuiShopItems(this.filteredCategories.get(catIndex), catIndex));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            // Accumulate scroll for smoother row-by-row scrolling
            accumulatedScroll += scrollY;

            int rowsPerPage = Math.max(1, (this.height - 85) / 34);
            int totalRows = (int) Math.ceil((double) this.filteredCategories.size() / (double) COLUMNS);
            int maxScrollOffset = Math.max(0, totalRows - rowsPerPage);

            // Process accumulated scroll
            int scrollUnits = (int) Math.floor(Math.abs(accumulatedScroll) / SCROLL_THRESHOLD);
            if (scrollUnits > 0) {
                int direction = accumulatedScroll > 0 ? -1 : 1; // Positive Y = scroll down
                int newOffset = this.scrollOffset + direction * scrollUnits;
                this.scrollOffset = Math.max(0, Math.min(maxScrollOffset, newOffset));
                accumulatedScroll = accumulatedScroll % SCROLL_THRESHOLD;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int getMaxScrollPages(int rowsPerPage) {
        int totalSlots = COLUMNS * rowsPerPage;
        return Math.max(1, (int) Math.ceil((double) this.filteredCategories.size() / (double) totalSlots));
    }

    private void drawSlotBackground(GuiGraphics guiGraphics, int x, int y) {
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
