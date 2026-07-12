package asd.itamio.worldshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuiShopCategories extends Screen {
    private final List<ShopCategory> categories = WorldShop.getCategories();
    private int scrollOffset = 0;
    private static final int ICON_SIZE = 28;
    private static final int SPACING = 6;
    private static final int COLUMNS = 9;

    private EditBox searchField;
    private String searchText = "";
    private boolean searchMode = false;
    private List<SearchResult> searchResults = Collections.emptyList();

    public GuiShopCategories() {
        super(Component.literal("Shop Categories"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        // Search bar at the top
        int searchWidth = Math.min(300, this.width - 40);
        int searchX = (this.width - searchWidth) / 2;
        this.searchField = new EditBox(this.font, searchX, 4, searchWidth, 16, Component.literal("Search items..."));
        this.searchField.setMaxLength(40);
        this.searchField.setHint(Component.literal("\u00a77Search items across all categories..."));
        this.addRenderableWidget(this.searchField);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(0, 0, this.width, this.height, -870441442);

        String searchValue = this.searchField.getValue();
        boolean searching = searchValue != null && !searchValue.trim().isEmpty();
        if (searching != this.searchMode || !searchValue.equals(this.searchText)) {
            this.searchText = searchValue != null ? searchValue.trim().toLowerCase() : "";
            this.searchMode = searching;
            if (this.searchMode) {
                buildSearchResults();
            }
            this.scrollOffset = 0;
        }

        if (this.searchMode) {
            renderSearchResults(guiGraphics, mouseX, mouseY);
        } else {
            renderCategories(guiGraphics, mouseX, mouseY);
        }

        // Draw search field on top
        if (this.searchField != null) {
            this.searchField.render(guiGraphics, mouseX, mouseY, 0);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void renderCategories(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a76\u00a7lShop - Categories"), this.width / 2, 22, 0xFFFFFF);

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
            drawSlotBackground(guiGraphics, x, y, ICON_SIZE, ICON_SIZE);
            ItemStack icon = category.getIcon();
            guiGraphics.renderItem(icon, x + 6, y + 6);
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
            guiGraphics.renderTooltip(this.font, Collections.singletonList(Component.literal("\u00a7f" + name + " \u00a77(" + category.getItems().size() + " items)")), java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        if (this.categories.size() > visibleCount) {
            int maxRows = getMaxRowOffset(rowsPerPage);
            String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + (maxRows + 1);
            guiGraphics.drawCenteredString(this.font, Component.literal(scrollInfo), this.width / 2, this.height - 30, 0xFFFFFF);
        }
        String footer = "\u00a77Click a category to browse items | ESC to close | Type to search";
        guiGraphics.drawCenteredString(this.font, Component.literal(footer), this.width / 2, this.height - 15, 0xAAAAAA);
    }

    private void renderSearchResults(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawCenteredString(this.font, Component.literal("\u00a76\u00a7lSearch: \u00a7f\"" + this.searchText + "\""), this.width / 2, 22, 0xFFFFFF);

        int cellSize = 26;
        int gridWidth = COLUMNS * cellSize - SPACING;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 35;
        int availableHeight = this.height - guiTop - 50;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;
        int startIndex = this.scrollOffset * COLUMNS;

        // Draw search result items
        for (int i = 0; i < visibleCount && startIndex + i < this.searchResults.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            int resultIndex = startIndex + i;
            SearchResult result = this.searchResults.get(resultIndex);
            drawSlotBackground(guiGraphics, x, y, 22, 22);
            guiGraphics.renderItem(result.item, x + 2, y + 2);
        }

        // Draw tooltips for results
        for (int i = 0; i < visibleCount && startIndex + i < this.searchResults.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot(mouseX, mouseY, x, y, 22, 22)) continue;
            int resultIndex = startIndex + i;
            SearchResult result = this.searchResults.get(resultIndex);
            PriceEngine priceEngine = WorldShop.getPriceEngine();
            double buyPrice = priceEngine.getBuyPrice(result.item);
            double sellPrice = priceEngine.getSellPrice(result.item);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("\u00a7f" + result.item.getHoverName().getString()));
            tooltip.add(Component.literal("\u00a77Category: " + formatCategoryName(result.category.getName())));
            tooltip.add(Component.literal("\u00a7aBuy: $" + String.format("%.2f", buyPrice)));
            tooltip.add(Component.literal("\u00a7cSell: $" + String.format("%.2f", sellPrice)));
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            break;
        }

        String countStr = "\u00a77Found " + this.searchResults.size() + " items matching \"" + this.searchText + "\"";
        guiGraphics.drawCenteredString(this.font, Component.literal(countStr), this.width / 2, this.height - 30, 0xAAAAAA);
        String footer = "\u00a7eLeft-click: Buy menu | ESC to close | Clear search for categories";
        guiGraphics.drawCenteredString(this.font, Component.literal(footer), this.width / 2, this.height - 15, 0xAAAAAA);
    }

    private void buildSearchResults() {
        this.searchResults = new ArrayList<>();
        String query = this.searchText.toLowerCase();
        for (ShopCategory category : this.categories) {
            for (ItemStack item : category.getItems()) {
                if (item == null || item.isEmpty()) continue;
                String name = item.getHoverName().getString().toLowerCase();
                if (name.contains(query)) {
                    this.searchResults.add(new SearchResult(category, item));
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (this.searchField != null && this.searchField.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }

        if (mouseButton == 0) {
            int cellSize = this.searchMode ? 26 : 34;
            int gridWidth = COLUMNS * cellSize - SPACING;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 35;
            int availableHeight = this.searchMode ? this.height - guiTop - 50 : this.height - guiTop - 40;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            if (this.searchMode) {
                for (int i = 0; i < visibleCount && startIndex + i < this.searchResults.size(); i++) {
                    int col = i % COLUMNS;
                    int x = guiLeft + col * cellSize;
                    int row = i / COLUMNS;
                    int y = guiTop + row * cellSize;
                    if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, 22, 22)) continue;
                    int resultIndex = startIndex + i;
                    SearchResult result = this.searchResults.get(resultIndex);
                    int catIndex = this.categories.indexOf(result.category);
                    if (catIndex >= 0) {
                        int itemIndex = result.category.getItems().indexOf(result.item);
                        if (itemIndex >= 0) {
                            Minecraft.getInstance().setScreen(new GuiShopItems(result.category, catIndex));
                        }
                    }
                    return true;
                }
            } else {
                for (int i = 0; i < visibleCount && startIndex + i < this.categories.size(); i++) {
                    int col = i % COLUMNS;
                    int x = guiLeft + col * cellSize;
                    int row = i / COLUMNS;
                    int y = guiTop + row * cellSize;
                    if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
                    int catIndex = startIndex + i;
                    Minecraft.getInstance().setScreen(new GuiShopItems(this.categories.get(catIndex), catIndex));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int cellSize = this.searchMode ? 26 : 34;
            int guiTop = 35;
            int availableHeight = this.searchMode ? this.height - guiTop - 50 : this.height - guiTop - 40;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int maxRow = getMaxRowOffset(rowsPerPage);
            int delta = scrollY > 0 ? -1 : 1;
            this.scrollOffset = Math.max(0, Math.min(maxRow, this.scrollOffset + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int getMaxRowOffset(int rowsPerPage) {
        int totalItems = this.searchMode ? this.searchResults.size() : this.categories.size();
        int totalSlots = COLUMNS * rowsPerPage;
        if (totalItems <= totalSlots) return 0;
        return (int) Math.ceil((double) (totalItems - totalSlots) / (double) COLUMNS);
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

    private void drawSlotBackground(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, -1438366652);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, -1439485133);
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchField != null && this.searchField.isFocused()) {
            if (keyCode == 256) { // ESC
                if (!this.searchField.getValue().isEmpty()) {
                    this.searchField.setValue("");
                    this.searchMode = false;
                    this.searchText = "";
                    this.scrollOffset = 0;
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            return this.searchField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Helper class for search results
    private static class SearchResult {
        final ShopCategory category;
        final ItemStack item;

        SearchResult(ShopCategory category, ItemStack item) {
            this.category = category;
            this.item = item;
        }
    }
}
