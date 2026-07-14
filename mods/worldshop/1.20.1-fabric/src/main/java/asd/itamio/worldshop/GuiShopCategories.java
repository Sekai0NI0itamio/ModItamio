package asd.itamio.worldshop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GuiShopCategories extends Screen {
    private List<ShopCategory> categories;
    private List<ShopCategory> filteredCategories;
    private int scrollOffset = 0;
    private double accumulatedScroll = 0.0;
    private static final int ICON_SIZE = 28;
    private static final int COLUMNS = 9;

    // Admin mode: if true, admin controls (X buttons, Add Category) are shown
    private final boolean adminMode;

    // Search mode: when searching, we show items (not categories)
    private EditBox searchField;
    private String searchText = "";
    private boolean searchItemsMode = false;
    // For search results: flat list of (categoryIndex, itemIndex, itemStack)
    private List<SearchResult> searchResults = new ArrayList<>();
    // Categories that contributed to search results
    private int searchResultCategoryCount = 0;

    // Scrollbar state
    private boolean isDraggingScrollbar = false;
    private double dragStartMouseY = 0;
    private int dragStartScrollOffset = 0;
    private static final int SCROLLBAR_WIDTH = 6;
    private int scrollbarX = 0;
    private int scrollbarTop = 0;
    private int scrollbarHeight = 0;

    // Detail view for search result items
    private boolean detailView = false;
    private SearchResult detailResult = null;
    private EditBox quantityField;
    private boolean stackMode = false;

    // Admin: "Add Category" button
    private Button addCategoryButton;

    // ========== Layout Edit Mode ==========
    private boolean layoutEditMode = false;
    /** Grid slot -> category index (-1 = empty). Grows dynamically. */
    private int[] layoutGrid;
    /** Snapshot of the original category order for Cancel. */
    private int[] originalOrder;
    /** Which grid slot is currently "picked up" (-1 = none). */
    private int pickedUpSlot = -1;
    /** Scroll offset snapshot when entering layout mode. */
    private int layoutScrollSnapshot = 0;
    /** Minimum size for the layout grid. */
    private static final int MIN_GRID_SIZE = 500;

    private Button editLayoutButton;
    private Button saveLayoutButton;
    private Button cancelLayoutButton;

    /** Fully opaque background to prevent text overlap from previous screen. */
    private static final int BG_COLOR = 0xFF1A1A1A;

    // Debug: track last rendered order to avoid spam
    private String lastRenderedOrder = "";

    // Helper class for search results
    private static class SearchResult {
        final int categoryIndex;
        final int itemIndex;
        final ItemStack item;
        final String categoryName;

        SearchResult(int categoryIndex, int itemIndex, ItemStack item, String categoryName) {
            this.categoryIndex = categoryIndex;
            this.itemIndex = itemIndex;
            this.item = item;
            this.categoryName = categoryName;
        }
    }

    /**
     * Create with automatic admin mode detection based on player permissions.
     */
    protected GuiShopCategories() {
        this(false);
    }

    /**
     * Create shop categories screen.
     * @param forcePlayerMode if true, hide all admin controls regardless of OP status
     */
    protected GuiShopCategories(boolean forcePlayerMode) {
        super(Component.literal("Shop - Categories"));
        this.categories = WorldShop.getCategories();
        this.filteredCategories = categories;
        boolean isOp = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
        this.adminMode = isOp && !forcePlayerMode;
        // Debug: log the category order this screen sees
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < categories.size() && i < 11; i++) {
            if (i > 0) sb.append(", ");
            sb.append(i).append(":").append(categories.get(i).getName());
        }
        WorldShop.LOGGER.info("[GUI_ORDER] GuiShopCategories created with {} categories: [{}]", categories.size(), sb.toString());
    }

    @Override
    protected void init() {
        super.init();
        layoutEditMode = false;
        pickedUpSlot = -1;
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearWidgets();
        int fieldW = 250;
        int fieldH = 16;
        int fieldY = 30;

        if (layoutEditMode) {
            // In layout edit mode: only show Save/Cancel buttons
            int btnW = 100;
            this.saveLayoutButton = Button.builder(
                    Component.literal("\u00a7aSave Layout"),
                    btn -> saveLayout()
            ).bounds(this.width / 2 - btnW - 5, this.height - 25, btnW, 20).build();
            this.addRenderableWidget(this.saveLayoutButton);

            this.cancelLayoutButton = Button.builder(
                    Component.literal("\u00a7cCancel"),
                    btn -> cancelLayout()
            ).bounds(this.width / 2 + 5, this.height - 25, btnW, 20).build();
            this.addRenderableWidget(this.cancelLayoutButton);
        } else {
            // Normal mode: search field
            this.searchField = new EditBox(this.font, this.width / 2 - fieldW / 2, fieldY, fieldW, fieldH, Component.literal("Search items across all categories..."));
            this.searchField.setMaxLength(40);
            this.searchField.setResponder(this::onSearchChanged);
            this.addRenderableWidget(this.searchField);

            // Admin buttons at bottom — moved up to avoid overlapping scroll info (height-25) and footer (height-12)
            if (adminMode) {
                int btnW = 120;
                this.editLayoutButton = Button.builder(
                        Component.literal("\u00a7e\u00a7lEdit Layout"),
                        btn -> enterLayoutMode()
                ).bounds(this.width / 2 - btnW - 5, this.height - 55, btnW, 20).build();
                this.addRenderableWidget(this.editLayoutButton);

                this.addCategoryButton = Button.builder(
                        Component.literal("\u00a7a+ Add Category"),
                        btn -> ScreenManager.open(new GuiAddCategory(this))
                ).bounds(this.width / 2 + 5, this.height - 55, btnW, 20).build();
                this.addRenderableWidget(this.addCategoryButton);
            }
        }
    }

    // ========== Layout Edit Mode ==========

    private void enterLayoutMode() {
        layoutEditMode = true;
        pickedUpSlot = -1;
        layoutScrollSnapshot = this.scrollOffset;

        // Use saved positions or build a grid sized to the max occupied slot + buffer
        int[] savedPositions = WorldShop.getCategorySlotPositions();

        if (savedPositions != null && savedPositions.length > 0) {
            // Find the last non-empty slot, or use saved size
            int maxNeeded = savedPositions.length;
            for (int i = savedPositions.length - 1; i >= 0; i--) {
                if (savedPositions[i] >= 0) { maxNeeded = i + 1; break; }
            }
            int gridSize = Math.max(maxNeeded + 50, MIN_GRID_SIZE);
            layoutGrid = new int[gridSize];
            java.util.Arrays.fill(layoutGrid, -1);
            for (int i = 0; i < savedPositions.length; i++) {
                layoutGrid[i] = savedPositions[i];
            }
            WorldShop.LOGGER.info("[LAYOUT] enter: restored from slotPositions (grid={})", gridSize);
        } else {
            // Sequential: categories at slots 0..N-1
            int gridSize = Math.max(categories.size() + 200, MIN_GRID_SIZE);
            layoutGrid = new int[gridSize];
            java.util.Arrays.fill(layoutGrid, -1);
            for (int i = 0; i < categories.size(); i++) {
                layoutGrid[i] = i;
            }
            WorldShop.LOGGER.info("[LAYOUT] enter: sequential (grid={}, cats={})", gridSize, categories.size());
        }

        // Reset scroll to show from the beginning
        this.scrollOffset = 0;

        originalOrder = new int[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            originalOrder[i] = i;
        }
        rebuildButtons();
    }

    private void saveLayout() {
        // Build compact name list from non-empty grid slots
        List<String> nameList = new ArrayList<>();
        List<ShopCategory> cats = WorldShop.getCategories();
        for (int slot = 0; slot < layoutGrid.length; slot++) {
            int catIdx = layoutGrid[slot];
            if (catIdx >= 0 && catIdx < cats.size() && cats.get(catIdx) != null) {
                nameList.add(cats.get(catIdx).getName());
            }
        }

        // Log
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < layoutGrid.length; i++) {
            if (layoutGrid[i] >= 0 && layoutGrid[i] < cats.size()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(i).append(":").append(cats.get(layoutGrid[i]).getName());
            }
        }
        WorldShop.LOGGER.info("[LAYOUT] save: positions=[{}]", sb.toString());

        // Send compact names to server
        String[] newOrderNames = nameList.toArray(new String[0]);
        FriendlyByteBuf buf = PacketByteBufs.create();
        ShopPacket.write(ShopPacket.reorderCategories(newOrderNames), buf);
        ClientPlayNetworking.send(ShopPacket.PACKET_ID, buf);

        // Persist grid positions for home page rendering (trim trailing empties)
        int lastNonEmpty = -1;
        for (int i = layoutGrid.length - 1; i >= 0; i--) {
            if (layoutGrid[i] >= 0) { lastNonEmpty = i; break; }
        }
        if (lastNonEmpty >= 0) {
            int[] trimmed = new int[lastNonEmpty + 1];
            System.arraycopy(layoutGrid, 0, trimmed, 0, lastNonEmpty + 1);
            WorldShop.setCategorySlotPositions(trimmed);
            WorldShop.LOGGER.info("[LAYOUT] saved trimmed grid length={}", trimmed.length);
        } else {
            WorldShop.setCategorySlotPositions(layoutGrid.clone());
        }

        // Reorder local categories compactly
        List<ShopCategory> reordered = new ArrayList<>();
        for (String name : newOrderNames) {
            for (ShopCategory cat : cats) {
                if (cat.getName().equals(name) && !reordered.contains(cat)) {
                    reordered.add(cat);
                    break;
                }
            }
        }
        cats.clear();
        cats.addAll(reordered);

        layoutEditMode = false;
        pickedUpSlot = -1;
        this.scrollOffset = 0;
        this.searchText = "";
        this.searchItemsMode = false;
        this.detailView = false;
        this.detailResult = null;
        this.searchResults.clear();
        this.filteredCategories = cats;
        this.clearWidgets();
        rebuildButtons();
        this.lastRenderedOrder = "";
    }

    private void cancelLayout() {
        layoutEditMode = false;
        pickedUpSlot = -1;
        this.scrollOffset = layoutScrollSnapshot;
        this.searchText = "";
        this.searchItemsMode = false;
        this.detailView = false;
        this.detailResult = null;
        this.searchResults.clear();
        this.filteredCategories = categories;
        this.clearWidgets();
        rebuildButtons();
    }

    // ========== Search ==========

    private void onSearchChanged(String text) {
        this.searchText = text.toLowerCase().trim();
        this.scrollOffset = 0;
        this.accumulatedScroll = 0.0;
        this.detailView = false;
        this.detailResult = null;
        applyFilter();
    }

    private void applyFilter() {
        if (searchText.isEmpty()) {
            this.searchItemsMode = false;
            this.searchResults.clear();
            this.searchResultCategoryCount = 0;
            this.filteredCategories = this.categories;
        } else {
            this.searchItemsMode = true;
            this.filteredCategories = this.categories;
            // Search ALL items across ALL categories
            searchResults.clear();
            java.util.Set<String> categoryNames = new java.util.HashSet<>();
            for (int catIdx = 0; catIdx < categories.size(); catIdx++) {
                ShopCategory cat = categories.get(catIdx);
                List<ItemStack> items = cat.getItems();
                for (int itemIdx = 0; itemIdx < items.size(); itemIdx++) {
                    ItemStack item = items.get(itemIdx);
                    String itemName = item.getHoverName().getString().toLowerCase();
                    if (itemName.contains(searchText)) {
                        searchResults.add(new SearchResult(catIdx, itemIdx, item, cat.getName()));
                        categoryNames.add(cat.getName());
                    }
                }
            }
            this.searchResultCategoryCount = categoryNames.size();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Fully opaque background — prevents any text overlap from previous screen
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);

        if (layoutEditMode) {
            drawLayoutEditor(guiGraphics, mouseX, mouseY);
            return;
        }

        int titleY = 8;
        String title = "\u00a76\u00a7lShop - " + (searchItemsMode ? "Search Results" : "Categories");
        if (searchItemsMode) {
            title += " \u00a77(" + searchResults.size() + " items from " + searchResultCategoryCount + " categories)";
        } else if (!searchText.isEmpty()) {
            title += " \u00a77(filtered: " + filteredCategories.size() + "/" + categories.size() + ")";
        }
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, titleY, 0xFFFFFF);

        // Draw search label
        guiGraphics.drawCenteredString(this.font, "\u00a77" + (searchItemsMode ? "Search items:" : "Search categories:"), this.width / 2, 18, 0xAAAAAA);

        if (detailView && detailResult != null) {
            drawDetailView(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        int cellSize = searchItemsMode ? 22 : 34;
        int iconSize = searchItemsMode ? 18 : ICON_SIZE;
        int gridWidth = COLUMNS * cellSize - 4;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 52;
        int availableHeight = this.height - guiTop - 35;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);

        if (searchItemsMode) {
            // ---- SEARCH RESULTS MODE - show items ----
            guiTop = 52;
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            // Calculate scrollbar position
            int gridRight = guiLeft + gridWidth;
            scrollbarX = gridRight + 4;
            scrollbarTop = guiTop;
            scrollbarHeight = availableHeight;

            // Draw items
            for (int i = 0; i < visibleCount && startIndex + i < searchResults.size(); i++) {
                int col = i % COLUMNS;
                int row = i / COLUMNS;
                int x = guiLeft + col * cellSize;
                int y = guiTop + row * cellSize;
                int resultIndex = startIndex + i;
                SearchResult result = searchResults.get(resultIndex);
                drawSlotBackground(guiGraphics, x, y, iconSize, iconSize);
                guiGraphics.renderItem(result.item, x + 2, y + 2);
            }

            // Tooltips for search result items
            for (int i = 0; i < visibleCount && startIndex + i < searchResults.size(); i++) {
                int col = i % COLUMNS;
                int row = i / COLUMNS;
                int x = guiLeft + col * cellSize;
                int y = guiTop + row * cellSize;
                if (!isMouseInSlot(mouseX, mouseY, x, y, iconSize, iconSize)) continue;
                int resultIndex = startIndex + i;
                SearchResult result = searchResults.get(resultIndex);
                double buyPrice = WorldShop.getPriceEngine().getBuyPrice(result.item);
                double sellPrice = WorldShop.getPriceEngine().getSellPrice(result.item);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("\u00a7f" + result.item.getHoverName().getString()));
                tooltip.add(Component.literal("\u00a77Category: \u00a7e" + formatCategoryName(result.categoryName)));
                tooltip.add(Component.literal("\u00a7aBuy: $" + String.format("%.2f", buyPrice)));
                tooltip.add(Component.literal("\u00a7cSell: $" + String.format("%.2f", sellPrice)));
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("\u00a7eLeft-click: Buy menu"));
                tooltip.add(Component.literal("\u00a7bRight-click: Quick buy stack"));
                guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }

            // Draw scrollbar
            int totalItems = searchResults.size();
            int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / (double) visibleCount));
            drawScrollbar(guiGraphics, scrollbarX, scrollbarTop, scrollbarHeight, maxPages, mouseX, mouseY);

            // Scroll info
            if (totalItems > visibleCount) {
                String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + maxPages;
                guiGraphics.drawCenteredString(this.font, scrollInfo, this.width / 2, this.height - 25, 0xFFFFFF);
            }
        } else {
            // ---- CATEGORIES MODE - show category tiles ----
            int visibleCount = COLUMNS * rowsPerPage;
            // Use slotPositions to place categories at exact grid positions
            int[] slotPos = WorldShop.getCategorySlotPositions();
            int totalSlots = (slotPos != null && slotPos.length > 0) ? slotPos.length : filteredCategories.size();

            // Find the lowest occupied slot for scroll range
            int lastOccupied = -1;
            if (slotPos != null) {
                for (int i = slotPos.length - 1; i >= 0; i--) {
                    if (slotPos[i] >= 0) { lastOccupied = i; break; }
                }
            }
            int scrollRange = (lastOccupied >= 0) ? lastOccupied + 1 : totalSlots;
            int maxPages = Math.max(1, (int) Math.ceil((double) scrollRange / (double) visibleCount));

            // Only draw occupied categories at their slot positions (no empty cells)
            // Show the current page based on scrollOffset
            int startSlot = this.scrollOffset * COLUMNS;
            int endSlot = Math.min(startSlot + visibleCount, totalSlots);

            for (int i = startSlot; i < endSlot; i++) {
                int localIndex = i - startSlot;
                int col = localIndex % COLUMNS;
                int row = localIndex / COLUMNS;
                int x = guiLeft + col * cellSize;
                int y = guiTop + row * cellSize;

                int catIdx = (slotPos != null && i < slotPos.length) ? slotPos[i] : i;
                if (catIdx < 0 || catIdx >= filteredCategories.size()) continue;
                ShopCategory category = filteredCategories.get(catIdx);
                if (category == null) continue;
                drawSlotBackground(guiGraphics, x, y, ICON_SIZE, ICON_SIZE);
                guiGraphics.renderItem(category.getIcon(), x + 6, y + 6);
            }

            // Tooltips for categories at their slot positions
            for (int i = startSlot; i < endSlot; i++) {
                int localIndex = i - startSlot;
                int col = localIndex % COLUMNS;
                int row = localIndex / COLUMNS;
                int x = guiLeft + col * cellSize;
                int y = guiTop + row * cellSize;
                if (!isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;

                int catIdx = (slotPos != null && i < slotPos.length) ? slotPos[i] : i;
                if (catIdx < 0 || catIdx >= filteredCategories.size()) continue;
                ShopCategory category = filteredCategories.get(catIdx);
                if (category == null) continue;
                String name = formatCategoryName(category.getName());
                int itemCount = category.getItems().size();
                List<Component> tooltipLines = new ArrayList<>();
                tooltipLines.add(Component.literal("\u00a7f" + name + " \u00a77(" + itemCount + " items)"));
                if (adminMode) {
                    tooltipLines.add(Component.literal(""));
                    tooltipLines.add(Component.literal("\u00a7cX: Remove category"));
                }
                guiGraphics.renderTooltip(this.font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }

            // X buttons (admin mode) — only for categories at slot positions
            if (adminMode) {
                for (int i = startSlot; i < endSlot; i++) {
                    int localIndex = i - startSlot;
                    int col = localIndex % COLUMNS;
                    int row = localIndex / COLUMNS;
                    int x = guiLeft + col * cellSize + ICON_SIZE - 10;
                    int y = guiTop + row * cellSize;

                    int catIdx = (slotPos != null && i < slotPos.length) ? slotPos[i] : i;
                    if (catIdx < 0 || catIdx >= filteredCategories.size()) continue;
                    ShopCategory category = filteredCategories.get(catIdx);
                    if (category == null) continue;
                    guiGraphics.fill(x, y, x + 10, y + 10, 0xCCFF4444);
                    guiGraphics.drawString(this.font, "\u00a7c\u00a7lx", x + 1, y + 1, 0xFFFFFF);
                }
            }

            // Scrollbar based on lowest occupied slot
            drawScrollbar(guiGraphics, scrollbarX, scrollbarTop, scrollbarHeight, maxPages, mouseX, mouseY);

            if (scrollRange > visibleCount) {
                String scrollInfo = "\u00a77Scroll: " + (this.scrollOffset + 1) + "/" + maxPages;
                guiGraphics.drawCenteredString(this.font, scrollInfo, this.width / 2, this.height - 25, 0xFFFFFF);
            }
        }

    }

    // ========== Layout Editor Rendering ==========

    private void drawLayoutEditor(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawCenteredString(this.font, "\u00a76\u00a7l\u00a7nEdit Category Layout \u00a77(Scroll down for more slots)", this.width / 2, 8, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "\u00a77Click a category to pick it up, click another to swap, or click an empty slot to place it there", this.width / 2, 20, 0xAAAAAA);

        int cellSize = 34;
        int gridWidth = COLUMNS * cellSize - 4;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 30;
        int availableHeight = this.height - guiTop - 70;
        int rowsPerPage = Math.max(1, availableHeight / (cellSize + 2));
        int visibleCount = COLUMNS * rowsPerPage;

        // Starting slot index based on scroll offset
        int startSlot = this.scrollOffset * COLUMNS;
        int endSlot = Math.min(startSlot + visibleCount, layoutGrid.length);

        for (int i = startSlot; i < endSlot; i++) {
            int localIndex = i - startSlot;
            int col = localIndex % COLUMNS;
            int row = localIndex / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;

            int catIndex = layoutGrid[i];

            if (catIndex >= 0 && catIndex < categories.size()) {
                // Occupied slot with a valid category
                boolean isPickedUp = (pickedUpSlot == i);
                if (isPickedUp) {
                    guiGraphics.fill(x - 1, y - 1, x + ICON_SIZE + 1, y + ICON_SIZE + 1, 0xFFFFFF44);
                }
                drawSlotBackground(guiGraphics, x, y, ICON_SIZE, ICON_SIZE);
                ShopCategory category = categories.get(catIndex);
                guiGraphics.renderItem(category.getIcon(), x + 6, y + 6);
                String numStr = String.valueOf(i + 1);
                guiGraphics.drawString(this.font, "\u00a77" + numStr, x + ICON_SIZE - 8, y + ICON_SIZE - 8, 0x888888);
                if (!isPickedUp && isMouseInSlot(mouseX, mouseY, x, y, ICON_SIZE, ICON_SIZE)) {
                    guiGraphics.renderTooltip(this.font, Component.literal("\u00a7f" + formatCategoryName(category.getName()) + " \u00a77(Slot " + (i + 1) + ")"), mouseX, mouseY);
                }
            } else {
                // Empty slot — draw outline
                guiGraphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0xFF333333);
                guiGraphics.fill(x + 1, y + 1, x + ICON_SIZE - 1, y + ICON_SIZE - 1, BG_COLOR);
                guiGraphics.drawCenteredString(this.font, "\u00a78" + (i + 1), x + ICON_SIZE / 2, y + ICON_SIZE / 2 - 4, 0x555555);
            }
        }

        // Scroll info
        int maxPages = Math.max(1, (int) Math.ceil((double) layoutGrid.length / (double) visibleCount));
        guiGraphics.drawCenteredString(this.font, "\u00a77Page " + (this.scrollOffset + 1) + "/" + maxPages + " " + (rowsPerPage > 0 ? "(Rows " + (this.scrollOffset * rowsPerPage + 1) + "-" + ((this.scrollOffset + rowsPerPage) * rowsPerPage) + ")" : ""), this.width / 2, this.height - 38, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, "\u00a7aSave \u00a77\u00a7cCancel", this.width / 2, this.height - 12, 0xAAAAAA);
    }

    // ========== Mouse Handling ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (layoutEditMode) {
            return handleLayoutClick(mouseX, mouseY, mouseButton);
        }

        // Check search field first
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (detailView) {
            handleDetailViewClick(mouseX, mouseY, mouseButton);
            return true;
        }

        // Check scrollbar click
        if (searchItemsMode || filteredCategories.size() > 0) {
            int cellSize = searchItemsMode ? 22 : 34;
            int gridWidth = COLUMNS * cellSize - 4;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 52;
            int availableHeight = this.height - guiTop - 35;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int totalSize = searchItemsMode ? searchResults.size() : filteredCategories.size();
            int maxPages = Math.max(1, (int) Math.ceil((double) totalSize / (double) visibleCount));

            if (maxPages > 1 && mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_WIDTH && mouseY >= scrollbarTop && mouseY < scrollbarTop + scrollbarHeight) {
                int thumbHeight = Math.max(8, scrollbarHeight / maxPages);
                int maxScrollPos = scrollbarHeight - thumbHeight;
                int thumbY = scrollbarTop + (maxPages > 1 ? (this.scrollOffset * maxScrollPos) / (maxPages - 1) : 0);

                if (mouseY < thumbY) {
                    this.scrollOffset = Math.max(0, this.scrollOffset - 1);
                } else if (mouseY > thumbY + thumbHeight) {
                    this.scrollOffset = Math.min(maxPages - 1, this.scrollOffset + 1);
                } else {
                    this.isDraggingScrollbar = true;
                    this.dragStartMouseY = mouseY;
                    this.dragStartScrollOffset = this.scrollOffset;
                }
                return true;
            }
        }

        // Check buttons
        for (var widget : this.children()) {
            if (widget instanceof Button btn && btn.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }

        if (mouseButton == 0 || mouseButton == 1) {
            int cellSize = searchItemsMode ? 22 : 34;
            int iconSize = searchItemsMode ? 18 : ICON_SIZE;
            int gridWidth = COLUMNS * cellSize - 4;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 52;
            int availableHeight = this.height - guiTop - 35;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int startIndex = this.scrollOffset * COLUMNS;

            if (searchItemsMode) {
                // Handle search result clicks
                for (int i = 0; i < visibleCount && startIndex + i < searchResults.size(); i++) {
                    int col = i % COLUMNS;
                    int row = i / COLUMNS;
                    int x = guiLeft + col * cellSize;
                    int y = guiTop + row * cellSize;
                    if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, iconSize, iconSize)) continue;
                    int resultIndex = startIndex + i;
                    SearchResult result = searchResults.get(resultIndex);
                    if (mouseButton == 0) {
                        this.detailView = true;
                        this.detailResult = result;
                        this.stackMode = false;
                        initDetailView();
                    } else if (mouseButton == 1) {
                        quickBuyFromSearch(result);
                    }
                    return true;
                }
            } else {
                // Handle category clicks using slotPositions (with scroll offset)
                int[] slotPos = WorldShop.getCategorySlotPositions();
                int totalSlots = (slotPos != null && slotPos.length > 0) ? slotPos.length : filteredCategories.size();
                int startSlot = this.scrollOffset * COLUMNS;
                int endSlot = Math.min(startSlot + visibleCount, totalSlots);
                boolean clickedX = false;

                // In admin mode, check X button clicks first
                if (adminMode) {
                    for (int i = startSlot; i < endSlot; i++) {
                        int localIndex = i - startSlot;
                        int col = localIndex % COLUMNS;
                        int row = localIndex / COLUMNS;
                        int x = guiLeft + col * cellSize + ICON_SIZE - 10;
                        int y = guiTop + row * cellSize;
                        if (mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10) {
                            int catIdx = (slotPos != null && i < slotPos.length) ? slotPos[i] : i;
                            if (catIdx < 0 || catIdx >= filteredCategories.size()) continue;
                            ShopCategory category = filteredCategories.get(catIdx);
                            if (category == null) continue;
                            int originalIndex = this.categories.indexOf(category);
                            if (originalIndex >= 0) {
                                sendRemoveCategory(originalIndex);
                                clickedX = true;
                            }
                            return true;
                        }
                    }
                }

                if (clickedX) return true;

                // Handle category clicks
                for (int i = startSlot; i < endSlot; i++) {
                    int localIndex = i - startSlot;
                    int col = localIndex % COLUMNS;
                    int x = guiLeft + col * cellSize;
                    int row = localIndex / COLUMNS;
                    int y = guiTop + row * cellSize;
                    if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;
                    int catIdx = (slotPos != null && i < slotPos.length) ? slotPos[i] : i;
                    if (catIdx < 0 || catIdx >= filteredCategories.size()) continue;
                    ShopCategory category = filteredCategories.get(catIdx);
                    if (category == null) continue;
                    int originalIndex = this.categories.indexOf(category);
                    if (originalIndex >= 0) {
                        ScreenManager.open(new GuiShopItems(category, originalIndex, adminMode));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleLayoutClick(double mouseX, double mouseY, int mouseButton) {
        for (var widget : this.children()) {
            if (widget instanceof Button btn && btn.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }

        int cellSize = 34;
        int gridWidth = COLUMNS * cellSize - 4;
        int guiLeft = (this.width - gridWidth) / 2;
        int guiTop = 30;
        int availableHeight = this.height - guiTop - 70;
        int rowsPerPage = Math.max(1, availableHeight / (cellSize + 2));
        int visibleCount = COLUMNS * rowsPerPage;

        // Starting slot of the current visible page
        int startSlot = this.scrollOffset * COLUMNS;

        for (int localIdx = 0; localIdx < visibleCount; localIdx++) {
            int col = localIdx % COLUMNS;
            int row = localIdx / COLUMNS;
            int x = guiLeft + col * cellSize;
            int y = guiTop + row * cellSize;
            if (!isMouseInSlot((int) mouseX, (int) mouseY, x, y, ICON_SIZE, ICON_SIZE)) continue;

            int absoluteSlot = startSlot + localIdx;

            // If slot is beyond current grid, grow the grid
            if (absoluteSlot >= layoutGrid.length) {
                int newSize = Math.max(absoluteSlot + 100, layoutGrid.length * 2);
                int[] newGrid = new int[newSize];
                java.util.Arrays.fill(newGrid, -1);
                System.arraycopy(layoutGrid, 0, newGrid, 0, layoutGrid.length);
                layoutGrid = newGrid;
                WorldShop.LOGGER.info("[LAYOUT] Grid expanded to {} slots", newSize);
            }

            if (pickedUpSlot == -1) {
                // Pick up a category — only if this slot has one
                if (layoutGrid[absoluteSlot] >= 0) {
                    pickedUpSlot = absoluteSlot;
                    WorldShop.LOGGER.info("[LAYOUT] Picked slot {} ({})", absoluteSlot, categories.get(layoutGrid[absoluteSlot]).getName());
                }
            } else if (pickedUpSlot == absoluteSlot) {
                // Clicked same slot — deselect
                pickedUpSlot = -1;
                WorldShop.LOGGER.info("[LAYOUT] Deselected");
            } else {
                // Place the picked category
                int pickedIdx = layoutGrid[pickedUpSlot];
                int targetCurrent = layoutGrid[absoluteSlot];
                int oldPickedUpSlot = pickedUpSlot;

                if (targetCurrent >= 0) {
                    // SWAP: exchange the two categories
                    layoutGrid[pickedUpSlot] = targetCurrent;
                    layoutGrid[absoluteSlot] = pickedIdx;
                    WorldShop.LOGGER.info("[LAYOUT] Swapped slot {} <-> {}", oldPickedUpSlot, absoluteSlot);
                } else {
                    // MOVE: old slot becomes empty
                    layoutGrid[pickedUpSlot] = -1;
                    layoutGrid[absoluteSlot] = pickedIdx;
                    WorldShop.LOGGER.info("[LAYOUT] Moved from slot {} to empty slot {}", oldPickedUpSlot, absoluteSlot);
                }
                pickedUpSlot = -1;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScrollbar) {
            int totalSize = searchItemsMode ? searchResults.size() : filteredCategories.size();
            int cellSize = searchItemsMode ? 22 : 34;
            int gridWidth = COLUMNS * cellSize - 4;
            int guiLeft = (this.width - gridWidth) / 2;
            int guiTop = 52;
            int availableHeight = this.height - guiTop - 35;
            int rowsPerPage = Math.max(1, availableHeight / cellSize);
            int visibleCount = COLUMNS * rowsPerPage;
            int maxPages = Math.max(1, (int) Math.ceil((double) totalSize / (double) visibleCount));

            double deltaY = mouseY - this.dragStartMouseY;
            int thumbHeight = Math.max(8, scrollbarHeight / maxPages);
            int maxScrollPos = scrollbarHeight - thumbHeight;
            if (maxScrollPos > 0) {
                int newOffset = this.dragStartScrollOffset + (int) (deltaY / maxScrollPos * (maxPages - 1));
                this.scrollOffset = Math.max(0, Math.min(maxPages - 1, newOffset));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDraggingScrollbar) {
            this.isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ========== Detail View ==========

    private void initDetailView() {
        this.clearWidgets();
        if (this.searchField != null) {
            this.addRenderableWidget(this.searchField);
        }
        int btnW = 95;
        int btnH = 20;
        int centerX = this.width / 2;
        int bottomY = this.height - 10;

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aBuy"), button -> {
            int qty = getQuantity();
            if (qty > 0 && detailResult != null) {
                int actualItems = stackMode ? qty * detailResult.item.getMaxStackSize() : qty;
                sendBuyPacket(detailResult.categoryIndex, detailResult.itemIndex, actualItems);
            }
        }).bounds(centerX - btnW - 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack"), button -> {
            this.detailView = false;
            this.detailResult = null;
            init();
        }).bounds(centerX + 2, bottomY - btnH * 2 - 8, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(Component.literal(stackMode ? "\u00a77Mode: Stacks" : "\u00a77Mode: Items"), button -> {
            this.stackMode = !this.stackMode;
            if (this.quantityField != null) {
                this.quantityField.setValue("1");
            }
            initDetailView();
        }).bounds(centerX - btnW - 2, bottomY - btnH - 4, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7eMax Afford"), button -> {
            if (detailResult != null) {
                double buyPrice = WorldShop.getPriceEngine().getBuyPrice(detailResult.item);
                double balance = 999999999.0;
                int maxAfford = (int) (balance / buyPrice);
                if (stackMode) {
                    int maxStacks = maxAfford / detailResult.item.getMaxStackSize();
                    if (this.quantityField != null) {
                        this.quantityField.setValue(String.valueOf(maxStacks));
                    }
                } else if (this.quantityField != null) {
                    this.quantityField.setValue(String.valueOf(maxAfford));
                }
            }
        }).bounds(centerX + 2, bottomY - btnH - 4, btnW, btnH).build());

        int fieldW = 100;
        int fieldH = 20;
        this.quantityField = new EditBox(this.font, centerX - fieldW / 2, 0, fieldW, fieldH, Component.literal("Quantity"));
        this.quantityField.setValue("1");
        this.quantityField.setFocused(true);
        this.quantityField.setMaxLength(5);
        this.quantityField.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.quantityField);
    }

    private void handleDetailViewClick(double mouseX, double mouseY, int mouseButton) {
        for (var widget : this.children()) {
            if (widget instanceof Button button) {
                if (button.mouseClicked(mouseX, mouseY, mouseButton)) {
                    return;
                }
            }
        }
        if (this.quantityField != null) {
            this.quantityField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    private void drawDetailView(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (detailResult == null) return;

        ItemStack item = detailResult.item;
        double buyPricePerItem = WorldShop.getPriceEngine().getBuyPrice(item);
        double sellPricePerItem = WorldShop.getPriceEngine().getSellPrice(item);

        int centerX = this.width / 2;
        int y = 10;
        String title = "\u00a76\u00a7l" + item.getHoverName().getString();
        guiGraphics.drawCenteredString(this.font, title, centerX, y, 0xFFFFFF);

        y += 12;
        guiGraphics.drawCenteredString(this.font, "\u00a77Category: \u00a7e" + formatCategoryName(detailResult.categoryName), centerX, y, 0xCCCCCC);

        y += 14;
        int itemCenterY = y + 24;
        drawSlotBackground(guiGraphics, centerX - 24, itemCenterY - 24, 48, 48);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) (centerX - 16), (float) (itemCenterY - 16), 0.0f);
        guiGraphics.pose().scale(2.0f, 2.0f, 2.0f);
        guiGraphics.renderItem(item, 0, 0);
        guiGraphics.pose().popPose();

        y = itemCenterY + 30;
        guiGraphics.drawCenteredString(this.font, "\u00a7aBuy: $" + String.format("%.2f", buyPricePerItem) + " each", centerX, y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "\u00a7cSell: $" + String.format("%.2f", sellPricePerItem) + " each", centerX, y += 12, 0xFFFFFF);

        String modeLabel = stackMode ? "\u00a77Quantity (stacks):" : "\u00a77Quantity (items):";
        guiGraphics.drawCenteredString(this.font, modeLabel, centerX, y += 18, 0xCCCCCC);
        y += 14;

        if (this.quantityField != null) {
            this.quantityField.setY(y);
            this.quantityField.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int qty = getQuantity();
        int actualItems = stackMode ? qty * item.getMaxStackSize() : qty;
        double totalCost = buyPricePerItem * (double) actualItems;
        guiGraphics.drawCenteredString(this.font, "\u00a7eTotal: " + actualItems + " items = $" + String.format("%.2f", totalCost), centerX, y += 24, 0xFFFF55);
    }

    private void quickBuyFromSearch(SearchResult result) {
        double buyPrice = WorldShop.getPriceEngine().getBuyPrice(result.item);
        double balance = 999999999.0;
        int maxStackSize = result.item.getMaxStackSize();
        int maxAfford = (int) (balance / buyPrice);
        int quantity = Math.min(maxStackSize, maxAfford);
        if (quantity <= 0) return;
        sendBuyPacket(result.categoryIndex, result.itemIndex, quantity);
    }

    private void sendBuyPacket(int categoryIndex, int itemIndex, int quantity) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ShopPacket.write(ShopPacket.buyItem(categoryIndex, itemIndex, quantity), buf);
        ClientPlayNetworking.send(ShopPacket.PACKET_ID, buf);
    }

    private void sendRemoveCategory(int categoryIndex) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ShopPacket.write(ShopPacket.removeCategory(categoryIndex), buf);
        ClientPlayNetworking.send(ShopPacket.PACKET_ID, buf);
        if (categoryIndex >= 0 && categoryIndex < categories.size()) {
            categories.remove(categoryIndex);
            applyFilter();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (detailView) return false;

        if (layoutEditMode) {
            // Scroll through the infinite grid in layout mode
            int cellSize = 34;
            int guiTop = 30;
            int availableHeight = this.height - guiTop - 70;
            int rowsPerPage = Math.max(1, availableHeight / (cellSize + 2));
            int visibleCount = COLUMNS * rowsPerPage;
            int maxPages = Math.max(1, (int) Math.ceil((double) layoutGrid.length / (double) visibleCount));

            this.accumulatedScroll += scrollDelta;
            double SCROLL_THRESHOLD = 5.0;
            int steps = (int) (this.accumulatedScroll / SCROLL_THRESHOLD);
            if (steps != 0) {
                this.accumulatedScroll -= steps * SCROLL_THRESHOLD;
                this.scrollOffset = Math.max(0, Math.min(maxPages - 1, this.scrollOffset - steps));
            }
            return true;
        }

        int cellSize = searchItemsMode ? 22 : 34;
        int guiTop = 52;
        int availableHeight = this.height - guiTop - 35;
        int rowsPerPage = Math.max(1, availableHeight / cellSize);
        int visibleCount = COLUMNS * rowsPerPage;

        int totalSize;
        if (searchItemsMode) {
            totalSize = searchResults.size();
        } else {
            // Categories mode: use the lowest occupied slot position for scroll range
            int[] slotPos = WorldShop.getCategorySlotPositions();
            if (slotPos != null && slotPos.length > 0) {
                int lastOccupied = -1;
                for (int i = slotPos.length - 1; i >= 0; i--) {
                    if (slotPos[i] >= 0) { lastOccupied = i; break; }
                }
                totalSize = Math.max(filteredCategories.size(), lastOccupied + 1);
            } else {
                totalSize = filteredCategories.size();
            }
        }
        int maxPages = Math.max(1, (int) Math.ceil((double) totalSize / (double) visibleCount));

        this.accumulatedScroll += scrollDelta;
        double SCROLL_THRESHOLD = 5.0;
        int steps = (int) (this.accumulatedScroll / SCROLL_THRESHOLD);
        if (steps != 0) {
            this.accumulatedScroll -= steps * SCROLL_THRESHOLD;
            this.scrollOffset = Math.max(0, Math.min(maxPages - 1, this.scrollOffset - steps));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                for (var widget : this.children()) {
                    if (widget instanceof Button button && button.getMessage().getString().contains("Buy")) {
                        button.onPress();
                        return true;
                    }
                }
                return true;
            }
            if (this.quantityField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (this.searchField != null && this.searchField.isFocused()) {
            if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (detailView && this.quantityField != null && this.quantityField.isFocused()) {
            if (Character.isDigit(codePoint)) {
                return this.quantityField.charTyped(codePoint, modifiers);
            }
            return true;
        }

        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private int getQuantity() {
        if (this.quantityField == null) return 1;
        try {
            return Integer.parseInt(this.quantityField.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
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

    /** Draw a vertical scrollbar track and thumb. */
    private void drawScrollbar(GuiGraphics guiGraphics, int x, int top, int height, int maxPages, int mouseX, int mouseY) {
        if (maxPages <= 1) return;

        guiGraphics.fill(x, top, x + SCROLLBAR_WIDTH, top + height, 0xFF333333);

        int thumbHeight = Math.max(8, height / maxPages);
        int maxScrollPos = height - thumbHeight;
        int thumbY = top + (maxPages > 1 ? (this.scrollOffset * maxScrollPos) / (maxPages - 1) : 0);
        int thumbColor = 0xFF888888;
        if (mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
            thumbColor = 0xFFAAAAAA;
        }
        guiGraphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
