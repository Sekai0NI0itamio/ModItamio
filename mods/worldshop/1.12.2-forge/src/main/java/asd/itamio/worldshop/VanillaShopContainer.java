package asd.itamio.worldshop;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side vanilla container-based shop interface.
 * Renders the shop using a vanilla chest GUI, allowing players to browse
 * and buy items through a container interface.
 *
 * Navigation flow:
 *   PAGE_CATEGORIES (0) -> click category -> PAGE_ITEMS (1) -> click item -> PAGE_DETAIL (2) -> confirm buy -> back
 */
public class VanillaShopContainer {

    private static final int GUI_ID = 1000;

    private static final int PAGE_CATEGORIES = 0;
    private static final int PAGE_ITEMS = 1;
    private static final int PAGE_DETAIL = 2;
    private static final int TOTAL_SLOTS = 54;

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

    // ========== Widget Items (enchanted with glint for visibility) ==========
    // 1.12.2 note: Items.* fields don't exist for all blocks (barrier, structure_void,
    // stained glass panes, emerald_block, chest). Use Item.getByNameOrId() which
    // resolves through the item registry at class-load time (safe because this
    // class is first referenced when a player opens the GUI, well after registry init).

    private static final ItemStack BACK_ITEM = enchantGlint(createSimpleItem(Item.getByNameOrId("minecraft:barrier"),
        "\u00a7c\u00a7lBack", "\u00a77Click to go back"));
    private static final ItemStack CLOSE_ITEM = enchantGlint(createSimpleItem(Item.getByNameOrId("minecraft:structure_void"),
        "\u00a7c\u00a7lClose Shop", "\u00a77Click to close"));
    private static final ItemStack NEXT_PAGE_ITEM = enchantGlint(createSimpleItem(Items.ARROW,
        "\u00a7aNext Page \u00a77\u00bb", "\u00a77Click for next page"));
    private static final ItemStack PREV_PAGE_ITEM = enchantGlint(createSimpleItem(Items.ARROW,
        "\u00a7a\u00ab \u00a77Previous Page", "\u00a77Click for previous page"));

    private static final ItemStack DARK_PANE = createDarkPane();

    private static ItemStack createDarkPane() {
        ItemStack stack = new ItemStack(Item.getByNameOrId("minecraft:black_stained_glass_pane"));
        stack.setStackDisplayName(" ");
        return stack;
    }

    /**
     * Apply an enchantment glint to an item stack so it shimmers. Uses
     * Curse of Vanishing (a curse, not a beneficial enchant) and hides
     * ALL vanilla tooltip sections so widget items only show their custom
     * name and lore — the glint is purely visual.
     *
     * <p>The enchantment and HideFlags are written directly to the item's
     * NBT tag. The "ench" list tag holds the curse (using the numeric
     * enchantment ID for 1.12.2 compatibility), and "HideFlags" is set to
     * 255 (all 8 bits) which hides every vanilla tooltip section. This
     * guarantees "Curse of Vanishing" never appears in the tooltip.
     */
    private static ItemStack enchantGlint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagList enchantments = new NBTTagList();
        NBTTagCompound enchTag = new NBTTagCompound();
        Enchantment vanishingCurse = Enchantment.getEnchantmentByLocation("minecraft:vanishing_curse");
        if (vanishingCurse != null) {
            enchTag.setShort("id", (short) Enchantment.getEnchantmentID(vanishingCurse));
            enchTag.setShort("lvl", (short) 1);
            enchantments.appendTag(enchTag);
        }
        tag.setTag("ench", enchantments);
        tag.setInteger("HideFlags", 255);
        return stack;
    }

    private static ItemStack greenAddButton(int qty) {
        ItemStack stack = new ItemStack(Items.DYE, qty, 10); // lime dye = metadata 10 in 1.12.2
        stack.setStackDisplayName("\u00a7a\u00a7l+ " + qty);
        addLore(stack, "\u00a7aAdd " + qty + " to total quantity");
        return enchantGlint(stack);
    }

    private static ItemStack redRemoveButton(int qty) {
        ItemStack stack = new ItemStack(Items.DYE, qty, 1); // red dye = metadata 1 in 1.12.2
        stack.setStackDisplayName("\u00a7c\u00a7l- " + qty);
        addLore(stack, "\u00a7cRemove " + qty + " from total quantity");
        return enchantGlint(stack);
    }

    private static ItemStack buyButton(double totalCost) {
        return enchantGlint(createSimpleItem(Item.getByNameOrId("minecraft:emerald_block"),
            "\u00a7a\u00a7l\u2726 Confirm Purchase",
            "\u00a7aBuy for $" + String.format("%.2f", totalCost)));
    }

    private ItemStack modeToggleButton() {
        ItemStack stack;
        if (stackMode) {
            stack = createSimpleItem(Item.getByNameOrId("minecraft:chest"),
                "\u00a7b\u00a7l\u25b6 Stack Mode",
                "\u00a77Count shows stacks, lore shows items",
                "\u00a77Each click = \u00a7b64 items");
        } else {
            stack = createSimpleItem(Items.ITEM_FRAME,
                "\u00a7e\u00a7l\u25b6 Item Mode",
                "\u00a77Count shows items",
                "\u00a77Each click = \u00a7e1 item");
        }
        return enchantGlint(stack);
    }

    // ========== Instance State ==========

    private final InventoryBasic container;
    private final List<ShopCategory> categories;
    private final EntityPlayer player;
    private final VanillaShopMenu menu;

    private int currentPage = PAGE_CATEGORIES;
    private int currentCategoryIndex = -1;
    private int contentPage = 0;
    private int maxContentPage = 0;

    private ItemStack detailItem = ItemStack.EMPTY;
    private int totalQuantity = 1;
    private double detailBuyPrice = 0;
    private boolean stackMode = false;

    private int[] currentLayoutPositions = null;

    public VanillaShopContainer(EntityPlayer player) {
        this.categories = new ArrayList<>(WorldShop.getCategories());
        this.player = player;
        this.container = new InventoryBasic("shop", false, TOTAL_SLOTS);
        this.menu = new VanillaShopMenu(this, player.inventory, container, 6);
        refreshDisplay();
    }

    public VanillaShopMenu getMenu() {
        return menu;
    }

    /** Open the vanilla shop container for the given player. */
    public static void open(EntityPlayerMP player) {
        player.openGui(WorldShop.instance, GUI_ID, player.world, 0, 0, 0);
    }

    // ========== Display Methods ==========

    private void refreshDisplay() {
        container.clear();
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
            container.setInventorySlotContents(SLOT_BACK, CLOSE_ITEM.copy());
        } else {
            container.setInventorySlotContents(SLOT_BACK, BACK_ITEM.copy());
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
        container.setInventorySlotContents(SLOT_PAGE_INFO, enchantGlint(createSimpleItem(Items.PAPER, pageInfo, "")));

        EconomyProvider economy = WorldShop.getEconomyProvider(player.world);
        double balance = economy.getBalance(player.world, player.getUniqueID());
        String balanceStr = "\u00a7aBalance: $" + String.format("%.2f", balance);
        container.setInventorySlotContents(SLOT_BALANCE, enchantGlint(createSimpleItem(Items.GOLD_NUGGET, balanceStr, "\u00a77Your current balance")));

        for (int i = 3; i < SLOT_CONTENT_START; i++) {
            container.setInventorySlotContents(i, DARK_PANE.copy());
        }
    }

    private void displayCategories() {
        int maxShow = TOTAL_SLOTS - SLOT_CONTENT_START;
        int startSlot = SLOT_CONTENT_START;

        int[] slotPos = WorldShop.getCategorySlotPositions();
        if (slotPos == null || slotPos.length == 0) {
            slotPos = WorldShop.buildCenterSphereLayout(categories.size(), 9, 5);
        }
        currentLayoutPositions = slotPos;

        for (int i = 0; i < maxShow; i++) {
            container.setInventorySlotContents(startSlot + i, DARK_PANE.copy());
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
            icon.setStackDisplayName("\u00a7f\u00a7l" + category.getName());
            container.setInventorySlotContents(startSlot + i, icon);
        }

        int maxPages = Math.max(1, (int) Math.ceil((double) slotPos.length / (double) maxShow));
        maxContentPage = maxPages - 1;

        if (contentPage > 0) container.setInventorySlotContents(6, PREV_PAGE_ITEM.copy());
        if (contentPage < maxPages - 1) container.setInventorySlotContents(8, NEXT_PAGE_ITEM.copy());
    }

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
            container.setInventorySlotContents(i, DARK_PANE.copy());
        }
        for (int i = SLOT_ITEMS_START; i <= SLOT_ITEMS_END; i++) {
            container.setInventorySlotContents(i, DARK_PANE.copy());
        }
        for (int i = SLOT_ITEMS_BOTTOM_BORDER; i < TOTAL_SLOTS; i++) {
            container.setInventorySlotContents(i, DARK_PANE.copy());
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int itemIndex = contentPage * ITEMS_PER_PAGE + i;
            if (itemIndex >= items.size()) break;

            ItemStack item = items.get(itemIndex).copy();
            double buyPrice = WorldShop.getPriceEngine().getBuyPrice(item);

            addLore(item,
                "\u00a7aBuy: $" + String.format("%.2f", buyPrice),
                "\u00a77\u00a7oClick to view details and purchase"
            );
            container.setInventorySlotContents(SLOT_ITEMS_START + i, item);
        }

        int maxPages = Math.max(1, (int) Math.ceil((double) items.size() / (double) ITEMS_PER_PAGE));
        maxContentPage = maxPages - 1;

        if (contentPage > 0) container.setInventorySlotContents(6, PREV_PAGE_ITEM.copy());
        if (contentPage < maxPages - 1) container.setInventorySlotContents(8, NEXT_PAGE_ITEM.copy());
    }

    private void displayDetail() {
        if (detailItem.isEmpty()) {
            currentPage = PAGE_ITEMS;
            contentPage = 0;
            refreshDisplay();
            return;
        }

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            container.setInventorySlotContents(i, DARK_PANE.copy());
        }

        container.setInventorySlotContents(SLOT_BACK, BACK_ITEM.copy());

        ItemStack infoItem = new ItemStack(Items.PAPER);
        infoItem.setStackDisplayName("\u00a7f\u00a7l" + detailItem.getDisplayName());
        addLore(infoItem,
            "\u00a7aBuy price: $" + String.format("%.2f", detailBuyPrice) + " each",
            "\u00a77\u00a7oUse +/- buttons to adjust quantity, then confirm"
        );
        container.setInventorySlotContents(SLOT_PAGE_INFO, enchantGlint(infoItem));

        EconomyProvider economy = WorldShop.getEconomyProvider(player.world);
        double balance = economy.getBalance(player.world, player.getUniqueID());
        container.setInventorySlotContents(SLOT_BALANCE, enchantGlint(createSimpleItem(Items.GOLD_NUGGET,
            "\u00a7aBalance: $" + String.format("%.2f", balance),
            "\u00a77Your current balance")));

        container.setInventorySlotContents(SLOT_MODE_TOGGLE, modeToggleButton());

        for (int i = 0; i < QUANTITY_VALUES.length; i++) {
            container.setInventorySlotContents(GREEN_SLOTS[i], greenAddButton(QUANTITY_VALUES[i]));
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
        container.setInventorySlotContents(SLOT_ITEM_PREVIEW, preview);

        for (int i = 0; i < QUANTITY_VALUES.length; i++) {
            container.setInventorySlotContents(RED_SLOTS[i], redRemoveButton(QUANTITY_VALUES[i]));
        }

        container.setInventorySlotContents(SLOT_QTY_DISPLAY, qtyDisplayItem());

        double totalCost = detailBuyPrice * totalQuantity;
        container.setInventorySlotContents(SLOT_BUY, buyButton(totalCost));
    }

    private ItemStack qtyDisplayItem() {
        ItemStack stack = new ItemStack(Items.REPEATER);
        String modeLabel = stackMode ? "\u00a7b\u00a7l[Stack Mode]" : "\u00a7e\u00a7l[Item Mode]";
        stack.setStackDisplayName("\u00a7e\u00a7l\u2699 Quantity: \u00a7f\u00a7l" + totalQuantity + "  " + modeLabel);
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

    private void handleSlotClick(EntityPlayer player, int slotIndex, int button, ClickType clickType) {
        if (slotIndex == SLOT_BACK) {
            if (currentPage == PAGE_CATEGORIES) {
                player.closeScreen();
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
        this.detailItem = item.copy();
        this.totalQuantity = 1;
        this.detailBuyPrice = WorldShop.getPriceEngine().getBuyPrice(item);
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
                if (player instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) player).playSound(SoundEvents.BLOCK_NOTE_PLING, 1.0f, 1.8f);
                }
                refreshDisplay();
                return;
            }
        }

        for (int i = 0; i < RED_SLOTS.length; i++) {
            if (slotIndex == RED_SLOTS[i]) {
                int removeQty = stackMode ? QUANTITY_VALUES[i] * 64 : QUANTITY_VALUES[i];
                totalQuantity = Math.max(1, totalQuantity - removeQty);
                if (player instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) player).playSound(SoundEvents.BLOCK_NOTE_BASS, 1.0f, 0.5f);
                }
                refreshDisplay();
                return;
            }
        }

        if (slotIndex == SLOT_BUY) {
            executeBuy(player, detailItem, totalQuantity);
            return;
        }
    }

    private void executeBuy(EntityPlayer player, ItemStack item, int quantity) {
        EconomyProvider economy = WorldShop.getEconomyProvider(player.world);

        double cost = detailBuyPrice * quantity;
        double balance = economy.getBalance(player.world, player.getUniqueID());

        if (balance < cost) {
            player.sendMessage(new TextComponentString("\u00a7c\u2716 Insufficient funds! Need $" + String.format("%.2f", cost) + ", have $" + String.format("%.2f", balance)));
            refreshDisplay();
            return;
        }

        int space = 0;
        for (ItemStack invStack : player.inventory.mainInventory) {
            if (invStack.isEmpty()) {
                space += 64;
            } else if (invStack.getItem() == item.getItem() && invStack.getCount() < invStack.getMaxStackSize()) {
                space += invStack.getMaxStackSize() - invStack.getCount();
            }
        }
        int actualQty = Math.min(quantity, space);
        if (actualQty <= 0) {
            player.sendMessage(new TextComponentString("\u00a7c\u2716 Your inventory is full!"));
            refreshDisplay();
            return;
        }

        double actualCost = detailBuyPrice * actualQty;
        economy.subtractBalance(player.world, player.getUniqueID(), actualCost);

        ItemStack bought = item.copy();
        bought.setCount(actualQty);
        player.inventory.addItemStackToInventory(bought);

        player.sendMessage(new TextComponentString("\u00a7a\u2714 Bought " + actualQty + "x " + item.getDisplayName()
            + " for $" + String.format("%.2f", actualCost)));

        currentPage = PAGE_ITEMS;
        contentPage = 0;
        detailItem = ItemStack.EMPTY;
        refreshDisplay();
    }

    // ========== Helper Methods ==========

    private static void addLore(ItemStack stack, String... loreLines) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        NBTTagList loreList = new NBTTagList();
        for (String line : loreLines) {
            loreList.appendTag(new NBTTagString(line));
        }
        display.setTag("Lore", loreList);
        tag.setTag("display", display);
    }

    private static String formatItemCount(int count) {
        if (count >= 1000) {
            return (count / 1000) + "k+ items";
        }
        return count + " items";
    }

    private static ItemStack createSimpleItem(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.setStackDisplayName(name);
        if (lore.length > 0 && !lore[0].isEmpty()) {
            addLore(stack, lore);
        }
        return stack;
    }

    /**
     * Custom Container subclass that handles shop interactions.
     * Uses a chest-like layout with 6 rows of shop slots plus player inventory.
     */
    public static class VanillaShopMenu extends Container {
        private final VanillaShopContainer shop;
        private final IInventory container;
        private final int rows;

        protected VanillaShopMenu(VanillaShopContainer shop, InventoryPlayer playerInventory, IInventory container, int rows) {
            this.shop = shop;
            this.container = container;
            this.rows = rows;

            for (int row = 0; row < rows; ++row) {
                for (int col = 0; col < 9; ++col) {
                    this.addSlotToContainer(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
                }
            }

            int offsetY = (rows - 4) * 18;
            for (int row = 0; row < 3; ++row) {
                for (int col = 0; col < 9; ++col) {
                    this.addSlotToContainer(new Slot(playerInventory, 9 + col + row * 9, 8 + col * 18, 103 + row * 18 + offsetY));
                }
            }

            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 161 + offsetY));
            }
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
            if (slotId >= 0 && slotId < container.getSizeInventory()) {
                shop.handleSlotClick(player, slotId, dragType, clickTypeIn);
                return ItemStack.EMPTY;
            }
            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
