package asd.itamio.worldshop;

import asd.itamio.ModInfoPrinter;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Mod(WorldShop.MOD_ID)
public class WorldShop {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "World Shop";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel NETWORK;

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();

    public WorldShop() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        NETWORK = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                s -> true,
                s -> true
        );

        NETWORK.registerMessage(0, ShopPacket.class,
                ShopPacket::toBytes,
                ShopPacket::new,
                WorldShop::handlePacket
        );

        LOGGER.info("World Shop network channel registered");
    }

    private static void handlePacket(ShopPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                if (ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    handleServerPacket(packet, ctx);
                } else {
                    handleClientPacket(packet);
                }
            } catch (Exception e) {
                System.err.println("[MODAPP-ERROR] Error handling World Shop packet type " + packet.getType() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleServerPacket(ShopPacket packet, NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        switch (packet.getType()) {
            case ShopPacket.BUY_ITEM -> handleBuy(player, packet.getCategoryIndex(), packet.getItemIndex(), packet.getQuantity());
            case ShopPacket.SELL_HAND -> handleSellHand(player);
            case ShopPacket.SELL_GUI_ITEMS -> handleSellGuiItems(player, packet.getItems());
            default -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cUnknown packet type."));
        }
    }

    private static void handleClientPacket(ShopPacket packet) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        switch (packet.getType()) {
            case ShopPacket.OPEN_SHOP -> mc.setScreen(new GuiShopCategories());
            case ShopPacket.OPEN_SELL_GUI -> mc.setScreen(new GuiSellGui());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandShop.register(event.getDispatcher());
        CommandSellHand.register(event.getDispatcher());
        CommandSellGui.register(event.getDispatcher());
        CommandBalance.register(event.getDispatcher());
        CommandPay.register(event.getDispatcher());
        LOGGER.info("World Shop commands registered");
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                EconomyData economy = EconomyData.get(serverPlayer.serverLevel());
                economy.registerPlayer(serverPlayer.getScoreboardName(), serverPlayer.getUUID());
            } catch (Exception e) {
                System.err.println("[MODAPP-ERROR] Failed to register player on login: " + e.getMessage());
            }
        }
    }

    public static void buildShopCategories() {
        categories = ShopCategory.buildFromCreativeTabs();
        priceEngine.clearCache();
        LOGGER.info("Built {} shop categories from creative tabs", categories.size());
    }

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }

    private static void handleBuy(ServerPlayer player, int categoryIndex, int itemIndex, int quantity) {
        try {
            List<ShopCategory> cats = getCategories();
            if (categoryIndex < 0 || categoryIndex >= cats.size()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cInvalid category."));
                System.err.println("[MODAPP-ERROR] Buy: invalid category index " + categoryIndex);
                return;
            }
            ShopCategory category = cats.get(categoryIndex);
            List<net.minecraft.world.item.ItemStack> items = category.getItems();
            if (itemIndex < 0 || itemIndex >= items.size()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cInvalid item."));
                System.err.println("[MODAPP-ERROR] Buy: invalid item index " + itemIndex + " in category " + categoryIndex);
                return;
            }
            net.minecraft.world.item.ItemStack itemStack = items.get(itemIndex);
            RecipeManager recipeManager = player.serverLevel().getRecipeManager();
            RegistryAccess registryAccess = player.serverLevel().registryAccess();
            double pricePerItem = priceEngine.getBuyPrice(itemStack, recipeManager, registryAccess);
            double totalCost = pricePerItem * (double) quantity;
            EconomyData economy = EconomyData.get(player.serverLevel());
            java.util.UUID uuid = player.getUUID();

            if (!economy.subtractBalance(uuid, totalCost)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cYou need $" + String.format("%.2f", totalCost) + " but have $" + String.format("%.2f", economy.getBalance(uuid)) + "."));
                return;
            }

            int maxStackSize = itemStack.getMaxStackSize();
            for (int remaining = quantity; remaining > 0; remaining -= maxStackSize) {
                int stackSize = Math.min(remaining, maxStackSize);
                net.minecraft.world.item.ItemStack toGive = itemStack.copy();
                toGive.setCount(stackSize);
                if (!player.getInventory().add(toGive)) {
                    player.drop(toGive, false);
                }
            }

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7aBought " + quantity + "x " + itemStack.getHoverName().getString() + " for $" + String.format("%.2f", totalCost) + "!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(uuid))));
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling buy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleSellHand(ServerPlayer player) {
        try {
            net.minecraft.world.item.ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cYou are not holding any item."));
                return;
            }

            int totalSold = 0;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                net.minecraft.world.item.ItemStack slot = player.getInventory().items.get(i);
                if (slot.isEmpty() || slot.getItem() != held.getItem()) continue;
                totalSold += slot.getCount();
                player.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            }

            if (totalSold == 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cNo items found to sell."));
                return;
            }

            RecipeManager recipeManager = player.serverLevel().getRecipeManager();
            RegistryAccess registryAccess = player.serverLevel().registryAccess();
            double sellPricePerItem = priceEngine.getSellPrice(held, recipeManager, registryAccess);
            double totalEarnings = sellPricePerItem * (double) totalSold;
            EconomyData economy = EconomyData.get(player.serverLevel());
            economy.addBalance(player.getUUID(), totalEarnings);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7aSold " + totalSold + "x " + held.getHoverName().getString() + " for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
            player.getInventory().setChanged();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell hand: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleSellGuiItems(ServerPlayer player, List<net.minecraft.world.item.ItemStack> items) {
        try {
            if (items.isEmpty()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cNo items to sell."));
                return;
            }

            RecipeManager recipeManager = player.serverLevel().getRecipeManager();
            RegistryAccess registryAccess = player.serverLevel().registryAccess();
            double totalEarnings = 0.0;
            int totalSold = 0;

            for (net.minecraft.world.item.ItemStack sellStack : items) {
                if (sellStack == null || sellStack.isEmpty()) continue;
                double sellPrice = priceEngine.getSellPrice(sellStack, recipeManager, registryAccess);
                totalEarnings += sellPrice * (double) sellStack.getCount();
                totalSold += sellStack.getCount();
            }

            if (totalSold == 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cNo items to sell."));
                return;
            }

            // Remove items from inventory
            for (net.minecraft.world.item.ItemStack sellStack : items) {
                if (sellStack == null || sellStack.isEmpty()) continue;
                int remaining = sellStack.getCount();
                for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
                    net.minecraft.world.item.ItemStack invStack = player.getInventory().items.get(i);
                    if (invStack.isEmpty() || invStack.getItem() != sellStack.getItem()) continue;
                    int toRemove = Math.min(remaining, invStack.getCount());
                    invStack.shrink(toRemove);
                    remaining -= toRemove;
                    if (invStack.isEmpty()) {
                        player.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                }
            }

            EconomyData economy = EconomyData.get(player.serverLevel());
            economy.addBalance(player.getUUID(), totalEarnings);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7aSold " + totalSold + " items for $" + String.format("%.2f", totalEarnings) + "!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a77Balance: $" + String.format("%.2f", economy.getBalance(player.getUUID()))));
            player.getInventory().setChanged();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Error handling sell GUI items: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
