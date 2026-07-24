package asd.itamio.worldshop;

import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provides rarity-based price multipliers for items.
 *
 * Two multiplier sets are used:
 * - Uncraftable multipliers (1x/5x/30x/200x/2000x) applied to items with no recipe.
 * - Crafted multipliers (1x/1.5x/2x/3x/5x) applied ON TOP of recipe price.
 *
 * Items are looked up by registry name string so the override list works
 * across Minecraft versions without compile-time item constant references.
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

    private static final double BULK_DISCOUNT = 0.02;

    private static final Map<Item, Integer> manualRarity = new HashMap<>();
    private static final Set<Item> bulkItems = new HashSet<>();

    static {
        // --- UNCOMMON (tier 1) ---
        registerManual("minecraft:gold_ingot", 1);
        registerManual("minecraft:gold_ore", 1);
        registerManual("minecraft:iron_ore", 1);
        registerManual("minecraft:lapis_lazuli", 1);
        registerManual("minecraft:lapis_ore", 1);
        registerManual("minecraft:redstone", 1);
        registerManual("minecraft:redstone_ore", 1);
        registerManual("minecraft:quartz", 1);
        registerManual("minecraft:nether_quartz_ore", 1);
        registerManual("minecraft:clay_ball", 1);
        registerManual("minecraft:terracotta", 1);
        registerManual("minecraft:ink_sac", 1);
        registerManual("minecraft:slime_ball", 1);
        registerManual("minecraft:experience_bottle", 1);
        registerManual("minecraft:emerald", 1);

        // --- RARE (tier 2) ---
        registerManual("minecraft:diamond", 2);
        registerManual("minecraft:diamond_ore", 2);
        registerManual("minecraft:emerald_ore", 2);
        registerManual("minecraft:dragon_breath", 2);
        registerManual("minecraft:ghast_tear", 2);
        registerManual("minecraft:nether_star", 2);
        registerManual("minecraft:end_crystal", 2);
        registerManual("minecraft:chorus_flower", 2);
        registerManual("minecraft:chorus_fruit", 2);
        registerManual("minecraft:purpur_block", 2);
        registerManual("minecraft:end_rod", 2);
        registerManual("minecraft:ender_eye", 2);
        registerManual("minecraft:blaze_rod", 2);
        registerManual("minecraft:blaze_powder", 2);
        registerManual("minecraft:wither_skeleton_skull", 2);
        registerManual("minecraft:skeleton_skull", 2);
        registerManual("minecraft:zombie_head", 2);
        registerManual("minecraft:creeper_head", 2);
        registerManual("minecraft:player_head", 2);
        registerManual("minecraft:dragon_head", 2);
        registerManual("minecraft:nether_wart", 2);
        registerManual("minecraft:nether_wart_block", 2);
        registerManual("minecraft:end_portal_frame", 2);

        // --- EPIC (tier 3) ---
        registerManual("minecraft:elytra", 3);
        registerManual("minecraft:totem_of_undying", 3);
        registerManual("minecraft:shulker_shell", 3);

        // --- LEGENDARY (tier 4) ---
        registerManual("minecraft:nether_star", 4);
        registerManual("minecraft:dragon_egg", 4);
        registerManual("minecraft:elytra", 4);
        registerManual("minecraft:totem_of_undying", 4);
        registerManual("minecraft:dragon_head", 4);
        registerManual("minecraft:beacon", 4);
        registerManual("minecraft:end_portal_frame", 4);
        registerManual("minecraft:barrier", 4);
        registerManual("minecraft:structure_block", 4);
        registerManual("minecraft:command_block", 4);
        registerManual("minecraft:command_block_minecart", 4);
        registerManual("minecraft:repeating_command_block", 4);
        registerManual("minecraft:chain_command_block", 4);

        // --- Diamond tools & armor (RARE) ---
        registerManual("minecraft:diamond_sword", 2);
        registerManual("minecraft:diamond_pickaxe", 2);
        registerManual("minecraft:diamond_axe", 2);
        registerManual("minecraft:diamond_shovel", 2);
        registerManual("minecraft:diamond_hoe", 2);
        registerManual("minecraft:diamond_helmet", 2);
        registerManual("minecraft:diamond_chestplate", 2);
        registerManual("minecraft:diamond_leggings", 2);
        registerManual("minecraft:diamond_boots", 2);

        // --- Golden apple (RARE) ---
        registerManual("minecraft:golden_apple", 2);

        // --- Music discs (RARE) ---
        registerManual("minecraft:record_13", 2);
        registerManual("minecraft:record_cat", 2);
        registerManual("minecraft:record_blocks", 2);
        registerManual("minecraft:record_chirp", 2);
        registerManual("minecraft:record_far", 2);
        registerManual("minecraft:record_mall", 2);
        registerManual("minecraft:record_mellohi", 2);
        registerManual("minecraft:record_stal", 2);
        registerManual("minecraft:record_strad", 2);
        registerManual("minecraft:record_ward", 2);
        registerManual("minecraft:record_11", 2);
        registerManual("minecraft:record_wait", 2);

        // --- Enchanted book (EPIC) ---
        registerManual("minecraft:enchanted_book", 3);

        // --- Saddle & name tag (RARE) ---
        registerManual("minecraft:saddle", 2);
        registerManual("minecraft:name_tag", 2);

        // --- Books (UNCOMMON) ---
        registerManual("minecraft:writable_book", 1);
        registerManual("minecraft:written_book", 1);

        // --- Bulk items (trivially mass-obtainable) ---
        registerBulk("minecraft:dirt");
        registerBulk("minecraft:grass");
        registerBulk("minecraft:grass_path");
        registerBulk("minecraft:coarse_dirt");
        registerBulk("minecraft:podzol");
        registerBulk("minecraft:mycelium");
        registerBulk("minecraft:cobblestone");
        registerBulk("minecraft:stone");
        registerBulk("minecraft:andesite");
        registerBulk("minecraft:granite");
        registerBulk("minecraft:diorite");
        registerBulk("minecraft:end_stone");
        registerBulk("minecraft:netherrack");
        registerBulk("minecraft:sand");
        registerBulk("minecraft:red_sand");
        registerBulk("minecraft:gravel");
        registerBulk("minecraft:flint");
        registerBulk("minecraft:sandstone");
        registerBulk("minecraft:red_sandstone");
        registerBulk("minecraft:clay");
        registerBulk("minecraft:snow_layer");
        registerBulk("minecraft:snow");
        registerBulk("minecraft:ice");
        registerBulk("minecraft:packed_ice");
        registerBulk("minecraft:obsidian");
        registerBulk("minecraft:nether_brick");
    }

    private static void registerManual(String registryName, int tier) {
        Item item = lookupItem(registryName);
        if (item != null) {
            manualRarity.put(item, tier);
        }
    }

    private static void registerBulk(String registryName) {
        Item item = lookupItem(registryName);
        if (item != null) {
            bulkItems.add(item);
        }
    }

    private static Item lookupItem(String registryName) {
        ResourceLocation rl = new ResourceLocation(registryName);
        return Item.getByNameOrId(registryName);
    }

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
            EnumRarity rarity = stack.getRarity();
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
            EnumRarity rarity = stack.getRarity();
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
            EnumRarity rarity = stack.getRarity();
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
