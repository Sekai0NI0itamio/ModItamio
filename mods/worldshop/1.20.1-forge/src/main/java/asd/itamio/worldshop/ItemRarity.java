package asd.itamio.worldshop;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides rarity-based price multipliers for items.
 *
 * <p>Rarity is determined in two layers:
 * <ol>
 *   <li>Manual overrides for specific vanilla items that should be priced
 *       higher based on how rare they are to obtain in gameplay (diamond,
 *       netherite, elytra, etc.). Minecraft's built-in Rarity enum is too
 *       coarse — most items are COMMON.</li>
 *   <li>Fallback to Minecraft's {@link ItemStack#getRarity()} for items
 *       without a manual override (UNCOMMON, RARE, EPIC get multipliers).</li>
 * </ol>
 *
 * <p>Two multiplier sets are used:
 * <ul>
 *   <li><b>Uncraftable multipliers</b> (COMMON=1x, UNCOMMON=5x, RARE=30x,
 *       EPIC=200x, LEGENDARY=2000x) — applied to items with no recipe.
 *       These are high so that rare uncraftable items like enchanted golden
 *       apples ($2,000) and elytra ($2,000) are properly expensive.</li>
 *   <li><b>Crafted multipliers</b> (COMMON=1x, UNCOMMON=1.5x, RARE=2x,
 *       EPIC=3x, LEGENDARY=5x) — applied ON TOP of the recipe price for
 *       crafted items. These are smaller because the rarity is already
 *       reflected in the ingredient prices through the recipe cascade.
 *       This prevents absurd prices like a $4M beacon.</li>
 * </ul>
 *
 * <p>The rarity multiplier cascades through the recipe system: each
 * ingredient's price includes its rarity multiplier, so crafted items
 * using rare materials are already expensive. The crafted multiplier
 * then adds a small premium on top.
 */
public class ItemRarity {

    // Rarity multipliers — how much to multiply the $1 base price based on rarity.
    // These are applied to UNcraftable items (no recipe) to determine their price.
    // Higher rarity = higher multiplier. Tuned so that:
    //   - Common items (dirt, wood) stay at $1 (bulk items $0.02)
    //   - Uncommon items (iron, gold) are ~$5
    //   - Rare items (diamond) are ~$30
    //   - Epic items (netherite ingot) are ~$200
    //   - Legendary items (enchanted golden apple, elytra) are ~$2,000
    public static final double COMMON_MULT = 1.0;
    public static final double UNCOMMON_MULT = 5.0;
    public static final double RARE_MULT = 30.0;
    public static final double EPIC_MULT = 200.0;
    public static final double LEGENDARY_MULT = 2_000.0;

    // Crafted rarity multipliers — applied ON TOP of the recipe price for
    // crafted items. These are SMALLER than the uncraftable multipliers
    // because the rarity is already reflected in the ingredient prices
    // (rarity cascades through the recipe system). The crafted multiplier
    // adds a small "rarity premium" so that a rare crafted item is slightly
    // more expensive than just the sum of its ingredients.
    // Example: diamond sword = (2 diamonds @ $30 each + stick) * 2.0 = ~$122
    public static final double CRAFTED_COMMON_MULT = 1.0;
    public static final double CRAFTED_UNCOMMON_MULT = 1.5;
    public static final double CRAFTED_RARE_MULT = 2.0;
    public static final double CRAFTED_EPIC_MULT = 3.0;
    public static final double CRAFTED_LEGENDARY_MULT = 5.0;

    /**
     * Manual rarity overrides for vanilla items based on gameplay rarity
     * (how hard the item is to obtain), not Minecraft's Rarity enum.
     *
     * <p>Tier mapping:
     * <ul>
     *   <li>1 = UNCOMMON (5.0x uncraftable, 1.5x crafted) — slightly rare (gold, iron, lapis)</li>
     *   <li>2 = RARE (30.0x uncraftable, 2.0x crafted) — hard to get (diamond, emerald)</li>
     *   <li>3 = EPIC (200.0x uncraftable, 3.0x crafted) — very rare (netherite, elytra)</li>
     *   <li>4 = LEGENDARY (2,000.0x uncraftable, 5.0x crafted) — extremely rare (enchanted golden apple, dragon egg)</li>
     * </ul>
     */
    private static final Map<Item, Integer> manualRarity = new HashMap<>();

    static {
        // --- UNCOMMON (1.5x) — somewhat rare materials ---
        manualRarity.put(Items.GOLD_INGOT, 1);
        manualRarity.put(Items.GOLD_ORE, 1);
        manualRarity.put(Items.DEEPSLATE_GOLD_ORE, 1);
        manualRarity.put(Items.RAW_GOLD, 1);
        manualRarity.put(Items.RAW_IRON, 1);
        manualRarity.put(Items.DEEPSLATE_IRON_ORE, 1);
        manualRarity.put(Items.LAPIS_LAZULI, 1);
        manualRarity.put(Items.LAPIS_ORE, 1);
        manualRarity.put(Items.DEEPSLATE_LAPIS_ORE, 1);
        manualRarity.put(Items.REDSTONE, 1);
        manualRarity.put(Items.REDSTONE_ORE, 1);
        manualRarity.put(Items.DEEPSLATE_REDSTONE_ORE, 1);
        manualRarity.put(Items.QUARTZ, 1);
        manualRarity.put(Items.NETHER_QUARTZ_ORE, 1);
        manualRarity.put(Items.AMETHYST_SHARD, 1);
        manualRarity.put(Items.BUDDING_AMETHYST, 1);
        manualRarity.put(Items.CLAY_BALL, 1);
        manualRarity.put(Items.TERRACOTTA, 1);
        manualRarity.put(Items.INK_SAC, 1);
        manualRarity.put(Items.GLOW_INK_SAC, 1);
        manualRarity.put(Items.COPPER_INGOT, 1);
        manualRarity.put(Items.RAW_COPPER, 1);
        manualRarity.put(Items.DEEPSLATE_COPPER_ORE, 1);
        manualRarity.put(Items.COPPER_ORE, 1);
        manualRarity.put(Items.EMERALD, 1); // emerald is tradeable with villagers, somewhat common
        manualRarity.put(Items.EXPERIENCE_BOTTLE, 1);
        manualRarity.put(Items.SLIME_BALL, 1);
        manualRarity.put(Items.HONEY_BOTTLE, 1);
        manualRarity.put(Items.NAUTILUS_SHELL, 1);
        manualRarity.put(Items.HEART_OF_THE_SEA, 1); // rare ocean item

        // --- RARE (3.0x) — hard to obtain ---
        manualRarity.put(Items.DIAMOND, 2);
        manualRarity.put(Items.DIAMOND_ORE, 2);
        manualRarity.put(Items.DEEPSLATE_DIAMOND_ORE, 2);
        manualRarity.put(Items.EMERALD_ORE, 2);
        manualRarity.put(Items.DEEPSLATE_EMERALD_ORE, 2);
        manualRarity.put(Items.ANCIENT_DEBRIS, 2); // actually epic, but moved below
        manualRarity.put(Items.NETHERITE_SCRAP, 2);
        manualRarity.put(Items.NETHERITE_INGOT, 2); // re-evaluated below as epic
        manualRarity.put(Items.TURTLE_EGG, 2);
        manualRarity.put(Items.DRAGON_BREATH, 2);
        manualRarity.put(Items.GHAST_TEAR, 2);
        manualRarity.put(Items.NETHER_STAR, 2); // actually legendary, see below
        manualRarity.put(Items.END_CRYSTAL, 2);
        manualRarity.put(Items.CHORUS_FLOWER, 2);
        manualRarity.put(Items.CHORUS_FRUIT, 2);
        manualRarity.put(Items.PURPUR_BLOCK, 2);
        manualRarity.put(Items.END_ROD, 2);
        manualRarity.put(Items.ENDER_EYE, 2);
        manualRarity.put(Items.BLAZE_ROD, 2);
        manualRarity.put(Items.BLAZE_POWDER, 2);
        manualRarity.put(Items.WITHER_SKELETON_SKULL, 2);
        manualRarity.put(Items.SKELETON_SKULL, 2);
        manualRarity.put(Items.ZOMBIE_HEAD, 2);
        manualRarity.put(Items.CREEPER_HEAD, 2);
        manualRarity.put(Items.PLAYER_HEAD, 2);
        manualRarity.put(Items.DRAGON_HEAD, 2);
        manualRarity.put(Items.NETHER_WART, 2);
        manualRarity.put(Items.NETHER_WART_BLOCK, 2);
        manualRarity.put(Items.WITHER_ROSE, 2);
        manualRarity.put(Items.CRYING_OBSIDIAN, 2);
        manualRarity.put(Items.RESPAWN_ANCHOR, 2);
        manualRarity.put(Items.END_PORTAL_FRAME, 2);

        // --- EPIC (5.0x) — very rare ---
        manualRarity.put(Items.NETHERITE_INGOT, 3); // override: netherite is EPIC
        manualRarity.put(Items.NETHERITE_SCRAP, 3); // override: scrap is EPIC
        manualRarity.put(Items.ANCIENT_DEBRIS, 3); // override: ancient debris is EPIC
        manualRarity.put(Items.ELYTRA, 3);
        manualRarity.put(Items.TOTEM_OF_UNDYING, 3);
        manualRarity.put(Items.DRAGON_BREATH, 3); // override: dragon breath is EPIC
        manualRarity.put(Items.END_CRYSTAL, 3); // override: end crystal is EPIC
        manualRarity.put(Items.SHULKER_SHELL, 3);
        manualRarity.put(Items.DRAGON_EGG, 3); // actually legendary, see below

        // --- LEGENDARY (10.0x) — extremely rare, unique items ---
        manualRarity.put(Items.NETHER_STAR, 4); // override: nether star is LEGENDARY
        manualRarity.put(Items.DRAGON_EGG, 4); // override: dragon egg is LEGENDARY
        manualRarity.put(Items.HEART_OF_THE_SEA, 4); // override: heart of the sea is LEGENDARY
        manualRarity.put(Items.ELYTRA, 4); // override: elytra is LEGENDARY (unique per world)
        manualRarity.put(Items.TOTEM_OF_UNDYING, 4); // override: totem is LEGENDARY
        manualRarity.put(Items.DRAGON_HEAD, 4); // override: dragon head is LEGENDARY
        manualRarity.put(Items.WITHER_SKELETON_SKULL, 3); // wither skull for beacon
        manualRarity.put(Items.BEACON, 4); // override: beacon is LEGENDARY (requires nether star)
        manualRarity.put(Items.CONDUIT, 4); // override: conduit is LEGENDARY (requires heart of the sea)
        manualRarity.put(Items.END_PORTAL_FRAME, 4); // override: end portal frame is LEGENDARY
        manualRarity.put(Items.BARRIER, 4); // admin-only block
        manualRarity.put(Items.STRUCTURE_BLOCK, 4);
        manualRarity.put(Items.COMMAND_BLOCK, 4);
        manualRarity.put(Items.COMMAND_BLOCK_MINECART, 4);
        manualRarity.put(Items.REPEATING_COMMAND_BLOCK, 4);
        manualRarity.put(Items.CHAIN_COMMAND_BLOCK, 4);
        manualRarity.put(Items.JIGSAW, 4);
        manualRarity.put(Items.SPAWNER, 4);

        // --- Diamond tools & armor (RARE — require diamond, a rare material) ---
        manualRarity.put(Items.DIAMOND_SWORD, 2);
        manualRarity.put(Items.DIAMOND_PICKAXE, 2);
        manualRarity.put(Items.DIAMOND_AXE, 2);
        manualRarity.put(Items.DIAMOND_SHOVEL, 2);
        manualRarity.put(Items.DIAMOND_HOE, 2);
        manualRarity.put(Items.DIAMOND_HELMET, 2);
        manualRarity.put(Items.DIAMOND_CHESTPLATE, 2);
        manualRarity.put(Items.DIAMOND_LEGGINGS, 2);
        manualRarity.put(Items.DIAMOND_BOOTS, 2);

        // --- Netherite tools & armor (EPIC — require netherite, very rare) ---
        manualRarity.put(Items.NETHERITE_SWORD, 3);
        manualRarity.put(Items.NETHERITE_PICKAXE, 3);
        manualRarity.put(Items.NETHERITE_AXE, 3);
        manualRarity.put(Items.NETHERITE_SHOVEL, 3);
        manualRarity.put(Items.NETHERITE_HOE, 3);
        manualRarity.put(Items.NETHERITE_HELMET, 3);
        manualRarity.put(Items.NETHERITE_CHESTPLATE, 3);
        manualRarity.put(Items.NETHERITE_LEGGINGS, 3);
        manualRarity.put(Items.NETHERITE_BOOTS, 3);

        // --- Netherite upgrade template (EPIC — rare smithing item) ---
        manualRarity.put(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3);

        // --- Golden apple (RARE — requires 8 gold ingots) ---
        manualRarity.put(Items.GOLDEN_APPLE, 2);

        // --- Enchanted golden apple (LEGENDARY — uncraftable, extremely rare) ---
        manualRarity.put(Items.ENCHANTED_GOLDEN_APPLE, 4);

        // --- Music discs (RARE — dungeon/structure loot only) ---
        manualRarity.put(Items.MUSIC_DISC_13, 2);
        manualRarity.put(Items.MUSIC_DISC_CAT, 2);
        manualRarity.put(Items.MUSIC_DISC_BLOCKS, 2);
        manualRarity.put(Items.MUSIC_DISC_CHIRP, 2);
        manualRarity.put(Items.MUSIC_DISC_FAR, 2);
        manualRarity.put(Items.MUSIC_DISC_MALL, 2);
        manualRarity.put(Items.MUSIC_DISC_MELLOHI, 2);
        manualRarity.put(Items.MUSIC_DISC_STAL, 2);
        manualRarity.put(Items.MUSIC_DISC_STRAD, 2);
        manualRarity.put(Items.MUSIC_DISC_WARD, 2);
        manualRarity.put(Items.MUSIC_DISC_11, 2);
        manualRarity.put(Items.MUSIC_DISC_WAIT, 2);
        manualRarity.put(Items.MUSIC_DISC_OTHERSIDE, 2);
        manualRarity.put(Items.MUSIC_DISC_5, 2);
        manualRarity.put(Items.MUSIC_DISC_PIGSTEP, 2);

        // --- Enchanted book (EPIC — requires XP to enchant) ---
        manualRarity.put(Items.ENCHANTED_BOOK, 3);

        // --- Saddle & name tag (RARE — dungeon/structure loot, uncraftable) ---
        manualRarity.put(Items.SADDLE, 2);
        manualRarity.put(Items.NAME_TAG, 2);

        // --- Smithing templates (RARE — structure loot) ---
        manualRarity.put(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
        manualRarity.put(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 3);

        // --- Potions & Tipped arrows (UNCOMMON — require brewing) ---
        manualRarity.put(Items.POTION, 1);
        manualRarity.put(Items.SPLASH_POTION, 1);
        manualRarity.put(Items.LINGERING_POTION, 1);
        manualRarity.put(Items.TIPPED_ARROW, 1);

        // --- Dragon breath & end-related (already EPIC above) ---

        // --- Books & enchanted items (UNCOMMON — require crafting) ---
        manualRarity.put(Items.WRITABLE_BOOK, 1);
        manualRarity.put(Items.WRITTEN_BOOK, 1);
    }

    /**
     * Bulk-obtainability discount. Items that are trivially obtainable in
     * massive quantities (dirt, cobblestone, sand, gravel, etc.) get a heavy
     * discount so they are nearly worthless — this prevents the bulk-sell
     * exploit where a player amasses thousands of dirt and sells them for a
     * large sum. Applied as an additional multiplier to base-material and
     * uncraftable prices (crafted items inherit the discount via their
     * cheap ingredients, so e.g. a stone pickaxe stays affordable).
     */
    private static final double BULK_DISCOUNT = 0.02;
    private static final java.util.Set<Item> bulkItems = java.util.Set.of(
            // Dirt family
            Items.DIRT, Items.GRASS_BLOCK, Items.COARSE_DIRT, Items.PODZOL,
            Items.MYCELIUM, Items.DIRT_PATH, Items.ROOTED_DIRT, Items.MOSS_BLOCK,
            Items.MUDDY_MANGROVE_ROOTS, Items.MUD, Items.PACKED_MUD,
            // Stone / cobblestone family
            Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DEEPSLATE,
            Items.TUFF, Items.ANDESITE, Items.GRANITE,
            Items.DIORITE, Items.BASALT, Items.BLACKSTONE, Items.END_STONE,
            Items.NETHERRACK, Items.NETHER_BRICKS, Items.DRIPSTONE_BLOCK,
            Items.POINTED_DRIPSTONE,
            // Sand / gravel family
            Items.SAND, Items.RED_SAND, Items.GRAVEL, Items.FLINT,
            Items.SANDSTONE, Items.RED_SANDSTONE, Items.SMOOTH_SANDSTONE,
            Items.SMOOTH_RED_SANDSTONE,
            // Other trivially-mass-obtainable
            Items.CLAY, Items.SNOW_BLOCK, Items.SNOW, Items.ICE, Items.PACKED_ICE,
            Items.BLUE_ICE, Items.OBSIDIAN,
            Items.NETHER_GOLD_ORE, Items.NETHER_QUARTZ_ORE
    );

    /**
     * Get the bulk-obtainability discount multiplier for an item. Returns
     * 0.02 for trivially-mass-obtainable items, 1.0 otherwise.
     */
    public static double getBulkDiscountMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0;
        }
        return bulkItems.contains(stack.getItem()) ? BULK_DISCOUNT : 1.0;
    }

    /**
     * Get the rarity multiplier for an item.
     *
     * <p>First checks the manual rarity overrides. If the item has a manual
     * override, returns the corresponding multiplier. Otherwise falls back
     * to Minecraft's built-in {@link ItemStack#getRarity()}.
     *
     * <p>This multiplier is applied to the $1 base price for uncraftable
     * items (items with no recipe). For crafted items, use
     * {@link #getCraftedRarityMultiplier(ItemStack)} instead, which returns
     * a smaller multiplier applied on top of the recipe price.
     *
     * @param stack The item stack to check
     * @return Price multiplier (1.0 for common, up to 2,000.0 for legendary)
     */
    public static double getRarityMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return COMMON_MULT;
        }

        // Check manual overrides first
        Integer manualTier = manualRarity.get(stack.getItem());
        if (manualTier != null) {
            switch (manualTier) {
                case 1: return UNCOMMON_MULT;
                case 2: return RARE_MULT;
                case 3: return EPIC_MULT;
                case 4: return LEGENDARY_MULT;
                default: return COMMON_MULT;
            }
        }

        // Fall back to Minecraft's built-in rarity
        try {
            Rarity rarity = stack.getRarity();
            if (rarity != null) {
                switch (rarity) {
                    case UNCOMMON: return UNCOMMON_MULT;
                    case RARE: return RARE_MULT;
                    case EPIC: return EPIC_MULT;
                    default: return COMMON_MULT;
                }
            }
        } catch (Exception ignored) {
        }

        return COMMON_MULT;
    }

    /**
     * Get the crafted rarity multiplier for a crafted item. This is a
     * SMALLER multiplier applied on top of the recipe price (which already
     * includes rarity-adjusted ingredient costs through the cascade).
     *
     * <p>Example: A diamond sword (RARE) has a recipe price of ~$61
     * (2 diamonds @ $30 each + stick). The crafted RARE multiplier of 2.0
     * brings the final price to ~$122.
     *
     * @param stack The crafted item stack
     * @return Crafted multiplier (1.0 for common, up to 5.0 for legendary)
     */
    public static double getCraftedRarityMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return CRAFTED_COMMON_MULT;
        }

        Integer manualTier = manualRarity.get(stack.getItem());
        if (manualTier != null) {
            switch (manualTier) {
                case 1: return CRAFTED_UNCOMMON_MULT;
                case 2: return CRAFTED_RARE_MULT;
                case 3: return CRAFTED_EPIC_MULT;
                case 4: return CRAFTED_LEGENDARY_MULT;
                default: return CRAFTED_COMMON_MULT;
            }
        }

        try {
            Rarity rarity = stack.getRarity();
            if (rarity != null) {
                switch (rarity) {
                    case UNCOMMON: return CRAFTED_UNCOMMON_MULT;
                    case RARE: return CRAFTED_RARE_MULT;
                    case EPIC: return CRAFTED_EPIC_MULT;
                    default: return CRAFTED_COMMON_MULT;
                }
            }
        } catch (Exception ignored) {
        }

        return CRAFTED_COMMON_MULT;
    }

    /**
     * Get the rarity tier name for display purposes.
     */
    public static String getRarityName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "Common";
        Integer manualTier = manualRarity.get(stack.getItem());
        if (manualTier != null) {
            switch (manualTier) {
                case 1: return "Uncommon";
                case 2: return "Rare";
                case 3: return "Epic";
                case 4: return "Legendary";
                default: return "Common";
            }
        }
        try {
            Rarity rarity = stack.getRarity();
            if (rarity != null) {
                switch (rarity) {
                    case UNCOMMON: return "Uncommon";
                    case RARE: return "Rare";
                    case EPIC: return "Epic";
                    default: return "Common";
                }
            }
        } catch (Exception ignored) {
        }
        return "Common";
    }

    /**
     * Get the rarity multiplier for an uncraftable item (no recipe).
     * This is the same as {@link #getRarityMultiplier(ItemStack)} — the
     * multiplier is applied to the $1 base price.
     *
     * <p>Example: enchanted golden apple (LEGENDARY) = $1 * 2,000 = $2,000.
     *
     * @param stack The item stack to check
     * @return Price multiplier (1.0 for common, up to 2,000.0 for legendary)
     */
    public static double getUncraftableRarityMultiplier(ItemStack stack) {
        return getRarityMultiplier(stack);
    }
}
