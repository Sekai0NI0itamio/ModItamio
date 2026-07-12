package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GuiShopCategories extends Screen {
    private List<ShopCategory> categories;
    private List<ShopCategory> filteredCategories;
    private int scrollOffset = 0;
    private double accumulatedScroll = 0.0;
    private static final int ICON_SIZE = 28;
    private static final int COLUMNS = 9;

    private EditBox searchField;
    private String searchText = "";

    protected GuiShopCategories() {
        super(Component.literal("Shop - Categories"));
        this.categories = WorldShop.getCategories();
        this.filteredCategories = categories;
    }

    @Override
    protected void init() {
        super.init();
        // Create search field at top
        int fieldW = 250;
        int fieldH = 16;
        int fieldY = 30;
        this.searchField = new EditBox(this.font, this.width / 2 - fieldW / 2, fieldY, fieldW, fieldH, Component.literal("Search categories..."));
        this.searchField.setMaxLength(40);
        this.searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchField);
    }

    private void onSearchChanged(String text) {
        this.searchText = text.toLowerCase().trim();
        this.scrollOffset = 0;
        this.accumulatedScroll = 0.0;
        applyFilter();
    }

    private void applyFilter() {
        if (searchText.isEmpty()) {
            this.filteredCategories = this.categories;
        } else {
            this.filteredCategories = this.categories.stream()
                    .filter(cat -> formatCategoryName(cat.getName()).toLowerCase().contains(searchText)
                            || cat.getName().toLowerCase().contains(searchText))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.fill(0, 0, this.width, this.height, -870441442);

        int titleY = 8;
        String title = "\u00a76\u00a7lShop - Categories";
        if (!searchText.isEmpty()) {
            title += " \u00a77(filtered: " + filteredCategories.size() + "/" + categories.size() + ")";
        }
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, titleY, 0xFFFFFF);

        // Draw search label
        guiGraphics.drawCenteredString(this.font, "\u00a77Search categories:", this.width / 2, 18, 0xAAAAAA);

        int cellSize = 34;
        int gridWidth = COLUMNS * cellSize - 6;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 52; // Moved down to make room for search bar
        int availableHeight = this.height - guiTop - 35;
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
            ItemStack icon = category.getIcon();
            guiGraphics.renderItem(icon, x + 6, y + 6);
        }

        // Draw tooltips
        for (int i = 0; i < visibleCount && startIndex + i < this.filteredCategories.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
            int catIndex = startIndex + i;
            ShopCategory category = this.filteredCategories.get(catIndex);
            String name = formatCategoryName(category.getName());
            int itemCount = category.getItems().size();
            guiGraphics.renderTooltip(this.font, Collections.singletonList(Component.literal("\u00a7f" + name + " \u00a77(" + itemCount + " items)")), java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        // Scroll info
        if (this.filteredCategories.size() > visibleCount) {
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + getMaxScrollPages(rowsPerPage);
            guiGraphics.drawCenteredString(this.font, scrollInfo, this.width / 2, this.height - 25, 0xFFFFFF);
        }
        String footer = "\u00a77Click a category to browse items | ESC to close";
        guiGraphics.drawCenteredString(this.font, footer, this.width / 2, this.height - 12, 0xAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        // Check search field first
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0) {
            int cellSize = 34;
            int gridWidth = COLUMNS * cellSize - 6;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 52;
            int availableHeight = this.height - guiTop - 35;
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
                ShopCategory category = this.filteredCategories.get(catIndex);
                // Find the original category index for the unfiltered list
                int originalIndex = this.categories.indexOf(category);
                if (originalIndex >= 0) {
                    Minecraft.getInstance().setScreen(new GuiShopItems(category, originalIndex));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        int guiTop = 52;
        int availableHeight = this.height - guiTop - 35;
        int rowsPerPage = Math.max(1, availableHeight / 34);
        this.accumulatedScroll += scrollDelta;
        double SCROLL_THRESHOLD = 5.0;
        int steps = (int) (this.accumulatedScroll / SCROLL_THRESHOLD);
        if (steps != 0) {
            this.accumulatedScroll -= steps * SCROLL_THRESHOLD;
            int maxPages = getMaxScrollPages(rowsPerPage);
            this.scrollOffset = Math.max(0, Math.min(maxPages - 1, this.scrollOffset - steps));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle search field input
        if (this.searchField != null && this.searchField.isFocused()) {
            if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Handle search field character typing
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
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
