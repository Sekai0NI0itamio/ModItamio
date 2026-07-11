package asd.itamio.worldshop;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.entity.player.EntityPlayerMP;
import asd.itamio.ModInfoPrinter;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

@Mod(modid = WorldShop.MOD_ID, name = WorldShop.MOD_NAME, version = WorldShop.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class WorldShop {
    public static final String MOD_ID = "worldshop";
    public static final String MOD_NAME = "World Shop";
    public static final String VERSION = "1.0.0";

    @Instance(MOD_ID)
    public static WorldShop instance;

    public static Logger LOGGER;
    public static SimpleNetworkWrapper NETWORK;

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("World Shop mod initializing...");
        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);
        NETWORK.registerMessage(ShopPacketHandler.class, ShopPacket.class, 0, Side.SERVER);
        NETWORK.registerMessage(ShopClientPacketHandler.class, ShopPacket.class, 1, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        buildShopCategories();
        LOGGER.info("World Shop initialized with " + categories.size() + " categories");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandShop());
        event.registerServerCommand(new CommandSellHand());
        event.registerServerCommand(new CommandSellGui());
        event.registerServerCommand(new CommandBalance());
        event.registerServerCommand(new CommandPay());
        // Register /bal alias as a separate command instance
        event.registerServerCommand(new CommandBalance() {
            @Override
            public String getName() {
                return "bal";
            }
        });
        buildShopCategories();
        LOGGER.info("World Shop commands registered");
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            EconomyData economy = EconomyData.get(player.getEntityWorld());
            economy.registerPlayer(player.getName(), player.getUniqueID());
            LOGGER.info("Registered player {} -> {}", player.getName(), player.getUniqueID());
        }
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
