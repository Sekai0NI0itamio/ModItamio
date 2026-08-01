package asd.itamio.modernshop;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side vanilla container-based shop interface.
 * Renders the shop using a vanilla chest GUI (no custom mod screen needed),
 * allowing players without the mod to browse and buy items.
 *
 * Navigation flow:
 *   PAGE_CATEGORIES (0) -> click category -> PAGE_ITEMS (1) -> click item -> PAGE_DETAIL (2) -> confirm buy -> back
 */
public class VanillaShopContainer {

    private static final int PAGE_CATEGORIES = 0;
    private static final int PAGE_ITEMS = 1;
    private static final int PAGE_DETAIL = 2;
    private static final int TOTAL_SLOTS = 54; // 6 rows x 9 cols

    private static final int SLOT_BACK = 0;
    private static final int SLOT_PAGE_INFO = 1;
    private static final int SLOT_BALANCE = 2;
    private static final int SLOT_CONTENT_START = 9;

    private static final int SLOT_ITEMS_TOP_BORDER = 9;
    private static final int SLOT_ITEMS_START = 18;
    private static final int SLOT_ITEMS_END = 44;
    private static final int SLOT_ITEMS_BOTTOM_BORDER = 45;
    private static final int ITEMS_PER_PAGE = 27;

    private static final int SLOT_MODE_TOGGLE = 13;
    private static final int SLOT_ITEM_PREVIEW = 22;
    private static final int SLOT_QTY_DISPLAY = 31;
    private static final int SLOT_BUY = 40;

    private static final int[] GREEN_SLOTS = {27, 28, 29, 30, 36, 37, 38};
    private static final int[] RED_SLOTS =   {32, 33, 34, 35, 42, 43, 44};

    private static final int[] QUANTITY_VALUES = {1, 2, 4, 8, 16, 32, 64};

    // ========== Navigation Items ==========

    private static final ItemStack BACK_ITEM = enchantGlint(createSimpleItem(Items.BARRIER,
        "\u00a7c\u00a7lBack", "\u00a77Click to go back"));
    private static final ItemStack CLOSE_ITEM = enchantGlint(createSimpleItem(Items.STRUCTURE_VOID,
        "\u00a7c\u00a7lClose Shop", "\u00a77Click to close"));
    private static final ItemStack NEXT_PAGE_ITEM = enchantGlint(createSimpleItem(Items.ARROW,
        "\u00a7aNext Page \u00a77\u00bb", "\u00a77Click for next page"));
    private static final ItemStack PREV_PAGE_ITEM = enchantGlint(createSimpleItem(Items.ARROW,
        "\u00a7a\u00ab \u00a77Previous Page", "\u00a77Click for previous page"));

    /** Dark window pane used to fill empty slots for a cleaner look. */
    private static final ItemStack DARK_PANE = createDarkPane();

    private static ItemStack createDarkPane() {
        ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        return stack;
    }

    /**
     * Apply an enchantment glint to an item stack so it shimmers. In 1.21.1
     * the DataComponents system replaces the old NBT-based enchantment glint:
     * {@code ENCHANTMENT_GLINT_OVERRIDE} set to true renders the glint without
     * requiring an actual enchantment, so there is no enchantment tooltip to
     * hide. This preserves the original behavior (glint + no enchantment
     * tooltip) that previously used Curse of Vanishing + HideFlags=255.
     */
    private static ItemStack enchantGlint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    // ========== Quantity Button Helpers ==========

    private static ItemStack greenAddButton(int qty) {
        ItemStack stack = new ItemStack(Items.LIME_DYE, qty);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7a\u00a7l+ " + qty));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("\u00a7aAdd " + qty + " to total quantity"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return enchantGlint(stack);
    }

    private static ItemStack redRemoveButton(int qty) {
        ItemStack stack = new ItemStack(Items.RED_DYE, qty);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7c\u00a7l- " + qty));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("\u00a7cRemove " + qty + " from total quantity"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return enchantGlint(stack);
    }

    private static ItemStack buyButton(double totalCost) {
        return enchantGlint(createSimpleItem(Items.EMERALD_BLOCK,
            "\u00a7a\u00a7l\u2726 Confirm Purchase",
            "\u00a7aBuy for $" + String.format("%.2f", totalCost)));
    }

    private ItemStack modeToggleButton() {
        ItemStack stack;
        if (stackMode) {
            stack = createSimpleItem(Items.CHEST,
                "\u00a7b\u00a7l\u25b6 Stack Mode",
                "\u00a77Count shows stacks, lore shows items\n\u00a77Each click = \u00a7b64 items");
        } else {
            stack = createSimpleItem(Items.ITEM_FRAME,
                "\u00a7e\u00a7l\u25b6 Item Mode",
                "\u00a77Count shows items\n\u00a77Each click = \u00a7e1 item");
        }
        return enchantGlint(stack);
    }

    // ========== Instance State ==========

    private final SimpleContainer container;
    private final List<ShopCategory> categories;
    private final ServerPlayer player;

    private int currentPage = PAGE_CATEGORIES;
    private int currentCategoryIndex = -1;
    private int contentPage = 0;
    private int maxContentPage = 0;

    private ItemStack detailItem = ItemStack.EMPTY;
    private int totalQuantity = 1;
    private double detailBuyPrice = 0;
    private boolean stackMode = false;

    private int[] currentLayoutPositions = null;

    public VanillaShopContainer(ServerPlayer player) {
        this.categories = new ArrayList<>(ModernShop.getCategories());
        this.player = player;
        this.container = new SimpleContainer(TOTAL_SLOTS);

        ServerLevel level = player.level();
        for (ShopCategory category : categories) {
            category.populateItemsServer(level);
        }
    }

    /**
     * Open the vanilla shop container for the given player.
     */
    public static void open(ServerPlayer player) {
        VanillaShopContainer shop = new VanillaShopContainer(player);
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("\u00a7l\u00a76\u25c8 Modern Shop \u25c8");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player p) {
                return new VanillaShopMenu(containerId, playerInventory, shop.container, 6, shop);
            }
        });
        shop.refreshDisplay();
    }

    // ========== Display Methods ==========

    private void refreshDisplay() {
        container.clearContent();
        switch (currentPage) {
            case PAGE_CATEGORIES:
                setSpecialSlots(PAGE_CATEGORIES);
                displayCategories();
                break;
            case PAGE_ITEMS:
                setSpecialSlots(PAGE_ITEMS);
                displayItems();
                break;
            case PAGE_DETAIL:
                displayDetail();
                break;
        }
    }

    private void setSpecialSlots(int page) {
        if (page == PAGE_CATEGORIES) {
            container.setItem(SLOT_BACK, CLOSE_ITEM.copy());
        } else {
            container.setItem(SLOT_BACK, BACK_ITEM.copy());
        }

        String pageInfo;
        if (page == PAGE_CATEGORIES) {
            pageInfo = "\u00a7e\u00a7l\u25b6 Categories";
        } else if (page == PAGE_ITEMS) {
            String catName = (currentCategoryIndex >= 0 && currentCategoryIndex < categories.size())
                ? categories.get(currentCategoryIndex).getName() : "Unknown";
            pageInfo = "\u00a7e" + catName + " \u00a77(p." + (contentPage + 1) + "/" + (maxContentPage + 1) + ")";
        } else {
            pageInfo = "\u00a7e\u00a7l\u25b6 Purchase";
        }
        container.setItem(SLOT_PAGE_INFO, enchantGlint(createSimpleItem(Items.PAPER, pageInfo, "")));

        EconomyProvider economy = ModernShop.getEconomyProvider(player.level());
        double balance = economy.getBalance(player.level(), player.getUUID());
        String balanceStr = "\u00a7aBalance: $" + String.format("%.2f", balance);
        container.setItem(SLOT_BALANCE, enchantGlint(createSimpleItem(Items.GOLD_NUGGET, balanceStr, "\u00a77Your current balance")));

        for (int i = 3; i < SLOT_CONTENT_START; i++) {
            container.setItem(i, DARK_PANE.copy());
        }
    }

    // ========== Categories Page ==========

    private void displayCategories() {
        int maxShow = TOTAL_SLOTS - SLOT_CONTENT_START;
        int startSlot = SLOT_CONTENT_START;

        int[] slotPos = ModernShop.getCategorySlotPositions();
        if (slotPos == null || slotPos.length == 0) {
            slotPos = ModernShop.buildCenterSphereLayout(categories.size(), 9, 5);
        }
        currentLayoutPositions = slotPos;

        for (int i = 0; i < maxShow; i++) {
            container.setItem(startSlot + i, DARK_PANE.copy());
        }

        int pageStartSlot = contentPage * maxShow;
        for (int i = 0; i < maxShow; i++) {
            int layoutSlot = pageStartSlot + i;
            if (layoutSlot >= slotPos.length) break;
            int catIndex = slotPos[layoutSlot];
            if (catIndex < 0 || catIndex >= categories.size()) continue;

            ShopCategory category = categories.get(catIndex);
            ItemStack icon = category.getIcon().copy();
            addLore(icon,
                "\u00a77" + formatItemCount(category.getItems().size()),
                "\u00a7e\u25b6 Click to browse items"
            );
            icon.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7f\u00a7l" + category.getName()));
            container.setItem(startSlot + i, icon);
        }

        int maxPages = Math.max(1, (int) Math.ceil((double) slotPos.length / (double) maxShow));
        maxContentPage = maxPages - 1;

        if (contentPage > 0) container.setItem(6, PREV_PAGE_ITEM.copy());
        if (contentPage < maxPages - 1) container.setItem(8, NEXT_PAGE_ITEM.copy());
    }

    // ========== Items Page ==========

    private void displayItems() {
        if (currentCategoryIndex < 0 || currentCategoryIndex >= categories.size()) {
            currentPage = PAGE_CATEGORIES;
            contentPage = 0;
            refreshDisplay();
            return;
        }

        ShopCategory category = categories.get(currentCategoryIndex);
        List<ItemStack> items = category.getItems();

        for (int i = SLOT_ITEMS_TOP_BORDER; i < SLOT_ITEMS_START; i++) {
            container.setItem(i, DARK_PANE.copy());
        }

        for (int i = SLOT_ITEMS_START; i <= SLOT_ITEMS_END; i++) {
            container.setItem(i, DARK_PANE.copy());
        }

        for (int i = SLOT_ITEMS_BOTTOM_BORDER; i < TOTAL_SLOTS; i++) {
            container.setItem(i, DARK_PANE.copy());
        }

        RecipeManager recipeManager = player.level().recipeAccess();
        RegistryAccess registryAccess = player.level().registryAccess();

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int itemIndex = contentPage * ITEMS_PER_PAGE + i;
            if (itemIndex >= items.size()) break;

            ItemStack item = items.get(itemIndex).copy();
            double buyPrice = ModernShop.getPriceEngine().getBuyPrice(item, recipeManager, registryAccess);

            addLore(item,
                "\u00a7aBuy: $" + String.format("%.2f", buyPrice),
                "\u00a77\u00a7oClick to view details and purchase"
            );
            container.setItem(SLOT_ITEMS_START + i, item);
        }

        int maxPages = Math.max(1, (int) Math.ceil((double) items.size() / (double) ITEMS_PER_PAGE));
        maxContentPage = maxPages - 1;

        if (contentPage > 0) container.setItem(6, PREV_PAGE_ITEM.copy());
        if (contentPage < maxPages - 1) container.setItem(8, NEXT_PAGE_ITEM.copy());
    }

    // ========== Detail Page ==========

    private void displayDetail() {
        if (detailItem.isEmpty()) {
            currentPage = PAGE_ITEMS;
            contentPage = 0;
            refreshDisplay();
            return;
        }

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            container.setItem(i, DARK_PANE.copy());
        }

        container.setItem(SLOT_BACK, BACK_ITEM.copy());

        ItemStack infoItem = new ItemStack(Items.PAPER);
        infoItem.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7f\u00a7l" + detailItem.getHoverName().getString()));
        addLore(infoItem,
            "\u00a7aBuy price: $" + String.format("%.2f", detailBuyPrice) + " each",
            "\u00a77\u00a7oUse +/- buttons to adjust quantity, then confirm"
        );
        container.setItem(SLOT_PAGE_INFO, enchantGlint(infoItem));

        EconomyProvider economy = ModernShop.getEconomyProvider(player.level());
        double balance = economy.getBalance(player.level(), player.getUUID());
        container.setItem(SLOT_BALANCE, enchantGlint(createSimpleItem(Items.GOLD_NUGGET,
            "\u00a7aBalance: $" + String.format("%.2f", balance),
            "\u00a77Your current balance")));

        container.setItem(SLOT_MODE_TOGGLE, modeToggleButton());

        for (int i = 0; i < QUANTITY_VALUES.length; i++) {
            container.setItem(GREEN_SLOTS[i], greenAddButton(QUANTITY_VALUES[i]));
        }

        ItemStack preview = detailItem.copy();
        int displayCount = stackMode ? Math.min(totalQuantity / 64, 99) : Math.min(totalQuantity, 99);
        preview.setCount(Math.max(1, displayCount));
        String totalDesc = stackMode
            ? "\u00a77Total: \u00a7f" + (totalQuantity / 64) + " stacks \u00a77(\u00a7f" + totalQuantity + " \u00a77items)"
            : "\u00a77Total: \u00a7f" + totalQuantity + "x";
        addLore(preview,
            "",
            "\u00a7aBuy: $" + String.format("%.2f", detailBuyPrice) + " each",
            totalDesc,
            "\u00a77Cost: \u00a7a$" + String.format("%.2f", detailBuyPrice * totalQuantity),
            "",
            "\u00a77\u00a7oGreen (+) on left adds",
            "\u00a77\u00a7oRed (-) on right removes"
        );
        container.setItem(SLOT_ITEM_PREVIEW, preview);

        for (int i = 0; i < QUANTITY_VALUES.length; i++) {
            container.setItem(RED_SLOTS[i], redRemoveButton(QUANTITY_VALUES[i]));
        }

        container.setItem(SLOT_QTY_DISPLAY, qtyDisplayItem());

        double totalCost = detailBuyPrice * totalQuantity;
        container.setItem(SLOT_BUY, buyButton(totalCost));
    }

    private ItemStack qtyDisplayItem() {
        ItemStack stack = new ItemStack(Items.REPEATER);
        String modeLabel = stackMode ? "\u00a7b\u00a7l[Stack Mode]" : "\u00a7e\u00a7l[Item Mode]";
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7e\u00a7l\u2699 Quantity: \u00a7f\u00a7l" + totalQuantity + "  " + modeLabel));
        addLore(stack,
            "\u00a77Current total: \u00a7f" + totalQuantity + "x",
            "\u00a77Total cost: \u00a7a$" + String.format("%.2f", detailBuyPrice * totalQuantity),
            "",
            "\u00a77\u00a7oGreen (+) = add quantity",
            "\u00a77\u00a7oRed (-) = remove quantity",
            "\u00a77\u00a7oMin 1, Max 4096"
        );
        return enchantGlint(stack);
    }

    // ========== Click Handler ==========

    private void handleSlotClick(ServerPlayer player, int slotIndex, int button, ContainerInput clickType) {
        if (slotIndex == SLOT_BACK) {
            if (currentPage == PAGE_CATEGORIES) {
                player.closeContainer();
            } else if (currentPage == PAGE_DETAIL) {
                currentPage = PAGE_ITEMS;
                contentPage = 0;
                detailItem = ItemStack.EMPTY;
                refreshDisplay();
            } else {
                currentPage = PAGE_CATEGORIES;
                contentPage = 0;
                refreshDisplay();
            }
            return;
        }

        if (currentPage != PAGE_DETAIL) {
            if (slotIndex == 6) {
                if (contentPage > 0) {
                    contentPage--;
                    refreshDisplay();
                }
                return;
            }
            if (slotIndex == 8) {
                int maxPages;
                if (currentPage == PAGE_CATEGORIES) {
                    int layoutSize = (currentLayoutPositions != null) ? currentLayoutPositions.length : categories.size();
                    int catsPerPage = TOTAL_SLOTS - SLOT_CONTENT_START;
                    maxPages = Math.max(1, (int) Math.ceil((double) layoutSize / (double) catsPerPage));
                } else {
                    int totalItems = (currentCategoryIndex >= 0 && currentCategoryIndex < categories.size())
                        ? categories.get(currentCategoryIndex).getItems().size() : 0;
                    maxPages = Math.max(1, (int) Math.ceil((double) totalItems / (double) ITEMS_PER_PAGE));
                }
                if (contentPage < maxPages - 1) {
                    contentPage++;
                    refreshDisplay();
                }
                return;
            }
        }

        if (currentPage == PAGE_DETAIL) {
            handleDetailClick(slotIndex);
            return;
        }

        if (currentPage == PAGE_CATEGORIES) {
            if (slotIndex >= SLOT_CONTENT_START && slotIndex < TOTAL_SLOTS) {
                int catsPerPage = TOTAL_SLOTS - SLOT_CONTENT_START;
                int layoutSlot = contentPage * catsPerPage + (slotIndex - SLOT_CONTENT_START);
                if (currentLayoutPositions != null && layoutSlot >= 0 && layoutSlot < currentLayoutPositions.length) {
                    int catIndex = currentLayoutPositions[layoutSlot];
                    if (catIndex >= 0 && catIndex < categories.size()) {
                        currentCategoryIndex = catIndex;
                        currentPage = PAGE_ITEMS;
                        contentPage = 0;
                        refreshDisplay();
                    }
                }
            }
        } else if (currentPage == PAGE_ITEMS) {
            if (slotIndex >= SLOT_ITEMS_START && slotIndex <= SLOT_ITEMS_END) {
                int itemIndex = contentPage * ITEMS_PER_PAGE + (slotIndex - SLOT_ITEMS_START);
                if (currentCategoryIndex >= 0 && currentCategoryIndex < categories.size()) {
                    List<ItemStack> items = categories.get(currentCategoryIndex).getItems();
                    if (itemIndex >= 0 && itemIndex < items.size()) {
                        openDetail(items.get(itemIndex));
                    }
                }
            }
        }
    }

    private void openDetail(ItemStack item) {
        RecipeManager recipeManager = player.level().recipeAccess();
        RegistryAccess registryAccess = player.level().registryAccess();

        this.detailItem = item.copy();
        this.totalQuantity = 1;
        this.detailBuyPrice = ModernShop.getPriceEngine().getBuyPrice(item, recipeManager, registryAccess);
        this.currentPage = PAGE_DETAIL;
        refreshDisplay();
    }

    private void handleDetailClick(int slotIndex) {
        if (detailItem.isEmpty()) return;

        if (slotIndex == SLOT_MODE_TOGGLE) {
            stackMode = !stackMode;
            refreshDisplay();
            return;
        }

        for (int i = 0; i < GREEN_SLOTS.length; i++) {
            if (slotIndex == GREEN_SLOTS[i]) {
                int addQty = stackMode ? QUANTITY_VALUES[i] * 64 : QUANTITY_VALUES[i];
                totalQuantity = Math.min(64 * 64, totalQuantity + addQty);
                player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.8f);
                refreshDisplay();
                return;
            }
        }

        for (int i = 0; i < RED_SLOTS.length; i++) {
            if (slotIndex == RED_SLOTS[i]) {
                int removeQty = stackMode ? QUANTITY_VALUES[i] * 64 : QUANTITY_VALUES[i];
                totalQuantity = Math.max(1, totalQuantity - removeQty);
                player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                refreshDisplay();
                return;
            }
        }

        if (slotIndex == SLOT_BUY) {
            executeBuy(player, detailItem, totalQuantity);
            return;
        }
    }

    // ========== Transaction Execution ==========

    private void executeBuy(ServerPlayer player, ItemStack item, int quantity) {
        EconomyProvider economy = ModernShop.getEconomyProvider(player.level());

        double cost = detailBuyPrice * quantity;
        double balance = economy.getBalance(player.level(), player.getUUID());

        if (balance < cost) {
            player.sendSystemMessage(Component.literal("\u00a7c\u2716 Insufficient funds! Need $" + String.format("%.2f", cost) + ", have $" + String.format("%.2f", balance)));
            refreshDisplay();
            return;
        }

        int space = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) {
                space += 64;
            } else if (invStack.getItem() == item.getItem() && invStack.getCount() < invStack.getMaxStackSize()) {
                space += invStack.getMaxStackSize() - invStack.getCount();
            }
        }
        int actualQty = Math.min(quantity, space);
        if (actualQty <= 0) {
            player.sendSystemMessage(Component.literal("\u00a7c\u2716 Your inventory is full!"));
            refreshDisplay();
            return;
        }

        double actualCost = detailBuyPrice * actualQty;
        economy.subtractBalance(player.level(), player.getUUID(), actualCost);

        ItemStack bought = item.copy();
        bought.setCount(actualQty);
        player.getInventory().add(bought);

        player.sendSystemMessage(Component.literal("\u00a7a\u2714 Bought " + actualQty + "x " + item.getHoverName().getString()
            + " for $" + String.format("%.2f", actualCost)));

        currentPage = PAGE_ITEMS;
        contentPage = 0;
        detailItem = ItemStack.EMPTY;
        refreshDisplay();
    }

    // ========== Helper Methods ==========

    private static void addLore(ItemStack stack, String... loreLines) {
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.literal(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    private static String formatItemCount(int count) {
        if (count >= 1000) {
            return (count / 1000) + "k+ items";
        }
        return count + " items";
    }

    private static ItemStack createSimpleItem(Item item, String name, String lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (!lore.isEmpty()) {
            List<Component> loreList = new ArrayList<>();
            loreList.add(Component.literal(lore));
            stack.set(DataComponents.LORE, new ItemLore(loreList));
        }
        return stack;
    }

    /**
     * Custom AbstractContainerMenu subclass that handles shop interactions.
     * Uses MenuType.GENERIC_9x6 for vanilla client compatibility.
     */
    private static class VanillaShopMenu extends AbstractContainerMenu {
        private final VanillaShopContainer shop;
        private final SimpleContainer container;
        private final int rows;

        protected VanillaShopMenu(int containerId, Inventory playerInventory, SimpleContainer container, int rows, VanillaShopContainer shop) {
            super(MenuType.GENERIC_9x6, containerId);
            this.shop = shop;
            this.container = container;
            this.rows = rows;

            for (int row = 0; row < rows; ++row) {
                for (int col = 0; col < 9; ++col) {
                    this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
                }
            }

            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142 + (rows - 4) * 18));
            }

            for (int row = 0; row < 3; ++row) {
                for (int col = 0; col < 9; ++col) {
                    this.addSlot(new Slot(playerInventory, 9 + col + row * 9, 8 + col * 18, 84 + (rows - 4) * 18 + row * 18));
                }
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            return ItemStack.EMPTY;
        }

        @Override
        public void clicked(int slotIndex, int button, ContainerInput clickType, Player player) {
            if (slotIndex >= 0 && slotIndex < container.getContainerSize()) {
                shop.handleSlotClick((ServerPlayer) player, slotIndex, button, clickType);
                return;
            }
            super.clicked(slotIndex, button, clickType, player);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
