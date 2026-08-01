package asd.itamio.modernshop;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides rarity-based price multipliers for items.
 *
 * <p>Two multiplier sets are used:
 * <ul>
 *   <li><b>Uncraftable multipliers</b> (COMMON=1x, UNCOMMON=5x, RARE=30x,
 *       EPIC=200x, LEGENDARY=2000x) — applied to items with no recipe.</li>
 *   <li><b>Crafted multipliers</b> (COMMON=1x, UNCOMMON=1.5x, RARE=2x,
 *       EPIC=3x, LEGENDARY=5x) — applied ON TOP of the recipe price for
 *       crafted items.</li>
 * </ul>
 */
public class ItemRarity {

    public static final double COMMON_MULT = 1.0;
    public static final double UNCOMMON_MULT = 5.0;
    public static final double RARE_MULT = 30.0;
    public static final double EPIC_MULT = 200.0;
    public static final double LEGENDARY_MULT = 2_000.0;

    public static final double CRAFTED_COMMON_MULT = 1.0;
    public static final double CRAFTED_UNCOMMON_MULT = 1.5;
    public static final double CRAFTED_RARE_MULT = 2.0;
    public static final double CRAFTED_EPIC_MULT = 3.0;
    public static final double CRAFTED_LEGENDARY_MULT = 5.0;

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
        manualRarity.put(Items.EMERALD, 1);
        manualRarity.put(Items.EXPERIENCE_BOTTLE, 1);
        manualRarity.put(Items.SLIME_BALL, 1);
        manualRarity.put(Items.HONEY_BOTTLE, 1);
        manualRarity.put(Items.NAUTILUS_SHELL, 1);
        manualRarity.put(Items.HEART_OF_THE_SEA, 1);

        // --- RARE (2.0x) — hard to obtain ---
        manualRarity.put(Items.DIAMOND, 2);
        manualRarity.put(Items.DIAMOND_ORE, 2);
        manualRarity.put(Items.DEEPSLATE_DIAMOND_ORE, 2);
        manualRarity.put(Items.EMERALD_ORE, 2);
        manualRarity.put(Items.DEEPSLATE_EMERALD_ORE, 2);
        manualRarity.put(Items.ANCIENT_DEBRIS, 2);
        manualRarity.put(Items.NETHERITE_SCRAP, 2);
        manualRarity.put(Items.NETHERITE_INGOT, 2);
        manualRarity.put(Items.TURTLE_EGG, 2);
        manualRarity.put(Items.DRAGON_BREATH, 2);
        manualRarity.put(Items.GHAST_TEAR, 2);
        manualRarity.put(Items.NETHER_STAR, 2);
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

        // --- EPIC (3.0x) — very rare ---
        manualRarity.put(Items.NETHERITE_INGOT, 3);
        manualRarity.put(Items.NETHERITE_SCRAP, 3);
        manualRarity.put(Items.ANCIENT_DEBRIS, 3);
        manualRarity.put(Items.ELYTRA, 3);
        manualRarity.put(Items.TOTEM_OF_UNDYING, 3);
        manualRarity.put(Items.DRAGON_BREATH, 3);
        manualRarity.put(Items.END_CRYSTAL, 3);
        manualRarity.put(Items.SHULKER_SHELL, 3);
        manualRarity.put(Items.DRAGON_EGG, 3);

        // --- LEGENDARY (5.0x) — extremely rare, unique items ---
        manualRarity.put(Items.NETHER_STAR, 4);
        manualRarity.put(Items.DRAGON_EGG, 4);
        manualRarity.put(Items.HEART_OF_THE_SEA, 4);
        manualRarity.put(Items.ELYTRA, 4);
        manualRarity.put(Items.TOTEM_OF_UNDYING, 4);
        manualRarity.put(Items.DRAGON_HEAD, 4);
        manualRarity.put(Items.WITHER_SKELETON_SKULL, 3);
        manualRarity.put(Items.BEACON, 4);
        manualRarity.put(Items.CONDUIT, 4);
        manualRarity.put(Items.END_PORTAL_FRAME, 4);
        manualRarity.put(Items.BARRIER, 4);
        manualRarity.put(Items.STRUCTURE_BLOCK, 4);
        manualRarity.put(Items.COMMAND_BLOCK, 4);
        manualRarity.put(Items.COMMAND_BLOCK_MINECART, 4);
        manualRarity.put(Items.REPEATING_COMMAND_BLOCK, 4);
        manualRarity.put(Items.CHAIN_COMMAND_BLOCK, 4);
        manualRarity.put(Items.JIGSAW, 4);
        manualRarity.put(Items.SPAWNER, 4);

        // --- Diamond tools & armor (RARE) ---
        manualRarity.put(Items.DIAMOND_SWORD, 2);
        manualRarity.put(Items.DIAMOND_PICKAXE, 2);
        manualRarity.put(Items.DIAMOND_AXE, 2);
        manualRarity.put(Items.DIAMOND_SHOVEL, 2);
        manualRarity.put(Items.DIAMOND_HOE, 2);
        manualRarity.put(Items.DIAMOND_HELMET, 2);
        manualRarity.put(Items.DIAMOND_CHESTPLATE, 2);
        manualRarity.put(Items.DIAMOND_LEGGINGS, 2);
        manualRarity.put(Items.DIAMOND_BOOTS, 2);

        // --- Netherite tools & armor (EPIC) ---
        manualRarity.put(Items.NETHERITE_SWORD, 3);
        manualRarity.put(Items.NETHERITE_PICKAXE, 3);
        manualRarity.put(Items.NETHERITE_AXE, 3);
        manualRarity.put(Items.NETHERITE_SHOVEL, 3);
        manualRarity.put(Items.NETHERITE_HOE, 3);
        manualRarity.put(Items.NETHERITE_HELMET, 3);
        manualRarity.put(Items.NETHERITE_CHESTPLATE, 3);
        manualRarity.put(Items.NETHERITE_LEGGINGS, 3);
        manualRarity.put(Items.NETHERITE_BOOTS, 3);

        // --- Netherite upgrade template (EPIC) ---
        manualRarity.put(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3);

        // --- Golden apple (RARE) ---
        manualRarity.put(Items.GOLDEN_APPLE, 2);

        // --- Enchanted golden apple (LEGENDARY) ---
        manualRarity.put(Items.ENCHANTED_GOLDEN_APPLE, 4);

        // --- Music discs (RARE) ---
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

        // --- Enchanted book (EPIC) ---
        manualRarity.put(Items.ENCHANTED_BOOK, 3);

        // --- Saddle & name tag (RARE) ---
        manualRarity.put(Items.SADDLE, 2);
        manualRarity.put(Items.NAME_TAG, 2);

        // --- Smithing templates (RARE) ---
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

        // --- Potions & Tipped arrows (UNCOMMON) ---
        manualRarity.put(Items.POTION, 1);
        manualRarity.put(Items.SPLASH_POTION, 1);
        manualRarity.put(Items.LINGERING_POTION, 1);
        manualRarity.put(Items.TIPPED_ARROW, 1);

        // --- Books (UNCOMMON) ---
        manualRarity.put(Items.WRITABLE_BOOK, 1);
        manualRarity.put(Items.WRITTEN_BOOK, 1);
    }

    private static final double BULK_DISCOUNT = 0.02;
    private static final java.util.Set<Item> bulkItems = java.util.Set.of(
            Items.DIRT, Items.GRASS_BLOCK, Items.COARSE_DIRT, Items.PODZOL,
            Items.MYCELIUM, Items.DIRT_PATH, Items.ROOTED_DIRT, Items.MOSS_BLOCK,
            Items.MUDDY_MANGROVE_ROOTS, Items.MUD, Items.PACKED_MUD,
            Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DEEPSLATE,
            Items.TUFF, Items.ANDESITE, Items.GRANITE,
            Items.DIORITE, Items.BASALT, Items.BLACKSTONE, Items.END_STONE,
            Items.NETHERRACK, Items.NETHER_BRICKS, Items.DRIPSTONE_BLOCK,
            Items.POINTED_DRIPSTONE,
            Items.SAND, Items.RED_SAND, Items.GRAVEL, Items.FLINT,
            Items.SANDSTONE, Items.RED_SANDSTONE, Items.SMOOTH_SANDSTONE,
            Items.SMOOTH_RED_SANDSTONE,
            Items.CLAY, Items.SNOW_BLOCK, Items.SNOW, Items.ICE, Items.PACKED_ICE,
            Items.BLUE_ICE, Items.OBSIDIAN,
            Items.NETHER_GOLD_ORE, Items.NETHER_QUARTZ_ORE
    );

    public static double getBulkDiscountMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0;
        }
        return bulkItems.contains(stack.getItem()) ? BULK_DISCOUNT : 1.0;
    }

    public static double getRarityMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return COMMON_MULT;
        }

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

    public static double getUncraftableRarityMultiplier(ItemStack stack) {
        return getRarityMultiplier(stack);
    }
}
