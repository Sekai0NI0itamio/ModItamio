package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import asd.itamio.ModInfoPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

@Mod(WorldShop.MOD_ID)
public class WorldShop {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "World Shop";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();

    public WorldShop(IEventBus modEventBus) {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("World Shop mod initializing...");

        // Register payload handlers (networking)
        modEventBus.addListener(this::onRegisterPayloadHandlers);

        // Register event bus handlers
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        // Common setup
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        buildShopCategories();
        LOGGER.info("World Shop initialized with " + categories.size() + " categories");
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Server-bound: shop actions (buy, sell, etc.)
        registrar.playToServer(
                ShopPacket.TYPE,
                ShopPacket.STREAM_CODEC,
                new ShopPacketHandler()
        );
        // Client-bound: open GUIs
        registrar.playToClient(
                ShopPacket.TYPE,
                ShopPacket.STREAM_CODEC,
                new ShopClientPacketHandler()
        );
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        CommandShop.register(dispatcher);
        CommandSellHand.register(dispatcher);
        CommandSellGui.register(dispatcher);
        CommandBalance.register(dispatcher);
        CommandPay.register(dispatcher);
        CommandBalance.registerAlias(dispatcher);
        LOGGER.info("World Shop commands registered");
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EconomyData economy = EconomyData.get(player.serverLevel());
            economy.registerPlayer(player.getGameProfile().getName(), player.getUUID());
            LOGGER.info("Registered player {} -> {}", player.getGameProfile().getName(), player.getUUID());
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("World Shop server starting");
    }

    private void buildShopCategories() {
        categories = ShopCategory.buildFromCreativeTabs();
        priceEngine.clearCache();
        LOGGER.info("Built " + categories.size() + " shop categories from creative tabs");
    }

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }
}
