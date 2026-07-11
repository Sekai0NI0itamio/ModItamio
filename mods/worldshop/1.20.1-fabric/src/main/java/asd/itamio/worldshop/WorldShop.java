package asd.itamio.worldshop;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class WorldShop implements ModInitializer {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "World Shop";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();

    @Override
    public void onInitialize() {
        ModInfoPrinter.print(new ModInfoPrinter.LogLine() {
            @Override
            public void log(String msg) {
                LOGGER.info(msg);
            }
        }, MOD_NAME, VERSION);
        LOGGER.info("World Shop mod initializing...");

        // Register server packet handler
        ServerPlayNetworking.registerGlobalReceiver(ShopPacket.PACKET_ID, new ServerPlayNetworking.PlayChannelHandler() {
            @Override
            public void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, net.fabricmc.fabric.api.networking.v1.PacketSender responseSender) {
                ShopPacket packet = ShopPacket.read(buf);
                server.execute(new Runnable() {
                    @Override
                    public void run() {
                        ServerPacketHandler.handle(packet, player);
                    }
                });
            }
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register(new CommandRegistrationCallback() {
            @Override
            public void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext registryAccess, net.minecraft.commands.Commands.CommandSelection environment) {
                ModCommands.register(dispatcher);
            }
        });

        // Register player login handler
        ServerPlayConnectionEvents.JOIN.register(new ServerPlayConnectionEvents.Join() {
            @Override
            public void onPlayReady(ServerGamePacketListenerImpl handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftServer server) {
                ServerPlayer player = handler.getPlayer();
                EconomyData economy = EconomyData.get(player.serverLevel());
                economy.registerPlayer(player.getScoreboardName(), player.getUUID());
                LOGGER.info("Registered player {} -> {}", player.getScoreboardName(), player.getUUID());
            }
        });

        buildShopCategories();
        LOGGER.info("World Shop initialized with {} categories", categories.size());
    }

    private void buildShopCategories() {
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
}
